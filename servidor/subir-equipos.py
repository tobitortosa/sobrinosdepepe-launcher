# -*- coding: utf-8 -*-
"""
Los equipos, y la barra de arriba que deja de ser un radar.

    python servidor/subir-equipos.py

Sube los dos jars a /mods y reinicia si alguno era nuevo:

  - **equipos-sobrinosdepepe** (mod-equipos/, nuestro): el comando /equipo, con
    crear, invitar, aceptar, echar y salir. Por debajo cada equipo es un equipo
    del scoreboard llamado `sdp_<nombre>`, y quien manda en cada uno sale de
    `config/equipos-de-pepe.json`, que escribe el mod solo.
  - **team-only-locator-bar** (de Modrinth, MIT, 5 KB): la barra de arriba pasa
    a mostrar unicamente a los del mismo equipo.

## Por que hacia falta

26.1 trae de fabrica la barra de waypoints arriba del inventario, y de fabrica
**muestra a todos los jugadores del servidor**. Es la gamerule `locator_bar`, en
`true`. En un survival de PvP libre donde matar paga 10 shards, el 10% de la
plata del muerto y todo el equipo que llevaba puesto, eso es un radar: nadie se
esconde, nadie se escapa y buscar pelea deja de tener merito. Es lo mismo que ya
nos hizo sacar los minimapas de Xaero el 2026-09-06, y por el mismo motivo.

Se podia apagar entera con `gamerule locator_bar false`. No se hizo asi: apagada
no molesta a nadie pero tampoco le sirve a nadie, y con team-only-locator-bar la
misma barra pasa a ser lo que hace que valga la pena armar equipo. El que no
tiene equipo no ve a nadie, que es exactamente lo que se pedia.

Si algun dia igual se quiere sin barra: `gamerule locator_bar false` y listo. El
mod no molesta con la gamerule apagada, simplemente no hay barra que filtrar.

## Los dos van SOLO del lado del servidor

Ninguno de los dos toca el launcher y nadie tiene que instalar nada:

  - el nuestro declara `environment: server`;
  - team-only-locator-bar declara `environment: *`, pero su unico entrypoint es
    `main` y su unico mixin es sobre `WaypointTransmitter.doesSourceIgnoreReceiver`,
    que corre en el servidor: es el servidor el que decide a quien le manda cada
    punto de la barra.

O sea que a los que juegan sin el launcher tampoco hay que pasarles ningun jar.
"""
import hashlib
import io
import os
import sys
import urllib.request

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# El mod de la barra, fijo con su sha1 como todo lo que se baja de afuera.
BARRA = "team-only-locator-bar-1.0.0+MC26.1.jar"
BARRA_SHA1 = "31ffe02a4ff31c2a1281c8269c4432f04b339fc6"
BARRA_URL = ("https://cdn.modrinth.com/data/eGoLIHLR/versions/NyunndYc/"
             "team-only-locator-bar-1.0.0%2BMC26.1.jar")

# El nuestro sale de mod-equipos/build/libs. Se compila con un JDK 25:
#     JAVA_HOME=<el jdk 25> ./gradlew build
NUESTRO = os.path.join(os.path.dirname(AQUI), "mod-equipos", "build", "libs",
                       "equipos-sobrinosdepepe-1.0.0.jar")

MOTIVO_DEL_KICK = "Volvemos en un minuto: se estan instalando los equipos."


def subir(nombre, contenido, prefijo):
    """
    Deja el jar en /mods y borra cualquier version anterior del mismo mod: dos
    jars del mismo mod hacen que Fabric no arranque. Devuelve si hace falta
    reiniciar.
    """
    estaban = mc.ls("/mods")
    viejos = [n for n in estaban if n.startswith(prefijo) and n != nombre]

    if nombre in estaban and not viejos:
        # El nuestro cambia de contenido sin cambiar de nombre en cada compilada,
        # asi que no alcanza con que el nombre este: se compara el archivo.
        if hashlib.sha1(mc_leer_binario("/mods/" + nombre)).hexdigest() == \
                hashlib.sha1(contenido).hexdigest():
            print("  ya estaba %s, igualito" % nombre)
            return False

    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    temporal = os.path.join(AQUI, nombre)
    with io.open(temporal, "wb") as archivo:
        archivo.write(contenido)
    try:
        mc.upload("/mods", temporal)
    finally:
        os.remove(temporal)
    print("  subido a mods/: %s" % nombre)
    return True


def mc_leer_binario(ruta):
    """
    El contenido de un archivo del servidor tal cual, sin pasar por texto.

    mc.read() devuelve str y a un .jar le rompe los bytes que no son UTF-8, asi
    que la comparacion daria siempre distinto y subiriamos el jar cada vez.
    """
    import json
    import urllib.parse
    firmada = json.loads(mc.call("/files/download?file=" + urllib.parse.quote(ruta)))
    with urllib.request.urlopen(firmada["attributes"]["url"]) as respuesta:
        return respuesta.read()


def bajar_la_barra():
    print("  bajando %s" % BARRA)
    with urllib.request.urlopen(BARRA_URL) as respuesta:
        contenido = respuesta.read()
    firma = hashlib.sha1(contenido).hexdigest()
    if firma != BARRA_SHA1:
        print("  el sha1 no coincide (%s), no subo nada" % firma)
        return None
    return contenido


if __name__ == "__main__":
    if not os.path.exists(NUESTRO):
        print("Falta %s. Compilalo con:" % NUESTRO)
        print("  cd mod-equipos && JAVA_HOME=<un jdk 25> ./gradlew build")
        sys.exit(1)

    barra = bajar_la_barra()
    if barra is None:
        sys.exit(1)

    with io.open(NUESTRO, "rb") as archivo:
        nuestro = archivo.read()

    hay_que_reiniciar = subir(BARRA, barra, "team-only-locator-bar")
    hay_que_reiniciar |= subir(os.path.basename(NUESTRO), nuestro, "equipos-sobrinosdepepe")

    if hay_que_reiniciar:
        # Echarlos antes les deja un motivo escrito; el reinicio los saca igual,
        # pero con un "Server closed" que no explica nada.
        mc.cmd("kick @a " + MOTIVO_DEL_KICK)
        mc.power("restart")
        print()
        print("Echados y reiniciando, porque algun jar es nuevo.")
        print("Los mods se cargan al arrancar: sin reinicio no pasa nada.")
    else:
        print()
        print("No cambio nada, asi que no reinicio.")
