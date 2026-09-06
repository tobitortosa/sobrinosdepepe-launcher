# -*- coding: utf-8 -*-
"""
Baja del servidor la configuración que mantenemos nosotros y la deja en este
repositorio, para que exista una copia fuera de Minehost.

    python servidor/respaldar.py

La otra mitad es servidor/subir-datapack.py, que hace el camino inverso.
"""
import json
import os
import sys
import urllib.parse

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# Lo que baja siempre, con su nombre local.
ARCHIVOS = [
    # La economía. Los mismos dos archivos los escribe generar-precios.py; esta
    # copia es la que está viva en el servidor, así que sirve para comparar.
    ("/config/economycraft/config.json", "economia/config.json"),
    ("/config/economycraft/prices.json", "economia/prices.json"),
    # La configuración del mod de los menús de cofre.
    ("/config/inventory-menu.json", "menus/inventory-menu.json"),
    # Essential Commands: acá está qué comandos existen y cuáles no
    # (enable_top, enable_enderchest, enable_warp...). No se puede editar con el
    # servidor prendido: el mod reescribe el archivo al apagarse.
    ("/config/EssentialCommands.properties", "esenciales/EssentialCommands.properties"),
]

# Estas carpetas se bajan enteras, así que agregar un archivo nuevo del lado del
# servidor no pide tocar ninguna lista.
CARPETAS = [
    ("/config/melius-commands/commands", "comandos"),
    ("/config/melius-commands/modifiers", "modificadores"),
    # La carpeta entera y no solo default.json: el mod escribe cuatro estilos de
    # ejemplo la primera vez que arranca, y uno se llama "disable". Cualquiera
    # que escriba /sidebar disable se queda sin cartel para siempre, porque la
    # eleccion se guarda por jugador y sobrevive el relogueo. Bajarlos es la
    # unica forma de darse cuenta de que estan.
    ("/config/styled-sidebars/styles", "cartel"),
    ("/world/datapacks/sobrinosdepepe", "datapack"),
]


def listar(ruta):
    return json.loads(mc.call("/files/list?directory=" + urllib.parse.quote(ruta)))["data"]


def bajar(remoto, local):
    destino = os.path.join(AQUI, local.replace("/", os.sep))
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    open(destino, "w", encoding="utf-8", newline="\n").write(mc.read(remoto))
    print("  " + local)


def bajar_carpeta(remoto, local):
    total = 0
    for entrada in listar(remoto):
        a = entrada["attributes"]
        if a["is_file"]:
            bajar(remoto + "/" + a["name"], local + "/" + a["name"])
            total += 1
        else:
            total += bajar_carpeta(remoto + "/" + a["name"], local + "/" + a["name"])
    return total


if __name__ == "__main__":
    total = 0
    for remoto, local in ARCHIVOS:
        try:
            bajar(remoto, local)
            total += 1
        except Exception as error:
            print("  FALTA %s (%s)" % (local, error))
    for remoto, local in CARPETAS:
        total += bajar_carpeta(remoto, local)
    print("bajados %d archivos" % total)
