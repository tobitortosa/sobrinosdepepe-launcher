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


def accion_hablada(comando):
    """
    Igual que accion() pero SIN silent, para envolver comandos de otro mod que
    contestan por `source.sendFailure`.

    `silent: true` le pone `withSuppressedOutput()` a la fuente, y eso se come los
    mensajes de error del comando envuelto. En /diario eso seria lo peor posible:
    el que ya lo reclamo apretaria enter y no pasaria absolutamente nada.
    """
    return {"command": comando, "silent": False, "as_console": False, "op_level": 4}


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
    ("pvp", "sdp:pvp"),
    ("extras", "sdp:extras"),
]:
    guardar(cid, {"executes": [accion("menu " + menu)]})

# ------------------------------------------------------------------------ /casa
# Abre el menu como los de arriba, y ademas tiene el subcomando que COBRA las
# casas extra: /casa pagar <nombre>.
#
# Por que el cobro vive en un comando propio y no adentro del /home set de
# Essential Commands: un modificador de Melius solo puede BLOQUEAR antes de que
# el comando corra (ContextChainMixin prueba los IsExecutableModifier y, si uno
# falla, corre su "failure" y devuelve sin ejecutar el original). No existe
# ningun modificador que corra algo DESPUES, asi que "cobrale y despues dejalo
# pasar" no se puede armar. Lo que si se puede es interceptar /home set,
# mostrar el precio, y hacer todo el trabajo desde este comando.
#
# El portero de saldo esta en modificadores/casa-pagar.json, sobre el nodo
# casa.pagar.nombre, y es la unica pieza de todo el servidor capaz de LEER la
# plata de EconomyCraft: el predicado `placeholder` de la predicate-api (que
# viene adentro del jar de Melius) resuelve %economycraft:balance% y
# more_or_equal lo compara con 50000. Ningun comando de EconomyCraft devuelve el
# saldo a Brigadier, asi que sin ese predicado no habria forma de verificar.
#
# El nombre de la casa viaja como ${nombre}: CommandAction arma su parser con
# los placeholders de servidor y con los ARGUMENTOS del comando, los dos con el
# formato ${...}, asi que ${nombre} se reemplaza por lo que tipeo el jugador.
guardar("casa", {
    "executes": [accion("menu sdp:casa")],
    "literals": [
        {
            "id": "pagar",
            "arguments": [
                {
                    "id": "nombre",
                    "type": "brigadier:string word",
                    "executes": [accion('function sdp:casa_cobrar {nombre:"${nombre}"}')],
                }
            ],
        }
    ],
})

# ------------------------------------------------------------------------ /diario
# El regalo diario de EconomyCraft se llama `/daily` y no tiene ningun alias
# (Commands.literal("daily"), sin mas). En un servidor donde todo lo demas esta en
# castellano —/ayuda, /casa, /tienda, /economia, /shards— ese es el unico comando
# que hay que escribir en ingles, y escrito mal ("dayly", "dayli") el juego contesta
# "Unknown or incomplete command", que se lee como que el comando esta roto.
#
# /daily sigue existiendo y funcionando: esto es un nombre mas, no un reemplazo.
#
# Va con accion_hablada y no con accion: los dos mensajes que puede contestar
# —"Already claimed today" y "Daily reward could not be added to your balance"—
# salen por source.sendFailure, y con silent se perderian.
guardar("diario", {"executes": [accion_hablada("daily")]})

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
        # Se anuncia /diario y no /daily, por lo mismo que se anuncia /ayuda y no
        # /comandos: es el nombre que un hispanohablante escribe sin equivocarse.
        ("/bal", False), ("/bal top", False), ("/diario", False), ("/shop", False),
        ("/sell", False), ("/worth", False), ("/ah", False), ("/orders", False),
        ("/transactions", False), ("/pay ", True),
    ]),
    # Esta lista y el menu de cofre de /casa tienen que decir lo mismo: son las
    # dos caras de la misma guia y un jugador que ve un comando en una y no en la
    # otra piensa que algo se rompio.
    #
    # El 2026-09-07 salieron cuatro de aca, los mismos cuatro que salieron del menu:
    #
    #   /spawn                anda mal y no le sirve a nadie.
    #   /back                 no se usaba.
    #   /tpaccept, /tpdeny    no hacen falta anunciarlos: cuando llega una
    #                         solicitud, Essential Commands manda un
    #                         ChatConfirmationPrompt con [Aceptar] y [Rechazar]
    #                         clickeables y el nombre del otro ya puesto
    #                         (TeleportAskCommand). Apretar ahi es mas rapido que
    #                         escribir el comando, y ademas no hay que acordarse
    #                         de a quien.
    #
    # Los comandos siguen existiendo y funcionando: lo que se saco es el anuncio.
    ("VIAJES", e.ACENTO, [
        ("/home casa", False), ("/home set casa", False),
        ("/home set ", True), ("/home delete ", True), ("/home list", False),
        ("/rtp", False), ("/tpa ", True),
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
