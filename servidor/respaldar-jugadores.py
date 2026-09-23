# -*- coding: utf-8 -*-
"""
Baja a esta PC lo que un jugador no puede recuperar: su inventario y su estado.

    python servidor/respaldar-jugadores.py

## Por que existe

El 2026-09-22 el lobby le borro el inventario a dos jugadores y **no se pudo
recuperar ni uno**. El servidor tiene un solo backup —el plan de Minehost permite
uno y ya estaba ocupado por uno de hace once dias— y Minecraft guarda nada mas que
dos copias de cada jugador, `<uuid>.dat` y `<uuid>.dat_old`, que se pisan entre
ellas cada vez que uno entra y sale. Para cuando alguien avisa, las dos ya son
copias del desastre.

Esto baja las dos, todos los dias que se corra, y las deja fechadas. Son unos
pocos MB: 50 archivos de jugador pesan menos que una foto.

## Que baja

  - `/world/players/data/` — los `.dat` y `.dat_old` de todos: inventario, cofre
    de ender, posicion, vida, experiencia y avances.
  - Los JSON de estado de nuestros mods, que son los que saben que le debemos a
    quien: `lobby-de-pepe.json`, `duelos-de-pepe.json`, `mirones-de-pepe.json` y
    `equipos-de-pepe.json`.
  - Las zonas protegidas de Safe Zone.

Y despues **muestra un resumen**: cuantos items tiene cada uno encima y en el cofre
de ender. Un jugador que ayer tenia 37 casilleros llenos y hoy tiene 0 salta a la
vista sin abrir nada.

## Como dejarlo automatico en Windows

Una vez, en una consola de administrador:

    schtasks /create /tn "Respaldo SOBRINOS DE PEPE" /tr ^
      "cmd /c cd /d C:\\Users\\Tobi\\Desktop\\servermc && python servidor\\respaldar-jugadores.py" ^
      /sc daily /st 06:00

Pide que la PC este prendida a esa hora. El respaldo del servidor entero lo hace
el panel solo, todos los dias a las 5:30 (schedule "Respaldo diario").
"""
import json
import os
import shutil
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

RAIZ = os.path.dirname(AQUI)
DESTINO = os.path.join(RAIZ, "respaldos")

JUGADORES = "/world/players/data"
SUELTOS = [
    "/config/lobby-de-pepe.json",
    "/config/duelos-de-pepe.json",
    "/config/mirones-de-pepe.json",
    "/config/equipos-de-pepe.json",
    "/world/safe-zone/claims.json",
]

# Cuantas carpetas de respaldo se conservan. Con una por dia, un mes y medio.
CUANTOS_GUARDO = 45


def bajar(ruta, adonde):
    """Baja un archivo del servidor tal cual, sin pasarlo por texto."""
    firmada = json.loads(
        mc.call("/files/download?file=" + urllib.parse.quote(ruta)))["attributes"]["url"]
    with urllib.request.urlopen(firmada) as respuesta:
        open(adonde, "wb").write(respuesta.read())


def leer_raiz(datos):
    """El NBT de un .dat, lo justo para contar lo que tiene adentro."""
    import gzip
    import struct

    def tag(d, i, t):
        if t == 1: return d[i] - 256 if d[i] > 127 else d[i], i + 1
        if t == 2: return struct.unpack_from(">h", d, i)[0], i + 2
        if t == 3: return struct.unpack_from(">i", d, i)[0], i + 4
        if t == 4: return struct.unpack_from(">q", d, i)[0], i + 8
        if t == 5: return struct.unpack_from(">f", d, i)[0], i + 4
        if t == 6: return struct.unpack_from(">d", d, i)[0], i + 8
        if t == 7:
            n = struct.unpack_from(">i", d, i)[0]
            return d[i + 4:i + 4 + n], i + 4 + n
        if t == 8:
            n = struct.unpack_from(">H", d, i)[0]
            return d[i + 2:i + 2 + n].decode("utf-8", "replace"), i + 2 + n
        if t == 9:
            et = d[i]; n = struct.unpack_from(">i", d, i + 1)[0]; i += 5
            out = []
            for _ in range(n):
                v, i = tag(d, i, et); out.append(v)
            return out, i
        if t == 10:
            out = {}
            while True:
                et = d[i]; i += 1
                if et == 0: return out, i
                ln = struct.unpack_from(">H", d, i)[0]; i += 2
                nom = d[i:i + ln].decode("utf-8", "replace"); i += ln
                v, i = tag(d, i, et); out[nom] = v
        if t == 11:
            n = struct.unpack_from(">i", d, i)[0]
            return list(struct.unpack_from(">%di" % n, d, i + 4)), i + 4 + 4 * n
        if t == 12:
            n = struct.unpack_from(">i", d, i)[0]
            return list(struct.unpack_from(">%dq" % n, d, i + 4)), i + 4 + 8 * n
        raise ValueError("tag %d" % t)

    d = gzip.decompress(datos)
    i = 0
    t = d[i]; i += 1
    ln = struct.unpack_from(">H", d, i)[0]; i += 2 + ln
    return tag(d, i, t)[0]


def nombres_por_uuid():
    """
    El nombre de cada UUID, para que el resumen se lea.

    Sale de `usercache.json`, que es la lista que el propio servidor mantiene con
    todos los que entraron alguna vez. El log del dia solo tiene a los de hoy.
    """
    try:
        return {x["uuid"]: x["name"] for x in json.loads(mc.read("/usercache.json"))}
    except Exception:
        return {}


def limpiar_los_viejos():
    if not os.path.isdir(DESTINO):
        return
    carpetas = sorted(n for n in os.listdir(DESTINO)
                      if os.path.isdir(os.path.join(DESTINO, n)))
    for vieja in carpetas[:-CUANTOS_GUARDO]:
        shutil.rmtree(os.path.join(DESTINO, vieja), ignore_errors=True)
        print("  borrado el respaldo viejo %s" % vieja)


if __name__ == "__main__":
    cuando = datetime.now().strftime("%Y-%m-%d-%H%M")
    carpeta = os.path.join(DESTINO, cuando)
    os.makedirs(os.path.join(carpeta, "jugadores"), exist_ok=True)

    print("respaldo %s" % cuando)

    # Primero se le pide al servidor que guarde. Sin esto, lo de los que estan
    # conectados ahora mismo vive en memoria y el archivo que se baja es el de la
    # ultima vez que salieron: justo la gente que esta jugando seria la que queda
    # peor respaldada.
    try:
        mc.cmd("save-all")
        time.sleep(8)
        print("  el servidor guardo lo que tenia en memoria")
    except Exception as error:
        print("  (no pude pedirle que guarde: %s)" % str(error)[:60])

    archivos = mc.ls(JUGADORES)
    for nombre in sorted(archivos):
        bajar(JUGADORES + "/" + nombre, os.path.join(carpeta, "jugadores", nombre))
    print("  %d archivos de jugador" % len(archivos))

    for ruta in SUELTOS:
        try:
            bajar(ruta, os.path.join(carpeta, os.path.basename(ruta)))
            print("  " + os.path.basename(ruta))
        except Exception as error:
            print("  (sin %s: %s)" % (os.path.basename(ruta), str(error)[:60]))

    limpiar_los_viejos()

    print("\nlo que tiene cada uno hoy:")
    tabla = nombres_por_uuid()
    filas = []
    for nombre in sorted(archivos):
        if not nombre.endswith(".dat"):
            continue
        uuid = nombre[:-4]
        try:
            raiz = leer_raiz(open(os.path.join(carpeta, "jugadores", nombre), "rb").read())
        except Exception:
            continue
        filas.append((tabla.get(uuid, uuid[:8]),
                      len(raiz.get("Inventory", [])),
                      len(raiz.get("EnderItems", [])),
                      raiz.get("Dimension", "?")))
    filas.sort(key=lambda f: (-f[1], f[0]))
    print("  %-18s %-10s %-10s %s" % ("jugador", "encima", "ender", "donde"))
    for quien, inv, ender, donde in filas:
        aviso = "   <- VACIO" if inv == 0 and ender > 0 else ""
        print("  %-18s %-10d %-10d %s%s" % (quien, inv, ender, donde, aviso))

    print("\nGuardado en respaldos/%s" % cuando)
