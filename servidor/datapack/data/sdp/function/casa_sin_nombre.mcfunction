# /home set sin nombre. De fabrica Essential Commands le pone "unnamed"
# (HomeSetCommand.runDefault), y eso seria una casa gratis mas: el cobro se
# engancha al nombre, asi que un nombre que el jugador no eligio se escaparia
# del precio. Se corta antes y se le dice como se usa.
tellraw @s ["", {"text": "  \u00bb ", "color": "dark_gray"}, {"text": "Ponele nombre a la casa:  ", "color": "gray"}, {"text": "/home set casa", "color": "#00a6ff", "bold": true, "click_event": {"action": "suggest_command", "command": "/home set casa"}, "hover_event": {"action": "show_text", "value": {"text": "Tu casa principal, y es gratis", "color": "gray"}}}, {"text": "   la principal es gratis", "color": "dark_gray"}]
