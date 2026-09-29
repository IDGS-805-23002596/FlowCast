from collections.abc import Iterator
from dataclasses import replace
from datetime import UTC, date, datetime, timedelta

import httpx2 as httpx
import pytest
from fastapi.testclient import TestClient

from app.main import aplicacion
from app.routers.conexiones import obtener_cliente_http
from app.services import cache, deportes, xtream
from tests.ayudantes import borrar_usuarios, crear_conexion, registrar

CORREO = "deportes@ejemplo.com"
HOY = date(2026, 11, 5)


@pytest.mark.parametrize(
    ("nombre", "esperado"),
    [
        (
            "11/05 Soccer 6:00pm Necaxa Femenil vs Atlas Femenil = FOX DEPORTES HD",
            ("Necaxa Femenil", "Atlas Femenil"),
        ),
        ("17:00 - Liga MX Fem - Necaxa vs. Atlas", ("Necaxa", "Atlas")),
        (
            "18:50 05/11 | Cancn FC vs. Piratas FC | Liga de Expansin MX ||",
            ("Cancn FC", "Piratas FC"),
        ),
        (
            "006 MX:MLB 04 / PHILLIES @ CARDINALS - 5:45PM en Espanol - ESPN",
            ("PHILLIES", "CARDINALS"),
        ),
        (
            "22:00  Miami Heat vs. Golden State Warriors  NBA",
            ("Miami Heat", "Golden State Warriors"),
        ),
        ("VIVO 0.09 | PSG vs Barcelona 4🄺 [3:00 PM] Español | ᵁᴴᴰ", ("PSG", "Barcelona")),
        ("05:00  T. Paul (USA) vs. Q. Halys (FRA)  ATP", ("T. Paul", "Q. Halys")),
    ],
)
def prueba_lee_los_formatos_del_proveedor(nombre: str, esperado: tuple[str, str]) -> None:
    assert deportes.leer_canal(nombre, HOY) == esperado


def prueba_descarta_canales_viejos_y_sin_partido() -> None:
    assert deportes.leer_canal("10/28 NBA 7:00pm Boston Celtics vs Miami Heat", HOY) is None
    assert deportes.leer_canal("UFC PPV 30 HD", HOY) is None
    assert deportes.leer_canal("NFL RED Zone HD", HOY) is None


def _equipo(id_: str, nombre: str, corto: str, apodo: str) -> deportes.Equipo:
    return deportes.Equipo(id_, nombre, corto, corto[:3].upper(), apodo, None)


def prueba_tolera_letras_perdidas_y_apodos() -> None:
    cancun = _equipo("1", "Cancún FC", "Cancún", "Cancún FC")
    queretaro = _equipo("2", "Querétaro", "Querétaro", "Querétaro")
    yankees = _equipo("3", "New York Yankees", "Yankees", "Yankees")
    assert deportes.parecido("Cancn", cancun) >= 0.8
    assert deportes.parecido("Quertaro", queretaro) >= 0.8
    assert deportes.parecido("YANKEES", yankees) >= 0.8
    assert deportes.parecido("Toluca", queretaro) < 0.8


AHORA = datetime.now(UTC)
LOCAL = AHORA.astimezone(deportes.ZONA)


def _competidor(lado: str, id_: str, nombre: str, corto: str, marcador: str) -> dict:
    return {
        "homeAway": lado,
        "score": marcador,
        "team": {
            "id": id_,
            "displayName": nombre,
            "shortDisplayName": corto,
            "abbreviation": corto[:3].upper(),
            "name": corto,
            "logo": f"https://logos/{id_}.png",
        },
    }


def _evento(id_: str, inicio: datetime, estado: dict, local: tuple, visitante: tuple) -> dict:
    return {
        "id": id_,
        "date": inicio.strftime("%Y-%m-%dT%H:%MZ"),
        "status": estado,
        "competitions": [
            {"competitors": [_competidor("home", *local), _competidor("away", *visitante)]}
        ],
    }


EN_JUEGO = {"type": {"state": "in", "name": "STATUS_IN_PROGRESS"}, "displayClock": "67'"}
PROGRAMADO = {"type": {"state": "pre", "name": "STATUS_SCHEDULED"}}
SEXTA = {
    "type": {"state": "in", "name": "STATUS_IN_PROGRESS", "shortDetail": "Top 6th"},
    "period": 6,
}
MARCADORES = {
    "soccer/mex.1": [
        _evento(
            "100",
            AHORA - timedelta(minutes=70),
            EN_JUEGO,
            ("227", "América", "América", "2"),
            ("228", "León", "León", "1"),
        ),
        _evento(
            "101",
            AHORA + timedelta(minutes=10),
            PROGRAMADO,
            ("229", "Guadalajara", "Chivas", "0"),
            ("230", "Toluca", "Toluca", "0"),
        ),
    ],
    "baseball/mlb": [
        _evento(
            "200",
            AHORA - timedelta(hours=2),
            SEXTA,
            ("10", "New York Yankees", "Yankees", "3"),
            ("11", "Boston Red Sox", "Red Sox", "1"),
        )
    ],
}
EQUIPOS_LIGA_MX = [
    {"team": {"id": "227", "displayName": "América", "logos": [{"href": "https://logos/227.png"}]}},
    {"team": {"id": "228", "displayName": "León", "logos": [{"href": "https://logos/228.png"}]}},
]


def _responder(peticion: httpx.Request) -> httpx.Response:
    if peticion.url.host == "site.api.espn.com":
        ruta = peticion.url.path.split("/sports/")[1]
        liga, recurso = ruta.rsplit("/", 1)
        if recurso == "teams":
            if liga != "soccer/mex.1":
                return httpx.Response(404)
            return httpx.Response(200, json={"sports": [{"leagues": [{"teams": EQUIPOS_LIGA_MX}]}]})
        return httpx.Response(200, json={"events": MARCADORES.get(liga, [])})
    if peticion.url.params.get("password") != "buena":
        return httpx.Response(200, json={"user_info": {"auth": 0}})
    return httpx.Response(
        200, json={"user_info": {"auth": 1, "status": "Active"}, "server_info": {}}
    )


CATEGORIAS = [
    xtream.Categoria("1", "PPV Futbol Liga MX", None),
    xtream.Categoria("2", "PPV MLB", None),
    xtream.Categoria("3", "Noticias", None),
    xtream.Categoria("4", "PPV Adultos XXX", None),
]


def _canal(id_: str, nombre: str, categoria: str) -> xtream.Canal:
    return xtream.Canal(
        id=id_,
        nombre=nombre,
        icono=None,
        categoria_id=categoria,
        numero=None,
        epg_id=None,
        dias_catchup=0,
        agregado_en=None,
    )


CANALES = [
    _canal("1", "17:00 - Liga MX - America vs. Leon", "1"),
    _canal("2", f"{LOCAL:%m/%d} Soccer 6:00pm America vs Leon = TUDN MEXICO HD", "1"),
    _canal("3", "006 USA: MLB 01 / YANKEES @ RED SOX - 5:07PM - YES", "2"),
    _canal("4", f"{LOCAL:%m/%d} Soccer 9:00pm Herediano vs Saprissa = FUTV", "1"),
    _canal("5", "Noticias 24 vs Clima", "3"),
    _canal("6", f"{LOCAL:%m/%d} Soccer 9:00pm Rival A vs Rival B", "4"),
]


@pytest.fixture(autouse=True)
def proveedor(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    async def cliente_falso():
        transporte = httpx.MockTransport(_responder)
        async with httpx.AsyncClient(transport=transporte, headers=xtream.CABECERAS) as cliente:
            yield cliente

    async def categorias(*_args, **_kwargs):
        return CATEGORIAS

    async def canales(*_args, **_kwargs):
        return CANALES

    monkeypatch.setattr(xtream, "obtener_categorias", categorias)
    monkeypatch.setattr(xtream, "obtener_canales", canales)
    cache.vaciar()
    aplicacion.dependency_overrides[obtener_cliente_http] = cliente_falso
    yield
    aplicacion.dependency_overrides.pop(obtener_cliente_http, None)
    cache.vaciar()


@pytest.fixture(autouse=True)
def usuario() -> Iterator[None]:
    borrar_usuarios([CORREO])
    yield
    borrar_usuarios([CORREO])


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    with TestClient(aplicacion) as cliente_http:
        yield cliente_http


def _preparar(cliente: TestClient) -> tuple[dict, str, str]:
    cabeceras = registrar(cliente, CORREO)
    conexion_id = crear_conexion(cliente, cabeceras)
    perfil = cliente.get("/perfiles", headers=cabeceras).json()[0]["id"]
    return cabeceras, conexion_id, perfil


def prueba_partidos_de_hoy_con_marcador_y_canales(cliente: TestClient) -> None:
    cabeceras, conexion_id, perfil = _preparar(cliente)

    respuesta = cliente.get(
        f"/conexiones/{conexion_id}/deportes", params={"perfil_id": perfil}, headers=cabeceras
    )

    assert respuesta.status_code == 200, respuesta.text
    partidos = respuesta.json()["partidos"]
    clasico = next(p for p in partidos if p["id"] == "espn:100")
    assert clasico["estado"] == "en_vivo"
    assert clasico["detalle"] == "67'"
    assert (clasico["local"]["marcador"], clasico["visitante"]["marcador"]) == ("2", "1")
    assert [c["id"] for c in clasico["canales"]] == ["2", "1"]
    beisbol = next(p for p in partidos if p["id"] == "espn:200")
    assert beisbol["detalle"] == "Alta 6ª entrada"
    assert [c["id"] for c in beisbol["canales"]] == ["3"]
    suelto = next(p for p in partidos if p["estado"] == "sin_datos")
    assert suelto["local"]["nombre"] == "Herediano"
    assert all(p["id"] != "espn:101" for p in partidos)
    nombres = {c["nombre"] for p in partidos for c in p["canales"]}
    assert not any("Noticias" in n or "Rival" in n for n in nombres)
    assert [d["clave"] for d in respuesta.json()["deportes"]][:2] == ["futbol", "beisbol"]
    assert respuesta.json()["mis_equipos"] == 0


def prueba_mis_equipos_desde_la_tele(cliente: TestClient) -> None:
    cabeceras, conexion_id, perfil = _preparar(cliente)

    equipos = cliente.get(
        "/deportes/ligas/mex.1/equipos", params={"perfil_id": perfil}, headers=cabeceras
    ).json()
    assert [(e["nombre"], e["favorito"]) for e in equipos] == [
        ("América", False),
        ("León", False),
    ]

    agregado = cliente.put(f"/perfiles/{perfil}/equipos/mex.1/229", headers=cabeceras)
    assert agregado.status_code == 404
    agregado = cliente.put(f"/perfiles/{perfil}/equipos/mex.1/228", headers=cabeceras)
    assert agregado.status_code == 200, agregado.text
    assert agregado.json()["liga_nombre"] == "Liga MX"

    mios = cliente.get(f"/perfiles/{perfil}/equipos", headers=cabeceras).json()
    assert [e["nombre"] for e in mios] == ["León"]
    equipos = cliente.get(
        "/deportes/ligas/mex.1/equipos", params={"perfil_id": perfil}, headers=cabeceras
    ).json()
    assert [e["favorito"] for e in equipos] == [False, True]

    partidos = cliente.get(
        f"/conexiones/{conexion_id}/deportes", params={"perfil_id": perfil}, headers=cabeceras
    ).json()["partidos"]
    assert partidos[0]["id"] == "espn:100"
    assert partidos[0]["favorito"] is True

    borrado = cliente.delete(f"/perfiles/{perfil}/equipos/mex.1/228", headers=cabeceras)
    assert borrado.status_code == 204
    assert cliente.get(f"/perfiles/{perfil}/equipos", headers=cabeceras).json() == []


def prueba_partido_de_tu_equipo_sin_canal_tambien_sale(cliente: TestClient) -> None:
    cabeceras, conexion_id, perfil = _preparar(cliente)
    equipos_chivas = [{"team": {"id": "229", "displayName": "Guadalajara"}}]
    EQUIPOS_LIGA_MX.extend(equipos_chivas)
    try:
        cliente.put(f"/perfiles/{perfil}/equipos/mex.1/229", headers=cabeceras)
    finally:
        del EQUIPOS_LIGA_MX[-1]

    partidos = cliente.get(
        f"/conexiones/{conexion_id}/deportes", params={"perfil_id": perfil}, headers=cabeceras
    ).json()["partidos"]

    assert partidos[0]["id"] == "espn:101"
    assert partidos[0]["canales"] == []


def prueba_catalogo_y_liga_inexistente(cliente: TestClient) -> None:
    cabeceras = registrar(cliente, CORREO)
    catalogo = cliente.get("/deportes", headers=cabeceras).json()
    futbol = next(d for d in catalogo if d["clave"] == "futbol")
    assert futbol["ligas"][0] == {"clave": "mex.1", "nombre": "Liga MX"}
    assert cliente.get("/deportes/ligas/no-existe/equipos", headers=cabeceras).status_code == 404
    assert cliente.get("/deportes/ligas/nba/equipos", headers=cabeceras).status_code == 502


def prueba_deporte_desde_el_nombre_si_la_categoria_no_lo_dice() -> None:
    canal = _canal("9", f"20:10 {LOCAL:%d/%m} | Aguilas De Mexicali vs. Venados | LMP |", "5")
    leidos = deportes.canales_con_partido(
        [canal], {"5": "PPV Eventos del dia"}, AHORA.astimezone(deportes.ZONA).date()
    )
    assert [c.deporte for c in leidos] == ["beisbol"]


def prueba_el_marcador_se_actualiza_sin_volver_a_cruzar_canales() -> None:
    liga = deportes.LIGAS_POR_CLAVE["mex.1"]
    local = _equipo("1", "Club León", "León", "León")
    visitante = _equipo("2", "América", "América", "Águilas")
    viejo = deportes.EventoEspn("77", liga, AHORA, "en_vivo", "10'", local, visitante)
    canal = _canal("5", "Leon vs America", "1")
    cruzados = deportes.armar_partidos(
        [viejo], [deportes.CanalPartido(canal, "futbol", "Leon", "America", False)]
    )
    fresco = deportes.EventoEspn(
        "77",
        liga,
        AHORA,
        "en_vivo",
        "67'",
        replace(local, marcador="2"),
        replace(visitante, marcador="1"),
    )
    [partido] = deportes.con_marcadores(cruzados, [fresco])
    assert (partido.detalle, partido.local.marcador, partido.visitante.marcador) == (
        "67'",
        "2",
        "1",
    )
    assert [c.id for c in partido.canales] == ["5"]
    assert cruzados[0].detalle == "10'"


def prueba_completa_escudos_por_nombre_aunque_falten_letras() -> None:
    indice = {"futbol": {}}
    for nombre in ("Cancún FC", "Durango"):
        for clave in deportes._claves_de_nombre(nombre):
            indice["futbol"][clave] = f"https://logos/{nombre}.png"
    sin_logo = deportes.Equipo("", "Cancn FC", "", "", "", None)
    durango = deportes.Equipo("9", "Durango", "Durango", "DUR", "", None)
    otro = deportes.Equipo("", "Aguilas De Mexicali", "", "", "", None)
    partido = deportes.Partido("canal:x", "futbol", "", "sin_datos", "", None, sin_logo, otro)
    de_espn = deportes.Partido("espn:1", "futbol", "", "en_vivo", "", None, durango, otro)
    deportes.completar_logos([partido, de_espn], indice)
    assert partido.local.logo == "https://logos/Cancún FC.png"
    assert partido.visitante.logo is None
    assert de_espn.local.logo == "https://logos/Durango.png"
    fresco = deportes.EventoEspn(
        "1", deportes.LIGAS_POR_CLAVE["mex.2"], AHORA, "en_vivo", "70'", durango, otro
    )
    [actualizado] = deportes.con_marcadores([de_espn], [fresco])
    assert actualizado.local.logo == "https://logos/Durango.png"
