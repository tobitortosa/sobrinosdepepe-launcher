# -*- coding: utf-8 -*-
"""
Prueba los selectores de sdp:tick con muñecos, en el servidor de verdad.

    python servidor/probar-tick.py

Las cuatro lineas del limite blando y las dos de las cajas corren sobre @a veinte
veces por segundo. Un volumen mal escrito no falla: teletransporta gente. Asi que
en vez de razonarlo, se ponen armor stands en puntos elegidos y se le pregunta al
servidor a cuales agarra cada selector.

Dos trampas de la prueba misma, las dos ya pagadas:

  - Nada de `Marker:1b`: un marker no tiene caja de colision y los selectores de
    volumen preguntan por interseccion, asi que no lo agarraria ninguno y la prueba
    daria que todo esta mal cuando esta bien.
  - Los tags de los muñecos no pueden ser prefijo uno de otro, y se buscan **entre
    comillas** en la respuesta. Si no, `sdppasadox` matchea dentro de
    `sdppasadoxneg` y aparecen agarrados los que no.
"""
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# nombre, x, y, z, y a que selectores TIENE que contestar que si
PUNTOS = [
    ("elspawn",         0,      100, 0,      set()),
    ("cercadelborde",   14000,  100, 0,      set()),
    ("afueraxmas",      20000,  100, 0,      {"banda+x"}),
    ("afueraxmenos",   -20000,  100, 0,      {"banda-x"}),
    ("afuerazmas",      0,      100, 20000,  {"banda+z"}),
    ("afuerazmenos",    0,      100, -20000, {"banda-z"}),
    # Parado en el patio del lobby: NO se lo toca. La caja solo agarra al que se
    # cayo, o sea al que esta por debajo del piso.
    ("enelpatio",       500000, 250, 0,      set()),
    ("cayendodellobby", 500000, 150, 0,      {"caja-lobby"}),
    ("enlasgradas",     501163, 267, 11631,  set()),
    ("cayendoalcoliseo", 501163, 150, 11631, {"caja-coliseo"}),
]

SELECTORES = {
    "banda+x":      "@s[x=15020,dx=384980]",
    "banda-x":      "@s[x=-400000,dx=384980]",
    "banda+z":      "@s[z=15020,dz=384980]",
    "banda-z":      "@s[z=-400000,dz=384980]",
    "caja-lobby":   "@s[x=499950,dx=100,y=-64,dy=294,z=-50,dz=100]",
    "caja-coliseo": "@s[x=501125,dx=100,y=-64,dy=304,z=11570,dz=100]",
}


def poner():
    for _, x, _, z, _ in PUNTOS:
        mc.cmd("execute in minecraft:overworld run forceload add %d %d" % (x, z))
    time.sleep(6)
    for nombre, x, y, z, _ in PUNTOS:
        mc.cmd('execute in minecraft:overworld run summon armor_stand %d %d %d '
               '{Tags:["sdpprueba","sdp%s"],Invulnerable:1b,NoGravity:1b}'
               % (x, y, z, nombre))
    time.sleep(4)


def preguntar():
    salieron = {}
    for cual, selector in SELECTORES.items():
        lineas = mc.responde(
            "execute as @e[type=armor_stand,tag=sdpprueba] at @s if entity %s "
            "run data get entity @s Tags" % selector, espera=2.5)
        texto = " ".join(lineas)
        salieron[cual] = {n for n, _, _, _, _ in PUNTOS if '"sdp%s"' % n in texto}
    return salieron


def limpiar():
    mc.cmd("kill @e[type=armor_stand,tag=sdpprueba]")
    for _, x, _, z, _ in PUNTOS:
        mc.cmd("execute in minecraft:overworld run forceload remove %d %d" % (x, z))


if __name__ == "__main__":
    poner()
    salieron = preguntar()
    limpiar()

    bien = True
    print("%-14s %-20s %s" % ("selector", "esperado", "contestó"))
    for cual in SELECTORES:
        esperado = {n for n, _, _, _, cuales in PUNTOS if cual in cuales}
        ok = esperado == salieron[cual]
        bien = bien and ok
        print("%-14s %-20s %-20s %s"
              % (cual, ",".join(sorted(esperado)) or "-",
                 ",".join(sorted(salieron[cual])) or "-",
                 "" if ok else "<- NO COINCIDE"))
    print()
    print("todo bien" if bien else "HAY UNA LINEA MAL: no la dejes corriendo sobre @a")
