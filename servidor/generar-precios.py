# -*- coding: utf-8 -*-
"""
Arma prices.json y lo sube al servidor.

    python servidor/generar-precios.py            # escribe y sube
    python servidor/generar-precios.py --seco     # solo lo escribe local

La tabla base es la que trae EconomyCraft adentro del jar: son 1.695 items con
precios relativos hechos a mano que ya funcionan bien de la mitad para abajo.
Este script le hace tres cosas encima.

1) RE-ANCLAJE DEL FIN DEL JUEGO. Los items que definen el final estaban
   regalados: el ingot de netherita valia 3 diamantes y una elytra 12. En el
   /worth real de DonutSMP (el precio que paga su servidor, no el del mercado
   entre jugadores) un ingot vale 208 diamantes y una elytra 250. Esa tabla la
   sacaron de 158 capturas del GUI de /worth in-game y esta en
   github.com/Aeripsen/donut-quant (quant/worth_table.csv, 2026-09-02).
   Aca se copian solo los items de fin del juego: el resto de la tabla de Donut
   esta deformada por su propia meta de farms (el vidrio les vale 70 veces mas
   que a nosotros) y copiarla entera rompia la coherencia interna.

   OJO con un dato que circula al reves: "una elytra vale 85 veces un ingot de
   netherita" es del MERCADO de Donut (ahi es 59x). En su /worth la elytra vale
   1,2 ingots. Los dos numeros son ciertos y miden cosas distintas.

2) LA TIENDA NO VENDE LO QUE DURA. Donut vendia baratos todos los consumibles
   de pelea (crystals, anchors, obsidiana, totems, gapples, perlas) justamente
   para que regearse fuera barato y la gente saliera a pelear, y NUNCA vendio
   equipamiento durable. Aca se hace igual: las categorias de armadura, armas,
   herramientas y libros encantados quedan sin compra, los durables sueltos
   tambien, y los consumibles bajan a los precios de la tienda de Donut.

3) NADA DE PLATA INFINITA. Despues de tocar los precios se recorren las 1.515
   recetas del juego: si todos los ingredientes de una receta se pueden comprar
   en la tienda y el resultado se vende por mas, se le baja el precio de venta
   al resultado hasta que craftear no de ganancia. La tabla de fabrica tenia 43
   de estas maquinas (la peor: un lodestone cuesta 31 en ingredientes y se
   vendia a 257).

Los precios tambien viajan adentro del mod cliente que dibuja el cartelito de
precio en cada item, asi que el script rearma ese jar. No hace falta compilar
nada: precios.json es un recurso del jar y se reemplaza dentro del zip.
"""
import importlib.util
import json
import math
import os
import shutil
import sys
import zipfile

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
sys.path.insert(0, AQUI)

VERSION_MOD = "1.6.0"
SALTO = chr(10)

# Toda la tabla de fabrica se multiplica por esto. No es inflacion decorativa:
# es resolucion. Con precios enteros que valen 1 no hay forma de cerrar las
# cadenas de crafteo que multiplican, porque el tope que hay que ponerle al
# resultado cae abajo de 1 y el item queda sin poder venderse. Medido: a escala
# 1 quedaban 17 items sin venta (los paneles de vidrio y el name tag); a escala
# 5 o mas, ninguno. Se escala TODO junto, incluidos el saldo inicial, el regalo
# diario, el tope de venta y los saldos que ya tienen los jugadores.
ESCALA = 10

# El diamante es el ancla: no se mueve, y todo lo de Donut se escala por el.
DIAMANTE = 84 * ESCALA
DIAMANTE_DONUT = 360


def d(valor_donut):
    """Pasa un precio del /worth de Donut a la escala de este servidor."""
    return int(round(valor_donut * DIAMANTE / DIAMANTE_DONUT))


# --------------------------------------------------------------- 1) fin del juego
# Precio de venta nuevo, sacado del /worth real de DonutSMP.
VENTA = {
    # La cadena de la netherita, que era la distorsion mas grande de la tabla.
    "minecraft:netherite_ingot": d(75000),        # 208 diamantes (valia 3)
    "minecraft:netherite_block": d(675000),       # 9 ingots exactos, igual que Donut
    "minecraft:netherite_scrap": d(9000),         # 25 diamantes
    "minecraft:ancient_debris": d(1800),          # 5 diamantes
    # Los items que definen el final del juego.
    "minecraft:elytra": d(90000),                 # 250 diamantes (valia 12)
    "minecraft:trident": d(30000),                # 83 diamantes
    "minecraft:beacon": d(22500),                 # 62 diamantes
    "minecraft:heavy_core": d(3000),
    "minecraft:mace": 750 * ESCALA,                        # el heavy core mas una vara de breeze
    "minecraft:nether_star": 4200 * ESCALA,                # no esta en Donut: lo dejo abajo del beacon
    "minecraft:conduit": d(1600),
    "minecraft:heart_of_the_sea": d(1200),
    "minecraft:enchanted_golden_apple": 700 * ESCALA,      # tampoco esta; lo alineo con las cabezas
    # Trofeos: no se craftean, no se farmean, y son lo que se cuelga en la base.
    "minecraft:dragon_egg": 2000000,              # Donut lo pone en 6.400 millones y rompe el /baltop
    "minecraft:dragon_head": d(3000),
    "minecraft:wither_skeleton_skull": d(3000),
    "minecraft:skeleton_skull": d(3000),
    "minecraft:creeper_head": d(3000),
    "minecraft:zombie_head": d(3000),
    "minecraft:piglin_head": d(600),
    # Cosas raras que estaban muy abajo.
    "minecraft:sponge": d(1500),
    "minecraft:wet_sponge": d(1200),
    "minecraft:enchanting_table": d(1500),
    "minecraft:diamond_horse_armor": d(1500),
    "minecraft:golden_horse_armor": d(900),
    "minecraft:iron_horse_armor": d(300),
    "minecraft:turtle_helmet": d(600),
    "minecraft:nautilus_shell": d(300),
    "minecraft:totem_of_undying": d(750),         # 2 diamantes: es consumible, no trofeo
    "minecraft:shulker_shell": d(150),
}

# --------------------------------------------------------------- 2) que se vende
# Categorias enteras que salen de la compra. Apagar una categoria solo la saca
# del /shop: el /sell nunca mira si esta habilitada, asi que seguir vendiendo si.
CATEGORIAS_SIN_COMPRA = ["armor", "weapons", "tools", "enchantments"]

# Categorias que ademas no se venden. Poner unit_sell en 0 alcanza: getUnitSell
# devuelve null cuando el precio no es mayor que cero, y ahi el item deja de ser
# vendible sin que el /sell se lo coma.
#
# Los libros encantados estan aca porque medimos el dano. El 5 de septiembre la
# economia creo 763.005 pesos y 732.060 de esos (el 96%) salieron de 285 libros
# encantados que vendio un solo jugador, 162 de ellos Mending a 4.200 cada uno.
#
# No es que el jugador hiciera trampa: la tabla estaba mal. A un librero curado se
# le saca Mending por UNA esmeralda, y el trueque se repone doce veces por
# aldeano. O sea que cualquier precio mayor que cero multiplicado por una sala de
# aldeanos es plata infinita, y ademas la tienda no puede saber que encantamiento
# tiene el libro que le estas vendiendo: le paga lo mismo a un Mending que a un
# Bane of Arthropods I.
#
# Los libros pasan a moverse entre jugadores por el /ah, que es justo lo que
# queremos: el equipo se consigue jugando, cambiando o matando, y ademas cada
# venta por /ah quema el 10% de impuesto, que es el unico sumidero real que hay.
CATEGORIAS_SIN_VENTA = ["enchantments"]

# Items durables sueltos que viven en categorias que si se compran.
SIN_COMPRA = [
    "minecraft:netherite_ingot", "minecraft:netherite_block", "minecraft:netherite_scrap",
    "minecraft:ancient_debris", "minecraft:netherite_upgrade_smithing_template",
    "minecraft:elytra", "minecraft:beacon", "minecraft:nether_star", "minecraft:heavy_core",
    "minecraft:conduit", "minecraft:heart_of_the_sea", "minecraft:dragon_egg",
    "minecraft:enchanting_table", "minecraft:anvil", "minecraft:chipped_anvil",
    "minecraft:damaged_anvil", "minecraft:sponge", "minecraft:wet_sponge",
    "minecraft:lodestone", "minecraft:spawner", "minecraft:trial_spawner",
    "minecraft:vault", "minecraft:budding_amethyst", "minecraft:enchanted_golden_apple",
]

# Apagar una categoria entera se lleva puestos tres items que no hacen fuerte a
# nadie: el soporte de armaduras es decoracion, y la cana y las tijeras se
# craftean con palos y hierro. Se mudan a "utiles" para que sigan comprables.
RECATEGORIZAR = {
    "minecraft:armor_stand": "utility",
    "minecraft:fishing_rod": "utility",
    "minecraft:shears": "utility",
    "minecraft:carrot_on_a_stick": "utility",
    "minecraft:warped_fungus_on_a_stick": "utility",
}

# Consumibles de pelea, a los precios de la tienda que tenia Donut. La idea es
# que regearse sea barato: si cuesta horas, nadie sale a buscar pelea.
COMPRA = {
    "minecraft:totem_of_undying": d(1500),
    "minecraft:end_crystal": d(350),
    "minecraft:respawn_anchor": d(1000),
    "minecraft:obsidian": d(100),
    "minecraft:crying_obsidian": d(150),
    "minecraft:glowstone": d(100),
    "minecraft:ender_pearl": d(75),
    "minecraft:golden_apple": d(250),
    "minecraft:golden_carrot": d(120),
    "minecraft:experience_bottle": d(100),
    "minecraft:ender_chest": d(2500),
    "minecraft:shulker_box": d(800),
    "minecraft:shulker_shell": d(350),
    "minecraft:blaze_rod": d(150),
    "minecraft:ghast_tear": d(350),
    "minecraft:dragon_breath": d(1000),
}

# El vidrio venia sin compra de la tabla de fabrica (786 de los 1.695 items
# vienen asi) y es lo unico que se pide para construir que no se podia comprar.
# No hace falta cuidarse de nada: la arena ya se vende a 20 y hay que fundirla,
# asi que comprar el vidrio hecho es la version comoda y mas cara de lo mismo.
# Los precios estan por arriba del costo de craftearlo desde la tienda (un panel
# sale 11 en vidrio y uno de color 15) y muy por arriba del doble de la venta,
# que es la linea abajo de la cual comprar y revender seria plata gratis.
COLORES_VIDRIO = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]
COMPRA["minecraft:glass"] = 3 * ESCALA
COMPRA["minecraft:glass_pane"] = 2 * ESCALA
COMPRA["minecraft:tinted_glass"] = 90 * ESCALA   # lleva 4 amatistas, que salen 100 cada una
for _color in COLORES_VIDRIO:
    COMPRA["minecraft:%s_stained_glass" % _color] = 4 * ESCALA
    COMPRA["minecraft:%s_stained_glass_pane" % _color] = 2 * ESCALA

# ------------------------------------------------------------------ 4) los ajustes
# El config de EconomyCraft. No tiene comando de reload: los cambios entran con
# el reinicio. Y ojo: si se toca a mano con el servidor prendido y despues
# alguien abre Admin > Settings en el juego, el mod reescribe el archivo con lo
# que tenia en memoria desde el arranque y se pierden las ediciones.
CONFIG = {
    # Doce diamantes para arrancar. Donut arranca en cero, pero aca son cuatro
    # amigos y empezar sin nada no le suma nada a nadie.
    "startingBalance": 1000 * ESCALA,
    "dailyAmount": 100 * ESCALA,

    # Sin tope. El mod toma cualquier valor <= 0 como "no hay limite":
    # tryRecordDailySell sale por false apenas lo ve, y getDailySellRemaining
    # devuelve Long.MAX_VALUE.
    #
    # Se saca sabiendo lo que cuesta. Con el tope en 2.500.000 ya habia cinco
    # jugadores que lo tocaban (14 ingots de netherita y se acabo el dia), y en
    # los ocho dias de log del 12 al 19 de septiembre la plata del servidor paso
    # de 12,4 a 31,5 millones, con el 95% de lo creado saliendo de venderle
    # netherita al servidor: el TNT sale 540 en la tienda y el ingot que sale de
    # ese TNT se vende a 175.000. El tope era lo unico que frenaba esa rueda, y
    # sin el la plata se crea al ritmo que le den las manos a cada uno.
    #
    # Queda asi a proposito hasta que haya mas gente y valga la pena mirar la
    # economia en serio; el arreglo de fondo es el precio de la netherita, no el
    # tope.
    "dailySellLimit": 0,

    # El 10% NO es un impuesto general: solo lo cobran las subastas y las
    # ordenes de compra. Es el unico sumidero real de plata que tiene el
    # servidor, porque el /pay, el /sell y la tienda no pagan nada.
    "taxRate": 0.1,
    # Lo que se lleva quien te mata, de tu bolsillo al suyo. No crea plata: la
    # mueve. Lo maneja el mod solo, en handlePvpKill.
    "pvp_balance_loss_percentage": 0.1,

    "standalone_commands": True,
    "standalone_admin_commands": False,

    # Tiene que quedar apagado: si se prende, EconomyCraft crea su objetivo
    # eco_balance y se apropia del lugar del cartel de la derecha, que es de
    # Styled Sidebars. Y al volver a apagarlo BORRA ese objetivo del mundo.
    "scoreboard_enabled": False,

    "shop_enabled": True,
    "auction_enabled": True,
    "sell_enabled": True,
    "worth_enabled": True,
    "orders_enabled": True,
    "balance_separator": ".",
    "transaction_log_enabled": True,
    "transaction_log_retention_days": 7,
    "order_expiration_hours": 168,
    "auction_expiration_hours": 168,
    "max_active_orders_per_player": 0,
    "max_active_auctions_per_player": 0,

    # Tiene que quedar apagado. Un multiplicador de venta que sube con el
    # volumen es exactamente lo que hundio la economia de DonutSMP: los
    # jugadores compraban en las ordenes por debajo de precio_base x
    # multiplicador y vendian al servidor, y uno solo llego a vender 15 billones
    # asi. Donut lo elimino el 2026-06-02.
    "dynamic_prices_enabled": False,
    "dynamic_price_min_multiplier": 0.5,
    "dynamic_price_max_multiplier": 5.0,
    "dynamic_price_min_active_days": 30,
}


# Como se ve cada categoria en el /shop: el color es uno de los 16 de Minecraft
# y el icono es el item que se dibuja en la GUI.
CATEGORIAS = {
    "ores":           ("Minerales",      "aqua",         "minecraft:diamond"),
    "blocks":         ("Bloques",        "gray",         "minecraft:stone"),
    "blocks.stones":  ("Piedra",         "gray",         "minecraft:stone_bricks"),
    "blocks.wood":    ("Madera",         "gold",         "minecraft:oak_log"),
    "blocks.nether":  ("Nether",         "dark_red",     "minecraft:netherrack"),
    "blocks.end":     ("End",            "light_purple", "minecraft:end_stone"),
    "blocks.copper":  ("Cobre",          "gold",         "minecraft:copper_block"),
    "blocks.sand":    ("Arena",          "yellow",       "minecraft:sand"),
    "blocks.earth":   ("Tierra",         "dark_green",   "minecraft:dirt"),
    "blocks.bricks":  ("Ladrillos",      "red",          "minecraft:bricks"),
    "blocks.light":   ("Luces",          "yellow",       "minecraft:glowstone"),
    "food":           ("Comida",         "green",        "minecraft:cooked_beef"),
    "plants":         ("Plantas",        "dark_green",   "minecraft:oak_sapling"),
    "redstone":       ("Redstone",       "red",          "minecraft:redstone"),
    "dyes":           ("Tinturas",       "light_purple", "minecraft:pink_dye"),
    "brewing":        ("Pociones",       "dark_purple",  "minecraft:brewing_stand"),
    "utility":        ("Utiles",         "white",        "minecraft:chest"),
    "transport":      ("Transporte",     "aqua",         "minecraft:minecart"),
    "drops":          ("Drops de mobs",  "dark_aqua",    "minecraft:rotten_flesh"),
    "ocean":          ("Oceano",         "blue",         "minecraft:prismarine"),
    "ice":            ("Hielo",          "aqua",         "minecraft:packed_ice"),
    "deep dark":      ("Deep Dark",      "dark_aqua",    "minecraft:sculk"),
    "archaeology":    ("Arqueologia",    "yellow",       "minecraft:brush"),
    "discs":          ("Discos",         "light_purple", "minecraft:jukebox"),
    # Las cuatro que quedan sin compra. Se siguen pudiendo vender.
    "armor":          ("Armaduras",      "dark_gray",    "minecraft:netherite_chestplate"),
    "weapons":        ("Armas",          "dark_gray",    "minecraft:netherite_sword"),
    "tools":          ("Herramientas",   "dark_gray",    "minecraft:netherite_pickaxe"),
    "enchantments":   ("Encantamientos", "dark_gray",    "minecraft:enchanted_book"),
}


def verificador():
    """Carga verificar-precios.py, que tiene el lector de recetas del juego."""
    ruta = os.path.join(AQUI, "verificar-precios.py")
    spec = importlib.util.spec_from_file_location("verificar_precios", ruta)
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def tabla_de_fabrica():
    """La tabla original. Se cachea en el repo para no depender del servidor."""
    copia = os.path.join(AQUI, "referencia", "prices-de-fabrica.json")
    if os.path.exists(copia):
        return json.load(open(copia, encoding="utf-8"))
    import mc
    datos = json.loads(mc.read("/config/economycraft/prices.json"))
    os.makedirs(os.path.dirname(copia), exist_ok=True)
    json.dump(datos, open(copia, "w", encoding="utf-8", newline=SALTO),
              indent=2, ensure_ascii=False)
    print("  guardada copia de la tabla de fabrica en servidor/referencia/")
    return datos


def reparar(base, verif):
    """
    Baja la venta de todo lo que se pueda fabricar por menos de lo que vale.

    El costo lo calcula verificar-precios.py con un punto fijo sobre las 1.515
    recetas, asi que cubre las cadenas enteras y no una sola receta: el name tag
    sale de papel y una pepita, el papel de la cana de azucar y la pepita de un
    lingote, y ninguno de esos pasos intermedios se vende en la tienda.
    """
    recetas, etiquetas, _ = verif.cargar_juego()
    costo = verif.costos(base, recetas, etiquetas)
    reparadas = []
    for item, p in sorted(base.items()):
        c = costo.get(item, float("inf"))
        if c < float("inf") and p["unit_sell"] > c:
            limite = int(math.floor(c))
            reparadas.append((item, p["unit_sell"], limite))
            p["unit_sell"] = limite
    return reparadas


def construir(verif):
    base = {}
    for k, v in tabla_de_fabrica().items():
        if k.startswith("_"):
            continue
        base[k] = dict(v, unit_buy=v["unit_buy"] * ESCALA, unit_sell=v["unit_sell"] * ESCALA)
    faltantes = []

    # 1) fin del juego. La compra sigue el mismo salto que la venta para que los
    #    que quedan comprables no se abaraten sin querer.
    for item, venta in VENTA.items():
        if item not in base:
            faltantes.append(item)
            continue
        p = base[item]
        if p["unit_buy"] > 0 and p["unit_sell"] > 0:
            p["unit_buy"] = int(round(p["unit_buy"] * venta / p["unit_sell"]))
        p["unit_sell"] = venta

    # 2) la tienda. La compra nunca baja de el doble de la venta: por debajo de
    #    eso comprar y revender seria plata gratis.
    for item, compra in COMPRA.items():
        if item not in base:
            faltantes.append(item)
            continue
        p = base[item]
        p["unit_buy"] = compra
        p["unit_sell"] = min(p["unit_sell"], compra // 2)
    for item in SIN_COMPRA:
        if item in base:
            base[item]["unit_buy"] = 0
        else:
            faltantes.append(item)
    # Va despues de VENTA y COMPRA a proposito: lo que la categoria saca de la
    # tienda no lo vuelve a meter ningun ajuste posterior.
    for p in base.values():
        if p["category"] in CATEGORIAS_SIN_VENTA:
            p["unit_buy"] = 0
            p["unit_sell"] = 0
    for item, categoria in RECATEGORIZAR.items():
        if item in base:
            base[item]["category"] = categoria
        else:
            faltantes.append(item)

    # 3) craftear no puede dar ganancia
    ratios = {k: v["unit_sell"] / v["unit_buy"]
              for k, v in base.items() if v["unit_buy"] > 0 and v["unit_sell"] > 0}
    reparadas = reparar(base, verif)
    for item, _, _ in reparadas:
        p = base[item]
        if p["unit_buy"] > 0 and item in ratios:
            p["unit_buy"] = max(p["unit_sell"] + 1, int(round(p["unit_sell"] / ratios[item])))

    doc = {"_categories": {}}
    for clave, (nombre, color, icono) in CATEGORIAS.items():
        doc["_categories"][clave] = {
            "name": nombre,
            "color": color,
            "icon": icono,
            "enabled": clave not in CATEGORIAS_SIN_COMPRA,
            "dynamic_price_enabled": True,
        }
    doc.update(dict(sorted(base.items())))
    return doc, reparadas, faltantes


def rearmar_mod(precios_mod, version=VERSION_MOD):
    """Reemplaza precios.json adentro del jar del mod cliente y sube la version."""
    libs = os.path.join(RAIZ, "mod-precios", "build", "libs")
    if not os.path.isdir(libs):
        print("no hay mod-precios/build/libs; salteo el rearmado del mod cliente")
        return
    candidatos = [n for n in os.listdir(libs)
                  if n.startswith("precios-sobrinosdepepe-") and n.endswith(".jar")]
    if not candidatos:
        print("no encuentro el jar del mod de precios; salteo el rearmado")
        return
    # El jar MAS NUEVO, no el primero por orden alfabetico. Antes agarraba el
    # 1.0.0 y le escribia encima los precios nuevos, asi que cualquier cambio de
    # codigo del mod se perdia en silencio la proxima vez que se regeneraban los
    # precios. Se ordena por fecha porque "1.10.0" es alfabeticamente menor que
    # "1.2.0" y comparar strings volveria a elegir mal.
    def clave(n):
        return os.path.getmtime(os.path.join(libs, n))

    origen = os.path.join(libs, max(candidatos, key=clave))
    print("  rearmando a partir de %s" % os.path.basename(origen))
    destino = os.path.join(libs, "precios-sobrinosdepepe-%s.jar" % version)
    temporal = destino + ".tmp"
    with zipfile.ZipFile(origen) as zin, \
            zipfile.ZipFile(temporal, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            datos = zin.read(item.filename)
            if item.filename == "precios.json":
                datos = json.dumps(precios_mod, separators=(",", ":"),
                                   sort_keys=True).encode("utf-8")
            elif item.filename == "fabric.mod.json":
                doc = json.loads(datos)
                doc["version"] = version
                datos = json.dumps(doc, indent=2, ensure_ascii=False).encode("utf-8")
            zout.writestr(item, datos)
    shutil.move(temporal, destino)
    print("rearmado %s" % os.path.basename(destino))
    print("  hay que publicarlo desde el panel para que los jugadores lo reciban")


def main():
    seco = "--seco" in sys.argv
    verif = verificador()
    doc, reparadas, faltantes = construir(verif)

    if faltantes:
        print("items que no estan en la tabla y se saltearon: %s"
              % ", ".join(sorted(set(f.replace("minecraft:", "") for f in faltantes))))

    print("reparadas %d recetas rentables:" % len(reparadas))
    for item, antes, ahora in reparadas[:30]:
        print("   %-42s venta %-8d -> %d" % (item.replace("minecraft:", ""), antes, ahora))
    if len(reparadas) > 30:
        print("   ... y %d mas" % (len(reparadas) - 30))
    sin_venta = [i for i, _, a in reparadas if a <= 0]
    if sin_venta:
        print("   quedaron sin poder venderse: %s"
              % ", ".join(s.replace("minecraft:", "") for s in sin_venta))

    texto = json.dumps(doc, indent=2, ensure_ascii=False)
    print("armados %d items de precios (%d KB)" % (len(doc) - 1, len(texto) // 1024))

    precios_mod = {k: v["unit_sell"] for k, v in doc.items()
                   if not k.startswith("_") and v["unit_sell"] > 0}
    open(os.path.join(RAIZ, "mod-precios", "src", "main", "resources", "precios.json"),
         "w", encoding="utf-8", newline=SALTO).write(
        json.dumps(precios_mod, separators=(",", ":"), sort_keys=True))
    print("escrito mod-precios/src/main/resources/precios.json (%d items)" % len(precios_mod))
    rearmar_mod(precios_mod)

    # El mismo chequeo que corre verificar-precios.py, sobre lo que se acaba de armar.
    recetas, etiquetas, trueques = verif.cargar_juego()
    limpio = {k: v for k, v in doc.items() if not k.startswith("_")}
    print()
    fallas = verif.informe(limpio, recetas, etiquetas, trueques)
    if fallas:
        print("HAY %d FALLAS: no subo nada" % fallas)
        return 1

    config = json.dumps(CONFIG, indent=2, ensure_ascii=False)
    ruta_config = os.path.join(AQUI, "economia", "config.json")
    os.makedirs(os.path.dirname(ruta_config), exist_ok=True)
    open(ruta_config, "w", encoding="utf-8", newline=SALTO).write(config)
    open(os.path.join(AQUI, "economia", "prices.json"), "w",
         encoding="utf-8", newline=SALTO).write(texto)
    print("escrito servidor/economia/config.json")

    if not seco:
        import mc
        mc.write("/config/economycraft/prices.json", texto)
        mc.write("/config/economycraft/config.json", config)
        print("subidos prices.json y config.json")
        # No hay comando de recarga, pero si un boton: el reloj "Reload from
        # disk" del /eco admin (slot 16) hace EconomyConfig.load + prices.reload
        # y aplica los dos archivos sin reiniciar. Lo tiene que apretar alguien
        # con op, porque /eco admin pide gamemaster.
        print("para que entren: /eco admin y tocar el reloj (o reiniciar)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
