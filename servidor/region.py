# -*- coding: utf-8 -*-
"""
Lee los archivos de region de Minecraft (.mca) y dice que bloques hay adentro.

    python servidor/region.py "lobby-e2451/lobby1 - Copy/region"

Existe para dos cosas, las dos de la mudanza al overworld:

  1. **Saber donde esta lo construido.** Un mundo de lobby bajado de internet
     tiene chunks generados por todos lados; lo que importa es la caja que
     realmente ocupan los edificios, y esa caja es la que se clona.
  2. **Verificar la copia.** Despues de clonar se bajan las regiones del destino
     y se comparan bloque por bloque con el origen. En este proyecto las
     conclusiones "por logica" ya fallaron tres veces: la copia se mira.

El formato, para el que venga despues:

  - El .mca arranca con 4096 bytes de tabla: 1024 entradas de 4 bytes, una por
    chunk, con el offset en sectores de 4096 y el largo en sectores. Todo en
    cero significa que ese chunk no existe.
  - Cada chunk empieza con su largo (4 bytes) y el tipo de compresion (1 byte):
    1 gzip, 2 zlib, 3 crudo. Adentro hay NBT.
  - Desde 1.18 los bloques viven en `sections`, una por cada 16 de alto, con su
    `block_states` = {`palette`: [...], `data`: long[]}. Si no hay `data`, toda
    la seccion es el unico elemento de la paleta (casi siempre aire).
  - Los indices de la paleta se empaquetan en longs **sin cruzar el borde** de
    cada long (formato 1.16+): entran 64 // bits por long y los bits que sobran
    arriba se tiran.
"""
import gzip
import os
import struct
import sys
import zlib

AIRE = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")


# ------------------------------------------------------------------------- NBT

def _cadena(datos, i):
    largo = struct.unpack_from(">H", datos, i)[0]
    return datos[i + 2:i + 2 + largo].decode("utf-8", "replace"), i + 2 + largo


def _valor(datos, i, tipo):
    """Devuelve (valor, siguiente indice). Los tipos son los del formato NBT."""
    if tipo == 1:
        return struct.unpack_from(">b", datos, i)[0], i + 1
    if tipo == 2:
        return struct.unpack_from(">h", datos, i)[0], i + 2
    if tipo == 3:
        return struct.unpack_from(">i", datos, i)[0], i + 4
    if tipo == 4:
        return struct.unpack_from(">q", datos, i)[0], i + 8
    if tipo == 5:
        return struct.unpack_from(">f", datos, i)[0], i + 4
    if tipo == 6:
        return struct.unpack_from(">d", datos, i)[0], i + 8
    if tipo == 7:
        n = struct.unpack_from(">i", datos, i)[0]
        return datos[i + 4:i + 4 + n], i + 4 + n
    if tipo == 8:
        return _cadena(datos, i)
    if tipo == 9:
        dentro = datos[i]
        n = struct.unpack_from(">i", datos, i + 1)[0]
        i += 5
        lista = []
        for _ in range(max(0, n)):
            item, i = _valor(datos, i, dentro)
            lista.append(item)
        return lista, i
    if tipo == 10:
        compuesto = {}
        while True:
            dentro = datos[i]
            i += 1
            if dentro == 0:
                return compuesto, i
            nombre, i = _cadena(datos, i)
            compuesto[nombre], i = _valor(datos, i, dentro)
    if tipo == 11:
        n = struct.unpack_from(">i", datos, i)[0]
        return list(struct.unpack_from(">%di" % n, datos, i + 4)), i + 4 + 4 * n
    if tipo == 12:
        n = struct.unpack_from(">i", datos, i)[0]
        return list(struct.unpack_from(">%dq" % n, datos, i + 4)), i + 4 + 8 * n
    raise ValueError("tipo NBT desconocido: %d" % tipo)


def leer_nbt(datos):
    """El compuesto raiz de un NBT ya descomprimido."""
    if datos[0] != 10:
        raise ValueError("el NBT no empieza con un compuesto")
    _, i = _cadena(datos, 1)
    valor, _ = _valor(datos, i, 10)
    return valor


# --------------------------------------------------------------------- regiones

def chunks(datos):
    """Itera (cx, cz, nbt) de un .mca, en coordenadas de chunk del mundo."""
    if len(datos) < 8192:
        return
    for indice in range(1024):
        entrada = struct.unpack_from(">I", datos, indice * 4)[0]
        offset, sectores = entrada >> 8, entrada & 0xFF
        if offset == 0 or sectores == 0:
            continue
        crudo = datos[offset * 4096:(offset + sectores) * 4096]
        if len(crudo) < 5:
            continue
        largo = struct.unpack_from(">I", crudo, 0)[0]
        cuerpo = crudo[5:4 + largo]
        tipo = crudo[4]
        if tipo == 1:
            cuerpo = gzip.decompress(cuerpo)
        elif tipo == 2:
            cuerpo = zlib.decompress(cuerpo)
        elif tipo != 3:
            continue
        nbt = leer_nbt(cuerpo)
        yield nbt.get("xPos", indice % 32), nbt.get("zPos", indice // 32), nbt


def cuando(datos):
    """
    {(cx, cz): hora} de cuando se guardo cada chunk, en segundos epoch.

    Los .mca traen una segunda tabla de 4096 bytes, justo despues de la de
    ubicaciones, con la fecha del ultimo guardado de cada chunk. Sirve para saber
    si un chunk es de siempre o se genero recien, que es la diferencia entre "el
    login tardo porque genero terreno" y "el login tardo por otra cosa".
    """
    salida = {}
    if len(datos) < 8192:
        return salida
    for indice in range(1024):
        entrada = struct.unpack_from(">I", datos, indice * 4)[0]
        if entrada == 0:
            continue
        salida[indice] = struct.unpack_from(">I", datos, 4096 + indice * 4)[0]
    return salida


def bloques(nbt):
    """
    Itera (x, y, z, nombre) de los bloques NO de aire de un chunk.

    Las coordenadas son absolutas, las del mundo.
    """
    cx, cz = nbt["xPos"] * 16, nbt["zPos"] * 16
    for seccion in nbt.get("sections", []):
        estados = seccion.get("block_states")
        if not estados:
            continue
        paleta = [p["Name"] for p in estados.get("palette", [])]
        if not paleta:
            continue
        base = seccion["Y"] * 16
        datos = estados.get("data")

        if datos is None:
            # Toda la seccion es el unico bloque de la paleta.
            if paleta[0] in AIRE:
                continue
            for y in range(16):
                for z in range(16):
                    for x in range(16):
                        yield cx + x, base + y, cz + z, paleta[0]
            continue

        bits = max(4, (len(paleta) - 1).bit_length())
        porLong = 64 // bits
        mascara = (1 << bits) - 1
        for i in range(4096):
            cual, dentro = divmod(i, porLong)
            if cual >= len(datos):
                break
            indice = (datos[cual] >> (dentro * bits)) & mascara
            if indice >= len(paleta):
                continue
            nombre = paleta[indice]
            if nombre in AIRE:
                continue
            y, resto = divmod(i, 256)
            z, x = divmod(resto, 16)
            yield cx + x, base + y, cz + z, nombre


def mapa(carpeta_o_archivos):
    """
    {(x, y, z): nombre} de todos los bloques no-aire.

    Se le pasa una carpeta de regiones o una lista de rutas a .mca.
    """
    if isinstance(carpeta_o_archivos, str):
        archivos = [os.path.join(carpeta_o_archivos, n)
                    for n in sorted(os.listdir(carpeta_o_archivos)) if n.endswith(".mca")]
    else:
        archivos = carpeta_o_archivos
    todo = {}
    for ruta in archivos:
        with open(ruta, "rb") as archivo:
            datos = archivo.read()
        for _, _, nbt in chunks(datos):
            for x, y, z, nombre in bloques(nbt):
                todo[(x, y, z)] = nombre
    return todo


def caja(puntos):
    """La caja mas chica que contiene todos los puntos: (x1,y1,z1,x2,y2,z2)."""
    xs = [p[0] for p in puntos]
    ys = [p[1] for p in puntos]
    zs = [p[2] for p in puntos]
    return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)


if __name__ == "__main__":
    todo = mapa(sys.argv[1])
    print("%d bloques no-aire" % len(todo))
    if not todo:
        sys.exit(0)
    x1, y1, z1, x2, y2, z2 = caja(todo)
    print("caja  x %d..%d   y %d..%d   z %d..%d" % (x1, x2, y1, y2, z1, z2))
    print("      %d x %d x %d" % (x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1))

    cuenta = {}
    for nombre in todo.values():
        cuenta[nombre] = cuenta.get(nombre, 0) + 1
    print("\nlos diez mas usados:")
    for nombre, cuantos in sorted(cuenta.items(), key=lambda p: -p[1])[:10]:
        print("  %-40s %d" % (nombre, cuantos))
