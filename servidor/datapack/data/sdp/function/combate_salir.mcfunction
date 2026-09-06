# Se cumplieron los quince segundos sin dar ni recibir un golpe.
tag @s remove sdp_combate
scoreboard players set @s sdp_combate 0
title @s actionbar ["", {"text": "\u2714 ", "color": "#55ff7f"}, {"text": "Saliste de la pelea", "color": "#55ff7f", "bold": true}, {"text": "  ya podes viajar", "color": "gray"}]
playsound minecraft:block.note_block.chime master @s ~ ~ ~ 0.4 1.6
