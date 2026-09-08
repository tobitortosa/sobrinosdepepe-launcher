# -*- coding: utf-8 -*-
"""
Prende el candado: al servidor se entra solo con el launcher.

    python servidor/subir-acceso.py

Sube el jar de mod-acceso a la carpeta mods del servidor y le escribe su config:
el candado (REQUIRE_LAUNCHER), el secreto y el link. Si el candado queda puesto,
echa a los que esten jugando diciendoles por que.

**El candado se prende y se apaga con REQUIRE_LAUNCHER en web/.env.local.** En
false el servidor revisa igual y anota en el log quien entro sin el launcher, pero
no echa a nadie; en true no entra nadie sin el launcher. El mod lee su config en
cada intento de entrar, asi que cambiarlo es correr este script de nuevo: no hace
falta reiniciar el servidor ni sacar a nadie.

EL ORDEN IMPORTA: esto es el ultimo de cuatro pasos. Corrido antes que los otros
tres, no entra NADIE, ni con el launcher.

  1. En Vercel, poner ACCESS_SECRET y SITE_URL y esperar el redeploy. Sin eso el
     launcher no puede pedir permisos de entrada. El secreto lo genera este mismo
     script la primera vez que lo corras.
  2. Publicar el pack con el mod del lado del cliente:
       cd mod-acceso && ./gradlew build
       cd web && npm run pack:jar -- ../mod-acceso/build/libs/acceso-sobrinosdepepe-1.0.0.jar
  3. Publicar el launcher que pide el permiso:
       pwsh launcher/pack-release.ps1 -Version <la que sigue> -Publish
     Los launchers ya instalados se actualizan solos al abrirse. El launcher
     viejo no manda ningun permiso, asi que sin este paso quedan todos afuera.
  4. Esto.

Se vuelve a correr cuando cambie el link o el jar: hace lo mismo de nuevo.
"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

JARS = os.path.join(os.path.dirname(AQUI), "mod-acceso", "build", "libs")
PREFIJO = "acceso-sobrinosdepepe"
CONFIG = "/config/acceso-de-pepe.json"

# Que valor de REQUIRE_LAUNCHER cuenta como "poner el candado". Cualquier otra cosa
# lo deja abierto: es el lado que no echa a nadie por un error de tipeo.
SI = ("true", "1", "si", "yes")

MOTIVO_DEL_KICK = (
    "Ahora al servidor se entra con el SOBRINOS DE PEPE Launcher. "
    "Descargalo en %s y entra desde ahi."
)


def secreto_o_generarlo():
    """
    El secreto con el que el backend firma los permisos y el servidor los revisa.

    Si todavia no existe, lo genera, lo deja en web/.env.local y para: hasta que
    no este tambien en Vercel, el launcher no puede pedir permisos y activar el
    candado dejaria a todos afuera.
    """
    secreto = mc.cfg.get("ACCESS_SECRET", "")
    if secreto:
        return secreto

    secreto = os.urandom(32).hex()
    with io.open(mc.ENV, "a", encoding="utf-8") as env:
        env.write("\nACCESS_SECRET=%s\n" % secreto)

    print("Te genere el secreto y lo guarde en web/.env.local:")
    print()
    print("  ACCESS_SECRET=%s" % secreto)
    print()
    print("Ponelo igual en Vercel (Settings -> Environment Variables), esperá el")
    print("redeploy y volvé a correr este script.")
    return None


def subir_el_jar():
    """
    Deja en mods/ el jar compilado y borra las versiones anteriores: dos jars del
    mismo mod hacen que Fabric no arranque, y el servidor queda apagado.

    Devuelve el nombre del jar y si hace falta reiniciar, o None si no hay nada
    que subir.
    """
    if not os.path.isdir(JARS):
        print("No encontré %s. Compilá el mod: cd mod-acceso && ./gradlew build" % JARS)
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

    # Fabric carga los mods una sola vez, al arrancar: un jar nuevo pide reinicio.
    # La config no, y por eso prender el candado no saca a nadie del juego.
    return nombre, nombre not in estaban


if __name__ == "__main__":
    secreto = secreto_o_generarlo()
    if secreto is None:
        sys.exit(1)

    link = mc.cfg.get("SITE_URL", "") or "sobrinosdepepe.vercel.app"
    exigir = mc.cfg.get("REQUIRE_LAUNCHER", "false").strip().lower() in SI

    subido = subir_el_jar()
    if subido is None:
        sys.exit(1)
    _, hay_que_reiniciar = subido

    mc.write(CONFIG, json.dumps(
        {"exigir": exigir, "secreto": secreto, "link": link}, indent=2) + "\n")
    print("  escrito %s (exigir=%s, el cartel manda a %s)" % (CONFIG, str(exigir).lower(), link))

    # Echar antes de reiniciar es lo unico que les deja un motivo escrito: el
    # reinicio los saca a todos igual, pero con un "Server closed" que no explica
    # nada. Los que ya usan el launcher vuelven a entrar apretando JUGAR. Con el
    # candado abierto no se echa a nadie: no hay motivo que darles.
    if exigir and hay_que_reiniciar:
        mc.cmd("kick @a " + MOTIVO_DEL_KICK % link)
        print("  echados los que estaban jugando, con el motivo")

    if hay_que_reiniciar:
        mc.power("restart")
        print("  reiniciando, porque el jar es nuevo")

    print()
    if exigir:
        print("REQUIRE_LAUNCHER=true: al servidor se entra solo con el launcher.")
    else:
        print("REQUIRE_LAUNCHER=false: el servidor queda como antes, entra cualquiera.")
        print("En el log del servidor igual queda anotado quien entro sin el launcher.")
    print("Para cambiarlo: REQUIRE_LAUNCHER en web/.env.local y correr esto de nuevo.")
    print("El mod lee su config en cada intento de entrar, asi que no hace falta reiniciar.")
