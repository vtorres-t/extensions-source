import gzip
import html
import json
import sys
import asyncio
import aiohttp
from pathlib import Path

from google.protobuf import json_format

import index_pb2

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

to_delete: list[str] = json.loads(sys.argv[1])

# Limpieza de archivos eliminados
for module in to_delete:
    for file in REPO_APK_DIR.glob(f"tachiyomi-{module}-v*.*.*.apk"):
        print(f"removing {file.name}")
        file.unlink(missing_ok=True)
    for file in REPO_JAR_DIR.glob(f"tachiyomi-{module}-v*.*.*.jar"):
        print(f"removing {file.name}")
        file.unlink(missing_ok=True)

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


# ========================================================
# COMPROBACIÓN ASÍNCRONA ULTRA RÁPIDA (EN PARALELO)
# ========================================================

async def verificar_url(session, url: str) -> bool:
    """Realiza una petición HEAD asíncrona a la URL con un timeout estricto."""
    if not url:
        return False
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    try:
        # Timeout total de 4 segundos para evitar atascos
        timeout = aiohttp.ClientTimeout(total=4)
        async with session.head(url, headers=headers, timeout=timeout, allow_redirects=True) as response:
            return response.status < 400
    except Exception:
        return False

async def filtrar_extensiones_validas(extensiones_info: list) -> set[str]:
    """Comprueba todas las URLs del repositorio en paralelo."""
    urls_a_comprobar = []
    mapeo_url_paquete = []

    # Extraemos todas las URLs únicas para no repetir peticiones innecesarias
    for info, _ in extensiones_info:
        for source in info.get("sources", []):
            url = source.get("baseUrl")
            if url:
                urls_a_comprobar.append(url)
                mapeo_url_paquete.append((url, info["packageName"]))

    # Si no hay URLs que comprobar, todas son válidas
    if not urls_a_comprobar:
        return {info["packageName"] for info, _ in extensiones_info}

    # Lanzamos todas las peticiones HTTP al mismo tiempo
    async with aiohttp.ClientSession() as session:
        tareas = [verificar_url(session, url) for url in urls_a_comprobar]
        resultados = await asyncio.gather(*tareas)

    # Identificamos qué paquetes tienen al menos una fuente activa
    paquetes_activos = set()
    for (url, paquete), esta_activa in zip(mapeo_url_paquete, resultados):
        if esta_activa:
            paquetes_activos.add(paquete)
        else:
            print(f"  -> Fuente caída detectada: {url}")

    return paquetes_activos

# ========================================================

def main():
    new_extensions: list[index_pb2.Extension] = []
    extensiones_cargadas = []

    # 1. Leer los archivos JSON de los artefactos compilados
    for info_file in ARTIFACTS_DIR.glob("**/keiyoushi-source-info.json"):
        with info_file.open(encoding="utf-8") as f:
            info = json.load(f)
        extensiones_cargadas.append((info, info_file))

    # 2. Ejecutar el filtro masivo en paralelo de forma asíncrona
    print("--- Iniciando verificación masiva y paralela de fuentes ---")
    paquetes_validos = asyncio.run(filtrar_extensiones_validas(extensiones_cargadas))
    print("--- Verificación finalizada ---\n")

    # 3. Procesar solo las extensiones cuyas fuentes respondieron correctamente
    for info, info_file in extensiones_cargadas:
        package_name = info["packageName"]

        if package_name not in paquetes_validos and info.get("sources"):
            print(f"❌ Omitiendo extensión (Todas sus fuentes están caídas): {info['name']}")
            continue

        apk = next((info_file.parent / "outputs/apk/release").glob("*.apk"), None)
        jar = next((info_file.parent / "outputs/jar/release").glob("*.jar"), None)

        if apk is None or jar is None:
            continue

        # Copiar binarios válidos al repositorio
        (REPO_APK_DIR / apk.name).write_bytes(apk.read_bytes())
        (REPO_JAR_DIR / jar.name).write_bytes(jar.read_bytes())

        new_extensions.append(
            index_pb2.Extension(
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
        )

    # --- Generación final de índices (JSON, PB, HTML) ---
    with REPO_DIR.joinpath("index.json").open() as f:
        remote_proto = json_format.Parse(f.read(), index_pb2.Index())

    all_extensions = [
        ext
        for ext in remote_proto.extensionList.extensions
        if not any(ext.packageName.endswith(f".{module}") for module in to_delete)
    ]
    all_extensions.extend(new_extensions)
    all_extensions.sort(key=lambda ext: ext.packageName)

    index = index_pb2.Index(
        name="Keiyoushi-vt",
        badgeLabel="VT",
        signingKey="DE0FDC4BC621BC9F68495CB030F4F23421D3257BA9A6DEBF3295C4076841C77B",
        contact=index_pb2.Contact(
            website="https://github.io", discord="https://discord.gg"
        ),
        extensionList=index_pb2.ExtensionList(extensions=all_extensions),
    )

    json_data = json_format.MessageToJson(index, always_print_fields_with_no_presence=False, preserving_proto_field_name=True)
    with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
        f.write(json_data)

    objeto_json = json.loads(json_data)
    json_minificado = json.dumps(objeto_json, separators=(',', ':'))
    with REPO_DIR.joinpath("index.min.json").open("w", encoding="utf-8") as f:
        f.write(json_minificado)

    with REPO_DIR.joinpath("index.pb").open("wb") as f:
        f.write(gzip.compress(index.SerializeToString(deterministic=True)))

    with REPO_DIR.joinpath("index.html").open("w", encoding="utf-8") as f:
        f.write('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n')
        for ext in all_extensions:
            apk_escaped = html.escape(ext.resources.apkUrl)
            name_escaped = html.escape(f"Tachiyomi: {ext.name}")
            f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
        f.write("</pre>\n</body>\n</html>\n")

if __name__ == '__main__':
    main()
