# -*- coding: utf-8 -*-
"""
Arma los comandos propios y los sube al servidor, junto con los modificadores.

    python servidor/generar-comandos.py

Los comandos los declara Melius Commands, y los modificadores son del mismo mod:
viven en servidor/modificadores/ y se suben tal cual, sin generarse. Hasta el
2026-09-06 solo se bajaban con respaldar.py y se subian a mano, asi que la copia
del repositorio era decorativa; ahora es la fuente y el servidor la copia. Desde que los menus son GUI de cofre, todos estos
comandos son una linea: abren el menu que corresponde o llaman a una funcion del
datapack.

**Cada ejecucion necesita `op_level: 4` explicito.** En Melius ese campo no
tiene valor por defecto: sin el, el comando corre con el nivel del jugador, y
`function`, `tellraw` y `scoreboard` piden nivel 2. A un operador le funciona y
a un viewer no, y como `silent` viene en `true` por defecto, el error no se ve en
ninguna parte: al viewer simplemente no le aparece nada al apretar enter.

Los simbolos se escriben con chr() y el JSON sale de json.dumps a proposito: una
barra invertida suelta en este archivo termina siendo un salto de linea real
adentro del comando y lo parte al medio.
"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import estilo as e
import mc

CARPETA = os.path.join(AQUI, "comandos")
MODIFICADORES = os.path.join(AQUI, "modificadores")


def accion(comando):
    return {"command": comando, "silent": True, "as_console": False, "op_level": 4}


def guardar(cid, doc):
    doc = dict(doc, id=cid)
    texto = json.dumps(doc, indent=2, ensure_ascii=False)
    os.makedirs(CARPETA, exist_ok=True)
    open(os.path.join(CARPETA, cid + ".json"), "w", encoding="utf-8", newline="\n").write(texto)
    mc.write("/config/melius-commands/commands/%s.json" % cid, texto)
    print("  %s" % cid)


def t(texto, color=None, negrita=False, run=None, hover=None):
    c = {"text": texto}
    if color:
        c["color"] = color
    if negrita:
        c["bold"] = True
    if run:
        c["click_event"] = {"action": "run_command", "command": run}
    if hover:
        c["hover_event"] = {"action": "show_text", "value": {"text": hover, "color": "gray"}}
    return c


# Los que solo abren un menu.
for cid, menu in [
    # /ayuda es el nombre que se publicita y /comandos queda de alias. Un jugador
    # hispanohablante tipea "/ayuda" sin que nadie le diga nada, y /help ya es del
    # vanilla. /menu no se puede usar: lo registra Inventory Menu y pide un id.
    ("ayuda", "sdp:comandos"),
    ("tienda", "sdp:tienda"),
    ("economia", "sdp:economia"),
    ("casa", "sdp:casa"),
    ("pvp", "sdp:pvp"),
    ("extras", "sdp:extras"),
]:
    guardar(cid, {"executes": [accion("menu " + menu)]})

# Los que llaman a una funcion del datapack.
for cid, funcion in [
    ("nv", "sdp:nv"),
    ("nightvision", "sdp:nv"),
    ("shards", "sdp:shards"),
]:
    guardar(cid, {"executes": [accion("function " + funcion)]})

# --------------------------------------------------------------------- /comandos
# Los dos nombres significan lo que dicen: /ayuda abre el menu de cofre para ir
# clickeando, y /comandos escupe la lista entera en el chat para el que ya sabe
# lo que busca y quiere escribirlo. Ninguno es obligatorio: todos los comandos
# de la lista funcionan solos, el menu es nada mas que un lanzador.
#
# Los que piden algo mas (un jugador, un monto, un nombre) van con
# suggest_command, que te lo deja escrito en el chat para completarlo. Los que
# se ejecutan solos van con run_command, asi es un click y listo.
GRUPOS = [
    ("PLATA", e.PLATA, [
        ("/bal", False), ("/bal top", False), ("/daily", False), ("/shop", False),
        ("/sell", False), ("/worth", False), ("/ah", False), ("/orders", False),
        ("/transactions", False), ("/pay ", True),
    ]),
    ("VIAJES", e.ACENTO, [
        ("/spawn", False), ("/home casa", False), ("/home set casa", False),
        ("/rtp", False), ("/back", False),
        ("/tpa ", True), ("/tpaccept", False), ("/tpdeny", False),
    ]),
    ("PELEA", e.KILLS, [
        ("/shards", False), ("/tienda", False),
    ]),
    ("EXTRAS", e.MARCA, [
        ("/nv", False),
        ("/msg ", True), ("/nickname set ", True), ("/skin set ", True),
    ]),
]


def boton(comando, pide_algo):
    """Un comando clickeable dentro de la lista."""
    c = {"text": comando.strip() + " ", "color": e.ACENTO, "italic": False,
         "hover_event": {"action": "show_text", "value": {
             "text": ("Clickea y completa lo que falta" if pide_algo
                      else "Clickea para usarlo"), "color": e.ETIQUETA}}}
    c["click_event"] = ({"action": "suggest_command", "command": comando} if pide_algo
                        else {"action": "run_command", "command": comando})
    return c


lista = ["", t(chr(10) + "  " + e.RAYA * 22 + chr(10), e.APAGADO),
         t("  TODOS LOS COMANDOS" + chr(10) * 2, e.MARCA, negrita=True)]
for nombre, color, comandos in GRUPOS:
    lista.append(t("  " + e.VINETA + " " + nombre + chr(10), color, negrita=True))
    lista.append(t("    ", e.APAGADO))
    for comando, pide in comandos:
        lista.append(boton(comando, pide))
    lista.append(t(chr(10)))
lista.append(t(chr(10) + "  Los de VIAJES no andan mientras estas EN PELEA:", e.ERROR))
lista.append(t(chr(10) + "  se te va la marca 15 segundos despues del ultimo golpe." + chr(10), e.APAGADO))
lista.append(t(chr(10) + "  Los podes escribir directo, o abrir el menu con ", e.ETIQUETA))
lista.append(t("/ayuda", e.ACENTO, negrita=True, run="/ayuda",
                hover="Abrir el menu"))
lista.append(t(chr(10) + "  " + e.RAYA * 22 + chr(10), e.APAGADO))

guardar("comandos", {"executes": [accion("tellraw @s " + json.dumps(lista))]})

# ------------------------------------------------------------- los modificadores
# Le cambian el requisito o la ejecucion a un comando que ya existe: los que se le
# esconden a los jugadores (operador 4) y la marca de pelea, que le agrega a los
# comandos de viaje un predicado que exige no tener la etiqueta sdp_combate.
print("modificadores:")
for nombre in sorted(os.listdir(MODIFICADORES)):
    if not nombre.endswith(".json"):
        continue
    ruta = os.path.join(MODIFICADORES, nombre)
    mc.write("/config/melius-commands/modifiers/" + nombre,
             open(ruta, encoding="utf-8").read())
    print("  %s" % nombre)

mc.cmd("reload")
print("  recargado")
