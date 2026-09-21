# -*- coding: utf-8 -*-
"""
Muestra el cofre de ender de un jugador, esté conectado o no.

    python servidor/ver-cofre.py                (lista los nombres conocidos)
    python servidor/ver-cofre.py Fulano         (el cofre de ender)
    python servidor/ver-cofre.py Fulano --todo  (tambien la mochila)

No es un comando del juego a proposito. Lo que hace falta mirar es el cofre de
alguien que ya no se puede conectar (un baneado por IP), y los comandos tipo
/invsee abren el inventario del jugador ONLINE: con el baneado no hay a quien
abrirle nada. Esto lee el archivo del jugador, que es donde quedo todo cuando se
desconecto por ultima vez. Corre en esta PC, contra la API del panel, asi que no
hay nada que instalar en el servidor ni forma de que otro jugador lo vea.

Tres cosas que tienen su trampa:

- **El UUID se calcula, no se busca.** El servidor esta en offline-mode, asi que
  el UUID de un jugador es MD5("OfflinePlayer:" + nombre) con los bits de
  version de un UUID v3. El `usercache.json` NO sirve para esto: tiene tambien
  entradas con el UUID real de Mojang (v4, de cuando alguien entro con cuenta
  premium) que no corresponden a ningun archivo. El nombre distingue mayusculas.

- **El archivo hay que bajarlo binario.** `mc.read()` decodifica a texto y deja
  el gzip hecho pedazos. El endpoint `/files/download` del panel devuelve una URL
  firmada y de ahi sale el archivo tal cual.

- **Es la foto del ultimo logout.** Si el jugador esta conectado, lo que se ve
  aca es lo que tenia cuando entro. Para un baneado da igual: no se va a volver
  a guardar nunca.

En 26.1 la carpeta es `world/players/data/` (antes era `world/playerdata/`).
"""
import gzip
import hashlib
import io
import json
import os
import struct
import sys
import urllib.parse
import urllib.request
import uuid as _uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import mc

CARPETA = "/world/players/data"
RANURAS_COFRE = 27


# ----------------------------------------------------------------------- NBT
# Un lector de NBT en 60 lineas. No hay libreria instalada y tampoco hace falta:
# el formato son trece tipos y ninguno tiene vuelta.
def _leer(f, tipo):
    if tipo == 1:
        return struct.unpack(">b", f.read(1))[0]
    if tipo == 2:
        return struct.unpack(">h", f.read(2))[0]
    if tipo == 3:
        return struct.unpack(">i", f.read(4))[0]
    if tipo == 4:
        return struct.unpack(">q", f.read(8))[0]
    if tipo == 5:
        return struct.unpack(">f", f.read(4))[0]
    if tipo == 6:
        return struct.unpack(">d", f.read(8))[0]
    if tipo == 7:
        return list(f.read(struct.unpack(">i", f.read(4))[0]))
    if tipo == 8:
        return f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8", "replace")
    if tipo == 9:
        interno = f.read(1)[0]
        cuantos = struct.unpack(">i", f.read(4))[0]
        # Una lista vacia se escribe con tipo 0 y ahi no hay nada que leer.
        if interno == 0:
            return []
        return [_leer(f, interno) for _ in range(cuantos)]
    if tipo == 10:
        compuesto = {}
        while True:
            interno = f.read(1)[0]
            if interno == 0:
                return compuesto
            largo = struct.unpack(">H", f.read(2))[0]
            # El nombre se lee ANTES que el valor y en su propia linea: Python
            # evalua el lado derecho de una asignacion primero, asi que
            # compuesto[leer_nombre()] = _leer(...) lee los bytes al reves y
            # desincroniza todo el archivo.
            nombre = f.read(largo).decode("utf-8", "replace")
            compuesto[nombre] = _leer(f, interno)
    if tipo == 11:
        cuantos = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(">%di" % cuantos, f.read(4 * cuantos)))
    if tipo == 12:
        cuantos = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(">%dq" % cuantos, f.read(8 * cuantos)))
    raise ValueError("tipo de NBT desconocido: %d" % tipo)


def cargar_nbt(datos):
    f = io.BytesIO(gzip.decompress(datos))
    tipo = f.read(1)[0]
    f.read(struct.unpack(">H", f.read(2))[0])  # el nombre de la raiz, siempre vacio
    return _leer(f, tipo)


# ------------------------------------------------------------------ servidor
def uuid_de(nombre):
    bytes_ = bytearray(hashlib.md5(("OfflinePlayer:" + nombre).encode("utf-8")).digest())
    bytes_[6] = (bytes_[6] & 0x0F) | 0x30  # version 3
    bytes_[8] = (bytes_[8] & 0x3F) | 0x80  # variante RFC 4122
    return str(_uuid.UUID(bytes=bytes(bytes_)))


def bajar(ruta):
    firmada = json.loads(mc.call("/files/download?file=" + urllib.parse.quote(ruta)))
    with urllib.request.urlopen(firmada["attributes"]["url"]) as respuesta:
        return respuesta.read()


def nombres_conocidos():
    """Los nombres del usercache, sin repetir y en el orden en que aparecen."""
    vistos = []
    for entrada in json.loads(mc.read("/usercache.json")):
        if entrada["name"] not in vistos:
            vistos.append(entrada["name"])
    return vistos


# ------------------------------------------------------------------- dibujar
def nombre_item(item):
    return item.get("id", "?").replace("minecraft:", "")


def detalles(item):
    """Lo que hace distinto a un item del mismo id: nombre puesto y encantamientos."""
    componentes = item.get("components", {})
    partes = []
    custom = componentes.get("minecraft:custom_name")
    if custom:
        partes.append(custom)
    for clave in ("minecraft:enchantments", "minecraft:stored_enchantments"):
        encantamientos = componentes.get(clave)
        if encantamientos:
            partes.append(", ".join(
                "%s %d" % (k.replace("minecraft:", ""), v)
                for k, v in sorted(encantamientos.items())))
    return "  (%s)" % ", ".join(partes) if partes else ""


def mostrar(items, ranuras, titulo):
    ocupadas = {i.get("Slot", 0) % 256: i for i in items}
    print("\n%s  -  %d de %d ranuras" % (titulo, len(ocupadas), ranuras))
    if not ocupadas:
        print("  (vacio)")
        return
    for ranura in range(ranuras):
        item = ocupadas.get(ranura)
        if not item:
            continue
        print("  %2d  %3d x %s%s" % (ranura, item.get("count", 1),
                                     nombre_item(item), detalles(item)))
        # Lo que guarda alguien casi siempre esta adentro de una shulker, asi que
        # sin esto el cofre entero se ve como "tres cajas moradas".
        for guardado in item.get("components", {}).get("minecraft:container", []):
            adentro = guardado.get("item", {})
            print("        %3d x %s%s" % (adentro.get("count", 1),
                                          nombre_item(adentro), detalles(adentro)))


# --------------------------------------------------------------------- main
argumentos = [a for a in sys.argv[1:] if not a.startswith("--")]
todo = "--todo" in sys.argv

if not argumentos:
    print("Jugadores que alguna vez entraron:\n")
    for nombre in nombres_conocidos():
        print("  " + nombre)
    print("\n    python servidor/ver-cofre.py <nombre>")
    sys.exit(0)

jugador = argumentos[0]
uuid = uuid_de(jugador)
try:
    datos = cargar_nbt(bajar("%s/%s.dat" % (CARPETA, uuid)))
except urllib.error.HTTPError:
    print("No hay archivo de '%s' (%s)." % (jugador, uuid))
    print("El nombre distingue mayusculas; los conocidos son:")
    print("  " + ", ".join(nombres_conocidos()))
    sys.exit(1)

print("%s  -  %s" % (jugador, uuid))
mostrar(datos.get("EnderItems", []), RANURAS_COFRE, "COFRE DE ENDER")
if todo:
    # La mochila son 36 ranuras (0-8 la barra, 9-35 el resto) y despues la
    # armadura y la mano izquierda, que en 26.1 ya no viven en Inventory.
    mostrar(datos.get("Inventory", []), 36, "MOCHILA")
