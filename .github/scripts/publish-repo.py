import gzip
import hashlib
import html
import json
import math
import subprocess
import sys
import time
import asyncio
import aiohttp
from pathlib import Path

import index_pb2
from google.protobuf import json_format

# --- Configuración de configuracion para verificacion HTTP ---
MAX_CONEXIONES_SIMULTANEAS = 25  # Número máximo de peticiones paralelas
HTTP_TIMEOUT_SEGUNDOS = 4       # Segundos de espera máxima por URL
URL_IGNORE_CONFIG = Path(".github/.urlignore")

# --- Configuración de directorios de artefactos ---
ARTIFACTS_DIR = Path.home() / "apk-artifacts"
REPO_DIR = Path.cwd()
REPO_APK_DIR = REPO_DIR / "apk"
REPO_JAR_DIR = REPO_DIR / "jar"
REPO_APK_DIR.mkdir(parents=True, exist_ok=True)
REPO_JAR_DIR.mkdir(parents=True, exist_ok=True)

APK_BASE_URL = "https://cdn.jsdelivr.net/gh/vtorres-t/ext@repo/apk"
JAR_BASE_URL = "https://raw.githubusercontent.com/vtorres-t/ext/repo/jar"
ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/vtorres-t/extensions-source@main"
REPO_NAME = "vtorres-t/ext"
RELEASE_BASE_URL = f"https://github.com/{REPO_NAME}/releases/download"
ASSET_LIMIT = 495  # Actual limit is 1000 but we upload 2 items per extension.
RETRY_ATTEMPTS = 4
RETRY_BASE_DELAY = 60  # Documented minimum wait; doubles per attempt.
# Uploading continuously trips a secondary rate limit after roughly 450 assets, and
# that limit clears again within a few minutes. Pace the uploads well under the rate
# that trips it. Small calls also cap how much completed work gh re-sends on retry,
# since it re-uploads the whole call.
UPLOAD_CHUNK_SIZE = 80
UPLOAD_CHUNK_INTERVAL = 30

to_delete: list[str] = json.loads(sys.argv[1])
current_sha = sys.argv[2]
current_sha_short = current_sha[:7]

beta_index_path = REPO_DIR / "index.beta.json"
if beta_index_path.exists():
    with beta_index_path.open() as f:
        remote_release_proto = json_format.Parse(f.read(), index_pb2.Index())
else:
    remote_release_proto = index_pb2.Index()

remote_release_extensions = {
    ext.packageName: ext for ext in remote_release_proto.extensionList.extensions
}

release_assets_path = REPO_DIR / "release-assets.json"
if release_assets_path.exists():
    with release_assets_path.open() as f:
        release_assets = json.load(f)
else:
    release_assets = {}

updated_release_assets = {
    package_name: assets
    for package_name, assets in release_assets.items()
    if not any(package_name.endswith(f".{module}") for module in to_delete)
}

SOURCE_DIR = Path(__file__).resolve().parents[2]
ICON_FILE = "res/mipmap-xhdpi/ic_launcher.png"

def get_icon_url(module: str, theme: str | None) -> str:
    module_icon = f"src/{module.replace('.', '/')}/{ICON_FILE}"
    if (SOURCE_DIR / module_icon).exists():
        return f"{ICON_BASE_URL}/{module_icon}"
    if theme:
        theme_icon = f"lib-multisrc/{theme}/{ICON_FILE}"
        if (SOURCE_DIR / theme_icon).exists():
            return f"{ICON_BASE_URL}/{theme_icon}"
    return f"{ICON_BASE_URL}/core/src/main/{ICON_FILE}"

def cargar_excepciones_url() -> list[str]:
    """Lee el archivo .urlignore omitiendo comentarios y líneas vacías."""
    excepciones = []
    if URL_IGNORE_CONFIG.is_file():
        try:
            with URL_IGNORE_CONFIG.open("r", encoding="utf-8") as f:
                for linea in f:
                    linea_limpia = linea.strip()
                    if linea_limpia and not linea_limpia.startswith("#"):
                        excepciones.append(linea_limpia)
            print(f"ℹ️ Cargadas {len(excepciones)} reglas de excepción desde {URL_IGNORE_CONFIG}")
        except Exception as e:
            print(f"⚠️ Error al leer el archivo de excepciones: {e}")
    else:
        print(f"ℹ️ No se encontró el archivo de excepciones {URL_IGNORE_CONFIG}. Se procederá sin excepciones.")
    return excepciones

# Cargamos las excepciones globalmente al arrancar
EXCEPCIONES_SIEMPRE_ACTIVAS = cargar_excepciones_url()

# ========================================================
# COMPROBACIÓN ASÍNCRONA ULTRA RÁPIDA (EN PARALELO)
# ========================================================

async def verificar_url(session, url: str) -> bool:
    """
    Realiza una petición HEAD asíncrona a la URL.
    Omite bloqueos de Cloudflare (403/503 con cabecera Cloudflare) y los da por válidos.
    """
    if not url:
        return False

    # Comprobación de excepciones por coincidencia en la URL
    if any(excepcion in url for excepcion in EXCEPCIONES_SIEMPRE_ACTIVAS):
            print(f"  -> [Excepción por URL] Forzando inclusión de: {url}")
            return True

    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
    try:
        timeout = aiohttp.ClientTimeout(total=HTTP_TIMEOUT_SEGUNDOS)
        async with session.head(url, headers=headers, timeout=timeout, allow_redirects=True) as response:
            # 1. Si responde con éxito estándar (Menor a 400)
            if response.status < 400:
                return True

            # 2. Si es un código típico de bloqueo Cloudflare (403 o 503)
            if response.status in (403, 503):
                # Comprobamos si la cabecera del servidor confirma que es Cloudflare
                server_header = response.headers.get("Server", "").lower()
                if "cloudflare" in server_header:
                    print(f"  -> [Cloudflare detectado] Omitiendo bloqueo {response.status} para: {url}")
                    return True # Damos la fuente por activa para no eliminarla

            return False
    except Exception as e:
        # En caso de errores de conexión críticos (DNS inválido o Servidor apagado), se mantiene como caído
        return False

async def filtrar_extensiones_validas(extensiones_info: list) -> set[str]:
    """Comprueba todas las URLs del repositorio en paralelo utilizando un semáforo."""
    urls_a_comprobar = []
    mapeo_url_paquete = []

    for info, _ in extensiones_info:
        # Si la extensión no declara fuentes, se aprueba automáticamente por seguridad
        if not info.get("sources"):
            continue

        for source in info.get("sources", []):
            url = source.get("baseUrl")
            if url:
                urls_a_comprobar.append(url)
                mapeo_url_paquete.append((url, info["packageName"]))

    # Crear conjunto inicial con todos los paquetes leídos
    paquetes_con_fuentes = {info["packageName"] for info, _ in extensiones_info if info.get("sources")}
    paquetes_sin_fuentes = {info["packageName"] for info, _ in extensiones_info if not info.get("sources")}

    if not urls_a_comprobar:
        return paquetes_sin_fuentes

    sem = asyncio.Semaphore(MAX_CONEXIONES_SIMULTANEAS)

    async def verificar_con_semaforo(session, url):
        async with sem:
            return await verificar_url(session, url)

    async with aiohttp.ClientSession() as session:
        tareas = [verificar_con_semaforo(session, url) for url in urls_a_comprobar]
        resultados = await asyncio.gather(*tareas)

    # Agrupamos los resultados para saber qué paquetes sobrevivieron
    paquetes_activos = set(paquetes_sin_fuentes) # Las que no tienen fuentes pasan directo

    for (url, paquete), esta_activa in zip(mapeo_url_paquete, resultados):
        if esta_activa:
            paquetes_activos.add(paquete)

    # Opcional: Imprimir en consola las que se van a tumbar definitivamente
    for paquete in paquetes_con_fuentes:
        if paquete not in paquetes_activos:
            print(f"  -> Fuente caída confirmada (No es Cloudflare ni responde): {paquete}")

    return paquetes_activos


# ========================================================

def get_release_tag(batch_index: int, release_count: int) -> str:
    return (
        f"{current_sha_short}-{batch_index}" if release_count > 1 else current_sha_short
    )

def create_index(
    name: str,
    badge_label: str,
    extensions: list[index_pb2.Extension],
) -> index_pb2.Index:
    return index_pb2.Index(
        name=name,
        badgeLabel=badge_label,
        signingKey="DE0FDC4BC621BC9F68495CB030F4F23421D3257BA9A6DEBF3295C4076841C77B",
        contact=index_pb2.Contact(
            website="",
            discord="",
        ),
        extensionList=index_pb2.ExtensionList(extensions=extensions),
    )


def write_index(filename: str, index: index_pb2.Index):
    with REPO_DIR.joinpath(f"{filename}.json").open("w", encoding="utf-8") as f:
        f.write(
            json_format.MessageToJson(
                index,
                always_print_fields_with_no_presence=False,
                preserving_proto_field_name=True,
            )
        )

    with REPO_DIR.joinpath(f"{filename}.pb").open("wb") as f:
        f.write(gzip.compress(index.SerializeToString(deterministic=True)))

def run_gh(*args: str, success_codes: tuple[int, ...] = ()) -> str:
    delay = RETRY_BASE_DELAY
    for attempt in range(1, RETRY_ATTEMPTS + 1):
        result = subprocess.run(
            ["gh", *args],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode == 0 or result.returncode in success_codes:
            return result.stdout.strip()

        # Secondary rate limits are explicitly retryable after a wait; everything else
        # is a real failure, so fail fast. The upload endpoint sends no retry-after
        # header, so back off from the documented one minute minimum.
        # https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
        if attempt < RETRY_ATTEMPTS and "secondary rate limit" in result.stderr.lower():
            print(
                f"secondary rate limit hit, retrying in {delay}s "
                f"(attempt {attempt}/{RETRY_ATTEMPTS})",
                file=sys.stderr,
            )
            time.sleep(delay)
            delay *= 2
            continue

        print(f"gh {' '.join(args)} failed: {result.stderr}", file=sys.stderr)
        sys.exit(result.returncode)

def create_release(tag: str):
    if run_gh(
        "release",
        "view",
        tag,
        "--repo",
        REPO_NAME,
        "--json",
        "tagName",
        success_codes=(1,),
    ):
        print(f"Release {tag} already exists")
        return

    print(f"Creating release {tag}")
    run_gh(
        "release",
        "create",
        tag,
        "--repo",
        REPO_NAME,
        "--draft",
        "--title",
        f"Repository Update {tag}",
        "--notes",
        f"Automated update from keiyoushi/extensions-source@{current_sha}",
    )


def publish_release(tag: str):
    print(f"Publishing release {tag}")
    run_gh("release", "edit", tag, "--repo", REPO_NAME, "--draft=false")


def upload_assets(tag: str, files: list[Path]):
    if not files:
        return
    print(f"Uploading {len(files)} assets to {tag}")
    for i in range(0, len(files), UPLOAD_CHUNK_SIZE):
        chunk = files[i : i + UPLOAD_CHUNK_SIZE]
        if i:
            time.sleep(UPLOAD_CHUNK_INTERVAL)
        print(f"  assets {i + 1}-{i + len(chunk)} of {len(files)}")
        run_gh(
            "release",
            "upload",
            tag,
            *[str(f) for f in chunk],
            "--repo",
            REPO_NAME,
            "--clobber",
        )
    publish_release(tag)

def main():
    new_extensions: list[tuple[index_pb2.Extension, Path, Path, bool]] = []
    published_files: set[Path] = set()
    extensiones_cargadas = []

    for info_file in ARTIFACTS_DIR.glob("**/keiyoushi-source-info.json"):
            with info_file.open(encoding="utf-8") as f:
                info = json.load(f)
            extensiones_cargadas.append((info, info_file))

    print("--- Iniciando verificación masiva y paralela de fuentes ---")
    paquetes_validos = asyncio.run(filtrar_extensiones_validas(extensiones_cargadas))
    print("--- Verificación finalizada ---\n")

    for info, info_file in extensiones_cargadas:
        package_name = info["packageName"]

        if package_name not in paquetes_validos and info.get("sources"):
            print(f"❌ Omitiendo extensión (Todas sus fuentes están caídas): {info['name']}")
            continue

        apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
        if apk is None:
            raise FileNotFoundError(
                f"{package_name}: no release apk found under {info_file.parent}"
            )

        jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)
        if jar is None:
            raise FileNotFoundError(
                f"{package_name}: no release jar found under {info_file.parent}"
            )

        apk_bytes = apk.read_bytes()
        jar_bytes = jar.read_bytes()
        repo_apk = REPO_APK_DIR / apk.name
        repo_jar = REPO_JAR_DIR / jar.name

        assets = {
            "apk": {
                "name": apk.name,
                "sha256": hashlib.sha256(apk_bytes).hexdigest(),
            },
            "jar": {
                "name": jar.name,
                "sha256": hashlib.sha256(jar_bytes).hexdigest(),
            },
        }
        changed = (
            package_name not in remote_release_extensions
            or release_assets.get(package_name) != assets
        )

        repo_apk.write_bytes(apk_bytes)
        repo_jar.write_bytes(jar_bytes)
        published_files.update((repo_apk, repo_jar))
        updated_release_assets[package_name] = assets

        ext = index_pb2.Extension(
            name=info["name"],
            packageName=package_name,
            resources=index_pb2.Resources(
                apkUrl=f"{APK_BASE_URL}/{apk.name}",
                jarUrl=f"{JAR_BASE_URL}/{jar.name}",
                iconUrl=get_icon_url(info["module"], info.get("theme")),
            ),
            extensionLib=info["extensionLib"],
            versionCode=info["versionCode"],
            versionName=info["versionName"],
            contentWarning=info["contentWarning"],
            sources=[
                index_pb2.Source(
                    id=int(source["id"]),
                    name=source["name"],
                    language=source["lang"],
                    homeUrl=source["baseUrl"],
                    mirrorUrls=source.get("mirrorUrls", []),
                )
                for source in info["sources"]
            ],
        )
        new_extensions.append((ext, apk, jar, changed))

    new_extensions.sort(key=lambda item: item[0].packageName)

    total_extensions = len(new_extensions)
    release_count = math.ceil(total_extensions / ASSET_LIMIT) if total_extensions else 0
    ext_per_release = math.ceil(total_extensions / release_count) if release_count else 0

    # Drop stale repo assets for modules that were deleted or rebuilt. Current artifacts are retained;
    # release upload checks use the checksum manifest above and do not depend on these repo copies.
    for module in to_delete:
        for file in REPO_APK_DIR.glob(f"tachiyomi-{module}-v*.*.*.apk"):
            if file not in published_files:
                print(f"removing {file.name}")
                file.unlink(missing_ok=True)
        for file in REPO_JAR_DIR.glob(f"tachiyomi-{module}-v*.*.*.jar"):
            if file not in published_files:
                print(f"removing {file.name}")
                file.unlink(missing_ok=True)

    # Merge with the already-published index, dropping the deleted/rebuilt modules.
    with REPO_DIR.joinpath("index.json").open() as f:
        remote_proto = json_format.Parse(f.read(), index_pb2.Index())

    all_extensions = [
        ext
        for ext in remote_proto.extensionList.extensions
        if not any(ext.packageName.endswith(f".{module}") for module in to_delete)
    ]
    all_extensions.extend([i[0] for i in new_extensions])
    all_extensions.sort(key=lambda ext: ext.packageName)

    new_release_extensions = {}
    for i, (ext, apk, jar, changed) in enumerate(new_extensions):
        release_ext = index_pb2.Extension()
        release_ext.CopyFrom(ext)

        if changed:
            tag = get_release_tag(i // ext_per_release, release_count)
            release_ext.resources.apkUrl = f"{RELEASE_BASE_URL}/{tag}/{apk.name}"
            release_ext.resources.jarUrl = f"{RELEASE_BASE_URL}/{tag}/{jar.name}"
        else:
            old_resources = remote_release_extensions[ext.packageName].resources
            release_ext.resources.apkUrl = old_resources.apkUrl
            release_ext.resources.jarUrl = old_resources.jarUrl

        new_release_extensions[ext.packageName] = release_ext

    all_release_extensions = []
    for ext in all_extensions:
        if ext.packageName in new_release_extensions:
            all_release_extensions.append(new_release_extensions[ext.packageName])
            continue

        if ext.packageName not in remote_release_extensions:
            raise ValueError(f"{ext.packageName}: no GitHub release asset mapping found")

        release_ext = index_pb2.Extension()
        release_ext.CopyFrom(ext)
        old_resources = remote_release_extensions[ext.packageName].resources
        release_ext.resources.apkUrl = old_resources.apkUrl
        release_ext.resources.jarUrl = old_resources.jarUrl
        all_release_extensions.append(release_ext)

    index = create_index("Keiyoushi-vt", "VT", all_extensions)
    release_index = create_index("Keiyoushi-vt (Beta)", "VT β", all_release_extensions)
    write_index("index", index)
    write_index("index.beta", release_index)

    with release_assets_path.open("w", encoding="utf-8") as f:
        json.dump(updated_release_assets, f, indent=2, sort_keys=True)
        f.write("\n")

    with REPO_DIR.joinpath("index.html").open("w", encoding="utf-8") as f:
        f.write(
            '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n'
        )
        for ext in all_extensions:
            apk_escaped = html.escape(ext.resources.apkUrl)
            name_escaped = html.escape(f"Tachiyomi: {ext.name}")
            f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
        f.write("</pre>\n</body>\n</html>\n")

    # --- Upload assets as release ---
    if not new_extensions:
        sys.exit(0)


    for i in range(0, total_extensions, ext_per_release):
        batch = new_extensions[i : i + ext_per_release]
        tag = get_release_tag(i // ext_per_release)
        files_to_upload = []
        for ext, apk, jar, changed in batch:
            if changed:
                files_to_upload.extend([apk, jar])

        if not files_to_upload:
            print(f"Nothing changed for {tag}, skipping release")
            continue

        create_release(tag)
        upload_assets(tag, files_to_upload)

if __name__ == '__main__':
    main()
