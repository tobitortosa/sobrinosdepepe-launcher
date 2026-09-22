# -*- coding: utf-8 -*-
"""
Deja EasyAuth como lo queremos: al servidor se entra con contrasena.

    python servidor/configurar-auth.py

Por que existe esto. El servidor es **offline-mode**, o sea que el nombre no lo
verifica nadie: cualquiera escribe "Chichon" y entra como Chichon. Mientras el
candado del launcher estuvo puesto eso no importaba, porque el permiso lo firmaba
el backend y sin permiso no entrabas. Desde que la puerta esta abierta y se
promociona la IP, lo unico que separa la casa de alguien de cualquiera que sepa
escribir su nombre es esta contrasena.

Como se ve desde adentro del juego:

  1. Entras y no te podes mover, ni pegar, ni hablar, ni abrir el inventario.
     Ademas sos invulnerable, los bichos te ignoran y los demas no te ven.
  2. El servidor te repite cada 10 segundos que escribas
     `/register <contrasena> <contrasena>` la primera vez, o `/login <contrasena>`
     las siguientes. Tambien andan `/reg` y `/l`.
  3. Listo. Todo lo tuyo sigue donde estaba.

**Nadie pierde nada al registrarse.** El inventario, la plata, los shards, las
casas y los avances cuelgan del UUID, y el UUID sale del nombre
(`OfflinePlayer:<nombre>`, v3) exactamente igual que antes. EasyAuth no lo toca:
`forced-offline-uuid` queda en false y `premium-auto-login` tambien, que son las
dos unicas opciones capaces de cambiarlo.

El mod guarda las contrasenas hasheadas en un SQLite propio
(`/config/EasyAuth/EasyAuth/easyauth.db`), y **eso no lo respalda este script**:
si se pierde, cada uno se vuelve a registrar, pero el nombre queda libre para el
primero que llegue.

Lo que NO esta puesto y se puede prender despues:

  hide-player-coords  Te teletransporta a un punto fijo mientras no estas
                      logueado, para que la pantalla no muestre donde vivis. Es
                      para cuando Tobias streamea. Se prende parandose donde se
                      quiera y escribiendo `/auth setWorldSpawn`, que escribe las
                      coordenadas solo; despues hay que poner esta opcion en true.
  ip-limit            Un tope de cuentas por IP. Apagado porque dos hermanos en
                      la misma casa son dos cuentas en la misma IP.
"""
import sys
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

CARPETA = "/config/EasyAuth/"

# Cada uno es (archivo, valor de fabrica, valor nuestro). Se aplica por reemplazo
# de texto y es idempotente: si ya esta como lo queremos, no encuentra nada y avisa.
AJUSTES = [
    # Las dos piden online-mode=true para hacer algo, y este servidor es offline.
    # Prendidas lo unico que logran es pegarle a la API de Mojang al pedo.
    ("main.conf", "premium-auto-login=true", "premium-auto-login=false"),
    ("main.conf", "floodgate-auto-login=true", "floodgate-auto-login=false"),
    # Un minuto es poco para alguien que entra por primera vez, tiene que pensar
    # una contrasena y escribirla dos veces.
    ("main.conf", "kick-timeout=60", "kick-timeout=120"),
    # Sin esto Simple Voice Chat no llega a hacer su handshake mientras el jugador
    # no esta logueado, y queda sin voz hasta que sale y vuelve a entrar.
    ("extended.conf", "allowed-custom-packets=[]",
     'allowed-custom-packets=[\n    "voicechat:"\n]'),
    # Aca no hay registro de cofres: cuando alguien reclame algo, el log va a ser
    # lo unico que haya. Que quede escrito quien se registro y quien entro.
    ("extended.conf", "log-player-registration=false", "log-player-registration=true"),
    ("extended.conf", "log-player-login=false", "log-player-login=true"),
    # El mod traduce sus mensajes al idioma de cada cliente; esto es para los que
    # no se pueden traducir.
    ("translation.conf", 'default-language="en_us"', 'default-language="es_ar"'),
]


def main():
    por_archivo = {}
    for archivo, viejo, nuevo in AJUSTES:
        por_archivo.setdefault(archivo, []).append((viejo, nuevo))

    for archivo, cambios in por_archivo.items():
        ruta = CARPETA + archivo
        texto = mc.read(ruta)
        tocado = False
        print(archivo)
        for viejo, nuevo in cambios:
            if nuevo in texto:
                print("  ya estaba: %s" % nuevo.splitlines()[0])
            elif viejo in texto:
                texto = texto.replace(viejo, nuevo, 1)
                tocado = True
                print("  %s -> %s" % (viejo, nuevo.splitlines()[0]))
            else:
                print("  OJO: no encontre %r (cambio el formato?)" % viejo)
        if tocado:
            mc.write(ruta, texto)

    mc.cmd("auth reload")
    print("\nrecargado. Se entra con /register <contrasena> <contrasena>.")


if __name__ == "__main__":
    main()
