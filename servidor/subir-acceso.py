# -*- coding: utf-8 -*-
"""
Prende el candado: al servidor se entra solo con el launcher.

    python servidor/subir-acceso.py

Sube el jar de mod-acceso a la carpeta mods del servidor, le escribe su config
con el secreto y el link, echa a los que estan jugando diciendoles por que, y
reinicia para que el mod cargue.

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
    """
    if not os.path.isdir(JARS):
        print("No encontré %s. Compilá el mod: cd mod-acceso && ./gradlew build" % JARS)
        return None

    nuestros = sorted(n for n in os.listdir(JARS) if n.startswith(PREFIJO) and n.endswith(".jar"))
    if not nuestros:
        print("No hay ningún %s*.jar en %s. Compilá el mod." % (PREFIJO, JARS))
        return None

    nombre = nuestros[-1]
    viejos = [n for n in mc.ls("/mods") if n.startswith(PREFIJO) and n != nombre]
    if viejos:
        mc.delete("/mods", viejos)
        for viejo in viejos:
            print("  borrado del servidor: %s" % viejo)

    mc.upload("/mods", os.path.join(JARS, nombre))
    print("  subido a mods/: %s" % nombre)
    return nombre


if __name__ == "__main__":
    secreto = secreto_o_generarlo()
    if secreto is None:
        sys.exit(1)

    link = mc.cfg.get("SITE_URL", "") or "sobrinosdepepe.vercel.app"

    if subir_el_jar() is None:
        sys.exit(1)

    mc.write(CONFIG, json.dumps({"secreto": secreto, "link": link}, indent=2) + "\n")
    print("  escrito %s (el cartel manda a %s)" % (CONFIG, link))

    # Echar antes de reiniciar es lo unico que les deja un motivo escrito: el
    # reinicio los saca a todos igual, pero con un "Server closed" que no explica
    # nada. Los que ya usan el launcher vuelven a entrar apretando JUGAR.
    mc.cmd("kick @a " + MOTIVO_DEL_KICK % link)
    print("  echados los que estaban jugando, con el motivo")

    mc.power("restart")
    print()
    print("Reiniciando. En un minuto el servidor solo deja entrar con el launcher.")
