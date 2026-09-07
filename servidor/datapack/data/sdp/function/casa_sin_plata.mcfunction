# El "failure" del portero de saldo (modificadores/casa-pagar.json): corre
# cuando el predicado more_or_equal sobre %economycraft:balance% dice que no
# llega a 50.000.
#
# Ojo con como falla el predicado: si el placeholder de EconomyCraft dejara de
# existir, GenericObject.toNumber no puede parsear el valor y devuelve 0, o sea
# que TODO el mundo cae aca y nadie puede comprar una casa extra. Falla cerrado
# y no abierto, que es el lado correcto: nadie se lleva una casa gratis.
tellraw @s ["", {"text": "  \u2716 ", "color": "#ff5555"}, {"text": "Una casa extra sale ", "color": "gray"}, {"text": "$50.000", "color": "#00ff00", "bold": true}, {"text": " y no te alcanza.", "color": "gray"}, {"text": "\n  \u25aa ", "color": "dark_gray"}, {"text": "Tu casa principal (", "color": "gray"}, {"text": "/home set casa", "color": "#00a6ff", "bold": true, "click_event": {"action": "suggest_command", "command": "/home set casa"}}, {"text": ") es gratis.", "color": "gray"}]
playsound minecraft:block.note_block.bass master @s ~ ~ ~ 0.5 0.6
