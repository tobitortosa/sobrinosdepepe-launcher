# El cartel de confirmacion: le dice al jugador que la casa extra sale 50.000 y
# le deja un boton para confirmar. NO cobra nada: cobrar es /casa pagar, que es
# lo que corre el boton.
#
# La llama Melius desde el "failure" de modificadores/casa-cobro.json, que
# intercepta cualquier /home set con un nombre distinto de "casa".
#
# El paso intermedio existe a proposito: 50.000 es plata de verdad (un lingote
# de netherita se vende a 175.000), asi que apretar enter de mas no puede
# costarle eso a nadie sin avisar.
#
# No se le muestra el saldo aca: los
# placeholders de la placeholder-api NO se resuelven adentro de un tellraw de
# datapack, solo en los mods que los piden (el cartel de Styled Sidebars y las
# acciones de Melius). El jugador ya tiene su saldo siempre a la vista en el
# cartel de la derecha, asi que aca no hace falta repetirlo.
$tellraw @s ["", {"text": "\n  \u00bb ", "color": "dark_gray"}, {"text": "Guardar la casa ", "color": "gray"}, {"text": "$(nombre)", "color": "#ffb02e", "bold": true}, {"text": " sale ", "color": "gray"}, {"text": "$50.000", "color": "#00ff00", "bold": true}, {"text": " de plata.\n", "color": "gray"}, {"text": "  \u25aa ", "color": "dark_gray"}, {"text": "Tu casa principal se llama ", "color": "gray"}, {"text": "casa", "color": "#ffb02e", "bold": true}, {"text": " y es gratis.\n", "color": "gray"}, {"text": "  \u25aa ", "color": "dark_gray"}, {"text": "Mover una casa extra vuelve a cobrar. Borrarla es gratis.\n\n", "color": "gray"}, {"text": "  [ CONFIRMAR Y PAGAR ]", "color": "#00a6ff", "bold": true, "click_event": {"action": "run_command", "command": "/casa pagar $(nombre)"}, "hover_event": {"action": "show_text", "value": {"text": "Te cobra 50.000 y guarda la casa donde estas parado", "color": "gray"}}}, {"text": "\n"}]
