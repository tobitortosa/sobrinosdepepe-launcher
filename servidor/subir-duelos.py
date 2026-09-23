# -*- coding: utf-8 -*-
"""
El coliseo: `/pvp <jugador>` y todo lo que viene con él.

    python servidor/subir-duelos.py

Sube el jar de `mod-duelos/` a `/mods` y reinicia si el archivo cambió, porque
Fabric carga los mods una sola vez al arrancar. Antes de reiniciar avisa por chat
y espera, que a esta altura casi siempre hay alguien jugando.

## Qué agrega

    /pvp <jugador> [shards] [clase]  retarlo a un duelo
    /pvp aceptar <jugador>     entrar a la arena contra él (le llega un botón)
    /pvp rechazar <jugador>    decirle que no
    /pvp rendirse              abandonar el duelo que estás peleando
    /pvp apostar <jug> <n>     ponerle shards a uno de los dos
    /pvp top                   los que más duelos ganaron
    /pvp duelos                cómo funciona todo esto
    /pvp arena ...             marcar y mirar la arena (operadores)
    /pvp arena zona <id> [alto] tomar una zona protegida como arena
    /pvp torneo ...            abrir, anotarse y arrancar el torneo
    /pvp probar                probar el guardado del inventario (operadores)

`/pvp` a secas **no se toca**: lo sigue abriendo Melius con el menú de cofre
`sdp:pvp`, que es el que tiene el `op_level: 4` que hace que el menú se le abra
también al que no es operador. El mod registra solamente los hijos, sin
`executes` en la raíz, y Brigadier los fusiona: `CommandNode.addChild` pisa el
`command` del nodo solo si el nuevo trae uno, así que con el nuestro en null el
de Melius sobrevive sin importar cuál de los dos registre primero.

## Cómo se marca la arena

**Lo más fácil, con una zona protegida de las de siempre:**

    click derecho en una esquina y en la opuesta   (la zona de siempre)
    parado adentro:  /pvp arena guardar [alto]
    /sz remove       parado adentro, para borrar la zona

**El tercer paso no es opcional.** Adentro de una zona de Safe Zone nadie puede
romper ni poner un bloque, y el dueño de la zona es inmune al daño de explosión
mientras esté parado adentro: con la zona encima, los kits de TNT y de crystals
quedan de adorno y el dueño del coliseo gana todos los duelos sin despeinarse.
Borrar la zona **no borra la arena**, que ya quedó guardada con sus coordenadas.
El mod avisa en rojo, con el id de la zona, en `/pvp arena guardar` y en
`/pvp arena`.

El alto lo pone el comando y no la zona, porque la protección de Safe Zone no
mira la altura: son 24 por defecto, contados desde un bloque abajo del piso que
se clickeó. `/pvp arena guardar 40` para otro, o `/pvp arena zona <id> 40` sin
estar parado adentro (el id sale de `/sz list`).

**La otra forma, sin crear ninguna zona**, es el mismo palo de depuración pero
con el click **izquierdo**: una esquina de abajo, la de arriba opuesta y
`/pvp arena guardar`. Ahí el alto sale de los dos clicks y no hay zona que
borrar.

Los dos gestos conviven porque son distintos: el derecho es de Safe Zone, el
izquierdo es de esto. Si fueran el mismo, marcar una arena reclamaría un terreno
sin querer, y el orden entre dos mods escuchando el mismo evento no está
garantizado.

**La caja va ALTA.** De ahí adentro no se sale mientras se pelea, así que si el
techo de la caja queda a la altura del piso, al primero que salte lo devuelve de
un tirón. Una esquina abajo del piso y la otra bien arriba de la pared.

El tope son 400.000 bloques (por ejemplo 100 x 100 de piso por 40 de alto):
antes de cada pelea se saca una foto entera de la arena para poder dejarla como
estaba, y esa foto es un arreglo con un bloque por casillero que vive en la RAM
del servidor.

## Las tres cosas que lo hacen distinto de salir a pelear afuera

- **No se pierde nada.** Adentro se juega con un kit prestado, el inventario de
  verdad se guarda y vuelve entero al terminar. Lo único que se juega es lo que
  se apostó. La muerte se cancela (`ServerLivingEntityEvents.ALLOW_DEATH`) y en
  su lugar termina el duelo: si la muerte pasara de verdad, el que pierde
  soltaría el kit, el que gana cobraría los 10 shards de la kill y el 10% de la
  plata del muerto, y dos amigos turnándose tendrían una máquina de shards.
- **No se mete nadie.** Al que no está peleando lo saca de la arena, y los golpes
  no pasan ni para adentro ni para afuera. Romper y poner bloques adentro es solo
  de los dos que pelean.
- **La arena vuelve a como estaba, después de CADA pelea.** Se puede romper todo,
  poner crystals y volar el piso con TNT. Vuelven los bloques, vuelve lo que los
  bloques tenían adentro (el contenido de un cofre, el texto de un cartel, el
  dibujo de un estandarte) y se barre lo que quedó tirado. La decoración que no es
  un bloque —cuadros, soportes de armadura, vitrinas— se hace intocable mientras
  dura el duelo, así no hay nada que rehacer. Lo que alguien construya o decore
  **entre** dos peleas entra en la foto siguiente y se respeta.

## La plata

**Todo lo que se apuesta es en shards y nada en plata**, y no es una preferencia:
la plata de EconomyCraft no vive en ningún scoreboard y sus comandos devuelven
éxito aunque el jugador no tenga un peso (`eco removemoney` y `pay` devuelven 1
siempre, `docs/05`). Cobrarle una apuesta en plata a alguien sin saldo saldría
bien y el servidor regalaría la diferencia.

Los shards de un duelo **no se crean ni se destruyen**: los que pone el que
pierde son exactamente los que cobra el que gana, y lo que apostaron los que
erraron se reparte entre los que acertaron en proporción a lo que arriesgaron.
Sin comisión de la casa. Es la única forma de meter apuestas sin inflar la
economía.

## Va solo del lado del servidor

Declara `environment: server`, así que **no toca el launcher** y nadie tiene que
instalar nada: los títulos de la cuenta regresiva, la barra de arriba y los
sonidos son todos del juego, mandados desde el servidor. Al jugador le aparece
`/pvp <jugador>` y listo.

## Probar que no se pierde nada, sin pelear

    /pvp probar

Guarda tu inventario como lo guardaría un duelo, lo escribe, lo vuelve a leer y
lo compara casillero por casillero. **No toca nada.** Contesta cuántos volvieron
idénticos y, si alguno no volvió, cuál. Correlo parado con lo mejor que tengas
—elytra, shulkers llenas, netherita encantada— porque así queda probado contra
items de verdad y no contra un ejemplo.

Además, dos candados que no dependen de nadie: un duelo **no arranca** si a
alguno de los dos le quedó un inventario sin devolver de antes, y `Registro`
**se niega a pisar** una copia que todavía no se devolvió. Pisarla sería la única
forma de perderlo todo de verdad: la segunda copia guardaría el kit prestado como
si fuera el inventario del jugador.

## Si el servidor se cae con una pelea empezada

Los inventarios de los dos están en `config/duelos-de-pepe.json` desde el momento
en que empieza el duelo, así que al arrancar de nuevo cada uno recupera lo suyo
al conectarse. Lo que **no** se recupera es la arena: la foto vive en la memoria,
así que si el servidor se cae en el medio el coliseo queda como haya quedado.
"""
import hashlib
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

MOD = os.path.join(os.path.dirname(AQUI), "mod-duelos")
PREFIJO = "duelos-sobrinosdepepe"
AVISO = 15  # segundos entre el aviso por chat y el reinicio


def version_del_mod():
    """
    La versión sale de gradle.properties, que es de donde la saca Gradle para
    nombrar el jar. Escrita en los dos lados, subir una versión nueva sale mal
    una de cada dos veces.
    """
    for linea in io.open(os.path.join(MOD, "gradle.properties"), encoding="utf-8"):
        if linea.startswith("mod_version="):
            return linea.split("=", 1)[1].strip()
    raise SystemExit("mod-duelos/gradle.properties no dice mod_version")


def leer_binario_del_servidor(ruta):
    """
    El contenido de un archivo del servidor tal cual, sin pasar por texto.

    mc.read() devuelve str y a un .jar le rompe los bytes que no son UTF-8, así
    que la comparación daría siempre distinto y subiríamos el jar cada vez.
    """
    firmada = json.loads(mc.call("/files/download?file=" + urllib.parse.quote(ruta)))
    with urllib.request.urlopen(firmada["attributes"]["url"]) as respuesta:
        return respuesta.read()


def subir():
    """Deja el jar en /mods y borra las versiones anteriores del mismo mod: dos
    jars del mismo mod hacen que Fabric no arranque y el servidor queda apagado.
    Devuelve si hace falta reiniciar."""
    nombre = "%s-%s.jar" % (PREFIJO, version_del_mod())
    local = os.path.join(MOD, "build", "libs", nombre)
    if not os.path.exists(local):
        print("Falta %s. Compilalo con:" % local)
        print("  cd mod-duelos && JAVA_HOME=<un jdk 25> ./gradlew build")
        return None

    with io.open(local, "rb") as archivo:
        contenido = archivo.read()

    estaban = mc.ls("/mods")
    viejos = [n for n in estaban if n.startswith(PREFIJO) and n != nombre]

    if nombre in estaban and not viejos:
        # El jar cambia de contenido sin cambiar de nombre en cada compilada, así
        # que no alcanza con que el nombre esté: se compara el archivo.
        if hashlib.sha1(leer_binario_del_servidor("/mods/" + nombre)).hexdigest() == \
                hashlib.sha1(contenido).hexdigest():
            print("  ya estaba %s, igualito" % nombre)
            return False

    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    mc.upload("/mods", local)
    print("  subido a mods/: %s" % nombre)
    return True


if __name__ == "__main__":
    hay_que_reiniciar = subir()
    if hay_que_reiniciar is None:
        sys.exit(1)

    if hay_que_reiniciar:
        mc.cmd("say Reinicio en %d segundos: se instala el coliseo. Vuelve enseguida." % AVISO)
        print("  avisado por chat; reiniciando en %d segundos" % AVISO)
        time.sleep(AVISO)
        # `restart` con el servidor ya apagado no lo prende: lo deja apagado y el
        # script sigue como si nada. Paso de verdad el 2026-09-22, despues de
        # apagarlo a mano para editar la config de la arena.
        estaba = json.loads(mc.call("/resources"))["attributes"]["current_state"]
        mc.power("start" if estaba == "offline" else "restart")
        print("  reiniciando, porque el jar es nuevo")
    else:
        print()
        print("No cambió nada, así que no reinicio.")

    print()
    print("Para dejar lista la arena, con la varita en la mano:")
    print("  click izquierdo en una esquina de abajo")
    print("  click izquierdo en la esquina de arriba opuesta")
    print("  /pvp arena guardar")
    print()
    print("Y después cualquiera puede escribir /pvp <jugador>.")
