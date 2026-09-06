# -*- coding: utf-8 -*-
"""
Deja el grupo `default` de LuckPerms con los permisos que necesitan los comandos
que la guía le promete al jugador.

    python servidor/configurar-permisos.py

Esto no vive en ningún archivo de configuración: LuckPerms lo guarda en una base
H2 adentro de `/mods/luckperms/luckperms-h2-v2.mv.db`, que no se puede leer ni
versionar. Este script es la única copia legible de qué permisos tiene que tener
un jugador común.

## Por qué hace falta

Essential Commands chequea permisos así (`ECPerms.check`, verificado en el
bytecode de 0.39.0):

    Permissions.getPermissionValue(fuente, permiso)
        .orElse(fuente.permissions().hasPermission(
            new Permission.HasCommandLevel(PermissionLevel.byId(Math.max(2, nivel)))))

El `Math.max(2, nivel)` es la trampa: **el nivel con el que el mod registra cada
comando no importa**. Si LuckPerms no dice explícitamente que sí, el comando pide
operador nivel 2 aunque esté declarado con nivel 0. O sea que en este servidor
hay dos estados y no tres: o el nodo está en LuckPerms, o el comando es de
operadores.

Con `use_permissions_api=true` en `EssentialCommands.properties`, que es como
está, esto aplica a **todos** los comandos del mod.

Hasta el 2026-09-06 el grupo `default` tenía siete permisos, y dos de ellos no
existen en el mod: `essentialcommands.home` pelado (LuckPerms solo expande
comodines que terminan en `.*`, así que no otorga `home.tp`) y
`essentialcommands.rtp` (el literal `rtp` es un alias que pide
`essentialcommands.randomteleport`). Resultado: `/spawn`, `/back`, `/rtp` y
`/nickname set` no le funcionaban a nadie que no fuera operador, sin ningún error
visible — el nodo ni siquiera le autocompletaba. Es la mitad de "los comandos de
EXTRAS no funcionan"; la otra mitad era que el menú no se cerraba (ver
generar-menus.py).

## Lo que a propósito NO se otorga

| Nodo | Por qué |
|---|---|
| `essentialcommands.near.self` | `/near` lista a los jugadores en 200 bloques. Es el radar del minimapa por otro camino, y los mods de mapa se sacaron justamente por eso. |
| `essentialcommands.enderchest` | Se sacó el comando entero (`enable_enderchest=false`): llevar el cofre de ender encima anula la única caja fuerte del servidor. |
| `essentialcommands.top` | Se sacó el comando entero (`enable_top=false`): busca tres bloques de aire desde arriba, así que en el Nether te deja arriba del techo de bedrock. |
| `essentialcommands.suicide` | Matarse en una pelea le saca al asesino los shards, el 10% de la plata y el botín. |
| `essentialcommands.afk` | El AFK ya lo pone el mod solo a los 15 minutos (`auto_afk_enabled`). |
| `essentialcommands.tpahere` | No está en la guía. Si algún día se otorga, hay que agregar `tpahere` a `modificadores/combate.json`, porque es otra forma de salir de una pelea. |
| `fly`, `invuln`, `heal`, `feed`, `repair`, `day`, `night` | Son de administrador. |
"""
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# Los comandos que la guía (/comandos y los menús) le promete al jugador.
OTORGAR = [
    ("essentialcommands.home.tp", "/home casa"),
    ("essentialcommands.home.set", "/home set casa"),
    ("essentialcommands.spawn.tp", "/spawn"),
    ("essentialcommands.back", "/back"),
    ("essentialcommands.randomteleport", "/rtp y /randomteleport"),
    ("essentialcommands.tpa", "/tpa y /tpcancel"),
    ("essentialcommands.tpaccept", "/tpaccept"),
    ("essentialcommands.tpdeny", "/tpdeny"),
    ("essentialcommands.nickname.self", "/nickname set y /nickname clear"),
]

# Nodos que estaban puestos y no los mira nadie.
SACAR = [
    ("essentialcommands.home", "no existe en el mod; los que sirven son home.tp y home.set"),
    ("essentialcommands.rtp", "el nodo real es essentialcommands.randomteleport"),
]

if __name__ == "__main__":
    for nodo, para_que in OTORGAR:
        mc.cmd("lp group default permission set %s true" % nodo)
        print("  %-40s %s" % (nodo, para_que))

    for nodo, por_que in SACAR:
        mc.cmd("lp group default permission unset %s" % nodo)
        print("  quitado %-32s %s" % (nodo, por_que))

    # LuckPerms escribe en su base y avisa a los jugadores conectados solo.
    time.sleep(2)
    mc.cmd("lp group default permission info")
    print("\nla lista quedó en el log: mc.read('/logs/latest.log')")
