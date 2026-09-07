# Corre cuando el guardado fallo, y con el tope en 3 casas la unica razon por la
# que puede fallar es que el jugador ya tenga las tres.
#
# Lo importante: si se llega aca desde sdp:casa_cobrar, NO se cobro nada: el
# cobro esta condicionado al mismo score que trajo al jugador hasta esta linea.
tellraw @s ["", {"text": "  \u2716 ", "color": "#ff5555"}, {"text": "Ya tenes las ", "color": "gray"}, {"text": "3", "color": "#ffb02e", "bold": true}, {"text": " casas, y no se te cobro nada.", "color": "gray"}, {"text": "\n  \u25aa ", "color": "dark_gray"}, {"text": "Borra una con ", "color": "gray"}, {"text": "/home delete <nombre>", "color": "#00a6ff", "bold": true, "click_event": {"action": "suggest_command", "command": "/home delete "}, "hover_event": {"action": "show_text", "value": {"text": "Borrar una casa es gratis", "color": "gray"}}}, {"text": "   o mira cuales tenes con ", "color": "gray"}, {"text": "/home list", "color": "#00a6ff", "bold": true, "click_event": {"action": "run_command", "command": "/home list"}}]
playsound minecraft:block.note_block.bass master @s ~ ~ ~ 0.5 0.6
