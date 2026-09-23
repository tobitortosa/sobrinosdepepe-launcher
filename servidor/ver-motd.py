# -*- coding: utf-8 -*-
"""
Muestra el cartel del servidor tal como lo ve el que lo agrega en Minecraft.

    python servidor/ver-motd.py

## Por que existe

El MOTD son las dos lineas que aparecen debajo del nombre en la lista de
servidores, y es lo primero que alguien lee del servidor. Se escribe en
`server.properties`, pero leer ese archivo no alcanza para saber como queda: los
codigos de color (§) no se ven, las tildes pueden salir rotas segun como el
servidor lea el archivo, y el cliente **corta** la linea que se pasa de ancho.

Esto le hace al servidor el mismo saludo que le hace el cliente —el Server List
Ping— y muestra lo que el servidor contesta de verdad, ya destildado de sus
codigos y con el ancho medido.

El protocolo, que es corto:

  1. Handshake: id 0x00, version del protocolo, direccion, puerto, y un 1 que
     significa "vengo a preguntar el estado".
  2. Pedido de estado: id 0x00 y nada mas.
  3. El servidor contesta un JSON con el MOTD, la version y cuantos hay adentro.

La version del protocolo que se manda da igual para preguntar el estado: el
servidor contesta lo mismo aunque no coincida, que es justo lo que hace que la
lista de servidores te muestre "versión incompatible" en vez de un error.
"""
import json
import os
import re
import socket
import struct
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# Lo ancho que entra en la lista de servidores antes de que el cliente corte con
# "...". No es una cuenta de caracteres exacta —la fuente de Minecraft no es
# monoespaciada— pero sirve para darse cuenta de que una linea se fue larga.
ANCHO = 45

# La version del protocolo que se dice ser. Da igual cual sea para preguntar el
# estado: el servidor contesta lo mismo aunque no coincida, y de hecho es asi como
# la lista de servidores te muestra "versión incompatible" en vez de un error.
PROTOCOLO = 767

COLORES = {
    "0": "negro", "1": "azul oscuro", "2": "verde oscuro", "3": "cian oscuro",
    "4": "rojo oscuro", "5": "violeta", "6": "dorado", "7": "gris",
    "8": "gris oscuro", "9": "azul", "a": "verde", "b": "cian", "c": "rojo",
    "d": "rosa", "e": "amarillo", "f": "blanco",
    "l": "negrita", "o": "cursiva", "n": "subrayado", "m": "tachado",
    "k": "revuelto", "r": "normal",
}


def donde_esta():
    """
    La IP y el puerto de verdad, preguntandole al panel.

    No se usa `sobrinosdepepe.com`: el dominio llega al servidor por un registro
    SRV, que es lo que le permite al jugador escribir la direccion sin puerto, y
    resolverlo a mano seria escribir un cliente de DNS para nada. El panel sabe
    cual es la allocation por defecto y la dice.
    """
    datos = json.loads(mc.call(""))
    for a in datos["attributes"]["relationships"]["allocations"]["data"]:
        if a["attributes"]["is_default"]:
            return a["attributes"]["ip"], a["attributes"]["port"]
    raise IOError("el panel no dice cual es la allocation por defecto")


def varint(n):
    salida = b""
    while True:
        byte = n & 0x7F
        n >>= 7
        salida += struct.pack("B", byte | (0x80 if n else 0))
        if not n:
            return salida


def leer_varint(sock):
    n = 0
    for desplazamiento in range(5):
        byte = sock.recv(1)
        if not byte:
            raise IOError("el servidor cortó la conexión")
        n |= (byte[0] & 0x7F) << (7 * desplazamiento)
        if not byte[0] & 0x80:
            return n
    raise IOError("varint demasiado largo")


def paquete(cuerpo):
    return varint(len(cuerpo)) + cuerpo


def preguntar(direccion, puerto):
    with socket.create_connection((direccion, puerto), timeout=10) as sock:
        destino = direccion.encode("utf-8")
        sock.sendall(paquete(
            b"\x00" + varint(-1 & 0xFFFFFFFF if False else 767)
            + varint(len(destino)) + destino
            + struct.pack(">H", puerto) + varint(1)))
        sock.sendall(paquete(b"\x00"))

        leer_varint(sock)          # el largo del paquete entero
        if leer_varint(sock) != 0:  # el id, que tiene que ser 0
            raise IOError("el servidor contestó otro paquete")
        largo = leer_varint(sock)
        datos = b""
        while len(datos) < largo:
            pedazo = sock.recv(largo - len(datos))
            if not pedazo:
                raise IOError("el servidor cortó la respuesta")
            datos += pedazo
    return json.loads(datos.decode("utf-8"))


def texto_de(descripcion):
    """El MOTD puede venir como texto plano o como un arbol de componentes."""
    if isinstance(descripcion, str):
        return descripcion
    if isinstance(descripcion, list):
        return "".join(texto_de(parte) for parte in descripcion)
    if isinstance(descripcion, dict):
        return (descripcion.get("text", "")
                + "".join(texto_de(parte) for parte in descripcion.get("extra", [])))
    return ""


def mostrar(crudo):
    for numero, linea in enumerate(crudo.split("\n"), 1):
        limpia = re.sub("§.", "", linea)
        usados = [COLORES.get(c, c) for c in re.findall("§(.)", linea)]
        print("  %d  %s" % (numero, limpia))
        print("     %d de %d de ancho%s   %s"
              % (len(limpia), ANCHO,
                 "" if len(limpia) <= ANCHO else "   <- SE PASA, el cliente la corta",
                 ", ".join(dict.fromkeys(usados))))


if __name__ == "__main__":
    DIRECCION, PUERTO = donde_esta()
    estado = preguntar(DIRECCION, PUERTO)
    crudo = texto_de(estado.get("description", ""))

    print("%s:%d" % (DIRECCION, PUERTO))
    print("  version: %s" % estado.get("version", {}).get("name", "?"))
    print("  adentro: %s de %s" % (estado.get("players", {}).get("online", "?"),
                                   estado.get("players", {}).get("max", "?")))
    print()
    print("el cartel, como lo ve el que lo agrega:")
    mostrar(crudo)
