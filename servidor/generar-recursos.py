# -*- coding: utf-8 -*-
"""
Arma el resource pack propio del servidor y lo publica.

    python servidor/generar-recursos.py            solo lo arma
    python servidor/generar-recursos.py --publicar lo sube a GitHub y apunta el servidor

Hoy tiene una sola cosa adentro: el cartel rojo que aparece cuando alguien intenta
romper un bloque en el spawn.

## Por qué hace falta un pack para eso

El texto lo escribe **el cliente**, no el servidor. `ServerPlayer` manda
`Component.translatable("build.spawn_protection")` y cada jugador lo traduce con su
idioma, así que desde el servidor no se puede cambiar ni una letra. Lo único que
alcanza esa clave es un resource pack.

El de fábrica en castellano dice algo así como "Esta zona está protegida por el
servidor" y no explica nada: el jugador nuevo no entiende por qué no puede picar ni
qué tiene que hacer. El nuestro dice qué es y cómo se sale.

**El rojo no se puede cambiar.** En el bytecode de 26.1 el componente se arma con
`.withStyle(ChatFormatting.RED)` y eso está fijo en el servidor. Lo que sí se puede
es empezar el texto con un código `§` heredado, que el dibujante de texto aplica
igual y pisa el rojo de ahí en adelante. Por eso el mensaje arranca con `\\u00a7e`.

## Cómo llega

Por `resource-pack` en `server.properties`: el cliente lo baja al entrar y lo aplica
solo, arriba de lo que cada uno tenga puesto. La primera vez pregunta una vez y no
vuelve a preguntar.

Se aloja en una **prerelease** de GitHub, y eso importa: si fuera una release normal
pasaría a ser la "latest" del repositorio y rompería
`releases/latest/download/SobrinosDePepe-win-Setup.exe`, que es el botón de descarga
de la página, y `releases/latest/download/releases.win.json`, que es de donde el
launcher lee si hay versión nueva. GitHub excluye las prereleases de "latest".

## El formato

`min_format` y `max_format` en 84, que es el `resource_major` de 26.1 (sale del
`version.json` del jar del juego). No lleva `pack_format`: desde el formato 82 ese
campo dejó de estar permitido, igual que en el datapack.
"""
import hashlib
import json
import os
import subprocess
import sys
import zipfile

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

DESTINO = os.path.join(AQUI, "recursos")
ZIP = os.path.join(DESTINO, "sdp-recursos.zip")

# 84 es el resource_major de 26.1.
FORMATO = 84

# El repositorio del launcher, y una etiqueta aparte para no pisar sus releases.
REPO = "tobitortosa/sobrinosdepepe-launcher"
ETIQUETA = "recursos-1"
URL = "https://github.com/%s/releases/download/%s/sdp-recursos.zip" % (REPO, ETIQUETA)

# Fijo a propósito: es la clave con la que el cliente cachea el pack. Cambiarlo
# obliga a todos a bajarlo de nuevo.
PACK_ID = "2f6a1c40-8b3d-4e77-9a15-c0d4e5f60718"

# Los saltos de línea van con DOS barras invertidas: server.properties lo lee
# java.util.Properties, que se come una, y con una sola al parser de JSON le llega un
# salto de línea real adentro de un string y el arranque escupe "Unescaped control
# characters are not allowed in strict mode". Medido: la primera versión de esto quedó
# sin cartel y con un stack trace en cada arranque.
PROMPT = (r'["",{"text":"SOBRINOS DE PEPE","color":"#ffb02e","bold":true},'
          r'{"text":"\\n\\nUnos carteles del servidor, en castellano.","color":"white"},'
          r'{"text":"\\nNo cambia nada del juego ni pisa tus texturas.","color":"gray"}]')

AMARILLO = chr(0xA7) + "e"
GRIS = chr(0xA7) + "7"

# Corto a propósito: es un cartel de una línea arriba de la barra de la vida.
MENSAJES = {
    "es_es": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "es_ar": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "es_mx": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "es_uy": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "es_cl": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "es_ve": AMARILLO + "Zona protegida del spawn" + GRIS
             + "  alejate unos bloques y ya podes romper y construir",
    "en_us": AMARILLO + "Spawn is protected" + GRIS
             + "  move a few blocks away and you can build again",
}

# El toast de "Los mensajes del chat no se pueden verificar" NO se arregla desde
# aca, y conviene que quede escrito para no volver a intentarlo. Lo dispara el
# cliente en `ClientPacketListener.handleLogin`:
#
#     if (serverData != null && !seenInsecureChatWarning && !enforcesSecureChat())
#
# y las cuatro salidas estan cerradas:
#
#   - `seenInsecureChatWarning` es un campo de la conexion y NO se guarda en
#     `servers.dat`, asi que se reinicia en cada entrada. No hay bandera que el
#     launcher pueda dejar puesta de antemano.
#   - `serverData` nunca es null: `QuickPlay.joinMultiplayerWorld` lo saca de
#     `servers.dat` o crea uno nuevo, y siempre se lo pasa a `startConnecting`.
#   - `enforcesSecureChat` no se puede prender: en offline-mode los jugadores no
#     tienen clave de perfil de Mojang, asi que con `enforce-secure-profile` en
#     true no podrian escribir en el chat.
#   - Pisar los textos con este pack **no lo esconde**: `SystemToast.multiline`
#     calcula el ancho con `Math.max(200, ...)`, la altura con
#     `20 + max(lineas, 1) * 12` y dibuja el fondo siempre. Quedaria una caja
#     vacia de 230x32, que es peor que el cartel.
#
# Se resolvio con un mod de cliente en el pack, `disableinsecurechattoast`
# (CC0, 3 KB), cuyo unico mixin redirige a un no-op la unica llamada a
# `ToastManager.addToast` que hay en `handleLogin`. Ahi el cartel no se dibuja
# nunca, que es lo que se queria.


def armar():
    os.makedirs(DESTINO, exist_ok=True)
    meta = {"pack": {
        "description": "SOBRINOS DE PEPE",
        "min_format": FORMATO,
        "max_format": FORMATO,
    }}

    # Sin fecha adentro y con las entradas ordenadas: el mismo contenido da siempre
    # el mismo zip y el mismo sha1, asi que rearmarlo sin cambios no obliga a nadie
    # a bajarlo de nuevo.
    with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        def poner(nombre, texto):
            info = zipfile.ZipInfo(nombre, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, texto)

        poner("pack.mcmeta", json.dumps(meta, indent=2, ensure_ascii=False) + "\n")
        for idioma in sorted(MENSAJES):
            poner("assets/minecraft/lang/%s.json" % idioma,
                  json.dumps({"build.spawn_protection": MENSAJES[idioma]},
                             indent=2, ensure_ascii=False) + "\n")

    sha1 = hashlib.sha1(open(ZIP, "rb").read()).hexdigest()
    print("  %s  %d bytes  sha1 %s" % (os.path.basename(ZIP), os.path.getsize(ZIP), sha1))
    print("  idiomas: %s" % ", ".join(sorted(MENSAJES)))
    return sha1


def publicar(sha1):
    # La prerelease se crea una sola vez; despues solo se reemplaza el archivo.
    existe = subprocess.run(["gh", "release", "view", ETIQUETA, "--repo", REPO],
                            capture_output=True, text=True).returncode == 0
    if not existe:
        subprocess.run([
            "gh", "release", "create", ETIQUETA, ZIP, "--repo", REPO, "--prerelease",
            "--title", "Recursos del servidor",
            "--notes", "El resource pack que el servidor le pide a cada jugador al entrar. "
                       "Es prerelease para no pisar la release del launcher, que es de donde "
                       "sale el boton de descarga y el chequeo de version.",
        ], check=True)
    else:
        subprocess.run(["gh", "release", "upload", ETIQUETA, ZIP, "--repo", REPO, "--clobber"],
                       check=True)
    print("  publicado en %s" % URL)

    texto = mc.read("/server.properties")
    lineas = texto.split("\n")
    cambios = {
        "resource-pack": URL,
        "resource-pack-sha1": sha1,
        "resource-pack-id": PACK_ID,
        "resource-pack-prompt": PROMPT,
        # Que una caída de GitHub no deje a nadie afuera del servidor.
        "require-resource-pack": "false",
    }
    for i, linea in enumerate(lineas):
        if "=" not in linea or linea.startswith("#"):
            continue
        clave = linea.split("=", 1)[0]
        if clave in cambios:
            lineas[i] = "%s=%s" % (clave, cambios.pop(clave))
    assert not cambios, "no encontre estas claves: %s" % list(cambios)
    mc.write("/server.properties", "\n".join(lineas))
    print("  server.properties apuntado al pack nuevo. Falta reiniciar.")


if __name__ == "__main__":
    sha1 = armar()
    if "--publicar" in sys.argv:
        publicar(sha1)
    else:
        print("\npara subirlo: python servidor/generar-recursos.py --publicar")
