# -*- coding: utf-8 -*-
"""
Deja los objetivos y los lugares del scoreboard como tienen que estar.

    python servidor/configurar-scoreboard.py

Esto no vive en ningún archivo de configuración: el juego lo guarda dentro del
mundo (en 26.1 la ruta es world/data/minecraft/scoreboard.dat, antes era
world/data/scoreboard.dat). Si algún día hay que rearmar el servidor o se pierde
el mundo, se corre esto y queda igual.

Los tres lugares que tiene el juego:
  - sidebar     el cartel de la derecha, que maneja Styled Sidebars por su cuenta
  - list        la tabla del TAB
  - below_name  debajo del nombre del jugador, en el mundo
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import estilo as e
import mc

OBJETIVOS = [
    # nombre, criterio, como se muestra
    ("HP", "health", {"text": chr(0x2764), "color": "red"}),
    ("Shards", "dummy", {"text": e.SHARD + " Shards", "color": e.SHARDS}),

    # El juego cuenta solo los ticks jugados de cada uno con este criterio, asi
    # que no hace falta contar ticks a mano. Arranca en cero cuando se crea el
    # objetivo y sigue sumando 20 por segundo mientras el jugador este conectado.
    ("sdp_tiempo", "minecraft.custom:minecraft.play_time", {"text": "ticks jugados"}),
    ("sdp_marca", "dummy", {"text": "ticks del ultimo shard"}),
    ("sdp_dif", "dummy", {"text": "resta de trabajo"}),
    ("sdp_x", "dummy", {"text": "x del ultimo turno"}),
    ("sdp_z", "dummy", {"text": "z del ultimo turno"}),

    ("sdp_death", "deathCount", {"text": "muertes del tick"}),
    ("sdp_killer", "dummy", {"text": "asesino del tick"}),
    # Ticks que faltan para que matar a este jugador vuelva a pagar shards.
    ("sdp_cd", "dummy", {"text": "enfriamiento de la kill"}),
    ("sdp_nv", "dummy", {"text": "vision nocturna"}),

    # Ticks que faltan para que se le caiga la marca de pelea. La etiqueta
    # sdp_combate es el candado que mira Melius; esto es el reloj que la suelta.
    ("sdp_combate", "dummy", {"text": "ticks de pelea"}),
    # Lo usa sdp:casa_guardar y sdp:casa_cobrar con el portador falso #casa:
    # guarda si el "essentialcommands overwritehome" tuvo exito, que es la unica
    # forma de saber si el jugador se paso del tope de 3 casas. De eso depende
    # que se le cobren o no los 50.000.
    ("sdp_ok", "dummy", {"text": "salio bien lo ultimo"}),
]

for nombre, criterio, display in OBJETIVOS:
    # Si ya existe, el comando falla sin consecuencias.
    mc.cmd("scoreboard objectives add %s %s %s" % (nombre, criterio, json.dumps(display)))
    print("  objetivo %-12s (%s)" % (nombre, criterio))

# El nombre que se muestra si cambio: modify no falla aunque ya este puesto.
for nombre, _, display in OBJETIVOS:
    mc.cmd("scoreboard objectives modify %s displayname %s" % (nombre, json.dumps(display)))

# La vida se dibuja como corazones y no como numero.
mc.cmd("scoreboard objectives modify HP rendertype hearts")

# La vida de todos en el TAB, y tambien sobre la cabeza de quien tenes enfrente.
mc.cmd("scoreboard objectives setdisplay list HP")
mc.cmd("scoreboard objectives setdisplay below_name HP")
print("  TAB y sobre la cabeza: corazones")
