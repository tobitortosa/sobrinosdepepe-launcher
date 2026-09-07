# Cobra 50.000 de plata y guarda una casa EXTRA (cualquier nombre que no sea
# "casa"). La llama /casa pagar <nombre>, que es el boton CONFIRMAR del cartel
# que saca sdp:casa_precio.
#
# El saldo ya lo verifico el portero: modificadores/casa-pagar.json le pone al
# nodo casa.pagar.nombre un predicate:add con
#     more_or_equal( placeholder economycraft:balance , 50000 )
# Aca NO se puede volver a chequear: la plata de EconomyCraft no vive en ningun
# scoreboard y ninguno de sus comandos devuelve el saldo a Brigadier (bal
# devuelve 1 siempre). El unico lector de saldo que hay en todo el servidor es
# ese predicado, y por eso la verificacion vive en el modificador y no aca.
#
# Y no hace falta desconfiar del cobro: BalanceMutationEngine nunca deja el
# saldo en negativo, asi que con el portero pasado los 50.000 estan garantizados.

# Que no sirva para escapar de una pelea (ver el comentario de sdp:casa_guardar).
execute if entity @s[tag=sdp_combate] run return run function sdp:combate_bloqueado

# GUARDAR PRIMERO Y COBRAR DESPUES, y solo si guardo. Al reves, el que ya tiene
# las 3 casas pagaria 50.000 por nada: PlayerData.addHome tira excepcion cuando
# se pasa del tope y ahi success queda en 0.
tag @s add sdp_cobro
$execute store success score #casa sdp_ok run essentialcommands overwritehome $(nombre)

# eco removemoney va con @a[tag=...] y nunca con @s: esta medido que los
# comandos de EconomyCraft no resuelven @s desde una funcion de datapack (ver
# LEEME.md, "Los comandos de EconomyCraft y el nivel de operador").
execute if score #casa sdp_ok matches 1 run eco removemoney @a[tag=sdp_cobro,limit=1] 50000
$execute if score #casa sdp_ok matches 1 run tellraw @s ["", {"text": "  \u2714 ", "color": "#55ff7f"}, {"text": "Casa ", "color": "gray"}, {"text": "$(nombre)", "color": "#ffb02e", "bold": true}, {"text": " guardada por ", "color": "gray"}, {"text": "$50.000", "color": "#00ff00", "bold": true}, {"text": "   volves con ", "color": "gray"}, {"text": "/home $(nombre)", "color": "#00a6ff", "bold": true, "click_event": {"action": "run_command", "command": "/home $(nombre)"}, "hover_event": {"action": "show_text", "value": {"text": "Viajar a esa casa", "color": "gray"}}}]
execute if score #casa sdp_ok matches 1 run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 0.5 1.5
execute unless score #casa sdp_ok matches 1 run function sdp:casa_sin_lugar
tag @s remove sdp_cobro
