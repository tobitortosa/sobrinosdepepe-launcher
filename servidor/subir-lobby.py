# -*- coding: utf-8 -*-
"""
El lobby: todos los que entran caen ahi, y al Survival se va a mano.

    python servidor/subir-lobby.py

Sube las dos partes de una: el mod que manda a todos ahi y los trae de vuelta, y
los dos ajustes de EasyAuth que hacen falta para que las dos cosas no se peleen.

El **mundo** del lobby ya no se sube: desde la mudanza del 2026-09-22 el lobby es
un pedazo del overworld y sus chunks estan adentro del mundo de siempre. Si alguna
vez hay que moverlo, se copia con `mudar-al-overworld.py`, que clona y despues
compara bloque por bloque.

## Como se ve desde adentro del juego

  1. Entras al servidor y caes en el patio del lobby, vinieras de donde vinieras,
     con el inventario vacio y una perla en la mano. **Tus cosas no se pierden**:
     quedan guardadas en `config/lobby-de-pepe.json` hasta que vuelvas.
  2. Si no estas autenticado, EasyAuth no te deja mover hasta que escribas
     `/register <contrasena> <contrasena>` la primera vez, o `/login <contrasena>`
     las siguientes. El que entra con el launcher se saltea esto: el ticket
     firmado ya probo quien es.
  3. Click derecho con la perla y se abre el menu de juegos. Hoy tiene un solo
     modo, el Survival SMP.
  4. Lo elegis y aparecas **exactamente donde estabas**: misma dimension, misma
     posicion, mismo inventario, misma vida y misma experiencia. El boton verde
     del chat y `/survival` hacen lo mismo, por si alguien tira la perla.
  5. `/lobby` te trae de vuelta cuando quieras, salvo que estes en combate.

## Las dos partes

**El lugar.** Un lobby chico y construido (62x37x61 bloques, un patio rodeado de
edificios) que flota en el **overworld** a x=500.000, y=250: a medio millon de
bloques del spawn y por arriba de las nubes, que quedan a y=192.

No es otro servidor, no hay proxy y —desde la mudanza— tampoco es una dimension
propia: es el mismo mundo de siempre, muy lejos. Eso ultimo es lo importante y
costo caro. Mientras fue la dimension `sdp:lobby`, el que se desconectaba adentro
no podia volver a entrar: el servidor lo agregaba dos veces al mundo
(`Force-added player with duplicate UUID`) y al cliente le quedaba el mapa vacio
cargando para siempre. Las tres formas posibles de evitarlo fallan, y una llego a
borrarle el inventario a dos jugadores. Con un solo nivel no hay dos que puedan
discutir de quien es el UUID.

**El mod** (`mod-lobby`). Es el que manda a todos al lobby al entrar, anota donde
estaban y los devuelve con `/survival`. Ademas en el lobby no se rompe, no se
pone y no se pega. Esto no lo puede hacer ninguna config: Minecraft guarda una
sola posicion por jugador, asi que si lo movemos al lobby sin anotar antes donde
estaba, el Survival arrancaria siempre en el spawn.

**Los dos ajustes de EasyAuth.**

  - `hide-player-coords` queda en **false**, y esto es importante. Prendido hace
    lo mismo que el mod (te manda a un punto fijo mientras no estas logueado),
    pero los dos enganchan el mismo momento del login y el orden entre mods no
    esta definido: si EasyAuth te mueve primero, el mod anota el lobby como "donde
    estabas" y el `/survival` te dejaria encerrado ahi. Una sola cosa mueve al
    jugador, y es el mod.
  - `session-timeout` pasa a **604800** (siete dias). Es la sesion por IP: el que
    vuelve dentro de la semana desde la misma IP entra sin escribir la contrasena.
    Lo unico que compra un numero mas chico es cubrirte de alguien de tu misma
    casa que sepa tu nombre.

## El orden, y por que

Apagar, subir, editar y prender, igual que ocultar-coordenadas.py: EasyAuth pisa
su config al apagarse, asi que editarla con el servidor prendido no sirve de nada.
Ademas las dimensiones se leen al arrancar y Fabric carga los mods una sola vez,
o sea que con `/reload` no alcanza para ninguna de las dos cosas.

Tres cosas que costaron un reinicio cada una y conviene no volver a aprender:

  0. **Al jugador que se desconecta no se le toca nada.** Ni el inventario, ni un
     teleport, ni borrarle la copia guardada. Es la regla que mas cara salio del
     proyecto: el `DISCONNECT` corre en el hilo de red y despues de que el
     servidor escribio el archivo del jugador, asi que lo que se le cambia ahi se
     pierde y lo que se le borra del JSON no vuelve. Borro inventarios de verdad.

     El teleport de entrada, en cambio, va en el tick SIGUIENTE al login y no
     adentro del evento: moverlo con el login todavia en curso lo agrega dos
     veces al mundo.

     Y los ids de los menus llevan namespace: la perla abre `menu sdp:juegos`.
     Sin el prefijo, Inventory Menu contesta "the menu doesn't exist".

  1. El lobby es una **caja de coordenadas** y no una dimension, asi que "estas
     en el lobby" se pregunta por posicion, en el mod y en el datapack. Y en el
     datapack nunca con `execute in`: eso le cambia la dimension al contexto pero
     deja el volumen del selector en la posicion de cada jugador, y termina
     agarrando a los que estan bajo tierra en el survival.
  2. Si el datapack no parsea, el servidor NO se cae: se queda colgado en
     "starting" para siempre, y un `restart` lo deja colgado en "stopping". La
     unica salida es `mc.power("kill")` y despues `start`.
"""
import json
import os
import re
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import estilo as e
import mc

RAIZ = os.path.dirname(AQUI)

JARS = os.path.join(RAIZ, "mod-lobby", "build", "libs")
PREFIJO = "lobby-sobrinosdepepe"

DATAPACK = "/world/datapacks/sobrinosdepepe"
DEL_DATAPACK = [
    "data/sdp/function/tick.mcfunction",
    "data/sdp/menu/juegos.json",
    "data/sdp/menu/juegos_afuera.json",
    "data/sdp/menu/coliseo.json",
    "data/sdp/menu/coliseo_kits.json",
    "data/sdp/menu/coliseo_armar.json",
    "data/sdp/menu/coliseo_1v1.json",
    "data/sdp/menu/coliseo_2v2.json",
    "data/sdp/menu/coliseo_equipos.json",
]

# Las dimensiones viejas, que se borran del datapack del servidor. Mientras esos
# JSON esten ahi los dos mundos siguen existiendo y el bug del login vuelve. Los
# chunks de /world/dimensions/sdp/ no se tocan, asi que esto se deshace volviendo
# a poner los cuatro archivos.
BORRAR_DEL_DATAPACK = [
    ("data/sdp/dimension", ["lobby.json", "coliseo.json"]),
    ("data/sdp/dimension_type", ["lobby.json", "coliseo.json"]),
]

# El centro del patio, ahora en el overworld. El piso solido esta en y=249, asi que
# se para en y=250. El mod tiene este mismo punto en LobbyServidor; si se mueve, se
# mueve en los dos lados.
SPAWN = [
    ("dimension", '"minecraft:overworld"'),
    ("x", "500000.5"),
    ("y", "250.0"),
    ("z", "0.5"),
    ("yaw", "0"),
    ("pitch", "0"),
]

AUTH = "/config/EasyAuth/main.conf"
AJUSTES = [
    ("hide-player-coords", "false", "al lobby te manda el mod, no EasyAuth"),
    ("session-timeout", "604800", "una semana de sesion por IP"),
]

ESPERA = 10
INTENTOS = 30


def t(texto, color=None, negrita=False):
    c = {"text": texto}
    if color:
        c["color"] = color
    if negrita:
        c["bold"] = True
    return c


def avisar():
    mensaje = ["",
               t("\n  " + e.VINETA + " ", e.MARCA),
               t("El servidor se apaga un minuto\n", e.MARCA, negrita=True),
               t("  Vuelve con un ", e.ETIQUETA),
               t("lobby", e.ACENTO, negrita=True),
               t(": de ahora en mas se entra ahi, y al Survival\n", e.ETIQUETA),
               t("  se pasa con el botón del chat o con ", e.ETIQUETA),
               t("/survival", e.ACENTO, negrita=True),
               t(". Aparecés donde lo dejaste.\n", e.ETIQUETA),
               t("  Vuelvan a entrar en un ratito.\n", e.APAGADO)]
    mc.cmd("tellraw @a " + json.dumps(mensaje))


def estado():
    return json.loads(mc.call("/resources"))["attributes"]["current_state"]


def esperar(hasta):
    for _ in range(INTENTOS):
        time.sleep(ESPERA)
        ahora = estado()
        print("    %s" % ahora)
        if ahora == hasta:
            return True
    return False


def borrar_las_dimensiones():
    """
    Saca del datapack los dos mundos propios.

    Es lo que hace que la mudanza sea de verdad: mientras esos JSON esten,
    `sdp:lobby` y `sdp:coliseo` siguen existiendo y alguien puede volver a quedar
    guardado adentro de uno.
    """
    for donde, cuales in BORRAR_DEL_DATAPACK:
        try:
            hay = [n for n in mc.ls(DATAPACK + "/" + donde) if n in cuales]
        except Exception:
            hay = []
        if not hay:
            print("  %s: ya no estaba" % donde)
            continue
        mc.delete(DATAPACK + "/" + donde, hay)
        print("  %s: %s" % (donde, ", ".join(hay)))


def subir_datapack():
    for relativa in DEL_DATAPACK:
        local = os.path.join(AQUI, "datapack", relativa.replace("/", os.sep))
        mc.write(DATAPACK + "/" + relativa, open(local, encoding="utf-8").read())
        print("  " + relativa)


def subir_el_jar():
    """
    Deja en mods/ el jar compilado y borra las versiones anteriores: dos jars del
    mismo mod hacen que Fabric no arranque, y el servidor queda apagado.
    """
    if not os.path.isdir(JARS):
        sys.exit("No encontré %s. Compilá el mod: cd mod-lobby && ./gradlew build" % JARS)

    nuestros = sorted(n for n in os.listdir(JARS)
                      if n.startswith(PREFIJO) and n.endswith(".jar"))
    if not nuestros:
        sys.exit("No hay ningún %s*.jar en %s. Compilá el mod." % (PREFIJO, JARS))

    nombre = nuestros[-1]
    viejos = [n for n in mc.ls("/mods") if n.startswith(PREFIJO) and n != nombre]
    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    mc.upload("/mods", os.path.join(JARS, nombre))
    print("  " + nombre)
    return nombre


def editar_auth():
    """
    Los dos ajustes, con el servidor apagado, que es la unica forma: prendido, el
    mod pisa el archivo al salir.
    """
    viejo = mc.read(AUTH)
    nuevo = viejo

    for clave, valor, por_que in AJUSTES:
        patron = re.compile(r"^%s=.*$" % re.escape(clave), re.MULTILINE)
        if not patron.search(nuevo):
            sys.exit("No encontré '%s' en %s. No toco nada." % (clave, AUTH))
        nuevo = patron.sub("%s=%s" % (clave, valor), nuevo)
        print("  %s=%s  (%s)" % (clave, valor, por_que))

    lineas = "".join("    %s=%s\n" % (clave, valor) for clave, valor in SPAWN)
    bloque = "world-spawn {\n" + lineas + "}"
    otro = re.sub(r"^world-spawn \{[^}]*\}", bloque, nuevo, count=1, flags=re.MULTILINE)
    if otro == nuevo and "x=500000.5" not in nuevo:
        sys.exit("No encontré el bloque world-spawn en %s. No toco nada." % AUTH)

    mc.write(AUTH, otro)


def verificar(jar):
    """Relee del servidor ya prendido: si algo no quedó, acá se ve."""
    bien = True

    texto = mc.read(AUTH)
    for clave, valor, _ in AJUSTES:
        ok = ("%s=%s" % (clave, valor)) in texto
        print("  %-34s %s" % ("%s=%s" % (clave, valor), "" if ok else "<- NO quedó"))
        bien = bien and ok

    ok = jar in mc.ls("/mods")
    print("  %-34s %s" % (jar, "" if ok else "<- no está en mods/"))
    bien = bien and ok

    registro = mc.read("/logs/latest.log")
    ok = "Lobby de Pepe listo" in registro
    print("  %-34s %s" % ("el mod arrancó", "" if ok else "<- no dijo nada en el log"))
    bien = bien and ok

    # La prueba de que la mudanza sirvio: los dos mundos propios ya no existen, y
    # entonces nadie puede quedar guardado adentro de uno.
    for cual in ("sdp:lobby", "sdp:coliseo"):
        ok = cual not in registro
        print("  %-34s %s" % (cual + " ya no existe",
                              "" if ok else "<- TODAVÍA aparece en el log"))
        bien = bien and ok

    ok = "Force-added player" not in registro
    print("  %-34s %s" % ("sin logins duplicados", "" if ok else "<- HAY, mirá el log"))
    bien = bien and ok

    feos = [linea for linea in registro.splitlines()
            if "lobbydepepe" in linea
            and ("ERROR" in linea or "WARN" in linea or "Failed" in linea)]
    if feos:
        bien = False
        print("\n  el log se queja:")
        for linea in feos[:10]:
            print("    " + linea.strip())
    else:
        print("  %-34s sin quejas en el log" % "el mod del lobby")

    return bien


if __name__ == "__main__":
    # Si ya esta apagado no hay a quien avisarle, y `tellraw` contra un servidor
    # offline contesta 502. Pasa cuando esto va detras de otro script que ya lo
    # bajo.
    if estado() == "offline":
        print("el servidor ya estaba apagado")
    else:
        avisar()
        print("avisado. apagando en 30 segundos")
        time.sleep(30)

        mc.power("stop")
        print("  apagando")
        if not esperar("offline"):
            sys.exit("no se terminó de apagar. No toques nada.")

    print("\nlas dimensiones viejas:")
    borrar_las_dimensiones()
    print("\nel datapack:")
    subir_datapack()
    print("\nel mod:")
    jar = subir_el_jar()
    print("\nEasyAuth:")
    editar_auth()

    mc.power("start")
    print("\n  prendiendo")
    if not esperar("running"):
        sys.exit("no terminó de arrancar. Mirá el panel: si quedó colgado en "
                 "'starting', es el datapack, y se sale con mc.power('kill').")

    time.sleep(15)
    print("\nverificación, releído del servidor:")
    if not verificar(jar):
        sys.exit("algo no quedó. Mirá arriba antes de anunciar nada.")

    print("\nListo. Entrá y mirá que:")
    print("  - caés en el lobby, no en tu casa")
    print("  - el botón verde (o /survival) te deja donde estabas")
    print("  - /lobby te trae de vuelta, y en combate te lo niega")
    print("  - en el lobby no podés romper ni poner bloques")
    print("  - SALÍ estando en el lobby y volvé a entrar: eso antes dejaba el")
    print("    mapa vacío, y es la prueba de que la mudanza sirvió")
