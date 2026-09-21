# -*- coding: utf-8 -*-
"""
Saca las coordenadas de los mensajes de viaje, para poder streamear tranquilo.

    python servidor/ocultar-coordenadas.py

Apaga el servidor, cambia una linea de EssentialCommands.properties y lo vuelve
a prender. El apagon dura lo que tarda en arrancar, que es medio minuto.

## Lo que esta mal

Con `print_teleport_coordinates=true`, cada vez que alguien viaja, Essential
Commands le contesta en el chat "Teleported to home: casa" con las coordenadas
pegadas atras. Pasa con `/home`, `/casa`, `/back`, `/spawn`, `/warp`, `/rtp` y
con los dos lados de un `/tpa`. El que este streameando acaba de mostrar donde
vive, y alcanza con que alguien pause el video.

El mensaje sale de `PlayerTeleporter`, con la clave `teleport.done` del idioma, y
las coordenadas se le pegan solo si:

    profile.shouldPrintTeleportCoordinates().orElse(CONFIG.PRINT_TELEPORT_COORDINATES)

O sea: primero manda la preferencia del jugador y, si no tiene ninguna puesta,
manda esta config. Nadie tiene preferencia puesta salvo que la haya escrito a
mano con `/profile printTeleportCoordinates`, asi que tocar esto una vez vale
para todos, para los que ya estan y para los que entren despues.

Va apagado para todo el mundo y no solo para los que streamean: el que quiera
sus coordenadas las tiene en F3, y el que no las quiera no las puede sacar de un
mensaje que ya se mando.

## Lo que esto NO tapa

**El `/tp` de vanilla.** Si sos operador y escribis `/teleport 100 64 200`, el
juego te contesta "Teleported Tobi to 100, 64, 200" y eso no lo controla ningun
mod: es el feedback de comandos de Minecraft. La gamerule que lo apaga
(`sendCommandFeedback`) apaga tambien el feedback de todos los demas comandos,
incluidos los de Essential Commands y los de la economia, que usan `sendSuccess`
para hablarle al jugador. No vale la pena: en stream conviene viajar con `/home`,
`/warp` o `/rtp`, que ya quedan tapados. Ademas el `/tp` muestra las coordenadas
igual mientras las escribis en la barra del chat.

**`/setspawn`.** Contesta "Spawn set at ..." con las coordenadas. Lo ve solo el
operador que lo escribio, y se usa una vez cada muerte de siglo.

**Una preferencia propia ya puesta.** Si alguna vez escribiste
`/profile printTeleportCoordinates true`, eso le gana a esta config y te las
siguen mostrando a vos solo. Se arregla adentro del juego:

    /profile printTeleportCoordinates false

## Por que hay que apagar

Essential Commands **reescribe el .properties cuando el servidor se apaga**, con
lo que tenga en memoria. Editarlo con el servidor prendido no sirve de nada: al
siguiente apagon el mod lo pisa y vuelve todo atras. Por eso el orden es apagar,
despues editar y despues prender. Es lo mismo que hacen configurar-rtp.py y
respaldar.py.
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

AJUSTES = [
    ("print_teleport_coordinates", "false",
     "que /home, /warp, /rtp, /back y /tpa no canten donde caes"),
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
               t("  Es para que el chat deje de mostrar las ", e.ETIQUETA),
               t("coordenadas", e.ACENTO, negrita=True),
               t("\n  cuando viajas con ", e.ETIQUETA),
               t("/home", e.ACENTO, negrita=True),
               t(" o ", e.ETIQUETA),
               t("/tpa", e.ACENTO, negrita=True),
               t(".\n", e.ETIQUETA),
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
    """Los valores como estan hoy, para poder no hacer nada si ya estan."""
    texto = mc.read(RUTA)
    puestos = {}
    for clave, _, _ in AJUSTES:
        encontrado = re.search(r"^%s=(.*)$" % re.escape(clave), texto, re.MULTILINE)
        puestos[clave] = encontrado.group(1).strip() if encontrado else None
    return puestos


def editar():
    """
    Cambia las lineas y nada mas. Con el servidor apagado, que es la unica
    forma: prendido, el mod pisa el archivo al salir.
    """
    viejo = mc.read(RUTA)
    nuevo = viejo

    for clave, valor, _ in AJUSTES:
        patron = re.compile(r"^%s=.*$" % re.escape(clave), re.MULTILINE)
        if not patron.search(nuevo):
            sys.exit("No encontre '%s' en %s. No toco nada." % (clave, RUTA))
        nuevo = patron.sub("%s=%s" % (clave, valor), nuevo)

    mc.write(RUTA, nuevo)


def verificar():
    """Lo relee del servidor ya prendido: si el mod lo piso, aca se ve."""
    puestos = leer_ahora()
    bien = True
    for clave, valor, _ in AJUSTES:
        ok = puestos[clave] == valor
        print("  %-30s %-8s %s" % (clave, puestos[clave], "" if ok else "<- esperaba " + valor))
        bien = bien and ok
    return bien


if __name__ == "__main__":
    antes = leer_ahora()
    if all(antes[clave] == valor for clave, valor, _ in AJUSTES):
        print("Las coordenadas ya estan apagadas. No hay nada que cambiar.")
        sys.exit(0)

    for clave, valor, por_que in AJUSTES:
        print("%s: de %s a %s" % (clave, antes[clave], valor))
        print("    %s" % por_que)

    avisar()
    print("\navisado. apagando en 30 segundos")
    time.sleep(30)

    mc.power("stop")
    print("  apagando")
    if not esperar("offline"):
        sys.exit("no se termino de apagar. No toque el archivo.")

    editar()
    print("\n  archivo cambiado con el servidor apagado")

    mc.power("start")
    print("\n  prendiendo")
    if not esperar("running"):
        sys.exit("no termino de arrancar. Mira el panel.")

    time.sleep(5)
    print("\nverificacion, releido del servidor:")
    if not verificar():
        sys.exit("el mod piso el archivo. Algo del orden salio mal.")

    print("\nListo. Entra y proba /home casa: tiene que decir solo a donde fuiste.")
    print("Si a vos todavia te las muestra, escribi adentro del juego:")
    print("    /profile printTeleportCoordinates false")
