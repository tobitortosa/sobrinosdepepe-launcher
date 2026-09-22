# -*- coding: utf-8 -*-
"""
Las dos cosas que frenan las trampas del lado del servidor.

    python servidor/subir-antitrampas.py

Deja en `/mods` el anti-xray y el anticheat, con la version exacta de siempre y
verificando el hash de lo que se baja. Si alguno era nuevo, reinicia.

## Que hace cada uno

**Anti Xray** (de DrexHD, es el anti-xray de Paper portado a Fabric). El servidor
le miente al cliente sobre lo que hay adentro de la piedra: donde no se ve, no
manda los minerales. Un xray no puede mostrar lo que nunca le llego. Se configura
solo en `config/antixray.toml`, y de fabrica viene bien: overworld en modo 3 y
nether en modo 1, con la netherita escondida. Es la unica defensa contra el xray
que no depende de en que confiemos del lado del jugador.

**GrimAC**. Simula el movimiento de cada jugador y compara con lo que dice el
cliente, asi que vuelo, speed, reach y killaura no le funcionan a nadie: no los
"detecta", los deshace. Los castigos estan en `config/GrimAC/punishments.yml`, y
ahi les agregamos el kick a los cuatro grupos que importan (Simulation, Reach,
Knockback y Misc). Las alertas le llegan por chat al que tenga el permiso
`grim.alerts`; a PEPE ya se lo dimos con LuckPerms:

    lp user PEPE permission set grim.alerts true
    lp user PEPE permission set grim.alerts.enable-on-join true

## Las dos cosas raras, que no son un descuido

**El jar suelto de `cloud`.** Grim trae adentro la libreria de comandos `cloud`,
pero la copia que trae pide Minecraft 26.2, asi que en 26.1.2 Fabric la descarta y
Grim no arranca (`NoClassDefFoundError: org/incendo/cloud/CommandManager`). Adentro
del mismo jar viene otra copia que si sirve, escondida en el modulo de mapeos
viejos. Esto la saca de ahi y la sube al lado. Con eso Grim arranca y protege; lo
unico que queda afuera son sus comandos (`/grim ...`), que avisa con un
"Grim will run without commands enabled!". El dia que Grim arregle su empaquetado,
este script deja de subir el jar suelto solo: se fija cual de las copias sirve.

**Grim necesita 26.1.2 o mas.** En 26.1 pelado no carga: por eso el servidor esta
en 26.1.2. Los jugadores siguen en 26.1 y entran igual, porque 26.1, 26.1.1 y
26.1.2 hablan el mismo protocolo (775).

## Los ajustes que no vienen de fabrica

**`disable-default-resync-handler` en true.** De fabrica Grim lee los bloques del
mundo del servidor y se los manda al cliente para "arreglar" desincronizaciones. Eso
pelea de frente con el anti-xray, que existe justamente para mentirle al cliente
sobre esos mismos bloques. Lo dice la propia config de Grim: con mods que mandan
bloques falsos por paquete, hay que apagarlo.

**`grim.nomodifypacket.fastbreak` para el grupo default.** El check FastBreak no
avisa y ya: **cancela el picado** (`blockBreak.cancel()` en su FastBreak.java). Para
saber si el jugador pico demasiado rapido calcula la dureza con el bloque del
servidor y con el item que el cree que tiene en la mano, y cuando pierde esa cuenta
--despues de un /home o un /tpa-- le sale que un deepslate_iron_ore tendria que
haber tardado 20 segundos mas. Ahi le cancela el picado a un jugador legitimo: los
bloques le quedan irrompibles y sin animacion hasta que se sale y vuelve a entrar,
porque reconectar es lo unico que resetea ese balance. Le paso a Hanselx911 la
primera noche, y son los mineros los que peor la pasan: el que mas bloques rompe es
el que antes lo acumula. Con este permiso Grim sigue simulando, detectando, avisando
por chat y dejandolo escrito en el log; lo unico que deja de hacer es cancelar.

**`grim.exempt.airliquidplace` para el grupo default.** AirLiquidPlace salta cuando
alguien apoya un bloque contra aire o contra un liquido, que es lo que hace un
scaffold. El problema es que tambien salta sin que nadie haga nada: el jugador
apoya contra un bloque que para el servidor ya no esta --porque otro lo rompio,
porque venia con lag, o porque acaba de teleportarse-- y para el check eso es
apoyar contra aire. Y viene con `cancelvl: 0`, o sea que cancela **desde la
primera** violacion: el bloque no se coloca y al jugador le parece que el juego le
come lo que tiene en la mano.

Del lado de Grim un check se saca con un permiso y no con la config: no hay ningun
`enabled` por check en `config.yml`. Los tres que arma la clase `Check` son
`grim.exempt.<check>`, `grim.nosetback.<check>` y `grim.nomodifypacket.<check>`, con
el nombre del `@CheckData` en minuscula. Con **exempt** el check no registra nada:
`recordFlag` se va antes de sumar violacion, asi que no avisa, no cancela y no
castiga. Es mas que lo de FastBreak, que solo dejo de cancelar. Si algun dia
queremos volver a enterarnos sin que moleste, el del medio es
`grim.nomodifypacket.airliquidplace`.

Entra en caliente: Grim relee los permisos solo (tiene `TickPermissions` y el
enganche con LuckPerms), asi que no hay que reiniciar ni que nadie se reconecte.

Los dos son del servidor: nadie tiene que actualizar el launcher ni el pack.
"""
import hashlib
import io
import json
import os
import re
import sys
import tempfile
import urllib.request
import zipfile

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

# Version fija y su hash, como todo lo que bajamos. Nunca "la ultima": la que
# probamos. Se cambian a mano cuando decidamos actualizar.
MODS = [
    {
        "prefijo": "antixray-fabric",
        "nombre": "antixray-fabric-1.4.16+26.1.jar",
        "sha1": "6b3a35c5873e066c7dce504131017f6304137540",
        "url": "https://cdn.modrinth.com/data/sml2FMaA/versions/AK313N9m/"
               "antixray-fabric-1.4.16%2B26.1.jar",
    },
    {
        "prefijo": "grimac-fabric",
        "nombre": "grimac-fabric-2.3.74-8eb5f28.jar",
        "sha1": "5a05aa902cdc6e824c1be96adab505c7d772942f",
        "url": "https://cdn.modrinth.com/data/LJNGWSvH/versions/enIsH6sX/"
               "grimac-fabric-2.3.74-8eb5f28.jar",
    },
]

# La version del juego que corre el servidor. La usa la busqueda del cloud que
# sirve: hay que subir el que NO pida una version mas nueva que esta.
DEL_SERVIDOR = (26, 1, 2)

# Lo que tiene que quedar puesto en la config de Grim, pase lo que pase. El porque
# de cada uno esta arriba.
AJUSTES = [
    ("disable-default-resync-handler", "true",
     "Grim resincroniza bloques y le pisa las mentiras al anti-xray"),
    ("max-transaction-time", "180",
     "a lanuerademari la echaba a los 60 segundos clavados de entrar, cuatro veces"),
    ("ping-abuse-limit-threshold", "-1",
     "TimerLimit solo castiga arriba de 1000 ms de ping, y la echaba por tener mal internet"),
]

# OJO: punishments.yml no lo toca este script, y ahi hay dos cambios hechos a mano
# que conviene no perder. FastBreak caia adentro de la seccion Misc, porque "Break"
# lo matchea por nombre, y eso le daba el kick de las 60 violaciones: le salta a
# todos por lag y no echa a nadie de verdad. Quedo excluido de Misc con "!FastBreak"
# y con seccion propia de solo [log], asi no avisa en el chat ni expulsa, pero sigue
# quedando registrado.

# Permisos de LuckPerms para el grupo default, o sea para todos.
PERMISOS = [
    ("grim.nomodifypacket.fastbreak",
     "FastBreak avisa, pero ya no le cancela el picado a nadie"),
    ("grim.exempt.airliquidplace",
     "AirLiquidPlace cancelaba colocaciones legitimas por desincronizacion"),
    # Los dos de abajo salieron de la noche del 2026-09-21, con el coliseo en obra.
    # A Felix1256 (nick ~elpetizoTV) en creativo y a Luquitas1410 sin creativo les
    # cancelaba la colocacion una y otra vez: "failed PositionPlace" y "failed Post
    # (player block placement)" cada pocos segundos, y los bloques no aparecian.
    #
    # La causa de fondo no era el check sino el lag: el servidor estaba corriendo
    # 40 ticks atras ("Can't keep up! Running 2327ms behind") con cinco jugadores.
    # Grim compara su simulacion con lo que dice el cliente, y dos segundos de
    # atraso alcanzan para que esa cuenta no cierre y marque lo que esta bien.
    #
    # Va nomodifypacket y no exempt a proposito, igual que FastBreak: sigue
    # simulando, detectando, avisando y escribiendo en el log; lo unico que deja de
    # hacer es cancelar. Construir sin que te cancelen es lo unico que hacia falta.
    ("grim.nomodifypacket.positionplace",
     "PositionPlace avisa, pero ya no le cancela la colocacion a nadie"),
    ("grim.nomodifypacket.post",
     "Post avisa, pero ya no le cancela la colocacion a nadie"),
]

CACHE = os.path.join(tempfile.gettempdir(), "sobrinosdepepe-antitrampas")


def bajar(mod):
    """Baja el jar a una carpeta temporal y no sigue si el hash no es el que va."""
    os.makedirs(CACHE, exist_ok=True)
    destino = os.path.join(CACHE, mod["nombre"])

    if not os.path.exists(destino):
        print("  bajando %s" % mod["nombre"])
        urllib.request.urlretrieve(mod["url"], destino)

    hash_real = hashlib.sha1(io.open(destino, "rb").read()).hexdigest()
    if hash_real != mod["sha1"]:
        os.remove(destino)
        raise SystemExit(
            "El hash de %s no es el que esperaba.\n"
            "  esperaba: %s\n  bajo:     %s\n"
            "No lo subo: un jar que no es el que probamos no va al servidor."
            % (mod["nombre"], mod["sha1"], hash_real))

    return destino


def version(texto):
    """'>=26.1.2 <26.3' -> (26, 1, 2). Lo que no entiende cuenta como sin limite."""
    numeros = ""
    for caracter in texto:
        if caracter.isdigit() or caracter == ".":
            numeros += caracter
        elif numeros:
            break
    partes = [int(p) for p in numeros.split(".") if p != ""]
    return tuple(partes + [0, 0])[:3] if partes else None


def cloud_que_sirve(jar_de_grim):
    """
    Saca de adentro del jar de Grim la copia de `cloud` que acepta la version del
    servidor, y la deja como archivo aparte. Devuelve la ruta, o None si la que
    Grim trae ya sirve y no hace falta ayudarlo.
    """
    with zipfile.ZipFile(jar_de_grim) as grim:
        anidados = [n for n in grim.namelist()
                    if n.startswith("META-INF/jars/") and n.endswith(".jar")]

        for modulo in anidados:
            with zipfile.ZipFile(io.BytesIO(grim.read(modulo))) as adentro:
                for archivo in adentro.namelist():
                    if "cloud-fabric" not in archivo:
                        continue

                    crudo = adentro.read(archivo)
                    with zipfile.ZipFile(io.BytesIO(crudo)) as cloud:
                        meta = json.loads(cloud.read("fabric.mod.json"))
                    pide = version(str(meta.get("depends", {}).get("minecraft", "")))

                    if pide is not None and pide > DEL_SERVIDOR:
                        print("  %s pide Minecraft %s: no sirve"
                              % (os.path.basename(archivo), ".".join(map(str, pide))))
                        continue

                    # En el modulo que Fabric va a cargar de verdad (el de mapeos
                    # nuevos, "official") esta la copia rota; la que sirve vive en el
                    # otro. Si encontramos una que sirve adentro del modulo official,
                    # Grim se arregla solo y no hay que subir nada.
                    if "official" in modulo:
                        print("  el cloud que trae Grim ya sirve: no subo ninguno suelto")
                        return None

                    destino = os.path.join(CACHE, os.path.basename(archivo))
                    io.open(destino, "wb").write(crudo)
                    print("  saco de adentro de Grim: %s" % os.path.basename(archivo))
                    return destino

    raise SystemExit("No encontre ningun cloud-fabric adentro del jar de Grim.")


def ajustar_config():
    """
    Deja los AJUSTES puestos en config/GrimAC/config.yml. Devuelve si cambio algo,
    que es lo que decide el reinicio: Grim lee su config una sola vez al arrancar.

    Cambia la linea y nada mas, sin parsear el YAML: el archivo es de ellos y viene
    lleno de comentarios que explican cada opcion, asi que reescribirlo los borraria.
    """
    ruta = "config/GrimAC/config.yml"
    viejo = mc.read(ruta)
    nuevo = viejo

    for clave, valor, _ in AJUSTES:
        patron = re.compile(r"^(%s:[ \t]*).*$" % re.escape(clave), re.MULTILINE)
        if not patron.search(nuevo):
            raise SystemExit(
                "No encontre '%s' en %s.\n"
                "Le habran cambiado el nombre a la opcion: hay que mirar la config."
                % (clave, ruta))
        nuevo = patron.sub(lambda coincide: coincide.group(1) + valor, nuevo)

    if nuevo == viejo:
        print("  la config de Grim ya estaba como va")
        return False

    mc.write(ruta, nuevo)
    for clave, valor, por_que in AJUSTES:
        print("  %s: %s" % (clave, valor))
        print("      %s" % por_que)
    return True


def dar_permisos():
    """Entran en caliente: LuckPerms los guarda y Grim los relee cuando el jugador
    entra, asi que no dependen del reinicio."""
    for nodo, por_que in PERMISOS:
        mc.cmd("lp group default permission set %s true" % nodo)
        print("  %-34s %s" % (nodo, por_que))


def subir(ruta, prefijo, estaban):
    """Sube el jar y borra las versiones viejas del mismo mod, que rompen Fabric."""
    nombre = os.path.basename(ruta)
    if nombre in estaban:
        print("  ya estaba: %s" % nombre)
        return False

    viejos = [n for n in estaban if n.startswith(prefijo) and n != nombre]
    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    mc.upload("/mods", ruta)
    print("  subido a mods/: %s" % nombre)
    return True


if __name__ == "__main__":
    estaban = mc.ls("/mods")
    hay_que_reiniciar = False

    for mod in MODS:
        local = bajar(mod)
        hay_que_reiniciar |= subir(local, mod["prefijo"], estaban)

        if mod["prefijo"] == "grimac-fabric":
            suelto = cloud_que_sirve(local)
            if suelto:
                hay_que_reiniciar |= subir(suelto, "cloud-fabric", estaban)

    print()
    hay_que_reiniciar |= ajustar_config()
    dar_permisos()

    print()
    if hay_que_reiniciar:
        mc.power("restart")
        print("Reiniciando, porque Fabric carga los mods una sola vez al arrancar.")
    else:
        print("No habia nada nuevo que subir.")
