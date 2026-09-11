# -*- coding: utf-8 -*-
"""
Cuanto le sobra o le falta al servidor. Corrrelo CON GENTE JUGANDO.

    python servidor/medir-carga.py            mide 10 minutos
    python servidor/medir-carga.py 30         mide 30 minutos

Toma una muestra cada quince segundos y al final dice los picos. Existe porque
"el servidor anda lento" y "hay que comprar mas RAM" son dos frases distintas, y
sin numeros no hay forma de saber si la segunda es la respuesta de la primera.

## Que mirar de lo que imprime

**El tiempo por tick es LO que importa.** El servidor tiene 50 ms para hacer cada
tick; si tarda menos, va perfecto, y si tarda mas, el juego se empieza a arrastrar
para todos. Es casi siempre un problema de procesador, no de memoria.

  - menos de 25 ms  sobra maquina
  - 25 a 40 ms      empieza a apretar; hora de mirar que lo carga
  - mas de 50 ms    el servidor ya no llega: eso es el lag que se siente

**La memoria casi nunca es el problema.** Java agarra toda la que le dejan y la
suelta cuando le hace falta, asi que verla alta no quiere decir que falte. Lo que
si importa es que el pico no se acerque al limite del plan, porque el sistema mata
el proceso sin avisar cuando se pasa: el servidor se cae de golpe y en el log no
queda ni una linea.

**El procesador** en este plan no tiene tope, asi que el numero es cuanto usa de un
nucleo (100% = un nucleo entero). Minecraft hace casi todo en un solo hilo, asi que
lo que importa es que ese nucleo sea rapido, no que haya muchos.
"""
import json
import os
import re
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

CADA = 15
MINUTOS = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 10


def limite_de_memoria():
    return json.loads(mc.call(""))["attributes"]["limits"]["memory"]


def una_muestra():
    """Memoria en MB, procesador en %, jugadores y ms por tick."""
    r = json.loads(mc.call("/resources"))["attributes"]
    memoria = r["resources"]["memory_bytes"] / 1024 / 1024
    cpu = r["resources"]["cpu_absolute"]

    antes = len(mc.read("/logs/latest.log").splitlines())
    mc.cmd("tick query")
    mc.cmd("list")
    time.sleep(2)
    nuevas = mc.read("/logs/latest.log").splitlines()[antes:]

    tick, jugadores = None, None
    for linea in nuevas:
        texto = linea.split("]: ")[-1]
        m = re.search(r"Average time per tick: ([\d.]+)ms", texto)
        if m:
            tick = float(m.group(1))
        m = re.search(r"There are (\d+) of a max", texto)
        if m:
            jugadores = int(m.group(1))

    return memoria, cpu, jugadores, tick


if __name__ == "__main__":
    limite = limite_de_memoria()
    print("plan: %d MB de memoria. Midiendo %d minutos, una muestra cada %d segundos."
          % (limite, MINUTOS, CADA))
    print()
    print("%-9s %-12s %-9s %-11s %s" % ("hora", "memoria", "cpu", "jugadores", "ms por tick"))

    muestras = []
    hasta = time.time() + MINUTOS * 60

    while time.time() < hasta:
        try:
            memoria, cpu, jugadores, tick = una_muestra()
        except Exception as e:
            print("  (no pude medir: %s)" % e)
            time.sleep(CADA)
            continue

        muestras.append((memoria, cpu, jugadores or 0, tick or 0))
        print("%-9s %-12s %-9s %-11s %s"
              % (time.strftime("%H:%M:%S"), "%d MB" % memoria, "%.0f%%" % cpu,
                 jugadores if jugadores is not None else "?",
                 "%.1f" % tick if tick else "?"))
        time.sleep(CADA)

    if not muestras:
        sys.exit("no salio ninguna muestra")

    pico_memoria = max(m[0] for m in muestras)
    pico_cpu = max(m[1] for m in muestras)
    pico_jugadores = max(m[2] for m in muestras)
    pico_tick = max(m[3] for m in muestras)

    print()
    print("PICOS de %d muestras" % len(muestras))
    print("  jugadores   : %d" % pico_jugadores)
    print("  memoria     : %d MB de %d (%.0f%% del plan)" % (pico_memoria, limite, pico_memoria / limite * 100))
    print("  procesador  : %.0f%% de un nucleo" % pico_cpu)
    print("  ms por tick : %.1f de 50" % pico_tick)
    print()

    if pico_tick > 45:
        print("El servidor no llega a hacer su trabajo en el tiempo que tiene.")
        print("Eso es procesador, no memoria: un plan con mas RAM no lo arregla.")
    elif pico_tick > 25:
        print("Va bien pero ya no le sobra. Vale la pena mirar que lo carga")
        print("(granjas grandes, muchas entidades) antes de gastar en un plan mas caro.")
    else:
        print("Al servidor le sobra maquina: usa %.0f%% del tiempo que tiene por tick." % (pico_tick / 50 * 100))

    if pico_memoria > limite * 0.85:
        print()
        print("OJO con la memoria: el pico llego al %.0f%% del limite del plan." % (pico_memoria / limite * 100))
        print("Cuando el proceso se pasa, el sistema lo mata sin avisar y el servidor")
        print("se cae de golpe. Ahi si tiene sentido mas RAM, o bajarle el -Xmx a Java.")
