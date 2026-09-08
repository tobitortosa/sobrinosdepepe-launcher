# -*- coding: utf-8 -*-
"""
Deja Safe Zone como una herramienta de administrador y no como un sistema de
reclamos para los jugadores.

    python servidor/configurar-zonas.py

Al final aplica `/sz reload`, así que no hace falta reiniciar.

## Para qué está

Para proteger una base: se eligen dos esquinas con una varita y toda la columna
—de la roca madre al cielo— queda irrompible. Nadie puede romper ni poner un
bloque adentro, ni con TNT, ni con fuego, ni tirando lava. Si la base está bajo
tierra, no hay forma de cavar para entrar.

La proteccion **no mira la altura**: `ClaimData.contains()` compara solo X y Z, asi
que la zona es la columna entera de la roca madre al cielo. Los `y1`/`y2` que quedan
en `claims.json` son el registro de las esquinas que se clickearon y nada mas. Por eso
hacer una torre y tirar TNT desde arriba no rompe nada adentro: el mod saca de la
explosion todos los bloques de la zona antes de que reviente (`ServerExplosionMixin`),
y lo mismo con el fuego que deja. Vale para toda explosion — TNT, crystals, camas,
respawn anchors, creepers, ghasts — y no hay ninguna opcion para apagarlo.

**Ojo con esto en el PvP, que no lo configuramos nosotros:** el dueño y los trusted
son **inmunes al daño de explosion** mientras esten parados ADENTRO de su zona
(`ClaimEntityProtection.shouldBlockExplosionDamage`). O sea que en tu propia base los
crystals y la TNT no te hacen nada. Un jugador ajeno adentro si recibe el daño normal.
Tambien quedan a salvo de las explosiones los cuadros, los armor stands y los barcos
y vagonetas de adentro.

Lo que la zona NO cubre: todo lo que este afuera del rectangulo X/Z, aunque este
pegado al borde. Y **no hay mixin de pistones** en el jar (estan los de fuego,
fluidos, baldes, explosiones, cuadros, armor stands y vehiculos), asi que empujar
bloques desde afuera hacia adentro, o tirar de los de adentro con un bloque de slime,
en principio no lo frena nada. Sin probar en vivo.

Un datapack no puede hacer esto: no hay ninguna forma de cancelar que un jugador
rompa un bloque desde un datapack. Por eso es un mod, **Safe Zone 1.5.0**, que es
`environment: server` — no va al pack del launcher y nadie tiene que actualizar
nada. Depende de fabric-api, que ya está.

## Los tres candados

El mod, de fábrica, es un sistema de reclamos **para todos**, y así como viene
mataría el raideo, que es el alma de este servidor. El cartel de `/pvp` dice "las
bases NO están protegidas" y tiene que seguir siendo verdad para todo el mundo
menos para lo que se proteja a mano. Tres cosas independientes lo cierran:

1. **`defaultMaxClaims` en 0.** El límite es por jugador y se chequea al crear:
   con 0, nadie puede crear una zona. Al final del script se le da un límite
   propio solo a la cuenta de administrador, con `/sz limits`. Este es el candado
   que de verdad importa.
2. **`starterKitEnabled` en false.** Venía en **true**: le regalaba una varita a
   cada jugador nuevo al entrar.
3. **La varita pasa a ser el palo de depuración.** `ModItems.isClaimWand` mira
   **solo el tipo de ítem** (`stack.is(item)`), no el nombre — o sea que con la
   varita de fábrica **cualquier azada de oro sirve**, y eso se craftea con dos
   lingotes y dos palos. El palo de depuración no tiene receta ni aparece en
   ningún cofre: la única forma de tenerlo es que un operador lo dé.

   **Ojo:** el palo de depuración cambia estados de bloque si lo usás en
   creativo, porque ahí `canUseGameMasterBlocks()` da true. En supervivencia no
   hace nada por su cuenta y la varita funciona bien. Usalo en supervivencia.

## Cómo se usa

    /give <vos> minecraft:debug_stick[enchantments={vanishing_curse:1},custom_name={text:"Varita de zonas",color:"#ffb02e",italic:false}] 1
    click derecho en una esquina
    click derecho en la esquina opuesta      -> queda protegido
    /clear <vos> minecraft:debug_stick       para guardarla

La maldicion de desaparicion no es decoracion: keep_inventory esta en false, asi
que sin eso al morir la varita se cae y la levanta el que te mato. Con la maldicion
el item se destruye al morir. `/sz givewand` da una sin la maldicion.

Y para sacarlo, click derecho adentro con la varita y confirmar, o `/sz remove`.

Dos límites del mod que conviene saber: las zonas son **solo del overworld**
(`ClaimWandHandler` tiene un mensaje de validación para eso), y cada una puede
medir como máximo lo que diga `maxClaimWidth`/`maxClaimDepth`. Si una base no
entra, se hacen dos zonas pegadas.
"""
import json
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

RUTA = "/world/safe-zone/config.json"
LOCAL = os.path.join(AQUI, "zonas", "config.json")

# Quién puede crear zonas. Es el candado principal: el límite se chequea al crear
# y el resto del servidor queda en el default, que es cero.
ADMINISTRADORES = {"PEPE": 20}

GAMEPLAY = {
    # Sin receta y sin cofre que lo tenga: solo lo da un operador.
    "claimWandItemId": "minecraft:debug_stick",
    # Venía en true: le daba una varita a cada jugador nuevo.
    "starterKitEnabled": False,
    "dropStarterKitWhenInventoryFull": False,
    # Nadie, salvo los de ADMINISTRADORES.
    "defaultMaxClaims": 0,
    # 128 de lado alcanza para una base entera de una sola vez.
    "maxClaimWidth": 128,
    "maxClaimDepth": 128,
    # Que una zona no se caduque sola y deje la base abierta sin que nadie se
    # entere. Cero es "no vence nunca".
    "claimExpiryDays": 0,
    # Avisa al dueño cuando alguien intenta romper adentro.
    "notificationsEnabled": True,
}

OPS = {
    # Es la única copia de qué está protegido: si el archivo se corrompe, la base
    # queda abierta y nadie se da cuenta hasta que la vacían.
    "createDataBackups": True,
    "recoverFromBackupOnLoadFailure": True,
    "auditLogEnabled": True,
    # A su propio archivo y no al log del servidor, que ya tiene bastante.
    "mirrorAuditToServerLog": False,
}

if __name__ == "__main__":
    config = json.loads(mc.read(RUTA))
    config.setdefault("gameplay", {}).update(GAMEPLAY)
    config.setdefault("ops", {}).update(OPS)

    mc.write(RUTA, json.dumps(config, indent=2, ensure_ascii=False) + "\n")
    os.makedirs(os.path.dirname(LOCAL), exist_ok=True)
    open(LOCAL, "w", encoding="utf-8", newline="\n").write(
        json.dumps(config, indent=2, ensure_ascii=False) + "\n")

    for clave, valor in sorted(GAMEPLAY.items()):
        print("  %-32s %s" % (clave, valor))

    mc.cmd("sz reload")
    time.sleep(2)
    for nombre, limite in ADMINISTRADORES.items():
        mc.cmd("sz limits %s %d" % (nombre, limite))
        print("  %-32s %d zonas" % (nombre, limite))

    time.sleep(2)
    print("\nlo que dice el servidor:")
    for linea in mc.read("/logs/latest.log").splitlines()[-14:]:
        if "afe Zone" in linea or "imit" in linea or "laim" in linea:
            print("  " + linea.split("]: ")[-1])
