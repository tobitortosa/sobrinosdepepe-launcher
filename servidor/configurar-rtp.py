# -*- coding: utf-8 -*-
"""
Hace que /rtp mande a un lugar de verdad al azar adentro del borde.

    python servidor/configurar-rtp.py

Apaga el servidor, cambia tres líneas de EssentialCommands.properties y lo vuelve
a prender. El apagón dura lo que tarda en arrancar, que es medio minuto.

## Lo que estaba roto

**`/rtp` contestaba "spawn not set" y no teleportaba a nadie.** Venía
`rtp_center=Spawn`, y ese Spawn **no es el spawn del servidor**: es uno propio de
Essential Commands, que se setea con su comando y se guarda aparte, en
`world/essentialcommands/world_data.dat`. Ese archivo pesa 23 bytes y adentro solo
tiene `warps` vacío, o sea que nunca se seteó. El mod ni mira el world spawn de
Minecraft (que está en 48, 97, 0), así que el comando moría antes de empezar
(`RandomTeleportCommand.java`, en el `worldSpawn.isEmpty()`).

**Y aunque hubiera andado, no era al azar.** Estaban `rtp_min_radius=1000` y
`rtp_radius=1000`, los dos iguales, y el mod hace:

    int r = r_max == r_min ? r_max : rand.nextInt(r_min, r_max);

Con los dos en 1000 el radio es siempre exactamente 1000: lo único que sorteaba
era el ángulo. Todo el mundo caía en la misma circunferencia, a mil bloques del
centro, con 15.000 de borde.

## Lo que queda

El centro pasa a ser el 0,0 en coordenadas fijas, y no el spawn. Son dos cosas
distintas y conviene que sea así: el 0,0 es **el centro del borde**, y el círculo
del rtp queda inscrito justo adentro del cuadrado del borde. Si el centro fuera el
spawn (que está 48 bloques corrido en X) un sorteo al máximo radio podría dejar a
alguien 48 bloques afuera. Además así el rtp no depende de que el spawn de
Essential Commands exista, que es lo que lo tenía roto.

El máximo sale del mismo `WORLD_BORDER_RADIUS` que usa configurar-borde.py, menos
un margen, así que agrandar el borde agranda el rtp solo y nunca puede quedar
apuntando afuera.

**Ojo con lo que cuesta.** Casi todo /rtp ahora cae en terreno que no se generó
nunca, y generarlo es trabajo del servidor y lugar en el disco. Con
`rtp_max_attempts=15`, un sorteo que no encuentre piso seguro puede generar hasta
quince zonas nuevas. Si el servidor empieza a sufrir, servidor/medir-carga.py lo
dice, y lo primero para bajar es el máximo de acá.

## Por qué hay que apagar

Essential Commands **reescribe el .properties cuando el servidor se apaga**, con lo
que tenga en memoria. Editarlo con el servidor prendido no sirve de nada: al
siguiente apagón el mod lo pisa y vuelve todo atrás. Por eso el orden es apagar,
después editar y después prender. Es lo mismo que avisa respaldar.py.

## Lo que esto NO arregla

**`/spawn` sigue roto**, y es el mismo agujero: `SpawnCommand` pide ese mismo spawn
de Essential Commands que nunca se seteó, así que contesta el mismo error. No se
puede arreglar desde acá: su comando toma la posición del jugador que lo escribe
(`new MinecraftLocation(senderPlayer)`), así que no anda desde la consola. Lo
arregla un operador, adentro del juego, parado donde quiera que caiga la gente:

    /setspawn
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

RUTA = "config/EssentialCommands.properties"

# El centro del borde, que es el que importa acá. No es el spawn: el spawn está en
# (48, 97, 0) y correr el centro 48 bloques puede tirar a alguien afuera del borde.
CENTRO_X, CENTRO_Z = 0, 0

# El mismo número que usa configurar-borde.py, así el rtp sigue al borde solo.
RADIO_DEL_BORDE = int(mc.cfg.get("WORLD_BORDER_RADIUS", "10000"))

# Caer justo contra la pared es feo: no se puede seguir para ese lado y la pantalla
# ya está roja. Medio kilómetro alcanza para no notarlo.
MARGEN = 500

MINIMO = 1000
MAXIMO = RADIO_DEL_BORDE - MARGEN

AJUSTES = [
    ("rtp_center", "Coordinates(%d,%d)" % (CENTRO_X, CENTRO_Z),
     "el centro del borde, en vez del spawn que nunca se seteo"),
    ("rtp_min_radius", str(MINIMO),
     "lo mas cerca que te puede dejar, para no caer encima del spawn"),
    ("rtp_radius", str(MAXIMO),
     "lo mas lejos, que es el borde menos el margen"),
]

# Lo que tarda en apagarse y en arrancar, mirando el panel en vez de adivinar.
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
               t("  Es para arreglar el ", e.ETIQUETA),
               t("/rtp", e.ACENTO, negrita=True),
               t(", que tiraba error.\n", e.ETIQUETA),
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


def leer_ahora():
    """Los tres valores como están hoy, para poder no hacer nada si ya están."""
    texto = mc.read(RUTA)
    puestos = {}
    for clave, _, _ in AJUSTES:
        encontrado = re.search(r"^%s=(.*)$" % re.escape(clave), texto, re.MULTILINE)
        puestos[clave] = encontrado.group(1).strip() if encontrado else None
    return puestos


def editar():
    """
    Cambia las tres líneas y nada más. Con el servidor apagado, que es la única
    forma: prendido, el mod pisa el archivo al salir.
    """
    viejo = mc.read(RUTA)
    nuevo = viejo

    for clave, valor, _ in AJUSTES:
        patron = re.compile(r"^%s=.*$" % re.escape(clave), re.MULTILINE)
        if not patron.search(nuevo):
            sys.exit("No encontré '%s' en %s. No toco nada." % (clave, RUTA))
        nuevo = patron.sub("%s=%s" % (clave, valor), nuevo)

    mc.write(RUTA, nuevo)


def verificar():
    """Lo relee del servidor ya prendido: si el mod lo pisó, acá se ve."""
    puestos = leer_ahora()
    bien = True
    for clave, valor, por_que in AJUSTES:
        ok = puestos[clave] == valor
        print("  %-16s %-20s %s" % (clave, puestos[clave], "" if ok else "<- esperaba " + valor))
        bien = bien and ok
    return bien


if __name__ == "__main__":
    if MAXIMO <= MINIMO:
        sys.exit("El borde (%d) es muy chico para un rtp de %d bloques." % (RADIO_DEL_BORDE, MINIMO))

    antes = leer_ahora()
    if all(antes[clave] == valor for clave, valor, _ in AJUSTES):
        print("El /rtp ya está como va. No hay nada que cambiar.")
        sys.exit(0)

    print("de %s a un sorteo entre %d y %d bloques del %d,%d"
          % (antes["rtp_center"], MINIMO, MAXIMO, CENTRO_X, CENTRO_Z))

    avisar()
    print("\navisado. apagando en 30 segundos")
    time.sleep(30)

    mc.power("stop")
    print("  apagando")
    if not esperar("offline"):
        sys.exit("no se terminó de apagar. No toqué el archivo.")

    editar()
    print("\n  archivo cambiado con el servidor apagado")
    for clave, valor, por_que in AJUSTES:
        print("    %s=%s" % (clave, valor))
        print("        %s" % por_que)

    mc.power("start")
    print("\n  prendiendo")
    if not esperar("running"):
        sys.exit("no terminó de arrancar. Mirá el panel.")

    time.sleep(5)
    print("\nverificación, releído del servidor:")
    if not verificar():
        sys.exit("el mod pisó el archivo. Algo del orden salió mal.")

    print("\n/rtp sortea un punto entre %d y %d bloques del %d,%d, adentro del borde de %d."
          % (MINIMO, MAXIMO, CENTRO_X, CENTRO_Z, RADIO_DEL_BORDE))
    print("Falta /spawn: un operador tiene que escribir /setspawn adentro del juego.")
