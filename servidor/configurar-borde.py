# -*- coding: utf-8 -*-
"""
Pone el borde del mundo, igual en las tres dimensiones.

    python servidor/configurar-borde.py

**El tamano es WORLD_BORDER_RADIUS en web/.env.local**, en bloques a cada lado
desde el spawn. El borde de Minecraft se define por su ancho total, asi que el
script pone el doble de eso.

Achicarlo deja afuera lo que ya construyeron, y eso no se deshace: si el numero
del .env es mas chico que el borde que ya tiene el servidor, el script avisa y no
toca nada. Para achicarlo de verdad hay que pedirlo con --achicar.

En 26.1 el borde es POR DIMENSIÓN. `WorldBorderCommand` trabaja siempre sobre
`source.getLevel().getWorldBorder()`, y cada nivel guarda el suyo aparte como
SavedData `minecraft:world_border` dentro de su propia carpeta `data/`. O sea que
un `/worldborder set` suelto en la consola configura solamente el overworld y en
el Nether se sigue caminando hasta el infinito. Por eso cada comando va con
`execute in <dimensión>`.

El tamaño sale de medir el mundo, no de una corazonada.

**2026-09-04, cuando se puso el borde por primera vez en 8.000:** las regiones
generadas llegaban a 2.560 bloques en el overworld y a 1.024 en el Nether, el End
no existía, el jugador más lejos era Titit0N en z = -1.860 y la cama más lejana la
de Felix_1256 en (922, -1.593). Con 4.000 de radio no quedaba afuera ni un chunk
de los que ya existían.

**2026-09-07, agrandado a 12.000:** se les quedó chico. Las regiones generadas del
overworld llegan hasta los 4.000 exactos, o sea que ya tocaron la pared; el Nether
igual, y el End —que en septiembre ni existía— también. El disco no es una traba:
el plan de Minehost está en ilimitado y el mundo entero pesa 1,3 GB.

En ese momento se eligió 6.000 de radio y no el doble a propósito. El borde no está
para que el mundo sea chico porque sí: está para que la gente se cruce, que es de lo
que vive un servidor de PvP. Con 6.000 había 50% más de distancia en cada dirección
y 2,25 veces más tierra —cruzarlo corriendo pasaba de 12 a 18 minutos— y encontrarse
seguía siendo algo que pasa.

**2026-09-08, agrandado a 10.000 de radio**, por pedido de Tobías. Son 2,8 veces la
tierra que había con 6.000 y cruzarlo corriendo pasa de 18 a unos 30 minutos. El
argumento de arriba no deja de ser cierto por eso: cuanto más grande, menos se
cruzan, y si el servidor se empieza a sentir vacío la palanca es justamente esta
variable. Lo que cambia es que ahora se toca sin editar código.

**2026-09-11, agrandado a 15.000 de radio**, otra vez por pedido de Tobías. Son
30.000 bloques de lado y 2,25 veces la tierra que había con 10.000. Vale lo mismo
que arriba, ahora con más fuerza: cruzarlo corriendo pasa de media hora a unos 45
minutos, y esta variable es la palanca para el otro lado si el servidor se empieza
a sentir vacío.

Y agrandar es una puerta de una sola dirección: achicarlo después dejaría afuera
todo lo que hayan construido en la tierra nueva.

El Nether lleva el mismo número y no la octava parte. Achicarlo para que
coincidiera geográficamente con el overworld cortaría chunks que ya están
generados, que es justo lo que no queremos.

**2026-09-22, el overworld deja de tener borde de verdad.** El lobby y el coliseo
se mudaron al overworld, a 500.000 bloques del spawn, y afuera del borde el juego
daña y empuja: con una pared en 15.000 no se puede jugar ahí. Como en 26.1 el
borde es por dimensión, el overworld pasa a 30.000.000 —sin límite práctico— y el
Nether y el End conservan el de siempre.

El límite del survival no desaparece: lo hace el datapack, en `sdp:tick`, que
devuelve al que se mete en la banda que va de 15.020 a 400.000 del spawn. Más allá
de los 400.000 están los dos lugares nuestros, y para llegar caminando hay que
cruzar la banda primero. WORLD_BORDER_RADIUS sigue siendo la palanca única: pone
la pared del Nether y del End, y este script verifica que el datapack diga lo
mismo.

Lo que se pierde con esto es el aviso rojo en pantalla al acercarse, que el borde
de verdad daba gratis. A cambio, el que cruza no recibe daño: lo devuelven.
"""
import io
import json
import os
import re
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import estilo as e
import mc

# El spawn está en (48, 97, 0), o sea a 48 bloques del centro: la diferencia no se
# nota ni caminando, y los números redondos hacen que el borde se explique solo.
CENTRO_X, CENTRO_Z = 0, 0

# Los bloques a cada lado desde el spawn, que es como se piensa un borde. El comando
# de Minecraft pide el ancho total, o sea el doble.
RADIO = int(mc.cfg.get("WORLD_BORDER_RADIUS", "10000"))
TAMANO = RADIO * 2

# El aviso vanilla son 5 bloques, que es encima del borde. A 32 la pantalla se
# tiñe de rojo con tiempo de frenar.
#
# El tiempo va en TICKS y no en segundos, aunque el comando conteste en segundos:
# con 10 el servidor respondió "0.50 second(s)". El default de vanilla son 300,
# que son los 15 segundos de siempre.
AVISO_BLOQUES = 32
AVISO_TICKS = 200

# El borde de verdad, el que frena y hace daño, queda solamente en el Nether y en
# el End. En 26.1 cada nivel guarda el suyo aparte, asi que esto se puede.
CON_PARED = ["minecraft:the_nether", "minecraft:the_end"]

# El overworld no puede tener pared: el lobby y el coliseo estan a 500.000 del
# spawn y afuera del borde el juego daña y empuja.
SIN_PARED = "minecraft:overworld"
TAMANO_SIN_PARED = 30000000

# Donde el datapack escribe el mismo limite, para que no se separen.
TICK = os.path.join(AQUI, "datapack", "data", "sdp", "function", "tick.mcfunction")


def t(texto, color=None, negrita=False):
    c = {"text": texto}
    if color:
        c["color"] = color
    if negrita:
        c["bold"] = True
    return c


def miles(n):
    return "{:,}".format(n).replace(",", ".")


def aplicar():
    for dim in CON_PARED:
        for comando in [
            "worldborder center %d %d" % (CENTRO_X, CENTRO_Z),
            "worldborder set %d" % TAMANO,
            "worldborder warning distance %d" % AVISO_BLOQUES,
            "worldborder warning time %d" % AVISO_TICKS,
        ]:
            mc.cmd("execute in %s run %s" % (dim, comando))
        print("  %s: %d bloques de lado" % (dim, TAMANO))

    mc.cmd("execute in %s run worldborder center %d %d" % (SIN_PARED, CENTRO_X, CENTRO_Z))
    mc.cmd("execute in %s run worldborder set %d" % (SIN_PARED, TAMANO_SIN_PARED))
    print("  %s: %d (sin pared; el límite lo hace el datapack)"
          % (SIN_PARED, TAMANO_SIN_PARED))


def el_datapack_dice_lo_mismo():
    """
    El limite blando del overworld esta escrito en `sdp:tick`, y tiene que decir
    el mismo numero que el .env. Separados, el Nether tendria una pared en un lado
    y el overworld te devolveria en otro, y nadie entenderia por que.
    """
    if not os.path.isfile(TICK):
        print("  (no encontré %s: no lo puedo verificar)" % TICK)
        return True
    texto = io.open(TICK, encoding="utf-8").read()
    esperado = "tp @s %d ~ ~" % (RADIO - 10)
    ok = esperado in texto
    print("  %-34s %s" % ("el datapack devuelve a %d" % (RADIO - 10),
                          "" if ok else "<- dice otra cosa, miralo"))
    return ok


def anunciar():
    mensaje = ["",
               t(chr(10) + "  " + e.RAYA * 22 + chr(10), e.APAGADO),
               t("  " + e.ESTRELLA + " EL MUNDO AHORA TIENE BORDE " + e.ESTRELLA + chr(10),
                 e.MARCA, negrita=True),
               t("  " + miles(RADIO) + " bloques", e.ACENTO, negrita=True),
               t(" para cada lado desde el spawn." + chr(10), e.ETIQUETA),
               t("  Todo lo que construyeron queda adentro." + chr(10), e.ETIQUETA),
               t("  " + e.RAYA * 22 + chr(10), e.APAGADO)]
    mc.cmd("tellraw @a " + json.dumps(mensaje))


def verificar():
    """Lee el log para confirmar los bordes, en vez de confiar en que salió."""
    antes = len(mc.read("/logs/latest.log").splitlines())
    for dim in CON_PARED + [SIN_PARED]:
        mc.cmd("execute in %s run worldborder get" % dim)
    time.sleep(2)
    lineas = mc.read("/logs/latest.log").splitlines()[antes:]
    encontrados = [l for l in lineas if "world border" in l.lower()]
    for l in encontrados:
        print("  " + l.split("]: ")[-1])
    return len(encontrados)


def radio_de_ahora():
    """
    El radio que ya tiene la pared, leido del servidor.

    Se lee del **Nether** y no del overworld: desde la mudanza el overworld no
    tiene pared —esta en 30.000.000— y preguntarle a el daria 15.000.000 de radio,
    o sea que cualquier numero del .env pareceria un achique.

    Es para no achicar el mundo por un numero mal tipeado: lo que quede afuera del
    borde nuevo no se puede volver a habitar, y las construcciones que haya ahi
    tampoco vuelven.
    """
    antes = len(mc.read("/logs/latest.log").splitlines())
    mc.cmd("execute in %s run worldborder get" % CON_PARED[0])
    time.sleep(2)
    for linea in mc.read("/logs/latest.log").splitlines()[antes:]:
        numeros = re.findall(r"[0-9]+", linea.split("]: ")[-1])
        if "world border" in linea.lower() and numeros:
            return int(numeros[0]) // 2
    return None


if __name__ == "__main__":
    ahora = radio_de_ahora()

    if ahora is None:
        sys.exit("no pude leer el borde que tiene el servidor. Esta prendido?")

    # Aunque la pared ya este bien, el overworld puede haber quedado con una: pasa
    # cada vez que alguien corre un `worldborder set` suelto en la consola, que va
    # a parar al overworld. Con el lobby a 500.000 eso deja a medio servidor afuera
    # del borde, recibiendo daño, asi que se revisa igual.
    if RADIO == ahora and str(TAMANO_SIN_PARED) in (mc.responde(
            "execute in %s run worldborder get" % SIN_PARED) or [""])[0]:
        print("El borde ya esta en %s de radio y el overworld sin pared." % miles(RADIO))
        if not el_datapack_dice_lo_mismo():
            sys.exit("pero el datapack dice otro numero. Arreglá sdp:tick.")
        sys.exit(0)

    if RADIO < ahora and "--achicar" not in sys.argv:
        print("WORLD_BORDER_RADIUS dice %s y el servidor tiene %s." % (miles(RADIO), miles(ahora)))
        print("Achicar el borde deja afuera todo lo que hayan construido entre los dos,")
        print("y eso no se deshace. No toque nada.")
        print("Si es a proposito: python servidor/configurar-borde.py --achicar")
        sys.exit(1)

    print("borde de %s a %s de radio" % (miles(ahora), miles(RADIO)))
    aplicar()
    # El anuncio va solo si se pide. La primera vez que se puso el borde valía la
    # pena avisar, porque era una pared nueva; agrandarlo no le saca nada a nadie.
    if "--anunciar" in sys.argv:
        anunciar()
    print("verificación:")
    if verificar() != len(CON_PARED) + 1:
        sys.exit("el servidor no confirmó el borde en las tres dimensiones")
    if not el_datapack_dice_lo_mismo():
        sys.exit("el datapack y el .env no dicen el mismo límite. Arreglá sdp:tick.")
    print("pared de %d bloques (%d de radio) en el Nether y el End, y el mismo"
          % (TAMANO, RADIO))
    print("límite hecho por el datapack en el overworld.")
