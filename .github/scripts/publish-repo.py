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
from check_urls import verificar_url, filtrar_extensiones_validas, MAX_CONEXIONES_SIMULTANEAS
from github_utils import create_release, upload_assets, REPO_NAME

import index_pb2
from google.protobuf import json_format

# --- Configuración de directorios de artefactos ---
ARTIFACTS_DIR = Path.home() / "apk-artifacts"

# The checked-out `repo` branch we publish into (the working directory).
REPO_DIR = Path.cwd()

ICON_BASE_URL = "https://cdn.jsdelivr.net/gh/vtorres-t/extensions-source@main"
RELEASE_BASE_URL = f"https://github.com/{REPO_NAME}/releases/download"
ASSET_LIMIT = 495  # Actual limit is 1000 but we upload 2 items per extension.

to_delete: list[str] = json.loads(sys.argv[1])
current_sha = sys.argv[2]
current_sha_short = current_sha[:7]

with REPO_DIR.joinpath("index.json").open() as f:
    remote_proto = json_format.Parse(f.read(), index_pb2.Index())

remote_extensions = {
    ext.packageName: ext for ext in remote_proto.extensionList.extensions
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

def get_release_tag(batch_index: int, release_count: int) -> str:
    return (
        f"{current_sha_short}-{batch_index}" if release_count > 1 else current_sha_short
    )


def main():
    new_extensions: list[tuple[index_pb2.Extension, Path, Path, bool, bool]] = []
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

        assets = {
            "apk": {
                "name": apk.name,
                "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            },
            "jar": {
                "name": jar.name,
                "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            },
        }
        old_assets = release_assets.get(package_name, {})
        apk_changed = (
            package_name not in remote_extensions
            or old_assets.get("apk") != assets["apk"]
        )
        jar_changed = (
            package_name not in remote_extensions
            or old_assets.get("jar") != assets["jar"]
        )

        updated_release_assets[package_name] = assets

        ext = index_pb2.Extension(
            name=info["name"],
            packageName=package_name,
            resources=index_pb2.Resources(
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
        new_extensions.append((ext, apk, jar, apk_changed, jar_changed))

    new_extensions.sort(key=lambda item: item[0].packageName)

    changed_extensions = [item for item in new_extensions if item[3] or item[4]]
    total_changed_extensions = len(changed_extensions)
    release_count = (
        math.ceil(total_changed_extensions / ASSET_LIMIT)
        if total_changed_extensions
        else 0
    )
    ext_per_release = (
        math.ceil(total_changed_extensions / release_count) if release_count else 0
    )

    changed_index = 0
    for ext, apk, jar, apk_changed, jar_changed in new_extensions:
        if apk_changed or jar_changed:
            tag = get_release_tag(changed_index // ext_per_release, release_count)
            old_resources = remote_extensions.get(ext.packageName)
            ext.resources.apkUrl = (
                f"{RELEASE_BASE_URL}/{tag}/{apk.name}"
                if apk_changed
                else old_resources.resources.apkUrl
            )
            ext.resources.jarUrl = (
                f"{RELEASE_BASE_URL}/{tag}/{jar.name}"
                if jar_changed
                else old_resources.resources.jarUrl
            )
            changed_index += 1
        else:
            old_resources = remote_extensions[ext.packageName].resources
            ext.resources.apkUrl = old_resources.apkUrl
            ext.resources.jarUrl = old_resources.jarUrl

    # Merge with the already-published index, dropping the deleted/rebuilt modules.
    final_extensions = []
    final_extensions.extend(
        ext
        for ext in remote_proto.extensionList.extensions
        if not any(ext.packageName.endswith(f".{module}") for module in to_delete)
    )
    final_extensions.extend(ext for ext, _, _, _, _ in new_extensions)
    final_extensions.sort(key=lambda ext: ext.packageName)

    index = index_pb2.Index(
       name="Keiyoushi-vt",
       badgeLabel="VT",
       signingKey="DE0FDC4BC621BC9F68495CB030F4F23421D3257BA9A6DEBF3295C4076841C77B",
       contact=index_pb2.Contact(
           website="https://keiyoushi.github.io",
       ),
       extensionList=index_pb2.ExtensionList(extensions=final_extensions),
    )

    with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
       f.write(
           json_format.MessageToJson(
               index,
               always_print_fields_with_no_presence=False,
               preserving_proto_field_name=True,
           )
       )

    with REPO_DIR.joinpath("index.pb").open("wb") as f:
       f.write(gzip.compress(index.SerializeToString(deterministic=True), mtime=0))

    with release_assets_path.open("w", encoding="utf-8") as f:
       json.dump(updated_release_assets, f, indent=2, sort_keys=True)
       f.write("\n")

    with REPO_DIR.joinpath("index.html").open("w", encoding="utf-8") as f:
       f.write(
           '<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>apks</title>\n</head>\n<body>\n<pre>\n'
       )
       for ext in final_extensions:
           apk_escaped = html.escape(ext.resources.apkUrl)
           name_escaped = html.escape(f"Tachiyomi: {ext.name}")
           f.write(f'<a href="{apk_escaped}">{name_escaped}</a>\n')
       f.write("</pre>\n</body>\n</html>\n")

    # --- Upload assets as release ---
    if not changed_extensions:
       sys.exit(0)


    for i in range(0, total_changed_extensions, ext_per_release):
        batch = changed_extensions[i : i + ext_per_release]
        tag = get_release_tag(i // ext_per_release, release_count)
        files_to_upload = [
            file
            for _, apk, jar, apk_changed, jar_changed in batch
            for file, changed in ((apk, apk_changed), (jar, jar_changed))
            if changed
        ]

        create_release(tag)
        upload_assets(tag, files_to_upload)

if __name__ == '__main__':
    main()
