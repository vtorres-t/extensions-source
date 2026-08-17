import asyncio
import itertools
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from check_urls import verificar_url, MAX_CONEXIONES_SIMULTANEAS

EXTENSION_REGEX = re.compile(r"^src/(?P<lang>\w+)/(?P<extension>\w+)")
MULTISRC_LIB_REGEX = re.compile(r"^lib-multisrc/(?P<multisrc>\w+)")
LIB_REGEX = re.compile(r"^lib/(?P<lib>\w+)")
MODULE_REGEX = re.compile(r"^:src:(?P<lang>\w+):(?P<extension>\w+)$")
CORE_FILES_REGEX = re.compile(
    r"^(common/|compiler/|core/|gradle/|build\.gradle\.kts|gradle\.properties|settings\.gradle\.kts|.github/scripts)"
)

def run_command(command: str) -> str:
    result = subprocess.run(command, capture_output=True, text=True, shell=True)
    if result.returncode != 0:
        print(result.stderr.strip())
        sys.exit(result.returncode)
    return result.stdout.strip()


def resolve_dependent_libs(libs: set[str]) -> set[str]:
    """
    returns all libs which depend on any of the passed libs (/lib),
    recursively resolving transitive dependencies
    """
    if not libs:
        return set()

    all_dependent_libs = set()
    to_process = set(libs)

    while to_process:
        current_libs = to_process
        to_process = set()

        lib_dependency = re.compile(
            rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, current_libs))})[\"']\)"
        )

        for lib in Path("lib").iterdir():
            if lib.name in all_dependent_libs or lib.name in libs:
                continue

            build_file = lib / "build.gradle.kts"
            if not build_file.is_file():
                continue

            content = build_file.read_text("utf-8")

            if lib_dependency.search(content):
                all_dependent_libs.add(lib.name)
                to_process.add(lib.name)

    return all_dependent_libs


def resolve_multisrc_lib(libs: set[str]) -> set[str]:
    """
    returns all multisrc which depend on any of the
    passed libs (/lib)
    """
    if not libs:
        return set()

    lib_dependency = re.compile(
        rf"project\([\"']:(?:lib):({'|'.join(map(re.escape, libs))})[\"']\)"
    )

    multisrcs = set()

    for multisrc in Path("lib-multisrc").iterdir():
        build_file = multisrc / "build.gradle.kts"
        if not build_file.is_file():
            continue

        content = build_file.read_text("utf-8")

        if (lib_dependency.search(content)):
            multisrcs.add(multisrc.name)

    return multisrcs

def resolve_ext(multisrcs: set[str], libs: set[str]) -> set[tuple[str, str]]:
    """
    returns all extensions which depend on any of the
    passed multisrcs or libs
    """
    if not multisrcs and not libs:
        return set()

    multisrc_pattern = '|'.join(map(re.escape, multisrcs)) if multisrcs else None
    lib_pattern = '|'.join(map(re.escape, libs)) if libs else None

    patterns = []
    if multisrc_pattern:
        patterns.append(rf"theme\s*=\s*['\"]({multisrc_pattern})['\"]")
    if lib_pattern:
        patterns.append(rf"project\([\"']:(?:lib):({lib_pattern})[\"']\)")

    regex = re.compile('|'.join(patterns))

    extensions = set()

    for lang in Path("src").iterdir():
        for extension in lang.iterdir():
            build_file = extension / "build.gradle.kts"
            if not build_file.is_file():
                continue

            content = build_file.read_text("utf-8")

            if regex.search(content):
                extensions.add((lang.name, extension.name))

    return extensions

def get_module_list(ref: str) -> tuple[list[str], list[str], list[str]]:
    diff_output = run_command(f"git diff --name-status {ref}").splitlines()

    changed_files = [
        file
        for line in diff_output
        for file in line.split("\t", 2)[1:]
    ]

    modules = set()
    multisrcs = set()
    libs = set()
    deleted = set()
    core_files_changed = False

    for file in map(lambda x: Path(x).as_posix(), changed_files):
        if CORE_FILES_REGEX.search(file):
            core_files_changed = True

        elif match := EXTENSION_REGEX.search(file):
            lang = match.group("lang")
            extension = match.group("extension")
            if Path("src", lang, extension).is_dir():
                modules.add(f':src:{lang}:{extension}')
            deleted.add(f"{lang}.{extension}")

        elif match := MULTISRC_LIB_REGEX.search(file):
            multisrc = match.group("multisrc")
            if Path("lib-multisrc", multisrc).is_dir():
                multisrcs.add(multisrc)

        elif match := LIB_REGEX.search(file):
            lib = match.group("lib")
            if Path("lib", lib).is_dir():
                libs.add(lib)

    if core_files_changed:
        (all_modules, all_deleted) = get_all_modules()

        # update existing set so we include deleted extensions
        modules.update(all_modules)
        deleted.update(all_deleted)

        return sorted(modules), sorted(deleted), get_all_lint_modules()

    # Resolve libs that depend on the changed libs (recursively)
    libs.update(
        resolve_dependent_libs(libs)
    )

    # Resolve multisrcs that depend on the changed libs
    multisrcs.update(
        resolve_multisrc_lib(libs)
    )

    # Resolve extensions that depend on the changed multisrcs or libs
    extensions = resolve_ext(multisrcs, libs)
    modules.update([f":src:{lang}:{extension}" for lang, extension in extensions])
    deleted.update([f"{lang}.{extension}" for lang, extension in extensions])

    lint_modules = {
        *(f":lib:{lib}" for lib in libs),
        *(f":lib-multisrc:{multisrc}" for multisrc in multisrcs),
    }

    return sorted(modules), sorted(deleted), sorted(lint_modules)

def get_all_modules() -> tuple[list[str], list[str]]:
    modules = []
    deleted = []
    for lang in Path("src").iterdir():
        for extension in lang.iterdir():
            modules.append(f":src:{lang.name}:{extension.name}")
            deleted.append(f"{lang.name}.{extension.name}")
    return modules, deleted

async def filtrar_modulos_validos(modules: list[str]) -> list[str]:
    """Cruza los módulos con el archivo index para descartar los que tengan URLs caídas."""
    import aiohttp

    index_path = Path("index.beta.json")
    if not index_path.exists():
        index_path = Path("index.json")

    package_urls = {}
    if index_path.exists():
        try:
            with index_path.open(encoding="utf-8") as f:
                data = json.load(f)
                extensions_list = data.get("extensionList", {}).get("extensions", []) if "extensionList" in data else data.get("extensions", [])
                for ext in extensions_list:
                    pkg = ext.get("packageName", "")
                    sources = ext.get("sources", [])
                    urls = [s.get("baseUrl") for s in sources if s.get("baseUrl")]
                    if pkg and urls:
                        package_urls[pkg] = urls
        except Exception as e:
            print(f"⚠️ No se pudo parsear el índice para validar URLs: {e}")

    if not package_urls:
        print("ℹ️ No se encontraron URLs previas en el índice. Se compilarán todos los módulos.")
        return modules

    urls_a_comprobar = []
    mapeo_url_modulo = []
    modulos_sin_url = []

    for mod in modules:
        match = MODULE_REGEX.match(mod)
        if match:
            lang = match.group("lang")
            ext_name = match.group("extension")
            pkg_id = f"tachiyomi.{lang}.{ext_name}"

            if pkg_id in package_urls:
                for url in package_urls[pkg_id]:
                    urls_a_comprobar.append(url)
                    mapeo_url_modulo.append((url, mod))
            else:
                modulos_sin_url.append(mod)
        else:
            modulos_sin_url.append(mod)

    if not urls_a_comprobar:
        return modules

    sem = asyncio.Semaphore(MAX_CONEXIONES_SIMULTANEAS)
    async def verificar_con_semaforo(session, url):
        async with sem:
            return await verificar_url(session, url)

    async with aiohttp.ClientSession() as session:
        tareas = [verificar_con_semaforo(session, url) for url in urls_a_comprobar]
        resultados = await asyncio.gather(*tareas)

    modulos_activos = set(modulos_sin_url)
    for (url, mod), esta_activa in zip(mapeo_url_modulo, resultados):
        if esta_activa:
            modulos_activos.add(mod)
        else:
            print(f"🛑 [URL CAÍDA]: Saltando generación de {mod} -> Fuente rota: {url}")

    return [m for m in modules if m in modulos_activos]

def get_all_lint_modules() -> list[str]:
    modules = [":core"]
    modules.extend(
        f":{directory}:{module.name}"
        for directory in ("lib", "lib-multisrc")
        for module in Path(directory).iterdir()
        if (module / "build.gradle.kts").is_file()
    )
    return sorted(modules)


def create_matrix(modules: list[str]) -> dict:
    return {
        "chunk": [
            {"number": i + 1, "modules": chunk}
            for i, chunk in enumerate(itertools.batched(
                modules,
                int(os.getenv("CI_CHUNK_SIZE", 65))
            ))
        ]
    }

async def main_async() -> None:
    _, ref = sys.argv
    modules, deleted, lint_modules = get_module_list(ref)

    matrix = create_matrix(modules)

    print(
        f"Module chunks to build:\n{json.dumps(matrix, indent=2)}\n\n"
        f"Modules to lint:\n{json.dumps(lint_modules, indent=2)}\n\n"
        f"Module to delete:\n{json.dumps(deleted, indent=2)}"
    )

    if os.getenv("CI") == "true":
        with open(os.getenv("GITHUB_OUTPUT"), 'a') as out_file:
            out_file.write(f"matrix={json.dumps(matrix)}\n")
            out_file.write(f"lint_modules={json.dumps(lint_modules)}\n")
            out_file.write(f"delete={json.dumps(deleted)}\n")

if __name__ == '__main__':
    asyncio.run(main_async())
