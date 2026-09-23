# -*- coding: utf-8 -*-
"""
Muda el lobby y el coliseo al overworld, lejisimos del spawn.

    python servidor/mudar-al-overworld.py            copia y verifica
    python servidor/mudar-al-overworld.py --verificar solo compara lo ya copiado

## Por que

Las dimensiones propias (`sdp:lobby` y `sdp:coliseo`) rompen el login: al
conectarse alguien que quedo guardado adentro de una, el servidor lo agrega dos
veces al mundo ("Force-added player with duplicate UUID") y al cliente le queda el
mapa vacio cargando para siempre. Las tres formas posibles de arreglarlo fallan, y
una llego a borrarle el inventario a dos jugadores. Con todo en el overworld el
problema no puede existir: hay un solo nivel que pueda reclamar el UUID.

## Adonde van

A 500.000 bloques del spawn en X, y por arriba de las nubes.

  - El **borde del overworld** se agranda a 30.000.000 para que ahi no haya daño
    ni empujon. Es por dimension en 26.1, asi que el Nether y el End conservan su
    borde de siempre. El limite del survival pasa a hacerlo el datapack, que
    devuelve al que se mete en la banda prohibida (15.000 a 400.000 del spawn).
  - La altura salio de **medir el destino**: el terreno del overworld llega a y=88
    donde va el lobby y a y=103 donde va el coliseo. Poniendolos a 250 y pico
    quedan 150 bloques de aire por debajo y no hay que limpiar ni un bloque. Las
    nubes estan a y=192, o sea que las dos construcciones quedan por encima.

## La aritmetica, que es toda la que hay

    lobby     x + 500.000    y + 250    z igual
    coliseo   x + 500.000    y + 180    z igual

Las cajas de origen no son a ojo: salen de leer los archivos de region con
`region.py` y quedarse con la caja exacta de los bloques que no son aire.

## Como se copia

Con `/clone from <mundo> ... to <mundo> ...`, que funciona entre dimensiones. Va
por franjas porque un clone no pasa de 32.768 bloques, y las dos areas tienen que
estar con `forceload` o el comando contesta "that position is not loaded" y no
copia nada.

**Y despues se verifica bloque por bloque**: se bajan las regiones del origen y
del destino, se aplica la traslacion y se comparan los dos diccionarios. Tiene que
dar cero faltantes, cero sobrantes y cero distintos. En este proyecto las
conclusiones "por logica" ya fallaron tres veces.

Lo viejo **no se borra**: las dimensiones quedan hasta que la copia este mirada.
"""
import json
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc
import region

SCRATCH = os.path.join(AQUI, "..", "respaldos", "mudanza")

# --------------------------------------------------------------------- las cajas
#
# x1,y1,z1,x2,y2,z2 del origen, y cuanto se le suma a cada eje. Las cajas de origen
# se midieron con region.py sobre los .mca bajados del servidor: son exactamente
# los bloques que no son aire, sin margen ni redondeo.

MUDANZAS = [
    {
        "nombre": "lobby",
        "origen": "sdp:lobby",
        "caja": (-30, -4, -30, 31, 32, 30),
        "mueve": (500000, 250, 0),
        "regiones_origen": ["r.0.0.mca", "r.0.-1.mca", "r.-1.0.mca", "r.-1.-1.mca"],
        "regiones_destino": ["r.976.0.mca", "r.976.-1.mca", "r.977.0.mca", "r.977.-1.mca"],
    },
    {
        "nombre": "coliseo",
        "origen": "sdp:coliseo",
        "caja": (1145, 70, 11590, 1205, 126, 11650),
        "mueve": (500000, 180, 0),
        "regiones_origen": ["r.2.22.mca", "r.2.23.mca", "r.1.22.mca", "r.3.22.mca"],
        "regiones_destino": ["r.978.22.mca", "r.978.23.mca", "r.979.22.mca", "r.979.23.mca"],
    },
]

DESTINO = "minecraft:overworld"
TOPE = 30000  # un clone no pasa de 32.768 bloques

# Lo que el juego cambia solo y no es un error de copia: el pasto crece sobre la
# tierra y se marchita segun la luz, asi que entre que se copia y que se mira ya
# hay bloques distintos en los dos lados. Medido: 5 de 32.323 en el coliseo.
IGUALES = [{"minecraft:grass_block", "minecraft:dirt"}]


def el_juego_lo_hizo(uno, otro):
    return any({uno, otro} <= par for par in IGUALES)


def bajar(mundo, nombres, carpeta):
    """Baja los .mca de una dimension y devuelve las rutas locales."""
    os.makedirs(carpeta, exist_ok=True)
    rutas = []
    for nombre in nombres:
        destino = os.path.join(carpeta, nombre)
        datos = mc.download("/world/dimensions/%s/region/%s" % (mundo, nombre))
        with open(destino, "wb") as archivo:
            archivo.write(datos)
        rutas.append(destino)
        print("    %-16s %8d bytes" % (nombre, len(datos)))
    return rutas


def carpeta_de(mundo):
    return mundo.replace(":", "/")


def recortar(todo, caja):
    """Los bloques de un mapa que caen adentro de la caja."""
    x1, y1, z1, x2, y2, z2 = caja
    return {p: n for p, n in todo.items()
            if x1 <= p[0] <= x2 and y1 <= p[1] <= y2 and z1 <= p[2] <= z2}


def mover(todo, mueve):
    dx, dy, dz = mueve
    return {(x + dx, y + dy, z + dz): n for (x, y, z), n in todo.items()}


def franjas(caja):
    """Las franjas de X que entran en el tope de un clone."""
    x1, y1, z1, x2, y2, z2 = caja
    porColumna = (y2 - y1 + 1) * (z2 - z1 + 1)
    ancho = max(1, TOPE // porColumna)
    x = x1
    while x <= x2:
        hasta = min(x + ancho - 1, x2)
        yield x, hasta
        x = hasta + 1


def forceload(mundo, caja, prender):
    x1, _, z1, x2, _, z2 = caja
    orden = "add %d %d %d %d" % (x1, z1, x2, z2) if prender else "remove %d %d %d %d" % (x1, z1, x2, z2)
    mc.cmd("execute in %s run forceload %s" % (mundo, orden))


def clonar(m):
    x1, y1, z1, x2, y2, z2 = m["caja"]
    dx, dy, dz = m["mueve"]
    quejas = []
    for desde, hasta in franjas(m["caja"]):
        comando = ("clone from %s %d %d %d %d %d %d to %s %d %d %d replace force"
                   % (m["origen"], desde, y1, z1, hasta, y2, z2, DESTINO,
                      desde + dx, y1 + dy, z1 + dz))
        respuesta = mc.responde(comando, espera=3)
        util = [l for l in respuesta if "cloned" in l.lower() or "not loaded" in l
                or "Too many" in l or "No blocks" in l or "Unknown" in l]
        print("    x %d..%d  ->  %s" % (desde, hasta, util[0] if util else "(sin respuesta)"))
        if not util or "cloned" not in util[0].lower():
            quejas.append("x %d..%d: %s" % (desde, hasta, util[0] if util else "sin respuesta"))
    return quejas


def verificar(m, carpeta):
    """Compara bloque por bloque el origen con lo que quedo en el destino."""
    print("  bajando el origen:")
    rutas_o = bajar(carpeta_de(m["origen"]), m["regiones_origen"],
                    os.path.join(carpeta, m["nombre"], "origen"))
    print("  bajando el destino:")
    rutas_d = bajar(carpeta_de(DESTINO), m["regiones_destino"],
                    os.path.join(carpeta, m["nombre"], "destino"))

    dx, dy, dz = m["mueve"]
    x1, y1, z1, x2, y2, z2 = m["caja"]
    cajaDestino = (x1 + dx, y1 + dy, z1 + dz, x2 + dx, y2 + dy, z2 + dz)

    origen = mover(recortar(region.mapa(rutas_o), m["caja"]), m["mueve"])
    destino = recortar(region.mapa(rutas_d), cajaDestino)

    faltan = {p: n for p, n in origen.items() if p not in destino}
    sobran = {p: n for p, n in destino.items() if p not in origen}
    distintos = {p: (n, destino[p]) for p, n in origen.items()
                 if p in destino and destino[p] != n
                 and not el_juego_lo_hizo(n, destino[p])}
    crecieron = sum(1 for p, n in origen.items()
                    if p in destino and destino[p] != n and el_juego_lo_hizo(n, destino[p]))

    print("  origen  %6d bloques no-aire" % len(origen))
    print("  destino %6d bloques no-aire" % len(destino))
    print("  faltan %d, sobran %d, distintos %d%s"
          % (len(faltan), len(sobran), len(distintos),
             "  (+%d que el pasto cambio solo)" % crecieron if crecieron else ""))
    for cual, cuales in (("faltan", faltan), ("sobran", sobran)):
        for punto in list(cuales)[:5]:
            print("    %s: %s %s" % (cual, punto, cuales[punto]))
    for punto, (a, b) in list(distintos.items())[:5]:
        print("    distinto: %s  %s -> %s" % (punto, a, b))
    return not (faltan or sobran or distintos)


CONFIG_DUELOS = "/config/duelos-de-pepe.json"


def apuntar_la_arena():
    """
    Le mueve al mod de duelos la arena y los dos puntos de aparicion, con la misma
    cuenta con la que se movieron los bloques.

    **Va con el servidor apagado.** El mod lee este archivo al arrancar y lo
    reescribe al guardar, asi que editarlo prendido no sirve de nada.

    Los giros no se tocan: la copia es una traslacion, no una rotacion, y el que
    aparece mirando al este sigue mirando al este.
    """
    coliseo = [m for m in MUDANZAS if m["nombre"] == "coliseo"][0]
    dx, dy, dz = coliseo["mueve"]

    datos = json.loads(mc.read(CONFIG_DUELOS))
    if "arena" not in datos:
        sys.exit("En %s no hay 'arena'. No toco nada." % CONFIG_DUELOS)

    arena = datos["arena"]
    if arena.get("mundo") == DESTINO:
        print("  la arena ya estaba en %s" % DESTINO)
        return
    if arena.get("mundo") != coliseo["origen"]:
        sys.exit("La arena esta en '%s' y esperaba '%s'. No toco nada."
                 % (arena.get("mundo"), coliseo["origen"]))

    # Las claves van escritas una por una a proposito. Adivinarlas por como
    # terminan es de esas cosas que funcionan hasta que alguien agrega un campo, y
    # una coordenada mal sumada es un jugador que aparece adentro de una pared.
    POR_EJE = {
        "x": ("x1", "x2", "punto1x", "punto2x"),
        "y": ("y1", "y2", "punto1y", "punto2y"),
        "z": ("z1", "z2", "punto1z", "punto2z"),
    }
    conocidas = {"mundo", "giro1", "giro2"}
    for claves in POR_EJE.values():
        conocidas.update(claves)
    de_mas = set(arena) - conocidas
    if de_mas:
        sys.exit("La arena tiene campos que no se como mover: %s. No toco nada."
                 % ", ".join(sorted(de_mas)))

    arena["mundo"] = DESTINO
    for eje, cuanto in (("x", dx), ("y", dy), ("z", dz)):
        for clave in POR_EJE[eje]:
            if clave in arena:
                arena[clave] += cuanto

    # Las gradas viven en LobbyServidor.java y no en este archivo, pero si algun dia
    # vuelven aca, que se muden solas.
    for otra in ("gradas",):
        if otra in datos and isinstance(datos[otra], dict):
            datos[otra]["mundo"] = DESTINO
            for eje, cuanto in (("x", dx), ("y", dy), ("z", dz)):
                if eje in datos[otra]:
                    datos[otra][eje] += cuanto

    mc.write(CONFIG_DUELOS, json.dumps(datos, indent=2, ensure_ascii=False))
    print("  arena: x %d..%d  y %d..%d  z %d..%d"
          % (arena["x1"], arena["x2"], arena["y1"], arena["y2"], arena["z1"], arena["z2"]))
    print("  aparecen en (%.1f, %.1f, %.1f) y (%.1f, %.1f, %.1f)"
          % (arena["punto1x"], arena["punto1y"], arena["punto1z"],
             arena["punto2x"], arena["punto2y"], arena["punto2z"]))


if __name__ == "__main__":
    if "--arena" in sys.argv:
        estado = json.loads(mc.call("/resources"))["attributes"]["current_state"]
        if estado != "offline":
            sys.exit("El servidor esta '%s'. Esto va con el servidor APAGADO: "
                     "el mod de duelos pisa su config al guardar." % estado)
        print("la arena del coliseo:")
        apuntar_la_arena()
        sys.exit(0)

    solo_verificar = "--verificar" in sys.argv
    carpeta = os.path.abspath(SCRATCH)

    borde = mc.responde("execute in %s run worldborder get" % DESTINO)
    print("borde del overworld: %s" % (borde[0] if borde else "?"))
    if not borde or "30000000" not in borde[0]:
        sys.exit("El borde del overworld no esta agrandado. A 500.000 el juego "
                 "dañaria y empujaria a todo el mundo. No copio nada.")

    if not solo_verificar:
        for m in MUDANZAS:
            x1, y1, z1, x2, y2, z2 = m["caja"]
            dx, dy, dz = m["mueve"]
            print("\n%s: x %d..%d  y %d..%d  z %d..%d" % (m["nombre"], x1, x2, y1, y2, z1, z2))
            print("  va a  x %d..%d  y %d..%d  z %d..%d"
                  % (x1 + dx, x2 + dx, y1 + dy, y2 + dy, z1 + dz, z2 + dz))

            forceload(m["origen"], m["caja"], True)
            forceload(DESTINO, (x1 + dx, y1 + dy, z1 + dz, x2 + dx, y2 + dy, z2 + dz), True)
            time.sleep(8)

            quejas = clonar(m)
            if quejas:
                print("  QUEJAS:")
                for q in quejas:
                    print("    " + q)

            forceload(m["origen"], m["caja"], False)

        mc.cmd("save-all")
        time.sleep(10)

    print("\nverificacion, bloque por bloque:")
    bien = True
    for m in MUDANZAS:
        print("\n  %s:" % m["nombre"])
        bien = verificar(m, carpeta) and bien

    print()
    if bien:
        print("La copia es identica en los dos. Las dimensiones viejas siguen ahi.")
    else:
        sys.exit("La copia NO coincide. No sigas con la mudanza hasta entender esto.")
