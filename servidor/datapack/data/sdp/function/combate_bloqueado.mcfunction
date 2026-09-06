# La corre Melius cuando alguien marcado intenta viajar: es el "failure" del
# modificador combate.json. El mensaje vive aca y no adentro de ese JSON para no
# tener el mismo tellraw escapado dos veces y para que se edite donde estan los
# demas mensajes del servidor.
title @s actionbar ["", {"text": "\u2716 ", "color": "#ff5555"}, {"text": "Estas EN PELEA", "color": "#ff5555", "bold": true}, {"text": "  no se puede viajar hasta que se te pase", "color": "gray"}]
playsound minecraft:block.note_block.bass master @s ~ ~ ~ 0.5 0.6
