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
"""
import hashlib
import io
import json
import os
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
    if hay_que_reiniciar:
        mc.power("restart")
        print("Reiniciando, porque Fabric carga los mods una sola vez al arrancar.")
    else:
        print("No habia nada nuevo que subir.")
