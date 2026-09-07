# Lo que vive en el servidor

Copia de respaldo de la configuración que hoy solo existe dentro del servidor de
Minehost. Si el mundo se pierde o hay que rearmar el servidor, esto se vuelve a
subir tal cual y todo queda como estaba.

| Script | Para qué |
|---|---|
| `respaldar.py` | Baja del servidor todo lo que mantenemos nosotros. |
| `subir-datapack.py` | Sube el datapack entero (funciones, advancements, menús) y recarga. |
| `generar-precios.py` | Arma `prices.json` y `config.json` de EconomyCraft, rearma el mod cliente de precios y verifica que no haya plata infinita. |
| `verificar-precios.py` | Solo la verificación, contra el servidor o contra un archivo. |
| `generar-menus.py` | Arma los menús de cofre (los deja en el datapack). |
| `generar-comandos.py` | Arma los comandos propios de Melius, sube también los modificadores y recarga. |
| `generar-cartel.py` | Arma el cartel de la derecha. |
| `configurar-scoreboard.py` | Rehace los objetivos y los lugares del scoreboard, que el juego guarda dentro del mundo. |
| `configurar-borde.py` | Pone el borde del mundo, igual en las tres dimensiones. |
| `configurar-permisos.py` | Le da al grupo `default` de LuckPerms los permisos de los comandos que la guía promete. |
| `configurar-horror.py` | Deja Server-Side Horror en modo "una pizca": ruidos y nada que toque el mundo. |
| `generar-recursos.py` | Arma el resource pack propio del servidor, lo publica en GitHub y apunta el servidor. |
| `configurar-zonas.py` | Deja Safe Zone como herramienta de administrador: nadie más puede proteger zonas. |
| `ajustar-saldos.py` | Deja el saldo de cada uno en proporción a las horas jugadas. |
| `estilo.py` | Los colores y los símbolos, en un solo lugar. |

Las credenciales salen de `web/.env.local`, que no está en el repositorio.

Nada de esto se instala en las máquinas de los jugadores: son mods y datapacks
de servidor, así que se aplican sin publicar una versión nueva del launcher. La
única excepción es el mod de precios, que sí es de cliente — ver más abajo.

## Cómo funciona la economía

Hay **dos monedas que no se cambian entre sí**, igual que en DonutSMP.

|  | Plata | Shards |
|---|---|---|
| Cómo se gana | vender al servidor, comerciar, matar (te llevás el 10% de la plata del muerto) | 10 por matar a un jugador, 1 cada 10 minutos jugados |
| Para qué sirve | consumibles en `/shop`, comerciar en `/ah` y `/orders` | spawners, armas, armaduras, herramientas, pociones y recompensas |
| Se compra con la otra | no | no |

**La tienda del servidor no vende nada durable.** Las categorías `armor`,
`weapons`, `tools` y `enchantments` están apagadas para la compra, y los items
durables sueltos (netherita, elytra, beacon, nether star, maza, huevo de dragón)
tienen `unit_buy: 0`. Todo eso se sigue pudiendo **vender**: apagar una
categoría solo la saca del `/shop`, el `/sell` nunca mira si está habilitada.

Lo que sí se compra barato son los **consumibles de pelea** — totems, gapples,
perlas, obsidiana, crystals, respawn anchors. Eso es lo que hace DonutSMP a
propósito: si regearse cuesta horas, nadie sale a buscar pelea. Es la
distinción que importa: **consumible sí, durable no.**

**Toda la tabla está multiplicada por 10** respecto de la de fábrica. No es
inflación decorativa: es resolución. Las recetas que multiplican (6 vidrios dan
16 paneles) obligan a ponerle un tope al precio de venta del resultado, y con
precios enteros que valen 1 ese tope cae abajo de 1 y el item queda sin poder
venderse. Medido: a escala 1 quedaban 17 items sin venta; a escala 5 o más,
ninguno. Se escaló todo junto — saldo inicial, regalo diario, tope de venta y
los saldos que ya tenían los jugadores.

### La trampa de los precios de Donut

Los números que circulan de DonutSMP (netherite ingot 3,5-6 millones, elytra
250-357 millones) son del **mercado entre jugadores**, no de lo que paga su
`/sell`. Su `/sell` real está en
[donut-quant](https://github.com/Aeripsen/donut-quant) (`quant/worth_table.csv`,
137 items sacados de 158 capturas del GUI de `/worth` in-game, 2026-09-02) y es
otra cosa completamente:

| Relación | En su mercado | En su `/sell` |
|---|---|---|
| elytra / netherite ingot | 59x | **1,2x** |
| netherite ingot / diamante | 912x | **208x** |

O sea que "una elytra vale 85 veces un ingot de netherita" es cierto del
mercado y falso del `/sell`. Nuestra tabla copia las relaciones del **`/sell`**,
que es lo comparable con el nuestro.

Del `/sell` de Donut se copian solo los items de fin del juego. El resto de su
tabla está deformada por su propia meta de farms — les vale el vidrio 70 veces
más que a nosotros y el redstone 35 veces más — y copiarla entera rompía la
coherencia con los otros 1.550 items.

### Las cuatro formas de sacar plata infinita

`verificar-precios.py` las busca todas, leyendo del jar del juego las 1.515
recetas y los 387 trueques de aldeano. **Correlo después de cada cambio de
precios.**

1. **Un item que se venda por más de lo que se compra.** Es la obvia y era la
   única que estaba controlada.
2. **Craftear comprando los ingredientes.** Si todos los ingredientes de una
   receta se venden en la tienda y el resultado se vende por más que la suma,
   es plata garantizada sin riesgo. Es lo que hundió la economía de Donut: allá
   un log de madera cuesta $72 en las órdenes y crafteado en 8 slabs vale $96
   en el `/sell`, o sea 30% seguro, escalable con autocrafters hasta $200M/hora.
   **La tabla de fábrica de EconomyCraft tenía 43 de estas máquinas**, y la peor
   era el lodestone: en 26.1 se craftea con 8 ladrillos de piedra cincelada y
   **un lingote de hierro** (antes era netherita), o sea 31 de ingredientes, y
   se vendía a 257.
3. **El bloque comprimido que vale más que sus nueve unidades.** Esto el script
   lo reporta como aviso y no como falla: comprimir para vender mejor no crea
   plata, porque las unidades hay que conseguirlas igual.

El punto 2 **no** se puede chequear receta por receta. La primera versión de
`verificar-precios.py` costeaba una sola receta y solo con precios directos de
la tienda, y así se le escaparon las dos peores:

- el **name tag** son 1 papel + 1 pepita de metal, y ninguno de los dos se vende
  en la tienda, así que la receta se salteaba entera. Pero el papel sale de la
  caña de azúcar y la pepita de un lingote de hierro: $23 de insumos para un
  item que estaba valuado en $1.400 copiando el `/sell` de Donut, donde el name
  tag **no** se craftea. 60 veces.
- la **arena** se compra, se funde en vidrio, y 6 vidrios dan 16 paneles: 33%
  garantizado. Es literalmente la misma máquina que hundió a Donut con los slabs
  de madera.

Por eso el costo de cada item se calcula con un **punto fijo** sobre las 1.515
recetas: `costo(x)` es el menor entre lo que sale comprarlo y, por cada receta
que lo produce, la suma del costo de sus ingredientes dividida por cuántos
salen. Se itera hasta que ningún costo baja más. El combustible de los hornos no
se cuenta, que es el lado conservador. Con ese cálculo aparecieron 61 items para
reparar y el chequeo queda en cero.

4. **El trueque de aldeano.** Es la que de verdad rompió la economía, y la que
   el punto fijo no veía porque **un trueque no es una receta**. Los aldeanos
   son la única máquina del juego que convierte cosas renovables en esmeraldas
   y esmeraldas en equipo, sin límite y sin costo real.

   El 5 de septiembre de 2026 la economía creó 763.005 pesos y **732.060 de
   esos (el 96%) salieron de 285 libros encantados que vendió un solo jugador**,
   162 de ellos Mending a 4.200 cada uno. No hubo trampa: a un librero curado se
   le saca Mending por **una** esmeralda, el trueque se repone doce veces por
   aldeano, y la tienda no puede saber qué encantamiento tiene el libro que le
   estás vendiendo (le pagaba lo mismo a un Mending que a un Bane of Arthropods
   I). Cualquier precio mayor que cero multiplicado por una sala de aldeanos es
   plata infinita.

   Arreglado poniendo **toda la categoría `enchantments` en 0 de compra y 0 de
   venta**: `getUnitSell` devuelve `null` cuando el precio no es mayor que cero,
   así que el item deja de ser vendible sin que el `/sell` se lo coma. Los
   libros ahora se mueven entre jugadores por el `/ah`, que es lo que queremos y
   además quema el 10% de impuesto.

   Dos detalles del chequeo, porque sin ellos no caza nada:

   - La tabla **no** tiene `minecraft:enchanted_book`: tiene 121 entradas
     `enchanted_book_<encantamiento>_<nivel>`. Buscando el id pelado el trueque
     del librero queda afuera del chequeo, que es exactamente lo que pasó. Hay
     que valuarlo con el **máximo** de las variantes, porque el jugador elige
     cuál conseguir.
   - En el JSON del trueque, `wants.count` del librero es **0**: el costo en
     esmeraldas lo calcula el juego en tiempo real. Tratarlo como gratis es el
     lado correcto, porque con el descuento de curar al aldeano es 1 esmeralda.

   El chequeo falla si un solo aldeano rinde más de 25.000 por día. Con la
   categoría en cero quedan 73 trueques con ganancia y **ninguno** pasa el tope;
   el más grande es el armero de nivel 5 con la pechera de diamante encantada,
   11.520 por día. Eso no es una máquina: es una granja, con aldeanos que hay
   que criar y subir de nivel.

   **No bajar el precio de la esmeralda para arreglar esto.** Probado: a 56 (que
   es lo que paga Donut, 24 escalado) mueren los trueques de basura → esmeralda
   pero explotan los de esmeralda → equipo, y el peor pasa de 1.920 a 5.824 por
   uso. El precio de la esmeralda no tiene una banda que cierre las dos puntas.

### La netherita no se craftea: se compra con shards

**Las doce recetas de smithing de netherita están apagadas** desde el
2026-09-07. La armadura, las armas y las herramientas de netherita salen ahora
de un solo lado: la tienda de shards, que las entrega con `give` y por lo tanto
no pasa por ninguna receta. Es la asimetría que hace que el equipo de fin del
juego se pague matando gente, que es de lo que vive el servidor.

Se apaga con **un archivo**:

```
datapack/data/minecraft/tags/item/netherite_tool_materials.json
{"replace": true, "values": []}
```

Las doce recetas usan esa etiqueta como `addition` — verificado abriendo el jar
de 26.1, doce de trece: la que sobra es
`netherite_upgrade_smithing_template`, la de duplicar la plantilla, que sin
smithing no sirve para nada. Se recarga con `/reload`, no pide reinicio y no
toca el launcher. En el log quedan **doce WARN** que son la confirmación de que
funcionó, no un problema:

```
Recipe minecraft:netherite_axe_smithing can't be placed due to empty ingredients and will be ignored
```

Por qué se eligió esta palanca y no las otras, con los números que había:

- **El netherite no había inundado nada.** En toda la historia del servidor se
  minaron 67 bloques de ancient debris y se craftearon 14 lingotes, con **un
  solo set completo** (Luquitas1410, en 38,6 horas jugadas). El problema no era
  la cantidad, era la tasa: el set de 16 debris sale en 11 a 22 horas de juego
  medidas sobre tres mineros reales, y los mismos 800 shards que cuesta el set
  en la tienda salen 133 horas a 6 shards por hora. **Minar era 6 a 12 veces más
  rápido que matar**, así que nadie "se salteó" los shards: los shards nunca
  compitieron.
- **No se saca el netherite del mapa.** El ancient debris se sigue minando y se
  sigue vendiendo — 4.200 el bloque, 21.000 el scrap, 175.000 el lingote — así
  que bajar al Nether sigue pagando, pero paga en **plata** y no en equipo. Las
  dos monedas quedan con su propia actividad y no se pisan.
- **No se tocó la generación del mundo.** Sobreescribir los `configured_feature`
  del debris solo afecta chunks nuevos (los registros de worldgen se cargan al
  abrir el mundo, no con `/reload`), y es el único cambio que puede dejar el
  servidor sin arrancar si el JSON no valida. Con el Nether generado en 24
  archivos de región no hacía falta correr ese riesgo.
- **No se tocó la lotería del drop.** Un debris que dropea al 5% se lee como un
  bug ("miné quince y no me dio nada") y encima ya no hace falta.

Lo que se rompe, y hay que saberlo:

- **Las herramientas y armas de netherita ya no se reparan con lingote en el
  yunque.** Esa etiqueta es también el `repairItems` de `ToolMaterial.NETHERITE`
  (es el sexto campo del record, verificado en el bytecode de 26.1). Mending
  sigue andando, y la tienda de shards las entrega con Mending, así que en la
  práctica no se nota. **La armadura no se toca**: usa
  `repairs_netherite_armor`, que quedó intacta a propósito para no castigar al
  que ya se ganó su set.
- **El que ya tiene netherita la conserva**, y por un buen rato va a ser el más
  fuerte del servidor. Es equipo que se ganó minando cuando se podía.
- No bloquea ningún contenido: en 26.1 el pico de **diamante mina todo**
  (`incorrect_for_diamond_tool` está vacía) y la armadura de diamante da los
  **mismos puntos de armadura** que la de netherita. La netherita suma dureza y
  resistencia al empuje, no acceso.

**Ojo con lo que se creía de DonutSMP.** Allá el netherite **no** está gateado
por shards: se compra con plata en el `/ah`, y un jugador nuevo llega al set en
su primer día. Lo que hace que allá dure es la densidad —decenas de miles de
jugadores vaciando un Nether de ±57.000 durante dos años, sin resets— y eso no
se puede copiar con nueve perfiles. Y hay algo más: **Donut eliminó los shards
por kill el 2026-06-02**; hoy allá el único ingreso de shards es 1 cada 10
minutos jugados. Nosotros copiamos las tasas viejas (10 por kill más 1 cada 10
minutos) con los precios divididos por 7,5, o sea que nuestra tienda de shards
es mucho más barata de lo que fue Donut incluso cuando Donut era generoso.

Nunca poner un **multiplicador de venta** que suba con el volumen. Es
exactamente lo que rompió Donut: los jugadores compraban en las órdenes por
debajo de `precio_base x multiplicador` y le vendían al servidor. Uno solo llegó
a vender 15 billones así, y Donut eliminó el sistema entero el 2026-06-02. Por
eso `dynamic_prices_enabled` queda en `false`.

## Qué mods van en el servidor y cuáles en el launcher

Son dos listas distintas y no tienen por qué coincidir. Al 2026-09-06 el
servidor tiene **16 jars** en `/mods` y el pack que baja el launcher tiene **14
entradas** (13 mods y el shader). **Los 6 que están en los dos lados son el mismo
archivo**, verificado por hash: fabric-api, cloth-config, lithium, modernfix,
sound-physics y voicechat. Los otros diez del servidor no los tiene nadie en su
PC, y está bien: son `environment: server` o `*` sin nada de cliente.

Cómo se decide dónde va cada uno: se lee el `fabric.mod.json` del jar.

- `"environment": "client"` → **no lo carga el servidor**, ni aunque esté en
  `/mods`. Va solo en el pack.
- `"environment": "*"` con entrypoint `main` y nada de `client` → va solo en el
  servidor (essential_commands, melius-commands, inventory-menu,
  styled-sidebars, skinrestorer).
- `"environment": "server"` → solo servidor (luckperms).

Ojo con los que declaran entrypoint de cliente pero no lo usan:
**EconomyCraft tiene `onInitializeClient()` vacío**, así que los jugadores no
necesitan tenerlo. Está bien que no esté en el pack.

En `/mods-apagados/` hay diez jars. Se movieron en vez de borrarse, así que
volver atrás es moverlos de nuevo a `/mods`:

| Jar | Por qué salió |
|---|---|
| xaerominimap, xaeroworldmap | **Sacados el 2026-09-06, y también del pack.** El minimapa trae radar de jugadores: `default_radar_categories_client.json` tiene la categoría `players` con sus subcategorías `friend`, `tracked`, `same_team` y `other_teams`. En un servidor de PvP libre eso es saber siempre dónde está todo el mundo, que es justo lo contrario de lo que hace divertido salir a buscar pelea. Son `environment: *`, así que el servidor **sí** los cargaba. Los configs de `overrides/config/xaero/` también salieron del launcher: `ConfigSeeder` nunca borra, así que a quien ya los tenía le quedan, pero no los lee nadie. |
| iris, sodium, sodium-extra, reeses-sodium-options, zoomx | Son `environment: client`. El servidor nunca los cargó (se ve en la lista de arranque). Ya están en el pack, que es donde sirven. |
| tl_skin_cape | Igual, pero además **no está en el pack**, o sea que no lo tenía nadie. Del lado del servidor las skins las resuelve `skinrestorer`. |
| maplink | Este sí cargaba. Sincroniza Xaero con un Bluemap/Dynmap/Squaremap, y acá no hay ninguno. Su `maplink.fabric.mixins.json` tiene la lista común **vacía**: todos sus mixins son de cliente, así que en un servidor dedicado no parchea nada. |
| player-revive (Simple Revive) | Peleaba con el diseño. Es un datapack puro, sin una sola clase, y el downed lo dispara `simplerevive.deathCount`, o sea que **la muerte pasa de verdad primero**: el asesino cobra kill, shards, recompensa y el 10% de la plata igual. Lo que rompía era el resto. Con `keepinv: 1b`, `as_item.mcfunction` le pone `PickupDelay: 0` a lo que soltaste y **te lo teletransporta encima**, así que revivir te devuelve el equipo y matar deja de dar botín. Y sobre todo: matarse con un amigo y revivirlo era una máquina de shards gratis. Al sacarlo quedaron 16 objetivos `simplerevive.*` huérfanos, que se borraron a mano. |

## El borde del mundo

**12.000 bloques de lado** desde el 2026-09-07, centrado en 0 0, **igual en las
tres dimensiones**. Lo pone `configurar-borde.py` y es idempotente: correrlo de
nuevo contesta "Nothing changed". El spawn está en (48, 97, 0), o sea a 48 bloques
del centro: no se nota.

El número nunca fue una corazonada: las dos veces se midió el mundo antes de
tocarlo, leyendo los `.dat` de los jugadores y los nombres de los archivos de
región.

| Qué | 2026-09-04 (se pone en 8.000) | 2026-09-07 (se agranda a 12.000) |
|---|---|---|
| Regiones generadas del overworld | 2.560 bloques | **4.000: tocaron la pared** |
| Regiones generadas del Nether | 1.024 bloques | 4.000, también |
| El End | no existía | 4.000, también |
| Jugador más lejos | Titit0N, z = -1.860 | — |
| Cama más lejos | Felix_1256, (922, -1.593) | — |
| El mundo en disco | — | 1,3 GB, y el plan está en ilimitado |

Con 4.000 de radio no quedó afuera ni un chunk de los que existían, y sobraban
1.440 bloques de frontera nueva. Tres días después estaba consumida.

**Se eligió 6.000 de radio y no el doble a propósito.** El borde no está para que
el mundo sea chico porque sí: está para que la gente se cruce, que es de lo que
vive un servidor de PvP. Con 6.000 hay 50% más de distancia en cada dirección y
2,25 veces más tierra — cruzarlo corriendo pasa de 12 a 18 minutos — y encontrarse
sigue siendo algo que pasa. Con 8.000 de radio serían cuatro veces más tierra y
cruzarse pasaría a ser casualidad.

**Agrandar es una puerta de una sola dirección.** Achicarlo después deja del lado
de afuera todo lo que hayan construido en la tierra nueva, así que conviene subir
de a poco y volver a subir, y no de una.

Hay cuatro regiones generadas a 250.000 bloques. **No son de nadie**: salieron de
probar spawners el 2026-09-04, y está en el log
(`Changed the block at 250000, 100, 250000`).

El Nether lleva el mismo número y **no la octava parte**. Achicarlo para que
coincidiera geográficamente con el overworld cortaría chunks que ya están
generados. Y no abre ningún agujero para escaparse, porque el juego
recorta el portal de vuelta contra el borde del overworld: caminar 6.000 bloques
de Nether no deja a nadie a 48.000 del spawn.

## Los shards

Un objetivo de scoreboard llamado `Shards`, que es lo único que se puede
verificar de verdad con comandos.

- **+10 al matar a otro jugador, y la misma víctima no vuelve a pagar por
  diez minutos.** Se paga en `sdp:tick` y no en el advancement, porque recién en
  el tick se sabe quién murió: matarse con la propia flecha también dispara el
  advancement, y así nadie cobra por su propia muerte.

  El recorrido va **por muerto y no por asesino** (`sdp:pagar_kill` corre como
  la víctima), y esa es la parte que importa: sin saber a quién mataron no se
  puede mirar el enfriamiento de esa víctima, y sin enfriamiento dos amigos se
  matan en loop y los shards salen de la nada. Respawnear desnudo al lado del
  otro no cuesta nada y son 10 shards por vuelta, contra los 144 por día que
  paga el tiempo jugado. El enfriamiento vive en `sdp_cd` y baja un tick por
  tick, solo mientras el jugador está conectado.

  De paso arregla algo que el recorrido por asesino hacía mal: `sdp_killer` es
  una marca y no una cuenta, así que matar a dos en el mismo tick pagaba una
  sola vez. Ahora paga una vez por muerto.
- **+1 cada 10 minutos jugados**, y el tiempo AFK no cuenta. El juego ya lleva
  el contador de ticks de cada jugador con el criterio
  `minecraft.custom:minecraft.play_time`, así que no hace falta contar ticks a
  mano: `sdp_tiempo` los cuenta gratis y `sdp_marca` guarda cuántos había la
  última vez que se pagó. Cuando la resta llega a 12.000, `sdp:turno` compara la
  posición del jugador con la de hace diez minutos y solo paga si se movió.
- **No se pierden al morir ni se pueden pasar a otro jugador.** Si se pudieran
  pasar, dejarían de ser una segunda moneda al instante.

Hubo un sistema de recompensas (`/bounty`, el objetivo `Bounty`, la fila "Tu
cabeza" del cartel) y **se sacó entero el 2026-09-05**, sin que nadie lo hubiera
usado nunca: todos los `Bounty` estaban en cero.

DonutSMP sí tiene bounties, pero **en plata** (`/bounty add` te descuenta del
saldo). Acá no se puede: `eco pay` y `eco removemoney` devuelven éxito aunque no
alcance, así que un jugador sin un peso pondría una recompensa de un millón
gratis. Por eso la nuestra era en shards, y ahí no cerraba: el mínimo eran 10
shards contra los 200 que sale una pieza de armadura, o sea que poner precio a
una cabeza competía con el equipo que estás ahorrando.

Tampoco se puede hacer que la recompensa **crezca con la plata** del jugador,
que sería la versión interesante: el objetivo `eco_balance` que crea
EconomyCraft con `scoreboard_enabled` no es el saldo de cada uno, es un **top 5**
(`computeLeaderboard(5)`), y al crearlo el mod se apodera del slot del cartel.

Matar ya paga por tres lados sin necesidad de una cuarta: 10 shards, el 10% de
la plata del muerto y todo el equipo que llevaba puesto.

**`scoreboard players operation` con `@a` de los dos lados hace producto
cartesiano**, y no falla: recorre las dos colecciones anidadas, así que cada
jugador termina con el valor del último de la lista y, en la resta, con la suma
de todos. Con un solo jugador conectado anda perfecto — por eso pasó la primera
prueba — y con dos deja de pagar. La comparación de `sdp_tiempo` contra
`sdp_marca` va con `@s` a los dos lados por eso.

El estado AFK de Essential Commands **no se puede leer desde un datapack**: es
un `private boolean` en memoria y el único placeholder que registra el mod es
`essentialcommands:nickname`. De ahí que la detección sea por posición. Leer la
posición es caro (el juego serializa la entidad entera), pero acá se hace dos
veces por jugador cada diez minutos, así que no se nota.

Los precios de la tienda de shards son los de Donut escalados por el spawner:
allá sale 1.500 y acá 200, o sea todo por 0,133. Lo que se mantiene es la
relación entre los items, que es la que define qué conviene comprar primero.

## Lo que sale la casa

**Tres casas por jugador. La primera es gratis y cada una de las otras dos sale
50.000 de plata.** Antes de esto el tope no se estaba aplicando: había seis
jugadores sin OP con dos y tres casas cada uno, y nada frenaba que fueran 500.

| | Crear | Mover | Borrar |
|---|---|---|---|
| la casa que se llama `casa` | gratis | gratis | gratis |
| cualquier otro nombre | **50.000** | 50.000 | gratis |

Mover una casa extra vuelve a cobrar, y eso **no** es un descuido: ni Melius ni
un datapack pueden saber si una casa ya existe, así que "crear" y "mover" son
indistinguibles desde afuera. Distinguirlas pedía llevar la cuenta de casas de
cada jugador en un scoreboard y hacer pasar `/home set`, `overwritehome` y
`/home delete` por comandos propios; un contador que se desincroniza o cobra de
más o regala casas, y se eligió no tenerlo. El cartel de confirmación lo avisa
antes de cobrar.

### El tope son tres y sale de un solo número

`home_limit` pasó de `[1, 2, 5]` a **`[3]`**. Esa lista **no** es un número: el
mod la convierte en un grupo de permisos numéricos
(`essentialcommands.home.limit.<n>`) con
`ECPerms.makeNumericPermissionGroup`, y `getHighestNumericPermission` arranca en
el **menor** de la lista porque `grant_lowest_numeric_by_default=true` y después
lo sube por cada nodo que el jugador tenga otorgado. Con un solo valor el piso y
el techo son el mismo: todos tienen exactamente 3, sin depender de LuckPerms.

Un operador tiene infinitas: `getHighestNumericPermission` devuelve
`Integer.MAX_VALUE` si `isSuperAdmin(source)`. Y `ops_bypass_teleport_rules` no
tiene nada que ver con esto, es solo de los teleports.

**Este archivo no se puede editar con el servidor prendido**: el mod lo
reescribe al apagarse. Va con el servidor detenido, igual que
`EssentialCommands.properties` siempre.

### El único lector de saldo que hay en el servidor

La plata de EconomyCraft **no vive en ningún scoreboard** y ninguno de sus
comandos la devuelve a Brigadier: `bal` devuelve 1 siempre, y `eco removemoney`
y `pay` también devuelven 1 aunque no alcance
(`EconomyCommands.java:375` y `:515`). O sea que `execute store result` no sirve
para leer el saldo, y por eso el sistema de recompensas en plata se había
descartado.

Lo que sí existe es **el predicado `placeholder` de la `predicate-api`**, que
viene adentro del jar de Melius (`META-INF/jars/predicate-api-0.8.1+26.1.jar`).
Se registra solo si está la placeholder-api, que también viene ahí. Con eso:

```json
{"type": "more_or_equal",
 "value_1": {"type": "placeholder", "placeholder": "economycraft:balance"},
 "value_2": 50000}
```

Tres detalles que lo hacen funcionar, y sin los cuales no anda:

- **Va `balance` pelado y nunca `balance_formatted` ni `balance_short`.**
  `NumberPredicate` compara con `GenericObject.toNumber`, que hace
  `Double.parseDouble` del texto. `balance` es `String.valueOf(long)` sin
  separadores; los otros dos pasan por `formatMoney`, que mete los puntos de
  miles, y `Double.parseDouble("1.234.567")` explota. El `catch` devuelve
  entonces `success ? 1.0 : 0.0`, o sea **1**: el jugador rico quedaría
  bloqueado y el bug sería invisible.
- **`value_1` acepta un predicado anidado** porque `GenericObject.CODEC` es un
  `either(predicado, either(string, double))`.
- **Falla cerrado.** Si el placeholder dejara de existir, `toNumber` devuelve 0
  y nadie puede comprar una casa extra. Nadie se lleva una casa gratis.

### Por qué el cobro no puede vivir en un modificador

Un `execution_modifier` solo puede **bloquear antes** de que el comando corra:
`ContextChainMixin` prueba los `IsExecutableModifier` y, en el primero que
falla, corre su `failure` y devuelve sin ejecutar el original. **No existe
ningún modificador que corra algo después**, y el `onSuccess` corre *antes* del
comando y solo lo implementa `cooldown:set`. Así que "cobrale y después dejalo
pasar" no se puede armar.

La salida es interceptar `/home set` con un predicado `always_false` — que manda
todo al `failure` — y hacer el trabajo desde ahí. Son cinco modificadores:

| Archivo | Qué agarra | Qué hace |
|---|---|---|
| `casa-gratis.json` | `home set casa` | `sdp:casa_guardar`: guarda gratis |
| `casa-cobro.json` | `home set <otro>` | `sdp:casa_precio`: el cartel con el precio y el botón |
| `casa-sinnombre.json` | `home set` pelado | explica que hay que ponerle nombre |
| `casa-pagar.json` | el nodo `casa.pagar.nombre` | el portero de saldo |
| `casa-overwrite.json` | `essentialcommands.overwritehome` | lo tapa con operador 2 |

Los tres primeros son `command:regex` y no `node:*` **a propósito**: el matcher
de nodo no puede mirar el argumento, y toda la gracia es distinguir `casa` de
cualquier otro nombre sin guardar estado. `RegexCommandMatcher` hace
`context.getInput().matches(regex)`, que es match **completo** de la línea, así
que `home set (?!casa$)\S+` agarra `home set tienda` y no `home set casa`.

Ese match completo es además lo que evita el bucle: la función corre
`execute store success score #casa sdp_ok run essentialcommands overwritehome
$(nombre)`, y esa línea empieza con `execute`, así que no matchea ninguno de los
regex.

### Las tres trampas que costaron una vuelta cada una

- **`/essentialcommands overwritehome <nombre nuevo>` CREA la casa.**
  `HomeOverwriteCommand` hace `removeHome` y después `addHome`, así que sobre un
  nombre que no existe la crea igual, y suelto era la puerta gratis para las tres
  casas. Está tapado con `casa-overwrite.json`.
- **Va con operador 2 y no con operador 4**, que es lo que usa el resto de
  `modificadores/`. Los datapacks se parsean con una fuente de **nivel 2**, así
  que con operador 4 el nodo queda invisible al compilar y
  `sdp:casa_guardar` no carga: `Failed to load function sdp:casa_guardar ...
  Incorrect argument for command at position 63`. Con 2 la función compila y el
  jugador —que es nivel 0— sigue afuera.
- **`/home set` sin nombre guardaba una casa llamada `unnamed`.** Es
  `HomeSetCommand.runDefault`, y era una casa gratis más, porque el cobro se
  engancha al nombre. Lo tapa `casa-sinnombre.json`.

Y una que no es trampa pero conviene saber: **`HomeSetCommand` devuelve 0
siempre**, igual que `HomeOverwriteCommand`, así que `store result` no dice
nada. Lo que sí distingue es la excepción: `PlayerData.addHome` tira
`CommandSyntaxException` al pasarse del tope, y ahí `store success` queda en 0.
Por eso las funciones **guardan primero y cobran después, y solo si guardaron**:
al revés, el que ya tiene las tres casas pagaba 50.000 por nada.

### El permiso que faltaba

`essentialcommands.home.delete` no estaba otorgado, o sea que **`/home delete`
no le funcionaba a nadie**. Con el tope en tres es imprescindible: llegar al
tope sin poder borrar deja al jugador trabado para siempre. Borrar es gratis
aunque la casa se haya pagado.

### Nada de esto sirve para escapar de una pelea

`sdp:casa_guardar` y `sdp:casa_cobrar` arrancan con
`execute if entity @s[tag=sdp_combate] run return run function
sdp:combate_bloqueado`. El chequeo va **adentro de la función** y no se delega a
`combate.json`, porque el `failure` de un modificador corre como cualquier
acción: si la marca de pelea bloqueara el `/home set` por su lado,
`ContextChainMixin` ya habría corrido mi `failure` primero y la casa quedaría
guardada igual. El orden entre modificadores no está garantizado, así que el
candado tiene que estar donde se hace el trabajo.

## La marca de pelea

Quince segundos desde el último golpe entre jugadores en los que **no se puede
viajar**: `/home`, `/spawn`, `/back`, `/rtp`, `/tpa` y `/tpaccept` contestan "Estás
EN PELEA" y no hacen nada. Sin esto una pelea se termina cuando el que va
perdiendo escribe `/home`, y entonces no hay pelea: hay dos personas mirando
quién aprieta enter primero.

Son tres piezas y ninguna sabe de las otras dos:

1. **Quién está en pelea.** Dos advancements sin `display` (o sea invisibles) con
   `rewards.function`. Cada uno se dispara de un lado del golpe, porque el juego
   solo le da la recompensa al jugador del trigger y desde el JSON no hay forma
   de nombrar al otro:

   | Archivo | Trigger | A quién marca |
   |---|---|---|
   | `advancement/pegar.json` | `minecraft:player_hurt_entity` con `entity` = jugador | al que pegó |
   | `advancement/pegado.json` | `minecraft:entity_hurt_player` con `damage.source_entity` = jugador | al que le pegaron |

   Los dos llaman a `sdp:combate`, que se revoca a sí mismo para volver a
   escuchar (igual que `sdp:kill`) y pone `sdp_combate` en 300.

   Ojo con dos campos que no son iguales aunque se llamen parecido:
   `entity` de `player_hurt_entity` es un `ContextAwarePredicate` (**una lista**
   de condiciones de loot), y `damage.source_entity` es un `EntityPredicate`
   pelado (**un objeto**). Poner la lista donde va el objeto no carga.

   Y `source_entity` y no `direct_entity`: el juego compara `source_entity`
   contra `DamageSource.getEntity()`, que es el **dueño** del golpe. Con eso
   entran la flecha, el tridente y la poción; con `direct_entity` habría que
   nombrar cada proyectil.

   `"dealt": {"min": 0.1}` no es decoración: la perla de ender pega con daño
   **cero** (`ThrownEnderpearl.onHitEntity` llama a `hurt(..., 0.0f)`) y dispara
   el trigger igual. Sin ese filtro, tirarle una perla a alguien lo metía en
   pelea de arriba.

2. **El reloj.** El objetivo `sdp_combate` baja un tick por tick en `sdp:tick` y
   a los 300 se le saca la etiqueta. Solo corre para los conectados, así que
   desconectarse no lo acelera. Morir sí lo termina, y no hace falta una línea
   para eso: `ServerPlayer.restoreFrom` no copia las etiquetas, o sea que el que
   respawnea ya no la tiene.

3. **El candado.** `modificadores/combate.json` es un modificador de Melius de
   tipo `node:starts_with`, pero con `execution_modifiers` en vez de
   `requirement_modifier`: en vez de esconder el comando le agrega un predicado a
   la ejecución.

   ```json
   {"type": "predicate:add",
    "predicate": {"type": "negate",
                  "value": {"type": "entity",
                            "value": {"nbt": "{Tags:[\"sdp_combate\"]}"}}},
    "failure": [{"command": "function sdp:combate_bloqueado", ...}]}
   ```

   Trampas de este archivo, las tres medidas contra el servidor:

   - **`failure` NO es un componente de texto**, aunque lo parezca: es una lista
     de **acciones de comando**, las mismas de `executes`. Con `{"text": ...}` el
     log dice `Failed to parse either. First: Not a json array; Second: No key
     command`. Por eso el mensaje vive en `sdp:combate_bloqueado` y no en el JSON.
   - **`node:starts_with` compara por segmentos, no por letras.** El path de un
     comando es `dispatcher.getPath` unido con puntos (`/tpa Pepe` es
     `tpa.target_player`), y compara segmento contra segmento: **`tpa` no matchea
     `tpaccept`**. Hay que listar los dos.
   - El predicado de entidad no tiene `scores`: `EntityPredicate` en 26.1 no lo
     trae. Por eso el candado es una **etiqueta** leída por `nbt`, que sí está en
     el NBT del jugador. `NbtUtils.compareNbt` con listas hace "contiene", así que
     `{Tags:["sdp_combate"]}` matchea aunque el jugador tenga otras etiquetas.

   El modificador se aplica a **cualquier** comando, no solo a los de Melius:
   `ContextChainMixin` envuelve `ContextChain.runExecutable`, que es por donde
   pasa todo. Por eso alcanza para tapar los de Essential Commands.

### Lo que esta marca NO agarra

Está medido en el bytecode de 26.1 y conviene saberlo antes de que alguien lo
descubra jugando:

- **Los lobos.** `DamageSources.mobAttack(lobo)` deja al lobo como dueño del
  golpe, así que ninguno de los dos triggers pasa. Y el juego **sí** le da la
  kill al dueño (`resolvePlayerResponsibleForDamage` mira si es un lobo
  domesticado), o sea que se puede matar con lobos sin quedar nunca en pelea. Es
  el agujero más grande y desde el JSON no se puede tapar.
- **Los crystals en cadena.** Un crystal que rompe un jugador sí marca; uno que
  detona otra explosión no, porque ahí el `DamageSource` se queda sin entidad.
- **Reventar una cama o un respawn anchor.** `badRespawnPointExplosion` se
  construye con una posición y sin entidad.
- **El fuego, la lava, la caída y el vacío.** El golpe con Fire Aspect sí marca,
  pero si la víctima se muere quemada veinte segundos después ya nadie está en
  pelea. Igual con empujar a alguien a la lava: marca el empujón, no la muerte.
- **Desloguearse.** Salir del juego marcado no es un escape (la etiqueta se
  guarda en el NBT y vuelve con el jugador), pero tampoco cuesta nada. Si alguna
  vez molesta, la solución de los servidores grandes es matar al que se va
  marcado; acá todavía no está.
- El TNT **sí** marca a los dos: `Explosion.getIndirectSourceEntity` devuelve el
  `PrimedTnt.getOwner()`.

Si algún día se habilita `/tpahere` o `/warp`, hay que agregarlos a
`combate.json`: son las otras dos formas de salir de un lugar.

## El terror de las cuevas

**Server-Side Horror 4.2** (más `deimos`, que es su librería de configuración y no
viene embebida). Los dos son de solo servidor: `client_side: unsupported`, así que
**no van al pack del launcher** y nadie tiene que actualizar nada. Se sacan
moviéndolos a `/mods-apagados` y reiniciando.

Lo que hace: pasos que no son de nadie, alguien picando piedra en algún lado, un
sonido raro cada tanto y, muy de vez en cuando, una figura que aparece a lo lejos,
te mira y desaparece. La gracia es que el jugador nunca sabe si lo escuchó de
verdad.

Todo lo configura `configurar-horror.py`, y ahí está el porqué de cada número. Lo
que importa saber acá:

- **La probabilidad se cuenta en "1 en N por tick y por jugador".** Una hora de
  juego son 72.000 ticks, así que `veces por hora = 72000 / chance`. Hoy son unos
  cuatro ruidos por hora y una figura cada cuatro horas.
- **Los dados se tiran por jugador y no se elige uno al azar.** Las cinco
  tiradas viven en `ServerPlayerMixin`, inyectado en `ServerPlayer.tick()` con
  `@At("TAIL")`: cada conectado tira las suyas una vez por tick, y no hay ningún
  `getRandomPlayer()`. La tasa por jugador no se divide por cuánta gente hay.
  Tampoco hay filtro por operador, gamemode, dimensión, luz ni altura.
- **Pero todos los efectos son locales**, y eso es lo que hace creer que le pasa
  a uno solo: los sonidos salen con `level.playSound` y llegan a **16 bloques**,
  las partículas a 32. Con el borde en 12.000 nadie está a 16 bloques de nadie,
  así que nadie presencia el evento de otro y cada uno cuenta nada más que los
  propios. Si a los demás no les pasa nada, no es que el mod los saltee.
- **El 48% de los sonidos no los escucha nadie.** `playScarySound` tira la
  posición a un offset de -16 a +15 bloques en cada eje y el radio audible es 16:
  solo el 51,8% de las 32.768 combinaciones cae adentro. El radio está fijo en el
  mixin. Por eso `scary_sound_chance` es 50.000 y no 100.000: compensa la
  pérdida, no sube la frecuencia.
- **Quien va montado no recibe ninguna tirada.** El bucle de entidades saltea a
  quien tiene vehículo y le llama `rideTick()` en vez de `tick()`. A caballo, en
  bote o en vagoneta no pasa nada, nunca.
- **El jumpscare exige estar quieto tres ticks**, así que solo le toca a quien
  está construyendo, mirando un cofre o AFK. Y si el jugador se desconecta con
  uno pendiente se pierde: `TO_BE_JUMP_SCARED` guarda el `ServerPlayer` y al
  reconectarse es otro objeto con otro id.
- **`herobrine_starer` es el único que no se sube.** Cada aparición hace **dos
  pedidos HTTP sincrónicos a Mojang** en el hilo del servidor, sin caché ni
  timeout, y después recorre un cubo de radio 40 (531.441 candidatos) con
  raycasts. Y el `catch` de `getSkin` llama a `getSkin("MarsThePlanet_")`, que es
  el mismo nombre que estaba pidiendo: si Mojang no contesta, es recursión
  infinita hasta el StackOverflowError adentro de un tick. Acá se decía que la
  skin venía adentro del jar y que no salía a internet: **es falso.**
- Cuando el herobrine aparece, el nombre `MarsThePlanet_` entra en la lista de
  **TAB de todos** los conectados hasta 20 minutos, porque los paquetes van con
  `broadcastAll` sin filtro de distancia. Es la única prueba compartida de que el
  evento pasó.
- **El log no sirve para contar eventos.** De los cinco prendidos, el único
  `LOG.info` es cuando el herobrine no encuentra dónde pararse; los que salen
  bien no dejan rastro. "No aparece nada en el log" no distingue "no pasó" de
  "pasó bien".
- **`FurnaceBlockEntityMixin` corre siempre y no se puede apagar.** Se inyecta en
  el `serverTick` de todos los hornos, ahumadores y altos hornos, sin gate de
  config, y si el slot de arriba tiene bedrock, structure_void, structure_block,
  jigsaw o barrier, cambia bloques del mundo. Solo se desactiva si el mundo se
  llama "Renovating Villager Houses" o "Traps"; el nuestro se llama `world`.
- **De los 28 eventos que trae, quedan cinco prendidos.** El mod viene con la
  mitad activada, y varios acá serían griefing y no terror: te tira un rayo, te
  quema la casa, te apaga las antorchas, te pone trampas con vagoneta de TNT, te
  reaparece adentro de un dungeon. Con PvP libre y sin `keep_inventory`, cualquier
  cosa que te mate te cuesta el inventario entero.
- **La lista de sonidos de fábrica no sirve en un servidor de PvP.** Trae
  `entity.tnt.primed`, `entity.creeper.primed`, `entity.arrow.hit` y
  `item.crossbow.hit`: eso no da miedo, hace creer que te están atacando. La
  nuestra son sonidos de cueva ambiguos — el `ambient.cave` de siempre, el enderman
  mirándote y los del Warden y el sculk.
- **El mod no distingue una cueva de la superficie.** Tira los dados una vez por
  tick contra cada jugador, sin mirar dónde está: los pasos también suenan arriba
  a pleno día. Para que pase solo bajo tierra habría que hacerlo con el datapack,
  que ya tiene `sdp:tick`.
- **No tiene comando de recarga.** Probado: `deimos`, `deimosconfig` y
  `serversidehorror` no existen como comandos. Después de tocar la config hay que
  reiniciar el servidor.

Los otros tres mods que se habían mirado para esto — **Silent Caves, Shy Dweller y
Dynamic Sound Filters — no existen para 26.1**: se quedaron en 1.21.1. Y el eco y
los sonidos amortiguados de las cuevas ya los da **Sound Physics Remastered**, que
está en el pack desde el principio.

Lo que se descartó a propósito: cualquier *dweller* (Schizo Cave Dweller y Verity
Dweller sí están para 26.1). Un monstruo que te caza en un servidor donde perdés
todo al morir hace que la gente deje de bajar a minar.

Y **Enhanced Darkness** (cuevas negras de verdad, de cliente, MIT, 26.1) quedó
afuera por un motivo puntual: `/nv` lo anula por completo, y está gratis e
infinito en EXTRAS. Si alguna vez se quiere, primero hay que decidir qué pasa con
`/nv`.

## El resource pack del servidor

Hay cosas que **las escribe el cliente y no el servidor**, así que desde acá no se
pueden cambiar ni con un comando ni con un mod de servidor. La única llave es un
resource pack, y el servidor puede pedirle uno a cada jugador al entrar
(`resource-pack` en `server.properties`). Lo arma y lo publica
`generar-recursos.py`.

Hoy tiene una sola cosa adentro: **el cartel del spawn**. Cuando alguien intenta
romper un bloque adentro de los 16 bloques de `spawn-protection`, el servidor manda
`Component.translatable("build.spawn_protection")` y cada cliente lo traduce con su
idioma. El texto de fábrica no explica nada — el jugador nuevo no entiende por qué no
puede picar ni qué tiene que hacer. El nuestro dice qué es y cómo se sale.

**El rojo no se puede cambiar desde el pack.** En el bytecode de 26.1 el componente
se arma con `.withStyle(ChatFormatting.RED)` y eso está fijo en el servidor. Lo que
sí se puede es empezar el texto con un código `§` heredado, que el dibujante aplica
igual y pisa el rojo de ahí en adelante.

Dos cosas que hay que respetar al tocarlo:

- **Se aloja en una _prerelease_ de GitHub, y tiene que seguir siéndolo.** Si fuera
  una release normal pasaría a ser la "latest" del repositorio y rompería dos cosas
  de una: `releases/latest/download/SobrinosDePepe-win-Setup.exe`, que es el botón de
  descarga de la página, y `releases/latest/download/releases.win.json`, que es de
  donde el launcher lee si hay versión nueva. GitHub excluye las prereleases de
  "latest".
- **`require-resource-pack` queda en `false`.** Si un día GitHub no contesta, el
  jugador ve un aviso y entra igual. En `true` no entraría nadie.
- **El cartel de "¿querés descargar el pack?" lo saca el launcher, no el servidor.**
  Esa decisión se guarda **por servidor** en `servers.dat`, en el campo
  `acceptTextures`; no hay ninguna opción global en `options.txt`. Desde la 1.10.4 el
  launcher escribe ese campo en la entrada del servidor cada vez que se aprieta
  JUGAR, así que nadie tiene que contestar nada. Los servidores que la persona haya
  agregado por su cuenta se leen y se vuelven a escribir tal cual: a esos se les
  sigue preguntando, que es lo correcto.

El formato del `pack.mcmeta` va con `min_format` y `max_format` en **84**, que es el
`resource_major` de 26.1 (sale del `version.json` del jar). Sin `pack_format`: desde
el formato 82 ese campo dejó de estar permitido, igual que en el datapack.

Acá vivió un rato **Remade Cave Ambient**, un pack de terceros que cambiaba los 23
sonidos de cueva de vanilla. Se sacó el 2026-09-07 por pedido: un pack del servidor
se le aplica a todo el mundo arriba de lo que cada uno tenga puesto, y no da gusto
hacer eso con las texturas ajenas. El nuestro pisa una sola línea de texto.

## Los items del que muere duran diez minutos

Vanilla los borra a los cinco. `Age` cuenta hasta 6000 ticks y ahí el item se
descarta; arrancando en **-6000** la cuenta tarda 12000, que son diez minutos. El
valor mágico -32768 sería "no desaparece nunca".

La línea vive en `sdp:tick` y va **solo sobre lo que cayó al morir**, recorriendo
doce bloques alrededor del muerto. Sobre todos los items del mundo duplicaría las
entidades tiradas de cualquier granja, y no es lo que se quiso: la idea es darle al
que murió tiempo de volver a buscar sus cosas.

Corre en el tick siguiente a la muerte, que es cuando el criterio `deathCount` ya
subió: ahí el inventario ya se soltó y el jugador todavía no respawneó, así que
sigue parado donde murió.

## Las zonas protegidas

**Safe Zone 1.5.0** (`safe-zone`), `environment: server`: no va al pack del
launcher y nadie tiene que actualizar nada. Depende de fabric-api, que ya está.

Sirve para proteger una base: se eligen dos esquinas con una varita y toda la
columna —de la roca madre al cielo— queda irrompible. No se puede romper ni poner
un bloque adentro, ni con TNT, ni con fuego, ni tirando lava: el mod parchea la
explosión, el fuego, el fluido y el balde. Si la base está bajo tierra, no hay
forma de cavar para entrar.

Un datapack **no puede** hacer esto. No existe ninguna forma de cancelar que un
jugador rompa un bloque desde un datapack, por eso es un mod y no una función.

Cómo se usa, y es todo lo que hay que saber:

    /give <vos> minecraft:debug_stick[enchantments={vanishing_curse:1},custom_name={text:"Varita de zonas",color:"#ffb02e",italic:false}] 1
    click derecho en una esquina
    click derecho en la esquina opuesta (en un bloque DISTINTO)
    /clear <vos> minecraft:debug_stick          para guardarla

**La varita va con maldición de desaparición a propósito.** `keep_inventory` está
en `false`, así que sin eso al morir se te cae y la levanta el que te mató. Con la
maldición el ítem **se destruye al morir** y no llega al suelo. `/sz givewand` da
una sin la maldición, así que conviene el `give` de arriba.

Aunque alguien la consiguiera igual no podría hacer nada: su límite de zonas es 0,
y para tocar una zona ajena `hasWandAccess` pide ser el dueño, estar confiado o ser
operador. La maldición es para que ni siquiera aparezca la pregunta.

Para sacarla: click derecho adentro con la varita y confirmar, o `/sz remove`
parado adentro. `/sz list` las lista y `/sz info` cuenta la de donde estás parado.

### Dejar entrar a alguien

Una zona tiene un dueño y una lista de **confiados**, que pueden construir adentro
sin ser operadores. Las dos formas piden el id de la zona, que sale de `/sz list` o
de pararse adentro y tirar `/claim here`:

    /sz trust <id> <jugador>       lo agrega directo
    /sz untrust <id> <jugador>     lo saca
    /claim trust <id>              abre un menú de cofre para agregar y sacar

El menú es más cómodo si son varios. `/claim` está tapado por operador 4, así que
solo lo ve quien administra — pero funciona igual para él.

### Los tres candados, porque de fábrica esto era para todos

El mod viene como un sistema de reclamos **para cualquier jugador**, y así como
viene mataría el raideo, que es el alma de este servidor: el cartel de `/pvp` dice
"las bases NO están protegidas" y tiene que seguir siendo verdad. Lo cierran tres
cosas independientes, todas en `configurar-zonas.py`:

1. **`defaultMaxClaims` en 0.** El límite es por jugador y se chequea al crear la
   zona, así que con 0 nadie puede crear ninguna. La cuenta de administrador tiene
   un límite propio de 20, puesto con `/sz limits PEPE 20`, que se guarda en
   `player_limits.json` con el UUID de offline. **Este es el candado que importa.**
2. **`starterKitEnabled` en false.** Venía en **true**: le regalaba una varita a
   cada jugador nuevo al entrar. Al instalarlo el 2026-09-07 alcanzó a repartir
   cuatro antes de que se apagara — quedaron cuatro azadas de oro comunes por ahí,
   que ya no sirven de nada, y no se crearon zonas.
3. **La varita es el palo de depuración y no la azada de oro.**
   `ModItems.isClaimWand` mira **solo el tipo de ítem** (`stack.is(item)`), no el
   nombre ni ningún componente, así que con la varita de fábrica **cualquier azada
   de oro sirve** — y eso se craftea con dos lingotes y dos palos. El palo de
   depuración no tiene receta ni aparece en ningún cofre: la única forma de tenerlo
   es que un operador lo dé.

   **Ojo con el creativo:** el palo de depuración cambia estados de bloque cuando
   `canUseGameMasterBlocks()` da true, o sea siendo operador **y** en creativo. En
   supervivencia no hace nada por su cuenta y la varita anda bien.

### Lo que no da

- **Las zonas son solo del overworld.** `ClaimWandHandler` tiene un mensaje de
  validación para eso ("Safe zones only work in the Overworld").
- **Cada zona mide como máximo 128 x 128**, que es lo que quedó en
  `maxClaimWidth`/`maxClaimDepth`. Si una base no entra, se hacen dos pegadas.
- **Los dos clicks tienen que ser en bloques distintos.** Clickeando dos veces el
  mismo bloque sale una zona de 1x1, que es lo que pasó las dos primeras veces que
  se probó. No es un error del mod: `ProtectionListener.onUseBlock` filtra
  `MAIN_HAND`, así que el click dispara una sola vez y el primero guarda la esquina.
  La altura de los clicks no importa: la zona es la columna entera igual, y el `y`
  que queda en `claims.json` es solo donde se clickeó.
- **El aviso al jugador que intenta romper está en inglés:** "You cannot build
  here". Son strings de Java hardcodeados en `SafeZoneText` y no claves de
  traducción, así que **no se pueden cambiar con un resource pack**. Se cambiarían
  editando el jar, que es MIT y lo permite, pero habría que rehacerlo en cada
  actualización del mod.
- La protección **no impide entrar caminando** por una abertura que ya exista: lo
  que no se puede es abrir una nueva.

### Que no se vea

La idea es que las zonas protegidas sean algo que solo existe para el operador, así
que no alcanza con que nadie pueda crearlas: no tiene que haber ni rastro.

- **`/claim` y `/claims` están tapados** con `modificadores/safezone.json`, porque
  el mod los registra sin `requires` y cualquiera los veía en el autocompletado.
  `/sz` y `/safezone` ya venían con `requires(GAMEMASTERS)`.
- **Nadie recibe ningún mensaje del mod al entrar**, porque el kit inicial está
  apagado. El título "Claim wand ready" que salía era de eso.
- **Las cuatro azadas que alcanzó a repartir se sacaron** con
  `clear <jugador> minecraft:golden_hoe 1`. Una sola por jugador y no todas: la que
  daba el mod era una azada común, sin nombre ni componentes, indistinguible de una
  crafteada, así que sacando una queda neutro el regalo sin llevarse la que alguien
  se haya hecho. Fueron los cuatro de `starter_kit_recipients.json` — PEPE, Titit0N,
  Luquitas1410 y lanuerademari — y quedó auditado con `clear @a
  minecraft:golden_hoe 0`, que cuenta sin sacar: "No items were found on 4 players".
- Lo único que un jugador puede llegar a ver es **"You cannot build here"** si
  intenta romper adentro de una zona, y eso tiene que estar: si no, parecería que el
  juego se rompió. El operador recibe el aviso de que alguien lo intentó.

`createDataBackups` y `recoverFromBackupOnLoadFailure` van en true a propósito:
`claims.json` es la única copia de qué está protegido, y si se corrompe la base
queda abierta sin que nadie se entere. El `auditLogEnabled` deja en
`safe-zone_audit.log` quién creó o borró cada zona.

## Los permisos de LuckPerms

**El nivel con el que Essential Commands registra cada comando no importa.**
`ECPerms.check` hace, textual:

```java
Permissions.getPermissionValue(fuente, permiso)
    .orElse(fuente.permissions().hasPermission(
        new Permission.HasCommandLevel(PermissionLevel.byId(Math.max(2, nivel)))))
```

Ese `Math.max(2, nivel)` es la trampa. Con `use_permissions_api=true`, que es
como está, hay **dos estados y no tres**: o el nodo está otorgado en LuckPerms, o
el comando pide operador. Un comando declarado con nivel 0 **no** es "para
todos".

Y como el `requires` de Brigadier se evalúa al parsear, al jugador sin el permiso
el comando **ni le autocompleta**: escribirlo contesta "Unknown or incomplete
command". No hay ningún error que diga "te falta un permiso".

Hasta el 2026-09-06 el grupo `default` tenía siete nodos y **dos de ellos no
existen en el mod**:

- `essentialcommands.home` pelado. LuckPerms solo expande comodines que terminan
  en `.*`, así que no otorgaba `home.tp`. Lo que hacía andar `/home` eran los
  otros dos.
- `essentialcommands.rtp`. El literal `rtp` es un alias que pide
  `essentialcommands.randomteleport`.

Resultado: `/spawn`, `/back`, `/rtp` y `/nickname set` no le funcionaban a nadie
que no fuera operador. Es la mitad de "los comandos de EXTRAS no funcionan"; la
otra mitad está en `generar-menus.py`, en `escribir()`.

La lista viva está en `configurar-permisos.py`, con el porqué de cada uno y de
los que a propósito **no** se otorgan (`near.self` es un radar, `suicide` le saca
la kill al asesino, `enderchest` y `top` ya no existen).

## Los comandos de EconomyCraft y el nivel de operador

`PermissionCompat.gamemaster()` devuelve `true` cuando la fuente **no es un
jugador**, y si es un jugador exige estar en `ops.json`. **El nivel de operador
no le importa**, así que `op_level: 4` de Melius no sirve para nada acá.

Medido en el servidor, con un jugador sin OP:

| Cómo se llama a `eco addmoney` | Anda |
|---|---|
| Melius, `as_console: false`, `@s` | no |
| Melius, `as_console: true`, `@s` | no |
| Melius, `as_console: true`, nombre del jugador | no |
| Melius, `as_console: false`, nombre del jugador | no |
| función de datapack, línea estática, `@a[tag=...]` | **sí** |
| función de datapack, línea de macro, `@a[tag=...]` | **sí** |
| función de datapack, `@s` (estática o macro) | no |
| acción `command` de Inventory Menu con `as_player: false` | **sí** |

Dos reglas salen de ahí:

1. **Los comandos de EconomyCraft van adentro de una función de datapack o de
   una acción de Inventory Menu**, nunca en un comando de Melius. En los dos
   casos la fuente no tiene entidad de jugador y el chequeo pasa siempre.
2. **Nunca `@s`.** Al jugador se lo nombra con `@a[tag=...,limit=1]` en el
   datapack o con `%name%` en Inventory Menu. `@s` no resuelve porque las
   líneas de una función se parsean con una fuente sin entidad — y una macro
   también, aunque se ejecute más tarde.

Esto anda por un `catch` de `getPlayerOrException()`. Si algún día ReaZip lo
cambia por `return false`, todos los `eco` del datapack dejan de compilar de
golpe y en silencio.

## `comandos/` → `/config/melius-commands/commands/`

Son diez: `/ayuda`, `/comandos`, `/tienda`, `/economia`, `/casa`, `/pvp`,
`/extras`, `/shards`, `/nv` y `/nightvision`. Desde que los menús son GUI, casi
todos son una línea: abren un menú o llaman a una función.

Los hace **Melius Commands** y se recargan con `/reload`. El esquema completo de
un archivo son seis campos: `id`, `literals`, `arguments`, `require`, `executes`
y `redirect`.

**Cada ejecución necesita `"op_level": 4` explícito.** En Melius ese campo no
tiene valor por defecto: sin él el comando corre con el nivel del jugador, y
`function`, `tellraw` y `scoreboard` piden nivel 2. A un operador le funciona y
a un viewer no, y como `silent` **sí** viene en `true` por defecto, el error no
se ve en ninguna parte: al viewer simplemente no le aparece nada al apretar
enter. Es el tipo de falla que solo se nota si se prueba sin ser operador
(`deop`, probar, `op`).

`as_console` viene en `true` por defecto, y **no borra la entidad de la fuente**:
solo cambia a dónde van los mensajes.

Los archivos de `modificadores/` los sube el mismo script. Hasta el 2026-09-06
solo se bajaban con `respaldar.py` y se subían a mano, así que la copia del
repositorio era decorativa; ahora es la fuente y el servidor la copia.

## `datapack/data/sdp/menu/` → los menús de cofre

Los dibuja **Inventory Menu** (`inventory_menu-1.2.0.jar`), que es server-side
puro: `client_side: unsupported`, sin dependencias más que el loader y el juego,
así que **no hay que publicar nada en el launcher**. Los menús viven en el
datapack y se recargan con `/reload`.

`/menu <id>` lo puede usar cualquiera (`menu_command_permission: 0` en
`config/inventory-menu.json`).

Trampas:

- El tipo de item `navigate` **no se usa**: si se le pone un `model`, el mod
  reemplaza el stack entero y pierde el nombre y la descripción. Va `type:
  "item"` con la acción aparte.
- **El campo `action` acepta una lista y las corre en orden.** Se llama en
  singular pero su codec es `Action.LIST_CODEC` y `MenuElement.onClick` hace
  `Action.executeAll`. Eso es lo que arregla los botones que mandan un comando
  para completar al chat: con el cofre abierto **el chat se ve pero no se puede
  clickear**, porque la pantalla del contenedor se come los clicks, así que el
  botón parecía no hacer nada. Ahora `escribir()` devuelve dos acciones: el
  mensaje y `{"type": "navigate", "action": "close"}`.
- Los placeholders `%...%` **aplanan el texto y le borran el formato a los
  hijos**: `PlaceholderResolver` hace `getString()` y rearma todo como un
  literal. Por eso los nombres y las descripciones no llevan ninguno, y el saldo
  de shards se muestra en el cartel de la derecha en vez de en el menú.
- El item del menú **no se valida al cargar** (`DeferredItemStack` guarda el
  JSON crudo): si el id o un componente están mal, el menú carga igual y en el
  slot aparece una barrera que dice "Invalid menu item". Lo que sí se valida es
  la estructura del menú, y ahí el mod avisa con
  `Error while reading file resource: sdp:menu/<archivo>`.
- `action_cost` de tipo `score` es lo que cobra los shards: el mod verifica el
  score y lo descuenta él, así que no hay forma de comprar sin pagar.
- **Los encantamientos no se pueden poner en el item que dibuja el menú.** El
  mod resuelve el stack con `JsonOps` pelado, sin acceso a los registros, y
  desde 1.21 los encantamientos son datapack: el item revienta con
  `Can't access registry minecraft:enchantment` y en el slot aparece una barrera
  que dice "Invalid menu item". Los spawners y las pociones sí andan, porque el
  NBT del bloque y los efectos no pasan por un registro de datapack. Los ítems
  de la tienda llevan `enchantment_glint_override` para verse encantados y los
  encantamientos de verdad viajan solo en el `give`.
- **El mensaje de una acción `message` va como UN componente y sin saltos de
  línea.** Con un salto, el mod parte el texto y rearma cada pedazo como un
  literal, y se pierden el click y el color. Con una lista de componentes, el
  codec es un `xor` entre "lista" y "componente" y un array parsea como los dos:
  el menú entero no carga y el log dice "Both alternatives read successfully".

## `cartel/` → `/config/styled-sidebars/styles/`

El cartel de la derecha (**Styled Sidebars**). Se recarga con
`/styledsidebars reload`, **no** con `/reload`.

La forma es la del scoreboard clásico de DonutSMP: etiqueta a la izquierda,
valor a la derecha, un icono por fila. La alineación **no** se hace con
espacios: cuando una línea es un array de dos strings, el mod manda la parte
derecha como el "score" de la fila y el cliente la dibuja pegada al borde.

Trampas:

- **14 líneas visibles.** Si se pasa, el mod empieza a scrollear solo.
- Un array de **un** elemento no es una línea normal: el texto se va a la
  derecha y la izquierda queda vacía.
- Los degradés (`<gr:#aabbcc:#ddeeff>`) **no pueden envolver un placeholder**:
  necesitan texto fijo. El degradé va en el título.
- `%player:objective X%` no formatea los miles y, si el jugador no tiene score
  en ese objetivo, devuelve el literal `[Invalid objective!]`. Por eso
  `sdp:tick` corre `scoreboard players add @a Shards 0` y lo mismo con `Bounty`
  y `sdp_marca`: cualquier objetivo nuevo que se ponga en el cartel necesita su
  inicializador.
- Un solo archivo de estilo mal hecho **deja sin cartel a todos**: el loader
  captura solo `IOException`, así que un JSON inválido se escapa, apaga el mod
  entero y `/styledsidebars reload` responde en rojo.
- Los códigos `&a` no se interpretan, y `<hover>` y `<click>` parsean pero no
  hacen nada: la sidebar no es texto interactivo.
- **`%player:playtime%` sin argumento devuelve vacío** en la beta de
  placeholder-api que hay instalada, y `%player:statistic play_time%` se pasa a
  días con decimal a las 12 horas ("0.58 d"). El que sirve es
  `%player:playtime H'h' m'm'%`, con el patrón explícito.
- **El mod escribe cuatro estilos de ejemplo la primera vez que arranca**
  (`disable`, `pages`, `right_text`, `scrolling`) y `/sidebar <style>` los
  ofrece a cualquiera: quien escriba `/sidebar disable` se queda sin cartel para
  siempre, porque la elección se guarda por jugador y sobrevive el relogueo.
  Están borrados, y `modificadores/sidebar.json` deja el comando para nivel 4.
  `/sidebar` es una raíz aparte: el modificador de `styledsidebars` no la tapa.

Los colores de los números son los de Donut, que están triangulados entre dos
configs de plugins réplica y el muestreo de píxeles de una captura real: verde
`#00ff00` la plata, violeta `#a503fc` los shards, rojo `#ff0000` las kills,
naranja `#fc7703` las muertes, amarillo `#ffe600` el tiempo. Lo que **no** se
copia es su color de marca: el de Donut es azul `#00a6ff` sobre negro, y este
servidor es dorado.

## `datapack/` → `/world/datapacks/sobrinosdepepe/`

Las recompensas, los shards, la visión nocturna y los menús.

Cuando un jugador mata a otro, el advancement marca al asesino y la función de
tick cruza esa marca con quién murió: el asesino y la muerte ocurren en el mismo
tick pero sin un orden garantizado, así que se resuelven al final del tick en vez
de confiar en cuál pasa primero.

`pack.mcmeta` en 26.1 lleva **solo `min_format` y `max_format`, y el formato es
101**. Sale del `version.json` del propio juego: `data_major: 101,
data_minor: 1`. El archivo decía 81, que es el formato de 1.21.7/1.21.8.
Medido: a un datapack de mundo **ya habilitado** el juego no le revalida el
formato, así que con 81 andaba igual — pero si algún día hay que rearmar el
mundo, con el número viejo el pack podría no cargar. Desde el formato 82 en
adelante `pack_format` y `supported_formats` **no están permitidos** y hay que
borrarlos.

También vive acá la visión nocturna (`/nv`). El mismo comando la prende y la
apaga: `nv.mcfunction` corta con `return run` antes de la segunda línea, porque
si no, apagarla dejaría la marca en cero y la línea siguiente la volvería a
prender en el mismo tick. La marca guarda la intención del jugador y la función
de tick repone el efecto a quien lo perdió al morir, mirando el predicado
`tiene_nv` para no reaplicarlo cada tick a quien ya lo tiene.

## El mod cliente de precios

`mod-precios/` dibuja "Precio: $N" en la descripción de cada item, y **los
precios viajan adentro del jar**. Si cambian los precios del servidor y no se
rearma el mod, los cartelitos mienten.

`generar-precios.py` lo rearma solo y **no hace falta compilar nada**:
`precios.json` es un recurso del jar y se reemplaza dentro del zip, dejando la
clase intacta. Eso importa porque para compilarlo hace falta un JDK 25 y en esta
PC no hay ninguno (solo un JRE 17 y el JRE 25 que baja el launcher).

El jar nuevo queda en `mod-precios/build/libs/` y **hay que publicarlo desde el
panel** para que llegue a los jugadores. Eso los obliga a actualizar, así que
conviene hacerlo cuando no haya nadie jugando.

## Un error conocido en el log

Al recargar o cuando entra alguien, el log escupe varios
`WrongMethodTypeException` desde `eu.pb4.predicate`. Es un bug de copiar y
pegar en `FabricPermissionBridge` de la `predicate-api 0.8.1`: su
`findCheckPermission()` asigna **siempre** a `permissionCheckCallCommandLevel`,
sin importar qué método buscó, así que después del bloque estático ese campo
apunta a `checkPermission(Identifier, boolean)` y cualquier llamada con un
`PermissionLevel` explota. El `catch` lo agarra, imprime el stack trace y cae a
LuckPerms, así que la respuesta sale bien y es solo ruido.

La consecuencia práctica: un predicado `{"type": "permission"}` **con** el campo
`operator` pasa por el camino roto. Sin ese campo usa la otra sobrecarga y no
explota. Aun así conviene usar `{"type": "operator", "operator": 4}`, que es lo
que ya está probado acá y no toca la librería.

No hay versión más nueva de Melius ni de Styled Sidebars para 26.1, así que se
resolvió por afuera: `modificadores/styledsidebars.json` reemplaza el requisito
de ese comando por `{"type": "operator", "operator": 4}`.

Lo peor de esto es cómo se veía: como el chequeo cortaba por lo alto cuando el
jugador era operador, a quien administra el servidor le funcionaba todo y a los
demás no les aparecía nada, sin ningún error visible en el juego.

## Comandos escondidos

`modificadores/` tiene un archivo por comando que se le tapa a los jugadores.
Todos hacen lo mismo: le reemplazan el requisito al nodo por operador 4, así que
el comando sigue existiendo pero no aparece ni autocompleta para quien no es op.
Se aplican con `/reload`.

| Comando | Por qué |
|---|---|
| `clear` | Le vacía el inventario a quien lo escribe, y el nombre invita a pensar que limpia el chat. Un operador todavía puede borrarse el inventario: para eso no hay red de contención. |
| `sidebar`, `styledsidebars` | Cualquiera podía escribir `/sidebar disable` y quedarse sin cartel para siempre. |
| `warp` | No hay ningún lugar creado, así que solo podía fallar. Se destapa borrando el archivo cuando existan. |
| `workbench`, `anvil`, `stonecutter` | Mesas portátiles. La idea es que cada uno tenga su mesa de crafteo de verdad, no llevarla en el bolsillo. |
| `essentialcommands overwritehome` | Suelto es la puerta gratis para crear casas: `HomeOverwriteCommand` hace `removeHome` y después `addHome`, así que sobre un nombre que no existe la crea igual. Va con **operador 2** y no con 4, porque los datapacks se parsean con una fuente de nivel 2 y con 4 la función `sdp:casa_guardar` no compila. Ver "Lo que sale la casa". |
| `claim`, `claims` | Los comandos de jugador de Safe Zone. **No traen `requires`**: sin este archivo los ve y los puede tipear cualquiera, y las zonas protegidas dejan de ser algo que solo existe para el operador. `/sz` y `/safezone` no hacen falta acá: esos sí se registran con `requires(GAMEMASTERS)`. |

Y `combate.json`, que es de otra clase: no reemplaza el requisito del nodo sino
que le agrega un predicado a la **ejecución**. Está explicado abajo, en "La marca
de pelea".

`/clearchat` existió y se sacó: eran sesenta líneas vacías y no lo usaba nadie.

## Comandos que se sacaron enteros

No con un modificador sino apagándolos en `/config/EssentialCommands.properties`,
que es lo que hace que el nodo **no exista**: `help top` contesta "Unknown
command". El archivo lo baja `respaldar.py` a `esenciales/`, y **no se puede
editar con el servidor prendido**: el mod lo reescribe al apagarse.

| Comando | Clave | Por qué |
|---|---|---|
| `/top` | `enable_top=false` | Está roto. `TopCommand.getTop` busca desde arriba tres bloques de aire seguidos, así que en el Nether te deja arriba del techo de bedrock y en una cueva te sube al primer hueco que encuentra, no a la superficie. |
| `/enderchest` | `enable_enderchest=false` | El cofre de ender es la única caja fuerte del servidor: lo que guardás ahí no se pierde al morir. Poder abrirlo desde cualquier lado, en medio de una pelea, lo convierte en un inventario infinito e inrobable. El bloque sigue existiendo y sigue sirviendo. |

## Otras cosas de 26.1 que ya nos costaron tiempo

- **Los símbolos van como `\uXXXX` dentro del JSON.** Una barra invertida suelta
  en un heredoc del shell termina siendo un salto de línea real adentro del
  comando y lo parte al medio; y un símbolo literal escrito en un heredoc **no
  llega igual** del otro lado. Los scripts los escriben con `chr()` y los
  `.mcfunction` quedan en ASCII puro.
- **Las gamerules son `snake_case`.** `max_command_sequence_length`,
  `keep_inventory`, `advance_time`, `send_command_feedback`, `show_death_messages`.
- **`schedule` acepta `t`, `s` y `d`, no `m`.** Diez minutos son `12000t`.
- **`minecraft.custom:minecraft.play_one_minute` no existe** desde 1.17. Es
  `play_time`, y cuenta en ticks: medido, sube 604 en 30 segundos.
- **Un objetivo con criterio de estadística arranca en cero** cuando se crea y
  suma desde ahí; no se le puede resetear el total, porque el juego le vuelve a
  escribir el valor absoluto en cuanto la estadística cambia.
- **`execute store result` con `data get` trunca hacia abajo**, no redondea.
- **Los componentes de item se validan al parsear** el comando, pero el NBT de
  adentro de `block_entity_data` no. Medido: un componente inexistente o un
  encantamiento inexistente dan error, pero
  `spawner[block_entity_data={id:"minecraft:chest"}]` pasa sin chistar.
- **El spawner con mob adentro** es
  `spawner[block_entity_data={id:"minecraft:mob_spawner",SpawnData:{entity:{id:"minecraft:skeleton"}}}]`.
  Verificado poniendo el bloque y leyéndolo: el juego completa `SpawnPotentials`
  solo.
- **`enchantments` ya no lleva el envoltorio `levels`**: es
  `enchantments={sharpness:5}` directo. `unbreakable={}` tampoco lleva
  `show_in_tooltip`.
- **En `tellraw` los eventos son `click_event` / `hover_event`** con `command` /
  `value`, y el slot del scoreboard es `below_name`, no `belowName`.
- **`/reload` recarga los datapacks, los menús de cofre y los comandos de
  Melius, pero NO el cartel** (`/styledsidebars reload`) **ni la configuración
  de EconomyCraft**. EconomyCraft no tiene comando de reload, pero sí un botón:
  el reloj **"Reload from disk"** del `/eco admin` (slot 16) hace
  `EconomyConfig.load` + `prices.reload` + `applyRuntimeSettings` y aplica
  `config.json` y `prices.json` sin reiniciar. Lo tiene que apretar alguien con
  op, porque `/eco admin` pide `gamemaster`.
- **El borde del mundo es POR DIMENSIÓN.** `WorldBorderCommand` trabaja siempre
  sobre `source.getLevel().getWorldBorder()`, y cada nivel guarda el suyo aparte
  como SavedData `minecraft:world_border` en su propia carpeta `data/`. Un
  `/worldborder set` suelto en la consola configura **solamente el overworld**.
  Van los tres con `execute in <dimensión>`, que es lo que hace
  `configurar-borde.py`.
- **El `warning time` del borde va en ticks**, aunque el comando conteste en
  segundos: con `10` el servidor respondió "0.50 second(s)". El default de
  vanilla son 300, que son los 15 segundos de siempre.
- **`level.dat` cambió de forma en 26.1.** El spawn ya no son `SpawnX`/`SpawnY`/
  `SpawnZ`: es un compound `spawn` con `pos` como array de tres enteros, más
  `pitch`, `yaw` y `dimension`. La dificultad también se mudó adentro de
  `difficulty_settings`.
- **El log dice `[EconomyCraft] Dynamic prices: ... multiplier 2.31x` aunque
  estén apagados.** Es ruido: el motor calcula y loguea siempre, pero
  `isDynamicPricingActive` exige `dynamicPricesEnabled`, que está en `false`, y
  sin eso `getEffectiveBuyPrice` devuelve el precio base sin tocar.
- **`dailySellLimit` es todo-o-nada por operación**, no un tope que se llena: si
  al jugador le quedan 500 de margen y quiere vender algo de 600, se le rechaza
  la venta completa. Por eso está en 250.000 y no en 10.000: con los precios
  nuevos un ingot de netherita vale 17.500 y con el límite viejo no se podía
  vender ni uno.
- **`scoreboard_enabled` de EconomyCraft tiene que quedar en `false`**: si se
  prende, el mod crea el objetivo `eco_balance` y se apropia del lugar del
  cartel. Y al volver a apagarlo **borra** ese objetivo del mundo.
- **26.1 movió las carpetas del mundo**: `data/scoreboard.dat` pasó a
  `data/minecraft/scoreboard.dat`, `stats/` a `players/stats/`, `playerdata/` a
  `players/data/`.
- **`balances.json` se escribe asincrónico** desde un mapa en memoria: editarlo a
  mano con el servidor prendido no sirve, el próximo guardado lo pisa.
- **Vender está bloqueado en silencio por daño y por contenido**: cualquier
  herramienta usada y cualquier shulker o bundle con cosas adentro no se pueden
  vender. Al jugador le va a parecer un bug del servidor y no una regla.

## Cómo verificar que algo quedó bien

No alcanza con que el servidor arranque.

1. `mc.read("/logs/latest.log")` después de cada cambio, buscando excepciones.
2. **Probar sin ser operador.** Es la falla que más se nos escapó. Con un
   jugador conectado que no sea OP, `execute as <jugador> run <comando>` desde
   la consola reproduce exactamente las condiciones de Melius: la fuente queda
   con la entidad de ese jugador.
3. Para saber si un comando se ejecuta y dónde muere, intercalar
   `scoreboard players set @s <objetivo> <n>` entre las líneas y leer el score
   después: dice exactamente hasta dónde llegó. Si el score queda **sin
   asignar** en vez de en cero, el comando no llegó ni a parsear.
4. `python servidor/verificar-precios.py` después de tocar precios.
5. Correr `servidor/respaldar.py` y commitear.
