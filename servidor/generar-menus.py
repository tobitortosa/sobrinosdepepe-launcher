# -*- coding: utf-8 -*-
"""
Arma los menus de cofre y los deja en el datapack.

    python servidor/generar-menus.py

Los dibuja Inventory Menu, que lee los menus del datapack (data/sdp/menu/*.json)
y los abre con /menu <id>. Es la forma en la que DonutSMP hace todos sus menus:
GUI de cofre y no texto clickeable en el chat.

Se sube con servidor/subir-datapack.py y se recarga con /reload.

Como es un archivo de menu:
  - "rows" de 1 a 6, y cada item lleva "slot": [fila, columna] con la fila de 1
    a 6 y la columna de 1 a 9.
  - el tipo "item" define el item entero y su accion aparte. El tipo "navigate"
    existe pero NO se usa: si se le pone un "model", el mod reemplaza el stack
    entero y pierde el nombre y la descripcion que le pusiste.
  - la accion "command" con as_player en false corre con la fuente del servidor,
    que no tiene entidad: @s no resuelve y hay que nombrar al jugador con %name%.
    Eso mismo es lo que hace que los comandos de EconomyCraft anden para quien no
    es operador, porque el mod solo se fija si la entidad de la fuente esta en
    ops.json y ahi no hay ninguna entidad.
  - "action_cost" de tipo "score" es lo que cobra los shards: el mod verifica el
    score y lo descuenta el mismo, sin que haya que hacerlo a mano.
  - los placeholders %...% aplanan el texto y le borran el formato a los hijos,
    asi que en los nombres y las descripciones no se usan.
"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

import estilo as e

DESTINO = os.path.join(AQUI, "datapack", "data", "sdp", "menu")

# Los precios de la tienda de shards. Son los de DonutSMP escalados por el
# spawner: alla sale 1.500 shards y aca 200, o sea todo por 0,133. Lo que se
# mantiene es la RELACION, que es la que define que conviene comprar primero.
PRECIO = {
    "spawner": 200,
    "armadura": 200,     # por pieza; el set completo son 800
    "espada": 200,
    "maza": 270,
    "pico": 130,
    "pala": 110,
    "hacha": 80,
    "arco": 70,
    # La unica que no sigue la regla de tres tal cual, y a proposito: en Donut
    # sale 6.000, que son CUATRO spawners. Estuvo en 400 por error hasta el
    # 2026-09-05, o sea la mitad de lo que le toca. Es el mejor sumidero de
    # shards que hay, porque multiplica lo que rendis minando en vez de darte
    # un objeto que ya tenes.
    "haste": 800,
}

# Los encantamientos de lo que vende la tienda. La armadura va con Proteccion y
# no con Blast Protection a proposito: es el detalle que usa Donut para dar
# acceso sin dar la ventaja que decide las peleas con crystals.
#
# OJO: estos encantamientos van SOLO en el comando give, nunca en el item que
# dibuja el menu. Inventory Menu resuelve el stack con JsonOps pelado, sin
# acceso a los registros, y desde 1.21 los encantamientos son datapack: el item
# revienta con "Can't access registry minecraft:enchantment" y en el slot
# aparece una barrera que dice Invalid menu item. Medido en el juego: fallaron
# los once items encantados y anduvieron los spawners y las pociones, porque el
# NBT del bloque y los efectos no pasan por un registro de datapack.
# Para que igual se vean encantados va enchantment_glint_override, que es un
# booleano y no toca ningun registro.
ENC_ARMADURA = {"protection": 4, "unbreaking": 3, "mending": 1}
ENC_ESPADA = {"sharpness": 5, "unbreaking": 3, "mending": 1, "looting": 3}
ENC_MAZA = {"density": 5, "unbreaking": 3, "mending": 1}
ENC_PICO = {"efficiency": 5, "unbreaking": 3, "mending": 1, "fortune": 3}
ENC_PALA = {"efficiency": 5, "unbreaking": 3, "mending": 1}
ENC_HACHA = {"efficiency": 5, "unbreaking": 3, "mending": 1}
ENC_ARCO = {"power": 5, "unbreaking": 3, "mending": 1, "flame": 1}
ENC_BALLESTA = {"piercing": 4, "unbreaking": 3, "mending": 1}


def texto(t, color=None, negrita=False, cursiva=False):
    c = {"text": t, "italic": cursiva}
    if color:
        c["color"] = color
    if negrita:
        c["bold"] = True
    return c


BRILLO = {"minecraft:enchantment_glint_override": True}


def enc(encantamientos):
    """Los encantamientos como los escribe el comando give, de una sola fuente."""
    return "enchantments={%s}" % ",".join("%s:%d" % par for par in encantamientos.items())


# Los items del menu son botones, no objetos: no tiene que aparecerles nada de
# lo que el juego dibuja solo. Una espada mostrando "7 de dano de ataque" abajo
# del cartel que explica cuanto sale marea, y no dice nada util.
#
# Ademas es la marca que usa el mod de precios para NO ponerle el "Precio: $180"
# a un lingote de oro que en realidad es el boton de ECONOMIA. El mod saltea
# cualquier item que traiga tooltip_display, que es exactamente "a este item
# alguien le curo la descripcion a mano".
SIN_ADORNOS = {
    "minecraft:tooltip_display": {
        "hidden_components": [
            "minecraft:attribute_modifiers",   # el dano de las espadas y la armadura
            "minecraft:potion_contents",       # los efectos de la pocion, que ya estan en el lore
            "minecraft:unbreakable",
            "minecraft:stored_enchantments",
            "minecraft:enchantments",
        ]
    },
    # La marca que lee el mod de precios para no ponerle "Precio: $180" al
    # lingote de oro que en realidad es el boton de ECONOMIA.
    #
    # Tiene que ser custom_data y NO tooltip_display, aunque tooltip_display
    # parezca la marca natural: ese componente esta en COMMON_ITEM_COMPONENTS,
    # o sea que TODOS los items lo traen por defecto. Con esa version el mod
    # salteaba el inventario entero y no mostraba ningun precio. custom_data no
    # esta en esa lista, asi que un item normal no lo tiene nunca.
    "minecraft:custom_data": {"sdp": "menu"},
}

# El relleno del marco no tiene nombre ni texto, asi que sin esto el juego le
# dibuja igual un cuadrito negro vacio al pasarle el mouse por encima. Los
# huecos de verdad (slots sin item) no dibujan nada, y quedaban dos clases de
# cuadrado vacio en la misma grilla.
SIN_CARTEL = {"minecraft:tooltip_display": {"hide_tooltip": True}}


def item(iid, nombre, color, lore=(), componentes=None, cantidad=1):
    """El stack como lo serializa el juego: id, count y components."""
    comp = {
        "minecraft:custom_name": texto(nombre, color, negrita=True),
        "minecraft:lore": [l if isinstance(l, dict) else texto("  " + l, e.ETIQUETA)
                           for l in lore],
    }
    comp.update(SIN_ADORNOS)
    comp.update(componentes or {})
    return {"id": iid, "count": cantidad, "components": comp}


def celda(fila, columna, stack, accion=None, sonido="click"):
    d = {"slot": [fila, columna], "type": "item", "item": stack, "sound": sonido}
    if accion:
        d["action"] = accion
    return d


def abrir(menu):
    return {"type": "navigate", "action": "open", "menu": menu}


def cerrar():
    return {"type": "navigate", "action": "close"}


def correr(comando):
    """Corre el comando como el jugador: los de Essential Commands lo necesitan."""
    return {"type": "command", "command": comando, "as_player": True, "silent": False}


def escribir(comando, explicacion):
    """
    Para los comandos que piden datos que una GUI de cofre no puede pedir. Manda
    una linea al chat que se clickea para que el comando quede escrito, y despues
    CIERRA el menu.

    Cerrarlo no es un detalle: con el cofre abierto el chat se ve pero no se puede
    clickear, porque la pantalla del contenedor se come los clicks. O sea que el
    boton parecia no hacer nada, y esa es la mitad de "los comandos de extras no
    funcionan": tres de los cinco botones de EXTRAS son de este tipo. La otra
    mitad eran los permisos que le faltaban al grupo default de LuckPerms.

    Que se puedan encadenar dos acciones sale de StandardItem: el campo se llama
    "action" pero su codec es Action.LIST_CODEC, o sea una lista o una sola, y
    MenuElement.onClick corre Action.executeAll en orden.

    El mensaje es UN componente y sin ningun salto de linea adentro, y las dos cosas
    importan:

      - con un salto, el mod parte el texto por los saltos y rearma cada pedazo
        como un literal: se pierden el click y el color de los hijos, y el item
        deja de servir. Es PlaceholderResolver.resolve(List<Component>).
      - con una lista de componentes, el codec del mensaje es un xor entre "una
        lista" y "un componente", y un array de componentes parsea como las dos
        cosas: el menu entero no carga y el log dice "Both alternatives read
        successfully, can not pick the correct one".
    """
    return [
        {"type": "message", "message": {"text": "", "extra": [
            texto("  " + e.FLECHA + " ", e.APAGADO),
            texto(explicacion + "   ", e.ETIQUETA),
            {"text": comando, "color": e.ACENTO, "bold": True, "italic": False,
             "click_event": {"action": "suggest_command", "command": comando},
             "hover_event": {"action": "show_text",
                             "value": texto("Clickea y completa lo que falta", e.ETIQUETA)}},
            texto("   clickealo y completalo", e.APAGADO),
        ]}},
        cerrar(),
    ]


def comprar(comando, precio):
    """
    Cobra shards y da el item. El mod verifica el score antes y lo descuenta
    solo si alcanza, asi que no hay forma de comprar sin pagar.
    """
    return {
        "type": "command", "command": comando, "as_player": False, "silent": True,
        "action_cost": [{
            "type": "score", "objective": "Shards", "amount": precio,
            "fail_message": {"message": {"text": "", "extra": [
                texto("  " + e.CRUZ + " ", e.ERROR),
                texto("Te faltan shards. ", e.ERROR),
                texto("Se ganan matando (10) y jugando (1 cada 10 min).", e.ETIQUETA),
            ]}},
        }],
    }


def marco(rows, saltar=()):
    """Vidrio gris arriba y abajo, para que el menu no se vea vacio."""
    relleno = item("minecraft:gray_stained_glass_pane", " ", e.APAGADO,
                   componentes=SIN_CARTEL)
    return [celda(f, c, relleno) for f in (1, rows) for c in range(1, 10)
            if (f, c) not in saltar]


def volver(menu="sdp:comandos", fila=None, columna=5):
    return celda(fila, columna,
                 item("minecraft:arrow", e.VOLVER + " VOLVER", e.MARCA, ["Al menu anterior"]),
                 abrir(menu), sonido="page_turn")


ANCHO = 7   # las siete columnas utiles: la 1 y la 9 son el borde


def lista(comandos, primera=2):
    """
    Acomoda una lista de comandos en filas de 7, dejando libres las columnas de
    los bordes. Cada entrada es (item, comando, texto, accion).

    Cada fila va centrada, incluida la ultima cuando queda incompleta. Con las
    filas pegadas a la izquierda un menu de cinco items queda todo amontonado en
    una esquina y se nota: los menus hechos a mano reparten simetrico alrededor
    de la columna 5 y estos quedaban distintos.
    """
    celdas = []
    for arranque in range(0, len(comandos), ANCHO):
        renglon = comandos[arranque:arranque + ANCHO]
        fila = primera + arranque // ANCHO
        primera_columna = 2 + (ANCHO - len(renglon)) // 2
        for i, (iid, nombre, desc, accion) in enumerate(renglon):
            celdas.append(celda(fila, primera_columna + i,
                                item(iid, nombre, e.ACENTO, desc), accion))
    return celdas


def guardar(nombre, doc):
    os.makedirs(DESTINO, exist_ok=True)
    usados = [tuple(i["slot"]) for i in doc["items"]]
    repetidos = {s for s in usados if usados.count(s) > 1}
    assert not repetidos, "%s tiene dos items en %s" % (nombre, repetidos)
    for f, c in usados:
        assert 1 <= f <= doc["rows"] and 1 <= c <= 9, \
            "%s: el slot %d,%d cae fuera del menu" % (nombre, f, c)
    open(os.path.join(DESTINO, nombre + ".json"), "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, indent=2, ensure_ascii=False))
    print("  %-22s %d filas, %2d items" % (nombre + ".json", doc["rows"], len(doc["items"])))


# ------------------------------------------------------------------ menu principal
guardar("comandos", {
    "name": texto("SOBRINOS DE PEPE", e.MARCA, negrita=True),
    "rows": 5,
    "items": marco(5) + [
        # Arriba y solo, como el cartel que explica en tienda y en pvp. Existe
        # para desarmar la idea de que hay que pasar por el menu para todo: los
        # comandos funcionan escribiendolos, y el menu es nada mas un lanzador.
        celda(2, 5, item("minecraft:writable_book", "TODOS LOS COMANDOS", e.MARCA_CLARA,
                         ["La lista entera en el chat",
                          "No hace falta el menu: todos",
                          "se pueden escribir directo"]),
              correr("comandos")),
        celda(3, 2, item("minecraft:gold_ingot", "ECONOMIA", e.PLATA,
                         ["Plata, tienda, subastas", "y ordenes de compra"]),
              abrir("sdp:economia"), "select"),
        celda(3, 3, item("minecraft:oak_door", "CASA Y VIAJES", e.MARCA,
                         ["Homes, spawn, rtp", "y teletransportes"]),
              abrir("sdp:casa"), "select"),
        celda(3, 4, item("minecraft:netherite_sword", "PVP", e.KILLS,
                         ["Como se pelea aca"]),
              abrir("sdp:pvp"), "select"),
        celda(3, 6, item("minecraft:amethyst_shard", "TIENDA DE SHARDS", e.SHARDS,
                         ["Lo que se compra matando:", "spawners, armas y armaduras"]),
              abrir("sdp:tienda"), "select"),
        celda(3, 7, item("minecraft:name_tag", "EXTRAS", e.ACENTO,
                         ["Vision nocturna, skin,", "apodo y privados"]),
              abrir("sdp:extras"), "select"),
        celda(3, 8, item("minecraft:barrier", "CERRAR", e.ERROR), cerrar(), "close"),
    ],
})

# ---------------------------------------------------------------------- economia
guardar("economia", {
    "name": texto("ECONOMIA", e.PLATA, negrita=True),
    "rows": 5,
    "items": marco(5, saltar=[(5, 5)]) + lista([
        ("minecraft:gold_ingot", "/bal", ["Cuanta plata tenes"], correr("bal")),
        ("minecraft:gold_block", "/bal top", ["El ranking de los mas ricos"], correr("bal top")),
        ("minecraft:sunflower", "/daily", ["Tu regalo diario"], correr("daily")),
        ("minecraft:chest", "/shop", ["Comprarle al servidor"], correr("shop")),
        ("minecraft:hopper", "/sell", ["Venderle al servidor"], correr("sell")),
        ("minecraft:name_tag", "/worth", ["Cuanto vale lo que tenes en la mano"], correr("worth")),
        ("minecraft:paper", "/pay", ["Pagarle a otro jugador"],
         escribir("/pay ", "Para pagarle a alguien:")),
        ("minecraft:ender_chest", "/ah", ["Subastas: venderle a los demas"], correr("ah")),
        ("minecraft:writable_book", "/orders", ["Ordenes de compra: pedir algo"], correr("orders")),
        ("minecraft:book", "/transactions", ["Tu historial de plata"], correr("transactions")),
    ]) + [volver(fila=5)],
})

# -------------------------------------------------------------------------- casa
guardar("casa", {
    "name": texto("CASA Y VIAJES", e.MARCA, negrita=True),
    "rows": 5,
    "items": marco(5, saltar=[(5, 5)]) + lista([
        # La casa que se llama "casa" es la principal y es GRATIS, todas las
        # veces que se quiera. Cualquier otro nombre sale 50.000 de plata, y el
        # tope son 3 casas. El precio no lo cobra este menu: /home set esta
        # interceptado por modificadores/casa-cobro.json, que saca el cartel con
        # el boton de confirmar. Ver LEEME.md, "Lo que sale la casa".
        ("minecraft:red_bed", "/home set casa", ["Guardar donde estas parado", "Tu casa principal es gratis"],
         escribir("/home set casa", "Para guardar tu casa:")),
        ("minecraft:oak_door", "/home casa", ["Viajar a tu casa"], correr("home casa")),
        # Cama celeste y no bloque de amatista: el violeta de la amatista es el
        # color de los shards en todo el servidor, y esto se paga con plata.
        # Y nada de minecraft:barrier para borrar: cuando Inventory Menu no
        # puede resolver un item dibuja justo una barrera que dice "Invalid menu
        # item", asi que una barrera de verdad se lee como un menu roto.
        ("minecraft:cyan_bed", "/home set <nombre>", ["Una casa extra: 50.000 de plata", "El tope son 3 casas"],
         escribir("/home set ", "Para una casa extra (50.000 de plata):")),
        ("minecraft:shears", "/home delete <nombre>", ["Borrar una casa, gratis"],
         escribir("/home delete ", "Para borrar una casa:")),
        ("minecraft:book", "/home list", ["Ver que casas tenes"], correr("home list")),
        # Este menu tenia tambien /spawn, /back, /tpaccept y /tpdeny, y salieron el
        # 2026-09-07. /spawn anda mal y no le sirve a nadie; y los dos de responder
        # un tpa no se usan desde aca: cuando te llega la solicitud, el mensaje del
        # chat ya es clickeable y es mas rapido apretar ahi que abrir un menu.
        # Los comandos siguen existiendo: lo que se saco es el boton.
        ("minecraft:ender_pearl", "/rtp", ["Tirarte a un lugar random"], correr("rtp")),
        ("minecraft:compass", "/tpa", ["Pedirle ir hasta el a alguien"],
         escribir("/tpa ", "Para pedirle a alguien ir hasta el:")),
    ]) + [volver(fila=5)],
})

# --------------------------------------------------------------------------- pvp
reglas = item("minecraft:netherite_chestplate", e.CALAVERA + " COMO SE PELEA ACA", e.KILLS, [
    texto("  El PvP esta activo en TODO el mundo", e.ERROR),
    texto("", None),
    texto("  " + e.VINETA + " Si te matan, perdes lo que llevas", e.ETIQUETA),
    texto("  " + e.VINETA + " Quien te mata se lleva el 10% de tu plata", e.ETIQUETA),
    texto("  " + e.VINETA + " Las bases NO estan protegidas", e.ETIQUETA),
    texto("  " + e.VINETA + " Peleando no se puede viajar", e.ETIQUETA),
    texto("", None),
    texto("  Desde que le pegas a alguien o te pegan quedas", e.ERROR),
    texto("  EN PELEA por 15 segundos, y ahi no andan", e.ERROR),
    texto("  /home, /spawn, /rtp, /back ni /tpa.", e.ERROR),
    texto("", None),
    texto("  Lo que guardas en el ender chest no se", e.ETIQUETA),
    texto("  pierde al morir y nadie te lo puede robar.", e.ETIQUETA),
])

guardar("pvp", {
    "name": texto("PVP", e.KILLS, negrita=True),
    "rows": 5,
    # La misma forma que sdp:tienda: el cartel que explica arriba y solo, y las
    # dos puertas abajo repartidas alrededor de la columna del medio. Quedo
    # corrido a la izquierda (columnas 2, 4 y 6) al sacar las dos entradas del
    # bounty y no volver a centrar lo que quedaba.
    "items": marco(5, saltar=[(5, 5)]) + [
        celda(2, 5, reglas),
        celda(3, 4, item("minecraft:amethyst_shard", "/shards", e.SHARDS,
                         ["Cuantos shards tenes", "y como se ganan"]),
              correr("shards")),
        celda(3, 6, item("minecraft:diamond_sword", "TIENDA DE SHARDS", e.SHARDS,
                         ["Lo que se compra matando"]),
              abrir("sdp:tienda"), "select"),
        volver(fila=5),
    ],
})

# ------------------------------------------------------------------------ extras
guardar("extras", {
    "name": texto("EXTRAS", e.ACENTO, negrita=True),
    "rows": 5,
    "items": marco(5, saltar=[(5, 5)]) + lista([
        ("minecraft:golden_carrot", "/nv", ["Ver de noche en las cuevas"], correr("nv")),
        ("minecraft:player_head", "/skin set mojang", ["Ponerte la skin de otra cuenta"],
         escribir("/skin set mojang ", "Para ponerte la skin de otra cuenta:")),
        ("minecraft:name_tag", "/nickname set", ["Tu apodo en el chat"],
         escribir("/nickname set ", "Para ponerte un apodo:")),
        ("minecraft:writable_book", "/msg", ["Mensaje privado a alguien"],
         escribir("/msg ", "Para mandarle un privado a alguien:")),
    ]) + [volver(fila=5)],
})

# ------------------------------------------------------------- tienda de shards
guardar("tienda", {
    "name": texto("TIENDA DE SHARDS", e.SHARDS, negrita=True),
    "rows": 5,
    "items": marco(5, saltar=[(5, 5)]) + [
        celda(2, 5, item("minecraft:amethyst_shard", e.SHARD + " COMO SE GANAN SHARDS", e.SHARDS, [
            texto("  " + e.VINETA + " 10 por matar a otro jugador", e.SHARDS),
            texto("  " + e.VINETA + " 1 por cada 10 minutos jugados", e.ETIQUETA),
            texto("", None),
            texto("  El tiempo AFK no cuenta: hay que", e.APAGADO),
            texto("  moverse para que el reloj corra.", e.APAGADO),
            texto("", None),
            texto("  Los shards no se compran con plata", e.ERROR),
            texto("  ni se pueden pasar a otro jugador.", e.ERROR),
        ])),
        celda(3, 2, item("minecraft:netherite_chestplate", "ARMADURA", e.SHARDS,
                         ["Netherita con Proteccion IV,",
                          "Irrompibilidad III y Reparacion",
                          "%d shards la pieza" % PRECIO["armadura"]]),
              abrir("sdp:tienda_armadura"), "select"),
        celda(3, 3, item("minecraft:netherite_sword", "ARMAS", e.SHARDS,
                         ["Espada, maza, arco y ballesta",
                          "desde %d shards" % PRECIO["arco"]]),
              abrir("sdp:tienda_armas"), "select"),
        celda(3, 5, item("minecraft:netherite_pickaxe", "HERRAMIENTAS", e.SHARDS,
                         ["Pico, pala y hacha de netherita",
                          "desde %d shards" % PRECIO["hacha"]]),
              abrir("sdp:tienda_herramientas"), "select"),
        celda(3, 7, item("minecraft:spawner", "SPAWNERS", e.SHARDS,
                         ["Generadores de mobs",
                          "%d shards cada uno" % PRECIO["spawner"]]),
              abrir("sdp:tienda_spawners"), "select"),
        celda(3, 8, item("minecraft:potion", "POCIONES", e.SHARDS,
                         ["Prisa II por 24 horas",
                          "%d shards" % PRECIO["haste"]]),
              abrir("sdp:tienda_pociones"), "select"),
        volver(fila=5),
    ],
})


def articulo(fila, columna, iid, nombre, precio, lore, componentes, comando):
    """Un renglon de la tienda: el item como se ve, y el comando que lo entrega."""
    detalle = list(lore) + [
        texto("", None),
        texto("  " + e.SHARD + " %d shards" % precio, e.SHARDS),
        texto("  Clickea para comprar", e.APAGADO),
    ]
    return celda(fila, columna, item(iid, nombre, e.SHARDS, detalle, componentes),
                 comprar(comando, precio), sonido=["success", "fail"])


def armadura(fila, columna, pieza, nombre):
    return articulo(
        fila, columna, "minecraft:netherite_" + pieza, nombre, PRECIO["armadura"],
        ["Proteccion IV", "Irrompibilidad III", "Reparacion"],
        BRILLO,
        "give %%name%% netherite_%s[%s] 1" % (pieza, enc(ENC_ARMADURA)))


guardar("tienda_armadura", {
    "name": texto("ARMADURA DE SHARDS", e.SHARDS, negrita=True),
    "rows": 4,
    "items": marco(4, saltar=[(4, 5)]) + [
        armadura(2, 3, "helmet", "CASCO"),
        armadura(2, 4, "chestplate", "PETO"),
        armadura(2, 6, "leggings", "PANTALONES"),
        armadura(2, 7, "boots", "BOTAS"),
        # Lleva Proteccion y no Proteccion contra explosiones, que en vanilla son
        # excluyentes. Es el detalle que usa Donut para dar acceso sin dar la
        # ventaja que decide las peleas con crystals: quien quiera esa la tiene
        # que armar con su propia mesa de encantamientos.
        celda(3, 5, item("minecraft:shield", "El set completo son %d shards"
                         % (PRECIO["armadura"] * 4), e.ETIQUETA,
                         ["Lleva Proteccion IV, que es la general.",
                          "Para pelear con crystals conviene",
                          "armarse una con Prot. contra explosiones:",
                          "esa no se vende, se encanta."])),
        volver("sdp:tienda", fila=4),
    ],
})

guardar("tienda_armas", {
    "name": texto("ARMAS DE SHARDS", e.SHARDS, negrita=True),
    "rows": 4,
    "items": marco(4, saltar=[(4, 5)]) + [
        articulo(2, 3, "minecraft:netherite_sword", "ESPADA", PRECIO["espada"],
                 ["Filo V", "Botin III", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% netherite_sword[" + enc(ENC_ESPADA) + "] 1"),
        articulo(2, 4, "minecraft:mace", "MAZA", PRECIO["maza"],
                 ["Densidad V", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% mace[" + enc(ENC_MAZA) + "] 1"),
        articulo(2, 6, "minecraft:bow", "ARCO", PRECIO["arco"],
                 ["Poder V", "Fuego", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% bow[" + enc(ENC_ARCO) + "] 1"),
        articulo(2, 7, "minecraft:crossbow", "BALLESTA", PRECIO["arco"],
                 ["Perforacion IV", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% crossbow[" + enc(ENC_BALLESTA) + "] 1"),
        volver("sdp:tienda", fila=4),
    ],
})

guardar("tienda_herramientas", {
    "name": texto("HERRAMIENTAS DE SHARDS", e.SHARDS, negrita=True),
    "rows": 4,
    "items": marco(4, saltar=[(4, 5)]) + [
        articulo(2, 3, "minecraft:netherite_pickaxe", "PICO", PRECIO["pico"],
                 ["Eficiencia V", "Fortuna III", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% netherite_pickaxe[" + enc(ENC_PICO) + "] 1"),
        articulo(2, 5, "minecraft:netherite_shovel", "PALA", PRECIO["pala"],
                 ["Eficiencia V", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% netherite_shovel[" + enc(ENC_PALA) + "] 1"),
        articulo(2, 7, "minecraft:netherite_axe", "HACHA", PRECIO["hacha"],
                 ["Eficiencia V", "Irrompibilidad III", "Reparacion"],
                 BRILLO,
                 "give %name% netherite_axe[" + enc(ENC_HACHA) + "] 1"),
        volver("sdp:tienda", fila=4),
    ],
})


def spawner(fila, columna, mob, nombre, drop):
    """El icono va SIN block_entity_data; el mob viaja solo en el give.

    Es el mismo cuidado que con los encantamientos, por otro motivo. Un item con
    block_entity_data de un tipo con onlyOpCanSetNbt (el spawner lo es) le agrega
    al tooltip la advertencia roja "el uso de este item puede llevar a la
    ejecucion de comandos". La condicion esta en BlockItem.shouldPrintOpWarning y
    solo la ven los operadores, pero igual ensucia el menu para quien lo
    administra, y no hay componente que la apague: ItemStack la agrega incluso
    con hide_tooltip.

    El icono no necesita los datos, solo tiene que parecer un spawner. El que
    genera mobs es el que sale del give, y ese si los lleva.
    """
    datos = 'block_entity_data={id:"minecraft:mob_spawner",SpawnData:{entity:{id:"minecraft:%s"}}}' % mob
    return articulo(
        fila, columna, "minecraft:spawner", "SPAWNER DE " + nombre, PRECIO["spawner"],
        ["Genera " + drop, "Se coloca y funciona solo"],
        None,
        "give %%name%% spawner[%s] 1" % datos)


guardar("tienda_spawners", {
    "name": texto("SPAWNERS DE SHARDS", e.SHARDS, negrita=True),
    "rows": 4,
    "items": marco(4, saltar=[(4, 5)]) + [
        spawner(2, 2, "skeleton", "ESQUELETO", "huesos y flechas"),
        spawner(2, 3, "zombie", "ZOMBIE", "carne podrida"),
        spawner(2, 4, "spider", "ARANA", "cuerda y ojos"),
        spawner(2, 6, "creeper", "CREEPER", "polvora"),
        spawner(2, 7, "blaze", "BLAZE", "varas de blaze"),
        spawner(2, 8, "cow", "VACA", "carne y cuero"),
        volver("sdp:tienda", fila=4),
    ],
})

guardar("tienda_pociones", {
    "name": texto("POCIONES DE SHARDS", e.SHARDS, negrita=True),
    "rows": 3,
    "items": marco(3, saltar=[(3, 5)]) + [
        articulo(2, 5, "minecraft:potion", "PRISA II", PRECIO["haste"],
                 ["Prisa II durante 24 horas", "Para minar de verdad"],
                 {"minecraft:potion_contents": {
                     "custom_effects": [
                         {"id": "minecraft:haste", "duration": 1728000, "amplifier": 1}],
                     "custom_color": 16755200}},
                 'give %name% potion[potion_contents={custom_effects:[{id:"minecraft:haste",'
                 'duration:1728000,amplifier:1}],custom_color:16755200},'
                 'custom_name={text:"Prisa II - 24 horas",color:"#a503fc",italic:false}] 1'),
        volver("sdp:tienda", fila=3),
    ],
})

print("menus escritos en servidor/datapack/data/sdp/menu/")
print("subilos con: python servidor/subir-datapack.py")
