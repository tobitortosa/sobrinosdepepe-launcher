# -*- coding: utf-8 -*-
"""
Los colores y los simbolos, en un solo lugar.

El cartel, los menus y los mensajes del datapack tienen que verse como una sola
cosa, asi que los valores viven aca y no repetidos en cada generador.

De DonutSMP se copian los colores de los NUMEROS, que son los que el jugador
aprende a leer de un vistazo: verde la plata, violeta los shards, rojo las
kills, naranja las muertes, amarillo el tiempo. Estan triangulados entre dos
configs de plugins replica y el muestreo de pixeles de una captura real.

Lo que NO se copia es el color de marca: el de Donut es azul (#00A6FF sobre
negro) y este servidor es dorado. Copiar el azul haria que se vea como Donut en
vez de verse como SOBRINOS DE PEPE.
"""

# Los numeros, igual que en Donut
PLATA = "#00ff00"
SHARDS = "#a503fc"
KILLS = "#ff0000"
MUERTES = "#fc7703"
TIEMPO = "#ffe600"

# La marca propia
MARCA = "#ffb02e"          # el dorado de SOBRINOS DE PEPE
MARCA_CLARA = "#ffd98a"    # para el degrade del titulo
ACENTO = "#00a6ff"         # lo clickeable, tomado del azul de Donut
ETIQUETA = "gray"
APAGADO = "dark_gray"
ERROR = "#ff5555"
BIEN = "#55ff7f"

# Simbolos. Todos del plano 0 de Unicode a proposito: los del plano 1 (las
# emojis de dado, hacha o dona) salen como un cuadrado vacio si la fuente del
# cliente no las trae, y aca nadie usa resource pack.
PESOS = chr(0x24)          # $
SHARD = chr(0x2726)        # simbolo de cuatro puntas para los shards
ESPADAS = chr(0x2694)      # kills
CALAVERA = chr(0x2620)     # muertes y recompensas
RELOJ = chr(0x231b)        # tiempo jugado
FLECHA = chr(0xbb)         # separa etiqueta de valor
VINETA = chr(0x25aa)       # las listas
RAYA = chr(0x25ac)         # los bordes
VOLVER = chr(0xab)
TILDE = chr(0x2714)
CRUZ = chr(0x2716)
ESTRELLA = chr(0x2605)
NOTA = chr(0x266a)         # la voz
