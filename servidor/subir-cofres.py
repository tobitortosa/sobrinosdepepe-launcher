# -*- coding: utf-8 -*-
"""
El comando para ver el cofre de ender de cualquiera.

    python servidor/subir-cofres.py

Sube el jar de mod-cofres a /mods y reinicia si era nuevo, porque Fabric carga
los mods una sola vez al arrancar. Antes de reiniciar avisa por chat y espera,
que a esta altura casi siempre hay alguien jugando.

## Qué hace

    /cofre <jugador>

Abre el cofre de ender de ese jugador y deja sacar y meter cosas, como si fuera
el tuyo. Funciona con el que está conectado y —lo que importa— con el que no: si
alguien quedó baneado por IP, su cofre sigue estando en el archivo del jugador y
esto lo lee de ahí. La lista de nombres que sugiere el tabulador son los
conectados más los baneados, que son a quienes se les mira el cofre.

Es **solo para operadores de nivel 4**: `Commands.LEVEL_OWNERS`. Al que no lo es
el comando ni le aparece cuando escribe la barra.

## Por qué es un mod y no un script

Lo mismo se puede mirar desde esta PC con `ver-cofre.py`, que no necesita nada
instalado. La diferencia es que ahí los items son texto y acá son items: se ven
las shulkers, los encantamientos y los nombres puestos, igual que si abrieras el
cofre.

## Lo que sacas se lo sacas

No hay item que aparezca de la nada: al cerrar la ventana, lo que quedó se
escribe en el archivo del jugador. Si la ventana trabajara sobre una copia,
sacar algo sería fabricarlo y el baneado se quedaría igual con lo suyo.

Tres cuidados que están adentro del mod y conviene saber que existen:

- Al guardar se **relee** el archivo y se le cambia únicamente `EnderItems`. Si
  no, la posición, la vida y la mochila volverían al estado que tenían cuando se
  abrió la ventana.
- Si el jugador **se conectó** mientras tanto no se guarda nada y te avisa por
  chat: su archivo pasa a manejarlo el servidor y escribirlo por debajo no sirve
  de nada, porque al desconectarse lo pisa igual.
- Se escribe a un temporal y recién después se renombra. Una escritura cortada a
  la mitad deja el archivo del jugador corrupto, y con él se pierde esa cuenta.
"""
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

JARS = os.path.join(os.path.dirname(AQUI), "mod-cofres", "build", "libs")
PREFIJO = "cofres-sobrinosdepepe"
AVISO = 15  # segundos entre el aviso por chat y el reinicio


def subir_el_jar():
    """
    Deja en mods/ el jar compilado y borra las versiones anteriores: dos jars del
    mismo mod hacen que Fabric no arranque, y el servidor queda apagado.
    """
    if not os.path.isdir(JARS):
        print("No encontré %s. Compilá el mod: cd mod-cofres && ./gradlew build" % JARS)
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
        mc.cmd("say Reinicio en %d segundos, vuelve enseguida." % AVISO)
        print("  avisado por chat; reiniciando en %d segundos" % AVISO)
        time.sleep(AVISO)
        mc.power("restart")
        print("  reiniciando, porque el jar es nuevo")

    print()
    print("Listo. Siendo operador: /cofre <jugador>, y se puede sacar y meter.")
    print("El tabulador sugiere los conectados y los baneados.")
