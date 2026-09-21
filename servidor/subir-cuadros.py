# -*- coding: utf-8 -*-
"""
El mod de los cuadros: colgar imagenes propias como pinturas.

    python servidor/subir-cuadros.py

Deja en `/mods` el jar de My Photo Paintings y reinicia si hizo falta. El jar va
en los dos lados (declara `environment: *`): del lado del jugador lo pone el pack
del launcher, con `npm run pack:jar`, porque este mod NO esta en Modrinth y por
eso el archivo lo sirve nuestro backend.

## Como se usa en el juego

Se craftea el item "Photo Painting" con ocho palos y un papel, se lo usa contra
una pared y se abre el explorador de archivos de Windows para elegir un PNG, JPG
o JPEG. Se elige la medida (de 1x1 hasta 16x16 bloques, con una sugerencia segun
la forma de la imagen) y se cuelga.

## Lo que hay que saber antes de esperar mucho

**El mod esta pensado para un solo jugador, y su autor lo dice.** Verificado en el
bytecode de 1.0.1: `client/PhotoImageStorage` guarda el PNG llamando a
`getSingleplayerServer()` y tiene preparado el mensaje "No singleplayer server
found.", y lo unico que viaja por la red (`PlacePhotoPaintingPayload`) es
`position; direction; width; height; imageId; cropMode; imageWidth; imageHeight`:
**la imagen no viaja**. En un servidor dedicado eso quiere decir que el archivo no
se guarda en ningun lado y que los demas no ven nada.

Esta puesto igual porque asi se decidio. Si no alcanza, el que si funciona en
servidor es Custom Paintings (las imagenes las elige el admin y las manda el
servidor) y volver es: `npm run pack:mod -- custom-paintings-mod` del lado del
launcher, y subir su jar aca.

## El jar

Va pinneado aca abajo con su sha1, y se baja del CDN de CurseForge, que es donde
esta publicado. Si el sha1 no coincide, no se sube nada.
"""
import hashlib
import io
import os
import sys
import urllib.request

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# My Photo Paintings 1.0.1 para 26.1, fijo a proposito. Pide `minecraft: "26.1"`
# exacto, asi que el dia que el servidor pase a 26.1.2 este mod deja de cargar.
JAR = "my_photo_paintings-1.0.1+mc26.1.jar"
JAR_SHA1 = "7af15971367bf165b9d0e0b9f30ef864218994cb"
JAR_URL = "https://mediafilez.forgecdn.net/files/8756/642/my_photo_paintings-1.0.1%2Bmc26.1.jar"

MOTIVO_DEL_KICK = "Volvemos en un minuto: se esta instalando el mod de los cuadros."


def subir_el_jar():
    """
    Deja el jar en /mods y borra cualquier version anterior: dos jars del mismo
    mod hacen que Fabric no arranque. Devuelve si hace falta reiniciar, o None si
    algo salio mal.
    """
    estaban = mc.ls("/mods")
    viejos = [n for n in estaban if n.startswith("my_photo_paintings") and n != JAR]
    if JAR in estaban and not viejos:
        print("  ya estaba %s, no hago nada" % JAR)
        return False

    print("  bajando %s" % JAR)
    with urllib.request.urlopen(JAR_URL) as respuesta:
        contenido = respuesta.read()

    firma = hashlib.sha1(contenido).hexdigest()
    if firma != JAR_SHA1:
        print("  el sha1 no coincide (%s), no subo nada" % firma)
        return None

    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    temporal = os.path.join(AQUI, JAR)
    with io.open(temporal, "wb") as archivo:
        archivo.write(contenido)
    try:
        mc.upload("/mods", temporal)
    finally:
        os.remove(temporal)
    print("  subido a mods/: %s" % JAR)
    return True


if __name__ == "__main__":
    hay_que_reiniciar = subir_el_jar()
    if hay_que_reiniciar is None:
        sys.exit(1)

    if hay_que_reiniciar:
        # Echarlos antes les deja un motivo escrito; el reinicio los saca igual,
        # pero con un "Server closed" que no explica nada.
        mc.cmd("kick @a " + MOTIVO_DEL_KICK)
        mc.power("restart")
        print("  echados y reiniciando, porque el jar es nuevo")

    print()
    print("Del lado del jugador va por el pack del launcher, no por aca.")
