# -*- coding: utf-8 -*-
"""
Prende y apaga la restriccion de la netherita.

    python servidor/configurar-netherite.py

**El interruptor es RESTRICT_NETHERITE en web/.env.local.**

  false  La netherita es normal: las doce recetas de smithing andan y se craftea
         minando, como en cualquier servidor. (Sigue estando en la tienda de
         shards: comprarla ahi es una forma mas, no la unica.)
  true   Las doce recetas quedan apagadas. La armadura, las armas y las
         herramientas de netherita salen de un solo lado, la tienda de shards,
         que las entrega con `give` y no pasa por ninguna receta. Es la asimetria
         que hace que el equipo de fin del juego se pague matando gente.

Todo pasa por un solo archivo del datapack, el tag que las doce recetas usan como
`addition`:

    data/minecraft/tags/item/netherite_tool_materials.json

Con `replace: true` y la lista vacia, la etiqueta queda sin ningun item y las
recetas se ignoran. Con `replace: false` y la lista vacia, el archivo esta pero no
cambia nada: los tags se mezclan, asi que el de vanilla queda intacto y las
recetas vuelven. Por eso apagar la restriccion no es borrar el archivo — que
subir-datapack.py volveria a dejar como estaba — sino dejarlo inerte.

Se aplica con /reload: no pide reinicio, no toca el launcher y no saca a nadie del
juego. Cuando la restriccion queda puesta, en el log del servidor aparecen doce
WARN "can't be placed due to empty ingredients", que son la confirmacion de que
funciono y no un problema.
"""
import json
import os
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import mc

RELATIVA = "data/minecraft/tags/item/netherite_tool_materials.json"
LOCAL = os.path.join(AQUI, "datapack", RELATIVA.replace("/", os.sep))
REMOTO = "/world/datapacks/sobrinosdepepe/" + RELATIVA

# Que valor de RESTRICT_NETHERITE cuenta como "restringir". Cualquier otra cosa la
# deja normal: es el lado que no cambia las reglas del juego por un error de tipeo.
SI = ("true", "1", "si", "yes")

if __name__ == "__main__":
    restringir = mc.cfg.get("RESTRICT_NETHERITE", "false").strip().lower() in SI

    # replace es todo: en true la etiqueta reemplaza a la de vanilla y queda vacia;
    # en false se mezcla con ella y no cambia nada.
    contenido = json.dumps({"replace": restringir, "values": []}, indent=2) + "\n"

    open(LOCAL, "w", encoding="utf-8", newline="\n").write(contenido)
    mc.write(REMOTO, contenido)
    print("  escrito %s (replace=%s)" % (RELATIVA, str(restringir).lower()))

    mc.cmd("reload")
    print("  recargado")
    time.sleep(3)

    apagadas = [l for l in mc.read("/logs/latest.log").splitlines()
                if "netherite" in l and "empty ingredients" in l]
    print()
    if restringir:
        print("RESTRICT_NETHERITE=true: la netherita no se craftea, se compra con shards.")
        print("Recetas apagadas segun el log del servidor: %d (tienen que ser 12)." % len(apagadas))
    else:
        print("RESTRICT_NETHERITE=false: la netherita es normal, se craftea minando.")
        print("La tienda de shards la sigue vendiendo.")
    print("Para cambiarlo: RESTRICT_NETHERITE en web/.env.local y correr esto de nuevo.")
