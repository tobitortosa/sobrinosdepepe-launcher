# -*- coding: utf-8 -*-
"""
La varita de zonas tambien protege bichos.

    python servidor/subir-varita.py

Sube el jar de mod-varita a /mods y reinicia si era nuevo, porque Fabric carga
los mods una sola vez al arrancar.

## Que hace

Click derecho con la varita sobre un animal —o sobre cualquier cosa que no sea un
jugador— y ese bicho queda intocable: no lo mata nadie, ni un jugador, ni un
creeper, ni el fuego, ni una caida. Otro click derecho y vuelve a la normalidad.
Es para el caballo, el perro o la vaca que uno no quiere perder.

Avisa en el momento: chispas verdes y un campanilleo cuando queda protegido, humo
y un chistido cuando se le saca. En el chat dice cual es y como quedo.

## Por que no hay nada que configurar

**Es la misma varita del mod Safe Zone**, el palo de depuracion que no tiene
receta y que solo puede dar un operador:

    /give PEPE minecraft:debug_stick[enchantments={vanishing_curse:1},custom_name={text:"Varita de zonas",color:"#ffb02e",italic:false}] 1

Las dos cosas no se pisan: Safe Zone trabaja con el click derecho sobre un
**bloque**, para marcar las esquinas de una zona, y esto con el click derecho
sobre una **entidad**, que en Minecraft es un evento aparte.

Tampoco hay archivo de estado: "invulnerable" es una marca que el juego ya guarda
con cada entidad, asi que sobrevive sola a los reinicios. Y es solo para
operadores: al que no lo es, el click derecho le sigue de largo como siempre.

## Los dos limites, que conviene saber antes de confiarse

**A un jugador no se le puede poner.** Un jugador invulnerable en un servidor de
PvP libre no es una comodidad, es hacer trampa: no se le puede sacar ni la plata
ni el equipo, que es de lo que vive el servidor. La varita lo dice y no lo hace.

**El vacio y el /kill igual lo matan.** El juego los deja pasar por encima de la
invulnerabilidad a proposito, para que nada quede trabado para siempre. O sea que
un bicho protegido empujado al vacio del End se muere igual.
"""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

JARS = os.path.join(os.path.dirname(AQUI), "mod-varita", "build", "libs")
PREFIJO = "varita-sobrinosdepepe"


def subir_el_jar():
    """
    Deja en mods/ el jar compilado y borra las versiones anteriores: dos jars del
    mismo mod hacen que Fabric no arranque, y el servidor queda apagado.
    """
    if not os.path.isdir(JARS):
        print("No encontré %s. Compilá el mod: cd mod-varita && ./gradlew build" % JARS)
        return None

    nuestros = sorted(n for n in os.listdir(JARS) if n.startswith(PREFIJO) and n.endswith(".jar"))
    if not nuestros:
        print("No hay ningún %s*.jar en %s. Compilá el mod." % (PREFIJO, JARS))
        return None

    nombre = nuestros[-1]
    estaban = mc.ls("/mods")
    viejos = [n for n in estaban if n.startswith(PREFIJO) and n != nombre]
    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    mc.upload("/mods", os.path.join(JARS, nombre))
    print("  subido a mods/: %s" % nombre)
    return nombre, nombre not in estaban


if __name__ == "__main__":
    subido = subir_el_jar()
    if subido is None:
        sys.exit(1)

    _, hay_que_reiniciar = subido

    if hay_que_reiniciar:
        mc.power("restart")
        print("  reiniciando, porque el jar es nuevo")

    print()
    print("Listo. Con la varita en la mano, click derecho a un animal y no lo mata nadie.")
    print("Otro click derecho y vuelve a ser un bicho normal.")
