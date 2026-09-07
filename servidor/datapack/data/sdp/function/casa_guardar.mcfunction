# Guarda (o mueve) la casa principal, que se llama "casa" y es GRATIS.
#
# La llama Melius desde el "failure" de modificadores/casa-gratis.json, o sea
# cuando el jugador escribe /home set casa. Se intercepta el comando de fabrica
# en vez de dejarlo pasar por un motivo puntual: el HomeSetCommand de Essential
# Commands, cuando la casa YA existe, no la mueve: te manda un cartel con un
# link a /essentialcommands overwritehome casa. Y ese comando esta tapado con
# operador 4 (modificadores/casa-overwrite.json), porque suelto es la puerta
# gratis para crear casas: HomeOverwriteCommand hace removeHome y despues
# addHome, asi que sobre un nombre que no existe la CREA.
#
# Aca se llama directo a overwritehome, que crea o mueve segun corresponda, y
# siempre gratis: la casa principal se puede mudar todas las veces que se quiera.
#
# El nivel de operador lo pone la accion de Melius (op_level 4) y de ahi lo
# hereda esta funcion, asi que el overwritehome tapado pasa igual.

# Que no sirva para escapar de una pelea. Este chequeo va aca y no en el
# modificador de combate porque el "failure" de un modificador corre igual que
# cualquier accion: si la marca de pelea bloqueara el /home set por su lado,
# esta funcion ya se habria ejecutado y la casa quedaria guardada.
execute if entity @s[tag=sdp_combate] run return run function sdp:combate_bloqueado

# store success y no store result: HomeSetCommand y HomeOverwriteCommand
# devuelven 0 SIEMPRE (los dos terminan en "return 0"), asi que el resultado no
# dice nada. Lo que si distingue es la excepcion: al pasarse del tope de casas,
# PlayerData.addHome tira CommandSyntaxException y ahi success queda en 0.
execute store success score #casa sdp_ok run essentialcommands overwritehome casa

execute if score #casa sdp_ok matches 1 run tellraw @s ["", {"text": "  \u2714 ", "color": "#55ff7f"}, {"text": "Guardaste tu casa", "color": "#55ff7f", "bold": true}, {"text": "   volves con ", "color": "gray"}, {"text": "/home casa", "color": "#00a6ff", "bold": true, "click_event": {"action": "run_command", "command": "/home casa"}, "hover_event": {"action": "show_text", "value": {"text": "Viajar a tu casa", "color": "gray"}}}]
execute if score #casa sdp_ok matches 1 run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.5 1.5
execute unless score #casa sdp_ok matches 1 run function sdp:casa_sin_lugar
