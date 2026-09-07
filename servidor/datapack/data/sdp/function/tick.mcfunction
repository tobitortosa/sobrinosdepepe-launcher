# Shards por matar. Se paga aca y no en el advancement porque el advancement no
# dice a quien mato, y el asesino se marca en el mismo tick que la muerte.
# Se recorre por MUERTO y no por asesino: sdp:pagar_kill necesita saber a quien
# mataron para mirar el enfriamiento de esa victima.
execute as @a[scores={sdp_death=1..}] run function sdp:pagar_kill

# Lo que solto el que murio dura diez minutos y no cinco, asi se puede volver a
# buscarlo. Age cuenta hasta 6000 ticks y ahi el item se borra; arrancando en -6000
# la cuenta tarda 12000, que son diez minutos. El -32768 seria "no desaparece nunca".
#
# Va solo sobre lo que cayo al morir y no sobre todos los items del mundo, que
# duplicaria las entidades tiradas de cualquier granja. Se recorre desde la posicion
# del muerto y esto corre en el tick siguiente a la muerte, cuando el inventario ya
# se solto y el jugador todavia no respawneo, asi que sigue parado donde murio.
execute as @a[scores={sdp_death=1..}] at @s as @e[type=item,distance=..12] run data modify entity @s Age set value -6000s

# La marca de pelea baja un tick por tick y se cae sola a los quince segundos. Solo
# corre para los conectados, asi que desconectarse no la acelera: el que sale
# marcado vuelve marcado y tiene que quemar los 300 ticks adentro del juego.
#
# Morir la borra sola y no hace falta una linea para eso: ServerPlayer.restoreFrom
# no copia las etiquetas, asi que el que respawnea ya no la tiene. El puntaje sigue
# bajando hasta cero sin molestar a nadie.
scoreboard players remove @a[scores={sdp_combate=1..}] sdp_combate 1
execute as @a[tag=sdp_combate,scores={sdp_combate=..0}] run function sdp:combate_salir

# El enfriamiento de cada uno baja un tick por tick. Solo corre mientras el
# jugador esta conectado, que es lo que se quiere: desconectarse no lo acelera.
scoreboard players remove @a[scores={sdp_cd=1..}] sdp_cd 1

# El contador de muertes se reinicia todos los ticks: solo interesa este.
scoreboard players reset * sdp_death
scoreboard players reset * sdp_killer

# Deja los shards en cero a quien no tenga, para que el cartel muestre algo.
# %player:objective% se cae si el jugador no tiene score en el objetivo.
scoreboard players add @a Shards 0

# Al que todavia no tiene turno asignado se le fija el primero y se le guarda la
# posicion, para que el primer shard no sea de regalo.
execute as @a unless score @s sdp_marca matches 1.. run function sdp:empezar

# Shards por tiempo. sdp_tiempo lo cuenta el juego solo (criterio play_time) y
# sdp_marca guarda en que tick jugado toca el proximo shard.
#
# La comparacion va con @s a los dos lados a proposito. Con @a NO anda, y lo
# peor es que no falla: scoreboard players operation recorre las dos colecciones
# anidadas, asi que cada jugador termina con el valor del ultimo de la lista y,
# en la resta, con la suma de todos. Medido en el servidor: con dos conectados
# los shards por tiempo dejaron de pagarse; con uno solo andaba perfecto.
execute as @a if score @s sdp_tiempo >= @s sdp_marca run function sdp:turno

# Repone la vision nocturna a quien la dejo prendida y la perdio al morir.
execute as @a[scores={sdp_nv=1}] unless predicate sdp:tiene_nv run effect give @s night_vision infinite 1 true
