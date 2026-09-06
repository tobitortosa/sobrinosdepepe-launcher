# La etiqueta es lo unico que mira Melius: el modificador combate.json le agrega a
# /home, /spawn, /back, /rtp, /tpa y /tpaccept un predicado de entidad que exige
# NO tenerla. El puntaje sdp_combate es el reloj y la etiqueta es el candado.
#
# Los simbolos van escapados como \uXXXX y no literales, igual que en el resto del
# datapack: un caracter suelto no llega igual del otro lado del panel.
tag @s add sdp_combate
title @s actionbar ["", {"text": "\u2694 ", "color": "#ff5555"}, {"text": "EN PELEA", "color": "#ff5555", "bold": true}, {"text": "  no podes viajar por 15 segundos", "color": "gray"}]
playsound minecraft:entity.wither.spawn master @s ~ ~ ~ 0.25 1.8
