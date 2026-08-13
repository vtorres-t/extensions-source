import asyncio
import json
import aiohttp
from pathlib import Path

# --- Configuración HTTP Global ---
MAX_CONEXIONES_SIMULTANEAS = 25
HTTP_TIMEOUT_SEGUNDOS = 4
URL_IGNORE_CONFIG = Path(".github/.urlignore")

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
            print(f"ℹ️ Cargadas {len(excepciones)} excepciones desde {URL_IGNORE_CONFIG}")
        except Exception as e:
            print(f"⚠️ Error al leer las excepciones: {e}")
    return excepciones

# Instancia global de excepciones compartida al importar
EXCEPCIONES_SIEMPRE_ACTIVAS = cargar_excepciones_url()

async def verificar_url(session: aiohttp.ClientSession, url: str) -> bool:
    """ Realiza una petición HEAD asíncrona validando bloqueos de Cloudflare. """
    if not url:
        return False

    if any(excepcion in url for excepcion in EXCEPCIONES_SIEMPRE_ACTIVAS):
        return True

    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
    try:
        timeout = aiohttp.ClientTimeout(total=HTTP_TIMEOUT_SEGUNDOS)
        async with session.head(url, headers=headers, timeout=timeout, allow_redirects=True) as response:
            if response.status < 400:
                return True
            if response.status in (403, 503):
                server_header = response.headers.get("Server", "").lower()
                if "cloudflare" in server_header:
                    return True
            return False
    except Exception:
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
