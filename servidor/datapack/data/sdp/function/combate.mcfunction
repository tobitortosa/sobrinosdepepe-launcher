# @s acaba de pegarle a otro jugador, o de recibir un golpe de otro jugador.
#
# Son dos advancements y no uno porque cada uno se dispara en un lado distinto:
# player_hurt_entity le llega al que PEGA y entity_hurt_player al que RECIBE. Con
# uno solo se marcaria a la mitad de la pelea, y el otro se iria con /home.
#
# Los dos revokes van siempre. El que no estaba dado falla en silencio: las
# funciones de recompensa corren con la salida suprimida.
advancement revoke @s only sdp:pegar
advancement revoke @s only sdp:pegado

# 300 ticks son quince segundos, y cada golpe los repone: la cuenta arranca de
# nuevo con el ultimo golpe y no con el primero, asi correr diez segundos y
# apretar /home no alcanza.
execute unless entity @s[tag=sdp_combate] run function sdp:combate_entrar
scoreboard players set @s sdp_combate 300
