# -*- coding: utf-8 -*-
"""
Le pone al servidor el pack de sonidos de cueva, que se lo baja cada jugador al
entrar.

    python servidor/configurar-resourcepack.py

Después hay que **reiniciar el servidor**: `server.properties` se lee al arrancar.

## Por qué un resource pack del servidor y no un mod

Los sonidos de cueva son `ambient.cave`, que es contenido del cliente. Hay tres
formas de cambiarlos y solo una sirve acá:

| Camino | Problema |
|---|---|
| Un mod de sonidos en el pack del launcher | Los tres que se habían mirado (Expanded Cave Sounds, Horror Cave Sounds, Scary Ambience) **no existen para 26.1**: Expanded Cave Sounds llega hasta 1.21.1. |
| El zip en `resourcepacks/` del launcher | `ConfigSeeder` no pisa `options.txt` si ya existe — y existe en la PC de todos. O sea que el pack llegaría apagado y habría que prenderlo a mano. |
| **`resource-pack` en `server.properties`** | Ninguno. El cliente lo baja al entrar y lo aplica solo, arriba de lo que cada uno tenga puesto. |

Y no rehosteamos nada: la URL apunta al CDN de Modrinth, igual que los mods del
launcher. El `sha1` es el del archivo, así que si Modrinth devolviera otra cosa el
cliente la rechaza.

## Qué pack y por qué ese

**Remade Cave Ambient 1.2** (`remade-cave-ambient` en Modrinth). Lo elegí después
de abrir los dos candidatos que tienen 26.1:

- Declara `pack_format: 84`, y el `resource_major` de 26.1 es **84** exacto
  (`version.json` del jar del juego). Sin cartel de "incompatible".
- Adentro tiene `cave1.ogg` … `cave23.ogg`: reemplaza **los 23 sonidos de cueva de
  vanilla uno por uno**, sin `sounds.json`. O sea que no cambia ni la frecuencia,
  ni el fade, ni el audio posicional: es el mismo sistema de siempre con otros
  sonidos. Eso es justo lo que se quería — más miedo, misma dinámica.
- El otro candidato, `cave-dweller-cave-sounds`, trae **4** sonidos con
  `replace: true`: cambia 23 por 4 y se vuelve repetitivo enseguida, además de que
  son los ruidos reconocibles del mod Cave Dweller.

Combinado con **Sound Physics Remastered**, que ya está en el pack, los sonidos
llegan con eco y amortiguados según la roca que haya en el medio: por eso parece
que vienen de un túnel y no de al lado.

## Trampas

- **`require-resource-pack` queda en `false`.** Modrinth no garantiza permanencia
  de los archivos; si algún día el CDN falla, el jugador ve un aviso y entra igual.
  En `true` no podría entrar nadie.
- **`resource-pack-id` es fijo y no se cambia.** Es el identificador con el que el
  cliente cachea el pack: si cambia, todos se lo vuelven a bajar. Solo hay que
  generar uno nuevo si se cambia de pack.
- Si se cambia de pack o de versión hay que actualizar la URL **y** el `sha1`
  juntos. Con el `sha1` viejo el cliente rechaza el archivo nuevo.
- Para sacarlo: dejar `resource-pack` vacío y reiniciar.
"""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

RUTA = "/server.properties"

# Remade Cave Ambient 1.2, "Cave Ambient 26.1+.zip" (2.001.917 bytes).
URL = "https://cdn.modrinth.com/data/wySfppXF/versions/LTo1rJ6w/Cave%20Ambient%2026.1%2B.zip"
SHA1 = "62849f799327015c072095de6e8140e1706ecf21"

# Fijo a propósito: es la clave con la que el cliente cachea el pack.
PACK_ID = "9c8b7a6d-5e4f-4a3b-9c2d-1e0f8a7b6c5d"

# Los saltos de linea van con DOS barras invertidas y no con una. server.properties
# lo lee java.util.Properties, que se come una barra: con una sola, al parser de
# JSON le llega un salto de linea de verdad adentro de un string y revienta con
# "Unescaped control characters are not allowed in strict mode". Medido: la primera
# version quedo sin prompt y con un stack trace en cada arranque.
PROMPT = (r'["",{"text":"SOBRINOS DE PEPE","color":"#ffb02e","bold":true},'
          r'{"text":"\\n\\nSonidos nuevos para las cuevas.","color":"white"},'
          r'{"text":"\\nNo cambia nada del juego, solo lo que se escucha abajo.","color":"gray"}]')

CAMBIOS = {
    "resource-pack": URL,
    "resource-pack-sha1": SHA1,
    "resource-pack-id": PACK_ID,
    "resource-pack-prompt": PROMPT,
    # Que un problema del CDN no deje a nadie afuera.
    "require-resource-pack": "false",
}

if __name__ == "__main__":
    texto = mc.read(RUTA)
    lineas = texto.split("\n")
    faltan = dict(CAMBIOS)

    for i, linea in enumerate(lineas):
        if "=" not in linea or linea.startswith("#"):
            continue
        clave = linea.split("=", 1)[0]
        if clave in faltan:
            nuevo = "%s=%s" % (clave, faltan.pop(clave))
            if linea != nuevo:
                print("  %s" % nuevo[:110])
                lineas[i] = nuevo
            else:
                print("  %s ya estaba" % clave)

    assert not faltan, "no encontre estas claves en server.properties: %s" % list(faltan)

    mc.write(RUTA, "\n".join(lineas))
    print("\nescrito. reinicia el servidor para que lo tome.")
