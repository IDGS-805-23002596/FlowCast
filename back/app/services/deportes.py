import asyncio
import re
import unicodedata
from collections.abc import Callable
from dataclasses import dataclass, field, replace
from datetime import UTC, date, datetime, timedelta
from difflib import SequenceMatcher
from typing import Any
from zoneinfo import ZoneInfo

import httpx2 as httpx

from app.services import cache

ZONA = ZoneInfo("America/Mexico_City")
URL_ESPN = "https://site.api.espn.com/apis/site/v2/sports"
SEGUNDOS_MARCADORES = 20
SEGUNDOS_EQUIPOS = 24 * 3600
HORAS_TERMINADOS = 6
CABECERAS_ESPN = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}


@dataclass(frozen=True)
class Liga:
    clave: str
    nombre: str
    deporte: str
    ruta: str


DEPORTES = {
    "futbol": "Fútbol",
    "beisbol": "Béisbol",
    "americano": "Fútbol americano",
    "basquet": "Básquetbol",
    "hockey": "Hockey",
    "tenis": "Tenis",
    "combate": "Box y MMA",
    "motor": "Automovilismo",
    "otros": "Otros",
}

LIGAS = [
    Liga("mex.1", "Liga MX", "futbol", "soccer/mex.1"),
    Liga("mex.w.1", "Liga MX Femenil", "futbol", "soccer/mex.w.1"),
    Liga("mex.2", "Liga de Expansión MX", "futbol", "soccer/mex.2"),
    Liga("concacaf.champions", "Concacaf Champions Cup", "futbol", "soccer/concacaf.champions"),
    Liga("uefa.champions", "Champions League", "futbol", "soccer/uefa.champions"),
    Liga("uefa.europa", "Europa League", "futbol", "soccer/uefa.europa"),
    Liga("uefa.europa.conf", "Conference League", "futbol", "soccer/uefa.europa.conf"),
    Liga("eng.1", "Premier League", "futbol", "soccer/eng.1"),
    Liga("esp.1", "LaLiga", "futbol", "soccer/esp.1"),
    Liga("ita.1", "Serie A", "futbol", "soccer/ita.1"),
    Liga("ger.1", "Bundesliga", "futbol", "soccer/ger.1"),
    Liga("fra.1", "Ligue 1", "futbol", "soccer/fra.1"),
    Liga("usa.1", "MLS", "futbol", "soccer/usa.1"),
    Liga("conmebol.libertadores", "Copa Libertadores", "futbol", "soccer/conmebol.libertadores"),
    Liga("arg.1", "Liga Profesional Argentina", "futbol", "soccer/arg.1"),
    Liga("fifa.friendly", "Amistosos internacionales", "futbol", "soccer/fifa.friendly"),
    Liga("mlb", "MLB", "beisbol", "baseball/mlb"),
    Liga("nfl", "NFL", "americano", "football/nfl"),
    Liga("college-football", "NCAA Football", "americano", "football/college-football"),
    Liga("nba", "NBA", "basquet", "basketball/nba"),
    Liga("wnba", "WNBA", "basquet", "basketball/wnba"),
    Liga(
        "mens-college-basketball",
        "NCAA Basketball",
        "basquet",
        "basketball/mens-college-basketball",
    ),
    Liga("nhl", "NHL", "hockey", "hockey/nhl"),
]
LIGAS_POR_CLAVE = {liga.clave: liga for liga in LIGAS}

_DEPORTE_DE_CATEGORIA = [
    (re.compile(r"(?i)mlb|\blmp\b|beisbol|béisbol|baseball"), "beisbol"),
    (re.compile(r"(?i)nfl|ncaa|americano"), "americano"),
    (re.compile(r"(?i)nba|basket|básquet|basquet"), "basquet"),
    (re.compile(r"(?i)hockey|nhl"), "hockey"),
    (re.compile(r"(?i)tenis|tennis"), "tenis"),
    (re.compile(r"(?i)mma|ufc|box|lucha"), "combate"),
    (re.compile(r"(?i)racing|f1|motor"), "motor"),
    (re.compile(r"(?i)futbol|fútbol|soccer|liga|uefa|concacaf"), "futbol"),
]
_CATEGORIA_DEPORTIVA = re.compile(r"(?i)\bppv\b|deport|sport")
_SEPARADOR_VS = re.compile(r"\s+(?:vs\.?|v\.|@)\s+", re.IGNORECASE)
_CORTES = re.compile(r"\s*(?:\|+|=|\[|\s-\s|\s/\s|\s{2,})\s*")
_HORA = re.compile(r"(?i)\b\d{1,2}:\d{2}\s*(?:am|pm)?\b")
_FECHA = re.compile(r"\b(\d{1,2})/(\d{1,2})\b")
_RUIDO = re.compile(r"(?i)\(.*?\)|\ben espa[nñ]ol\b|\*|\bhd\b|\bfhd\b|ᵁᴴᴰ")
_FEMENIL = re.compile(r"(?i)\bfem(?:enil)?\b|\bwomen\b")
_PALABRAS_VACIAS = {"fc", "cf", "sc", "ac", "club", "de", "the", "cd", "ca", "afc", "sk"}
_PREFERIDOS = re.compile(r"(?i)espa|mexico|\bmx\b|deportes|tudn|vix|claro|azteca|televisa|aym")


@dataclass
class Equipo:
    id: str
    nombre: str
    corto: str
    abreviatura: str
    apodo: str
    logo: str | None
    marcador: str | None = None


@dataclass
class EventoEspn:
    id: str
    liga: Liga
    inicio: datetime
    estado: str
    detalle: str
    local: Equipo
    visitante: Equipo


@dataclass
class CanalPartido:
    canal: Any
    deporte: str
    local: str
    visitante: str
    femenil: bool
    de_hoy: bool = True


@dataclass
class Partido:
    id: str
    deporte: str
    liga: str
    estado: str
    detalle: str
    inicio: datetime | None
    local: Equipo
    visitante: Equipo
    canales: list[Any] = field(default_factory=list)
    favorito: bool = False
    liga_clave: str | None = None


def normalizar(texto: str) -> str:
    texto = unicodedata.normalize("NFKD", texto or "")
    texto = "".join(c for c in texto if not unicodedata.combining(c)).lower()
    texto = re.sub(r"[^a-z0-9 ]+", " ", texto)
    return " ".join(p for p in texto.split() if p not in _PALABRAS_VACIAS)


def es_categoria_deportiva(nombre: str) -> bool:
    return bool(_CATEGORIA_DEPORTIVA.search(nombre or ""))


def deporte_de_categoria(nombre: str) -> str:
    for patron, deporte in _DEPORTE_DE_CATEGORIA:
        if patron.search(nombre or ""):
            return deporte
    return "otros"


def _limpiar_equipo(texto: str) -> str:
    texto = _RUIDO.sub(" ", texto)
    texto = re.sub(r"[^\w\s.&'-]", " ", texto)
    texto = re.sub(r"\s+\d+$", "", texto.strip())
    return re.sub(r"\s+", " ", texto).strip(" .-:")


def fecha_del_canal(nombre: str, hoy: date) -> date | None:
    for a, b in _FECHA.findall(nombre):
        for mes, dia in ((int(a), int(b)), (int(b), int(a))):
            try:
                fecha = date(hoy.year, mes, dia)
            except ValueError:
                continue
            if fecha in (hoy, hoy - timedelta(days=1)):
                return fecha
    return None


def _fecha_valida(nombre: str, hoy: date) -> bool:
    fechas = _FECHA.findall(nombre)
    if not fechas:
        return True
    aceptables = {hoy, hoy - timedelta(days=1)}
    for a, b in fechas:
        for mes, dia in ((int(a), int(b)), (int(b), int(a))):
            try:
                if date(hoy.year, mes, dia) in aceptables:
                    return True
            except ValueError:
                continue
    return False


def leer_canal(nombre: str, hoy: date) -> tuple[str, str] | None:
    partes = _SEPARADOR_VS.split(nombre, maxsplit=1)
    if len(partes) != 2 or not _fecha_valida(nombre, hoy):
        return None
    izquierda = _CORTES.split(partes[0].strip())[-1]
    horas = list(_HORA.finditer(izquierda))
    if horas:
        izquierda = izquierda[horas[-1].end() :]
    derecha = _CORTES.split(partes[1].strip())[0]
    derecha = _HORA.split(derecha)[0]
    local, visitante = _limpiar_equipo(izquierda), _limpiar_equipo(derecha)
    if len(normalizar(local)) < 2 or len(normalizar(visitante)) < 2:
        return None
    return local, visitante


def _alias(equipo: Equipo) -> set[str]:
    nombres = {equipo.nombre, equipo.corto, equipo.apodo}
    return {n for n in (normalizar(x) for x in nombres) if n}


def parecido(texto: str, equipo: Equipo) -> float:
    lado = normalizar(texto)
    if not lado:
        return 0.0
    if lado == normalizar(equipo.abreviatura):
        return 1.0
    mejor = 0.0
    compacto = lado.replace(" ", "")
    for alias in _alias(equipo):
        if lado == alias:
            return 1.0
        if len(alias) >= 4 and (alias in lado or lado in alias):
            mejor = max(mejor, 0.9)
            continue
        if alias[:2] != lado[:2] and alias.split()[-1][:3] != lado.split()[-1][:3]:
            continue
        mejor = max(mejor, SequenceMatcher(None, compacto, alias.replace(" ", "")).ratio())
    return mejor


def corresponde(canal: CanalPartido, evento: EventoEspn) -> bool:
    if evento.liga.deporte == "futbol" and canal.femenil != (evento.liga.clave == "mex.w.1"):
        return False
    directo = min(parecido(canal.local, evento.local), parecido(canal.visitante, evento.visitante))
    cruzado = min(parecido(canal.local, evento.visitante), parecido(canal.visitante, evento.local))
    return max(directo, cruzado) >= 0.8


def _detalle(estado: dict[str, Any], deporte: str) -> tuple[str, str]:
    tipo = estado.get("type") or {}
    fase = tipo.get("state")
    nombre = str(tipo.get("name") or "")
    if fase == "pre":
        if "POSTPONED" in nombre:
            return "terminado", "Pospuesto"
        return "programado", ""
    if fase == "post":
        if "POSTPONED" in nombre:
            return "terminado", "Pospuesto"
        if "CANCELED" in nombre:
            return "terminado", "Cancelado"
        return "terminado", "Final"
    if "HALFTIME" in nombre:
        return "en_vivo", "Medio tiempo"
    periodo = estado.get("period") or 0
    reloj = str(estado.get("displayClock") or "")
    if deporte == "futbol":
        return "en_vivo", reloj or "En juego"
    if deporte == "beisbol":
        corto = str(tipo.get("shortDetail") or "").lower()
        mitad = "Alta" if corto.startswith("top") else "Baja" if corto.startswith("bot") else ""
        if corto.startswith(("mid", "end")):
            mitad = "Fin de la"
        return "en_vivo", f"{mitad} {periodo}ª entrada".strip()
    unidad = "periodo" if deporte == "hockey" else "cuarto"
    return "en_vivo", f"{periodo}º {unidad} · {reloj}" if reloj else f"{periodo}º {unidad}"


def _equipo(datos: dict[str, Any]) -> Equipo:
    equipo = datos.get("team") or datos
    logos = equipo.get("logos") or []
    logo = equipo.get("logo") or (logos[0].get("href") if logos else None)
    return Equipo(
        id=str(equipo.get("id")),
        nombre=equipo.get("displayName") or equipo.get("name") or "",
        corto=equipo.get("shortDisplayName") or "",
        abreviatura=equipo.get("abbreviation") or "",
        apodo=equipo.get("name") or "",
        logo=logo,
        marcador=str(datos["score"]) if datos.get("score") not in (None, "") else None,
    )


def _evento(datos: dict[str, Any], liga: Liga) -> EventoEspn | None:
    competencia = (datos.get("competitions") or [{}])[0]
    equipos = competencia.get("competitors") or []
    local = next((e for e in equipos if e.get("homeAway") == "home"), None)
    visitante = next((e for e in equipos if e.get("homeAway") == "away"), None)
    if local is None or visitante is None:
        return None
    try:
        inicio = datetime.fromisoformat(str(datos.get("date")))
    except ValueError:
        return None
    estado, detalle = _detalle(datos.get("status") or {}, liga.deporte)
    return EventoEspn(
        id=str(datos.get("id")),
        liga=liga,
        inicio=inicio,
        estado=estado,
        detalle=detalle,
        local=_equipo(local),
        visitante=_equipo(visitante),
    )


async def _pedir(cliente: httpx.AsyncClient, url: str) -> Any:
    respuesta = await cliente.get(url, headers=CABECERAS_ESPN, timeout=8.0)
    respuesta.raise_for_status()
    return respuesta.json()


def _dias_a_consultar(ahora: datetime) -> list[date]:
    local = ahora.astimezone(ZONA)
    dias = [local.date(), local.date() + timedelta(days=1)]
    if local.hour < 6:
        dias.insert(0, local.date() - timedelta(days=1))
    return dias


async def _eventos_del_dia(liga: Liga, cliente: httpx.AsyncClient, dia: date) -> list[EventoEspn]:
    async def consultar() -> list[EventoEspn]:
        try:
            datos = await _pedir(cliente, f"{URL_ESPN}/{liga.ruta}/scoreboard?dates={dia:%Y%m%d}")
        except (httpx.HTTPError, ValueError):
            return []
        eventos = (_evento(e, liga) for e in datos.get("events") or [])
        return [e for e in eventos if e is not None]

    return await cache.obtener_o_calcular(
        f"deportes:marcador:{liga.clave}:{dia:%Y%m%d}", consultar, SEGUNDOS_MARCADORES
    )


async def eventos_de_liga(
    liga: Liga, cliente: httpx.AsyncClient, ahora: datetime
) -> list[EventoEspn]:
    listas = await asyncio.gather(
        *(_eventos_del_dia(liga, cliente, dia) for dia in _dias_a_consultar(ahora))
    )
    unicos = {e.id: e for lista in listas for e in lista}
    return list(unicos.values())


def _es_de_hoy(evento: EventoEspn, ahora: datetime) -> bool:
    if evento.estado == "en_vivo":
        return True
    if evento.estado == "terminado":
        return evento.inicio >= ahora - timedelta(hours=HORAS_TERMINADOS + 4) and (
            evento.inicio.astimezone(ZONA).date() == ahora.astimezone(ZONA).date()
            or evento.inicio >= ahora - timedelta(hours=HORAS_TERMINADOS)
        )
    return evento.inicio.astimezone(ZONA).date() == ahora.astimezone(ZONA).date()


async def eventos_de_hoy(cliente: httpx.AsyncClient, ahora: datetime) -> list[EventoEspn]:
    listas = await asyncio.gather(*(eventos_de_liga(liga, cliente, ahora) for liga in LIGAS))
    return [e for lista in listas for e in lista if _es_de_hoy(e, ahora)]


async def equipos_de_liga(liga: Liga, cliente: httpx.AsyncClient) -> list[Equipo]:
    async def consultar() -> list[Equipo]:
        datos = await _pedir(cliente, f"{URL_ESPN}/{liga.ruta}/teams?limit=1000")
        ligas = ((datos.get("sports") or [{}])[0].get("leagues")) or [{}]
        equipos = [_equipo(e) for e in ligas[0].get("teams") or []]
        return sorted(equipos, key=lambda e: normalizar(e.nombre))

    return await cache.obtener_o_calcular(
        f"deportes:equipos:{liga.clave}", consultar, SEGUNDOS_EQUIPOS
    )


def canales_con_partido(
    canales: list[Any], categorias: dict[str, str], hoy: date
) -> list[CanalPartido]:
    resultado = []
    for canal in canales:
        categoria = categorias.get(canal.categoria_id or "", "")
        if not es_categoria_deportiva(categoria):
            continue
        leido = leer_canal(canal.nombre, hoy)
        if leido is None:
            continue
        resultado.append(
            CanalPartido(
                canal=canal,
                deporte=_deporte_del_canal(categoria, canal.nombre),
                local=leido[0],
                visitante=leido[1],
                femenil=bool(_FEMENIL.search(canal.nombre)),
                de_hoy=fecha_del_canal(canal.nombre, hoy) == hoy,
            )
        )
    return resultado


def _deporte_del_canal(categoria: str, nombre: str) -> str:
    deporte = deporte_de_categoria(categoria)
    return deporte if deporte != "otros" else deporte_de_categoria(nombre)


def _orden_canal(canal: Any) -> tuple[int, str]:
    puntos = 0
    if _PREFERIDOS.search(canal.nombre):
        puntos -= 2
    if re.search(r"(?i)\b(?:f?hd|uhd)\b|ᵁᴴᴰ", canal.nombre):
        puntos -= 1
    return puntos, canal.nombre


def armar_partidos(
    eventos: list[EventoEspn], canales: list[CanalPartido], maximo_canales: int = 8
) -> list[Partido]:
    partidos: dict[str, Partido] = {}
    sueltos: dict[str, Partido] = {}
    for evento in eventos:
        partidos[evento.id] = Partido(
            id=f"espn:{evento.id}",
            deporte=evento.liga.deporte,
            liga=evento.liga.nombre,
            liga_clave=evento.liga.clave,
            estado=evento.estado,
            detalle=evento.detalle,
            inicio=evento.inicio,
            local=evento.local,
            visitante=evento.visitante,
        )
    for canal in canales:
        evento = next((e for e in eventos if corresponde(canal, e)), None)
        if evento is not None:
            partidos[evento.id].canales.append(canal.canal)
            continue
        if not canal.de_hoy:
            continue
        clave = " vs ".join(sorted([normalizar(canal.local), normalizar(canal.visitante)]))
        suelto = sueltos.get(clave)
        if suelto is None:
            suelto = sueltos[clave] = Partido(
                id=f"canal:{clave}",
                deporte=canal.deporte,
                liga="",
                estado="sin_datos",
                detalle="",
                inicio=None,
                local=Equipo("", canal.local, "", "", "", None),
                visitante=Equipo("", canal.visitante, "", "", "", None),
            )
        suelto.canales.append(canal.canal)
    resultado = list(partidos.values()) + list(sueltos.values())
    for partido in resultado:
        unicos = {c.id: c for c in partido.canales}
        partido.canales = sorted(unicos.values(), key=_orden_canal)[:maximo_canales]
    return resultado


def con_marcadores(partidos: list[Partido], eventos: list[EventoEspn]) -> list[Partido]:
    frescos = {f"espn:{e.id}": e for e in eventos}
    resultado = []
    for partido in partidos:
        evento = frescos.get(partido.id)
        if evento is None:
            resultado.append(partido)
            continue
        resultado.append(
            replace(
                partido,
                estado=evento.estado,
                detalle=evento.detalle,
                inicio=evento.inicio,
                local=replace(evento.local, logo=evento.local.logo or partido.local.logo),
                visitante=replace(
                    evento.visitante, logo=evento.visitante.logo or partido.visitante.logo
                ),
            )
        )
    return resultado


def _claves_de_nombre(texto: str) -> list[str]:
    normal = re.sub(r"^\d{1,2} ", "", normalizar(_RUIDO.sub(" ", texto or "")))
    if not normal:
        return []
    esqueleto = re.sub(r"[aeiou ]", "", normal)
    return [normal, f"~{esqueleto}"] if len(esqueleto) >= 4 else [normal]


async def indice_de_logos(cliente: httpx.AsyncClient) -> dict[str, dict[str, str]]:
    async def de_liga(liga: Liga) -> tuple[Liga, list[Equipo]]:
        try:
            return liga, await equipos_de_liga(liga, cliente)
        except (httpx.HTTPError, ValueError, KeyError, IndexError):
            return liga, []

    indice: dict[str, dict[str, str]] = {}
    for liga, equipos in await asyncio.gather(*(de_liga(liga) for liga in LIGAS)):
        del_deporte = indice.setdefault(liga.deporte, {})
        for equipo in equipos:
            if not equipo.logo:
                continue
            for nombre in (equipo.nombre, equipo.corto, f"{equipo.corto} {equipo.apodo}"):
                for clave in _claves_de_nombre(nombre):
                    del_deporte.setdefault(clave, equipo.logo)
    return indice


def _logo_por_nombre(nombre: str, deporte: str, indice: dict[str, dict[str, str]]) -> str | None:
    claves = _claves_de_nombre(nombre)
    tablas = sorted(indice.values(), key=lambda tabla: tabla is not indice.get(deporte))
    for clave in claves:
        for tabla in tablas:
            if clave in tabla:
                return tabla[clave]
    return None


def completar_logos(partidos: list[Partido], indice: dict[str, dict[str, str]]) -> None:
    for partido in partidos:
        for lado in ("local", "visitante"):
            equipo = getattr(partido, lado)
            if equipo.logo:
                continue
            logo = _logo_por_nombre(equipo.nombre, partido.deporte, indice)
            if logo:
                setattr(partido, lado, replace(equipo, logo=logo))


_ORDEN_ESTADO = {"en_vivo": 0, "programado": 1, "terminado": 2, "sin_datos": 3}
_LEJANO = datetime.max.replace(tzinfo=UTC)


def _orden_partido(partido: Partido) -> tuple:
    terminado = partido.estado == "terminado" and partido.inicio is not None
    return (
        not partido.favorito,
        _ORDEN_ESTADO.get(partido.estado, 4),
        -partido.inicio.timestamp() if terminado else 0,
        partido.inicio or _LEJANO,
        partido.local.nombre,
    )


def para_perfil(
    partidos: list[Partido],
    favoritos: set[tuple[str, str]],
    canal_visible: Callable[[Any], bool],
) -> list[Partido]:
    resultado = []
    for partido in partidos:
        favorito = (partido.liga_clave, partido.local.id) in favoritos or (
            partido.liga_clave,
            partido.visitante.id,
        ) in favoritos
        canales = [c for c in partido.canales if canal_visible(c)]
        if canales or favorito:
            resultado.append(replace(partido, favorito=favorito, canales=canales))
    return sorted(resultado, key=_orden_partido)
