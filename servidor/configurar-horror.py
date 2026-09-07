# -*- coding: utf-8 -*-
"""
Deja Server-Side Horror en modo "una pizca": ruidos y nada más.

    python servidor/configurar-horror.py

Después hay que **reiniciar el servidor**: el mod lee la configuración al
arrancar y no tiene comando de recarga (probado: `deimos`, `deimosconfig` y
`serversidehorror` no existen como comandos).

## Qué es y por qué este mod

Es terror ambiental del lado del servidor: pasos que no sabés de dónde vienen,
alguien picando piedra que no está, un sonido raro, y muy de vez en cuando una
figura que aparece a lo lejos, te mira y desaparece. La gracia es que el jugador
nunca sabe si lo escuchó de verdad.

**Es de solo servidor** (`client_side: unsupported` en Modrinth), así que no va
al pack del launcher y nadie tiene que actualizar nada. Necesita `deimos`, que es
su librería de configuración y tampoco viene embebida en el jar.

Los otros tres mods que se habían mirado para esto — Silent Caves, Shy Dweller y
Dynamic Sound Filters — **no existen para 26.1**: se quedaron en 1.21.1. Y el eco
y los sonidos amortiguados de las cuevas ya los da Sound Physics Remastered, que
está en el pack desde el principio.

## La probabilidad se cuenta en "1 en N por tick y por jugador"

Todos los `_chance` son enteros y se tiran una vez por tick contra cada jugador
conectado. Una hora de juego son **72.000 ticks**, así que:

    veces por hora = 72000 / chance

Con eso, lo que queda configurado acá es: un jugador que mina una hora escucha
algo unas cuatro veces, y ve la figura una vez cada cuatro horas más o menos.

## Los dados se tiran POR JUGADOR, no se elige uno al azar

Medido en el bytecode: las cinco tiradas viven en `ServerPlayerMixin`, inyectado
en `ServerPlayer.tick()` con `@At("TAIL")`. Cada jugador conectado tira sus
propios dados una vez por tick, y no hay ningún `getRandomPlayer()`. O sea que
la tasa por jugador **no** se divide por cuánta gente hay conectada, y no hay
nada que compensar. Tampoco hay filtro por operador, gamemode, dimensión, luz ni
altura: que al admin le pase y a los demás no es sesgo de muestra chica, no es un
privilegio.

Lo que sí es real, y explica que cada uno crea que le pasa solo a él: **todos los
efectos son locales**. Los sonidos salen con `level.playSound`, que llega a 16
bloques, y las partículas a 32. En un servidor con el borde en 12.000 nadie está
a 16 bloques de nadie, así que nadie presencia el evento de otro y cada uno
cuenta nada más que los propios.

## El 48% de los sonidos no los escucha nadie

`playScarySound` tira la posición del sonido a un offset uniforme de -16 a +15
bloques en **cada** eje, y el radio audible es 16. De las 32.768 combinaciones
posibles solo el 51,8% cae a menos de 16 bloques del propio jugador: el resto se
emite en un punto donde no hay nadie y se pierde. El radio está fijo en el mixin
y no se configura. Por eso `scary_sound_chance` va en 50.000 y no en 100.000: es
compensar la pérdida, no subir la frecuencia.

## Dos huecos que conviene conocer antes de que alguien los descubra

- **Quien va montado no recibe ninguna tirada.** El bucle de entidades saltea a
  quien tiene vehículo (`ServerLevel.tick` corta si `getVehicle()` no es null) y
  le llama `rideTick()` en lugar de `tick()`. A caballo, en bote o en vagoneta no
  pasa nada, nunca.
- **El jumpscare exige estar perfectamente quieto tres ticks.** Compara la
  posición contra las dos anteriores, así que solo le toca a quien está
  construyendo, mirando un cofre o AFK. Y si el jugador se desconecta con el
  jumpscare pendiente, se pierde: la lista `TO_BE_JUMP_SCARED` guarda el
  `ServerPlayer`, y al reconectarse es otro objeto con otro id de entidad.

## `herobrine_starer` es el único que no se sube, y por qué

Cada aparición hace **dos pedidos HTTP sincrónicos a Mojang** en el hilo del
servidor, sin caché y sin timeout (`getSkin` pega a `api.mojang.com` y a
`sessionserver.mojang.com`), y después recorre un cubo de radio 40 —531.441
candidatos— haciendo raycasts. La versión vieja de este archivo decía que la skin
viene adentro del jar y que no sale a internet: **es falso**, y conviene tenerlo
escrito.

Peor: el `catch` de `getSkin` llama a `getSkin("MarsThePlanet_")`, que es
exactamente el nombre que ya estaba pidiendo. Si Mojang no contesta, eso es
recursión infinita hasta el StackOverflowError, adentro de un tick. Subir la
frecuencia multiplica esa lotería, así que queda en 300.000 (una cada cuatro
horas por jugador) y ahí se queda.

Lo que sí da esto: cuando aparece, el nombre `MarsThePlanet_` entra en la lista
de TAB de **todos** los conectados hasta 20 minutos, porque los paquetes van con
`broadcastAll` sin filtro de distancia. Es la única prueba compartida y objetiva
de que el evento pasó.

## El log no sirve para contar eventos

De los cinco prendidos, el único `LOG.info` del camino automático es cuando el
herobrine **no** encuentra dónde pararse. Los eventos que salen bien no dejan
rastro, así que "no aparece nada en el log" no distingue "no pasó" de "pasó
bien". Para medirlo de verdad habría que contarlo desde el datapack.

## Un mixin que corre siempre y no se puede apagar

`FurnaceBlockEntityMixin` se inyecta en el `serverTick` de **todos** los hornos,
ahumadores y altos hornos, sin ningún gate de configuración, y si el slot de
arriba tiene bedrock, structure_void, structure_block, jigsaw o barrier, cambia
bloques del mundo. Solo se desactiva si el mundo se llama "Renovating Villager
Houses" o "Traps"; el nuestro se llama "world". Riesgo bajo (hay que ser
operador en creativo para tener esos bloques) pero no es cero, y no hay forma de
apagarlo desde la config.

## Lo que está apagado a propósito

El mod viene con la mitad de sus eventos prendidos, y varios en este servidor
serían griefing y no terror. Con PvP libre y sin `keep_inventory`, cualquier cosa
que te mate te cuesta el inventario entero:

| Evento | Qué hacía |
|---|---|
| `random_lightning` | te tira un rayo encima |
| `burn_down_house` | te prende fuego la casa al despertarte |
| `break_torches` / `replace_torches` | te apaga las antorchas, y ahí spawnean mobs |
| `traps` / `setting_up_new_traps` | genera trampas con vagoneta de TNT cerca tuyo |
| `joining_on_bedrock` | te reaparece en el límite de altura |
| `joining_in_dungeon` | te teletransporta adentro de un dungeon |
| `removing_leaves` | le saca las hojas a los bosques |
| `long_night` | noches largas: no da miedo, molesta, y son más mobs |
| `old_villages` | cambia la generación del mundo, y no tiene nada que ver con cuevas |

Y tres más que se apagan por otro motivo, que es que rompen la ambientación:

| Evento | Por qué |
|---|---|
| `fake_joiner`, `random_fake_joiner` | inventan que entró un jugador y que escribe en el chat. Acá la lista de quién puede entrar es cerrada y la maneja el launcher: un nombre desconocido en el chat no da miedo, hace pensar que se coló alguien. |
| `random_signs` | planta carteles con textos en inglés que nombran al autor del mod y a un youtuber. |
| `heads_from_list`, `random_heads` | pone cabezas de jugadores como bloque. Ensucia el mundo y nada que toque el mundo entra en "una pizca". |
| `starer` | es lo mismo que `herobrine_starer` pero con una lista de nombres, y para conseguir sus skins le pega a la API de Mojang. La versión de Herobrine trae la skin adentro del jar y no sale a internet. |

## Los sonidos

La lista que trae de fábrica no sirve acá: la mitad son `entity.tnt.primed`,
`entity.creeper.primed`, `entity.arrow.hit` y `item.crossbow.hit`. En un servidor
donde te pueden matar de verdad, eso no es terror, es una falsa alarma de que te
están atacando y sale todo el mundo corriendo.

Los de acá son ambiguos y de cueva: el sonido de cueva de siempre, el enderman
mirándote, y los del Warden y el sculk, que son los que el propio juego usa para
dar miedo bajo tierra. Todos verificados contra `SoundEvents` del jar de 26.1.
"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

RUTA = "/config/serversidehorror.json"
LOCAL = os.path.join(AQUI, "horror", "serversidehorror.json")

HORA = 72000  # ticks de una hora de juego, para leer los numeros de abajo

PRENDIDOS = {
    # pasos que no son de nadie
    "fake_steps_enable": True,
    "fake_steps_chance": 45000,            # 1,6 por hora
    # alguien picando piedra en algun lado
    "fake_mining_enable": True,
    "fake_mining_chance": 45000,           # 1,6 por hora
    # un sonido raro de la lista de abajo. El numero es la mitad de lo que
    # pediria la cuenta ingenua, y es por el 48% que se pierde (ver abajo).
    "scary_sound_enable": True,
    "scary_sound_chance": 50000,           # 1,44 por hora tirados, 0,75 oidos
    # la figura que aparece, te mira y se va. Es la unica que se ve, y la unica
    # que NO se sube: cada aparicion sale a internet. Ver abajo.
    "herobrine_starer_enable": True,
    "herobrine_starer_chance": 300000,     # una cada 4 horas
    # particulas rojas de golpe. No hace daño: es DustParticleOptions y nada mas.
    "jumpscare_enable": True,
    "jumpscare_chance": 300000,            # una cada 4 horas, y solo si esta quieto
}

APAGADOS = [
    "random_lightning_enable", "burn_down_house_enable",
    "break_torches_enable", "replace_torches_enable",
    "traps_enable", "setting_up_new_traps_enable",
    "joining_on_bedrock_enable", "joining_in_dungeon_enable",
    "removing_leaves_enable", "long_night_enable", "old_villages_enable",
    "fake_joiner_enable", "random_fake_joiner_enable",
    "random_signs_enable", "heads_from_list_enable", "random_heads_enable",
    "starer_enable",
]

SONIDOS = [
    "minecraft:ambient.cave",
    "minecraft:entity.enderman.stare",
    "minecraft:entity.warden.nearby_closer",
    "minecraft:entity.warden.nearby_closest",
    "minecraft:entity.warden.heartbeat",
    "minecraft:entity.warden.listening",
    "minecraft:block.sculk_shrieker.shriek",
    "minecraft:block.sculk_sensor.clicking",
    "minecraft:block.sculk.charge",
    "minecraft:entity.creaking.activate",
]

if __name__ == "__main__":
    config = json.loads(mc.read(RUTA))

    config.update(PRENDIDOS)
    for clave in APAGADOS:
        config[clave] = False
    config["scary_sound_list"] = SONIDOS

    # El periodo de gracia se cuenta desde que el mod empieza a llevar la cuenta,
    # y no queremos tres dias de nada despues de cada cambio.
    config["grace_period"] = 0

    mc.write(RUTA, json.dumps(config, indent=2, ensure_ascii=False))

    os.makedirs(os.path.dirname(LOCAL), exist_ok=True)
    open(LOCAL, "w", encoding="utf-8", newline="\n").write(
        json.dumps(config, indent=2, ensure_ascii=False) + "\n")

    print("prendidos:")
    for clave, valor in PRENDIDOS.items():
        if clave.endswith("_chance"):
            print("    %-28s 1 en %-8d  %.1f veces por hora" % (clave, valor, HORA / valor))
    print("  apagados: %d eventos" % len(APAGADOS))
    print("  sonidos:  %d" % len(SONIDOS))
    print("\nreinicia el servidor: el mod lee esto al arrancar.")
