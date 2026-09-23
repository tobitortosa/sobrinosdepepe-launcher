# Lo que vive en el servidor

Copia de respaldo de la configuración que hoy solo existe dentro del servidor de
Minehost. Si el mundo se pierde o hay que rearmar el servidor, esto se vuelve a
subir tal cual y todo queda como estaba.

| Script | Para qué |
|---|---|
| `respaldar.py` | Baja del servidor todo lo que mantenemos nosotros. |
| `subir-datapack.py` | Sube el datapack entero (funciones, advancements, menús) y recarga. |
| `subir-acceso.py` | Sube el mod de acceso y su config: el candado (`REQUIRE_LAUNCHER`), el secreto y el link. Con el candado puesto, al servidor se entra solo con el launcher. |
| `subir-cuadros.py` | Deja en `/mods` el jar de My Photo Paintings, el mod de colgar imágenes propias como cuadros. |
| `subir-duelos.py` | Sube el mod del coliseo: `/pvp <jugador>`, la arena, las apuestas y el torneo. |
| `subir-equipos.py` | Deja en `/mods` los dos jars de los equipos: el nuestro, que es el comando `/equipo`, y team-only-locator-bar, que deja la barra de arriba mostrando solo a los compañeros. |
| `generar-precios.py` | Arma `prices.json` y `config.json` de EconomyCraft, rearma el mod cliente de precios y verifica que no haya plata infinita. |
| `verificar-precios.py` | Solo la verificación, contra el servidor o contra un archivo. |
| `generar-menus.py` | Arma los menús de cofre (los deja en el datapack). |
| `generar-comandos.py` | Arma los comandos propios de Melius, sube también los modificadores y recarga. |
| `generar-cartel.py` | Arma el cartel de la derecha. |
| `configurar-scoreboard.py` | Rehace los objetivos y los lugares del scoreboard, que el juego guarda dentro del mundo. |
| `configurar-borde.py` | Pone el borde del mundo (`WORLD_BORDER_RADIUS`, bloques a cada lado del spawn), igual en las tres dimensiones. Se niega a achicarlo salvo con `--achicar`. |
| `configurar-permisos.py` | Le da al grupo `default` de LuckPerms los permisos de los comandos que la guía promete. |
| `configurar-horror.py` | Deja Server-Side Horror en modo "una pizca": ruidos y nada que toque el mundo. |
| `generar-recursos.py` | Arma el resource pack propio del servidor, lo publica en GitHub y apunta el servidor. |
| `configurar-zonas.py` | Deja Safe Zone como herramienta de administrador: nadie más puede proteger zonas. |
| `configurar-netherite.py` | Prende y apaga la restricción de la netherita (`RESTRICT_NETHERITE`): crafteable con las doce recetas de smithing, o solo desde la tienda de shards. |
| `ocultar-coordenadas.py` | Apaga `print_teleport_coordinates`: el chat deja de decir a qué coordenadas te llevó `/home`, `/warp`, `/rtp`, `/back` o un `/tpa`. Para poder streamear sin regalar dónde vivís. Apaga el servidor un minuto. |
| `ajustar-saldos.py` | Deja el saldo de cada uno en proporción a las horas jugadas. |
| `ver-cofre.py` | Muestra el cofre de ender (y con `--todo` la mochila) de cualquier jugador, aunque esté baneado y no se pueda conectar. Lee el archivo del jugador; no es un comando del juego y nadie más lo ve. |
| `subir-cofres.py` | Sube el mod de `/cofre <jugador>`, que abre ese mismo cofre de ender adentro del juego y deja sacar y meter cosas. Solo para operadores; al cerrar la ventana reescribe el archivo del jugador. |
| `configurar-auth.py` | Deja EasyAuth como lo queremos: al servidor se entra con contraseña. |
| `subir-lobby.py` | Sube las dos partes del lobby de una: el mod que manda a todos ahí, y los dos ajustes de EasyAuth. También borra del datapack las dimensiones viejas. |
| `respaldar-jugadores.py` | Baja a esta PC los `.dat` de todos los jugadores y los JSON de estado, y avisa a quién le quedó el inventario vacío. Lo que el plan de Minehost no cubre: el backup del panel es uno solo. |
| `mudar-al-overworld.py` | Copia el lobby y el coliseo al overworld, a 500.000 bloques del spawn, con `/clone from ... to ...`, y después compara bloque por bloque que la copia haya quedado idéntica. Con `--arena` le mueve al mod de duelos las coordenadas de la arena. |
| `ver-motd.py` | Muestra el cartel del servidor tal como lo ve el que lo agrega en Minecraft: le hace el mismo saludo que el cliente (Server List Ping) y dice el texto sin códigos, los colores y el ancho. Leer `server.properties` no alcanza: los `§` no se ven, las tildes pueden salir rotas y el cliente corta lo que se pasa de ancho. |
| `probar-tick.py` | Le pregunta al servidor, con muñecos puestos en puntos elegidos, si los selectores de `sdp:tick` agarran a quien tienen que agarrar. Esas líneas corren sobre `@a` veinte veces por segundo: un volumen mal escrito no falla, teletransporta gente. |
| `region.py` | Lee los archivos de región (`.mca`) y dice qué bloques hay adentro: la caja que ocupa lo construido y el conteo. Es lo que hace verificable una mudanza. |
| `estilo.py` | Los colores y los símbolos, en un solo lugar. |

Las credenciales salen de `web/.env.local`, que no está en el repositorio.

Nada de esto se instala en las máquinas de los jugadores: son mods y datapacks
de servidor, así que se aplican sin publicar una versión nueva del launcher. Las
excepciones son los dos mods nuestros que también van del lado del cliente: el
de precios (ver más abajo) y el de acceso, que es el que exige el launcher para
entrar — ese va en los dos lados y su orden de publicación está en el encabezado
de `subir-acceso.py`.

## Al servidor se entra con contraseña

Desde el 2026-09-21 la puerta está abierta: se promociona la IP y entra cualquiera,
con el Minecraft pelado y sin el launcher. Eso deja un agujero que antes no existía.
El servidor es **offline-mode**, o sea que el nombre no lo verifica nadie: cualquiera
escribe `Chichon` y entra como Chichon, con su casa y su plata. Mientras el candado
estuvo puesto no importaba, porque el permiso lo firmaba el backend.

Lo tapa **EasyAuth** (`easyauth-mc26.1-3.4.4.jar`, `environment: server`). Se
configura con `configurar-auth.py`, que explica en su encabezado cómo se ve desde
adentro del juego y qué quedó apagado a propósito.

**Nadie pierde nada al registrarse**, y esto es lo primero que va a preguntar todo el
mundo. El inventario, la plata, los shards, las casas y los avances cuelgan del UUID,
y el UUID sale del nombre (`OfflinePlayer:<nombre>`, v3), igual que siempre. Las dos
únicas opciones capaces de cambiarlo —`premium-auto-login` y `forced-offline-uuid`—
quedaron las dos en `false`.

**Lo que sí se puede perder es el nombre.** El primero que escribe `/register` con un
nombre se queda con él, y como la puerta se abrió antes de que esto estuviera puesto,
hubo un rato en que cualquiera pudo haber entrado como cualquiera. Si alguien
aparece diciendo que su cuenta ya estaba registrada y no fue él, se arregla con
`/auth unregister <nombre>` y que se registre de nuevo, pero conviene mirar el log
antes: `log-player-registration` quedó en `true` justo para eso.

Las contraseñas viven hasheadas en un SQLite del propio mod
(`/config/EasyAuth/EasyAuth/easyauth.db`) y **no las respalda ningún script**. Si ese
archivo se pierde, cada uno se vuelve a registrar —y el nombre vuelve a quedar libre
para el primero que llegue.

## El lobby

Desde el 2026-09-22 **nadie aparece en su casa al entrar**: todos caen en el lobby,
vengan de donde vengan, entren con el launcher o sin él, **con el inventario vacío
y una perla en la mano**. Click derecho a la perla y se abre el menú de juegos, que
hoy tiene un solo modo: SURVIVAL SMP. Al elegirlo aparecés **exactamente donde
estabas y con todo lo tuyo**: misma dimensión, misma posición, mismo inventario,
misma vida y misma experiencia. `/lobby` te trae de vuelta cuando quieras, salvo
que estés en combate.

Las cosas del Survival quedan en el Survival: el lobby está vacío a propósito. No
se pierde nada — el inventario completo (los 41 casilleros, o sea también la
armadura puesta y la mano de atrás), la vida, la comida y la experiencia se guardan
en `config/lobby-de-pepe.json` antes de tocarle nada, y vuelven al salir. Es la
misma clase `Guardado` que el mod de duelos usa para la arena, copiada: son dos
mods sueltos y el código que mueve inventarios ajenos es el último que conviene
acoplar de apuro.

**Si la copia no se puede escribir, al jugador no se le vacía nada** y se le avisa
en el chat. Que el lobby quede feo con alguien vestido de netherita es
infinitamente mejor que perderle el equipo.

El lobby es un lugar chico y construido (62×37×61 bloques, un patio rodeado de
edificios) que flota en el **overworld**, en `x=500.000, y=250, z=0`: a medio
millón de bloques del spawn y por encima de las nubes, que quedan a y=192. El
coliseo está igual de lejos, en `x=501.145, y=250, z=11.590`.

No hay proxy ni un segundo servidor: DonutSMP reparte su overworld en seis
proxies geográficos porque tiene decenas de miles de jugadores, y nosotros
tenemos un contenedor que ya se cae por RAM.

**Y tampoco son dimensiones propias, desde el 2026-09-22.** Eso es lo importante
de esta parte y está explicado abajo, en «El bug que costó la mudanza».

Sube el mod y los ajustes `subir-lobby.py`. El lugar en sí no se sube: los
chunks ya están adentro del mundo de siempre, y si alguna vez hay que moverlos,
los copia `mudar-al-overworld.py`.

### Por qué hay un mod y no alcanza la config

`mod-lobby` es el que manda a todos al lobby, anota dónde estaban y qué tenían, y
los devuelve. Eso no lo puede hacer ninguna opción de EasyAuth: **Minecraft guarda
una sola posición y un solo inventario por jugador**, los de cuando se desconectó,
así que si lo movemos al lobby sin anotar antes, el Survival arrancaría en el spawn
y el inventario del lobby sería el de verdad. Lo anotado se escribe en cada
anotación y no al apagar, porque el servidor se cae solo por RAM cada tanto.

**El teleport va en el tick siguiente al login, no dentro del evento.** Mover al
jugador mientras el login sigue en curso es pedir problemas; se arregla con
`servidor.execute(...)`, que corre la tarea cuando el tick actual terminó.

### El bug que costó la mudanza

Hasta el 2026-09-22 el lobby era la dimensión `sdp:lobby` y el coliseo
`sdp:coliseo`. El que se desconectaba adentro de una, al volver a entrar, se
quedaba cargando y aparecía con el mapa entero vacío. En el log:

```
Force-added player with duplicate UUID 7a067f19-...
UUID of added entity already exists: ServerPlayer['PEPE'/1138, ...]
```

Son dos altas de la misma entidad: la segunda se rechaza, el jugador nunca entra
de verdad al nivel y el cliente se queda sin chunks. El UUID queda sucio en el
`knownUuids` del nivel porque al desconectarse la entidad se remueve con un motivo
que no la destruye, y en una dimensión que nadie más tiene cargada eso no se
limpia hasta que el servidor reinicia.

Se probaron las tres formas posibles de taparlo y **las tres fallan**:

1. Sacarlo del lobby en el `DISCONNECT`: ese evento corre en el hilo de red y
   **después** de que el servidor escribió el archivo del jugador. Esto le borró
   el inventario a dos jugadores de verdad.
2. No tocarlo al salir: queda guardado adentro y se duplica al reconectar.
3. Sacarlo en el `LEAVE`, que sí corre en el hilo del servidor y antes del
   guardado: el teleport durante la desconexión igual deja el UUID colgado en el
   otro nivel.

El diagnóstico es que `Force-added player` aparece **solo en el login**, nunca en
un teleport: los `/tp` entre dimensiones andan perfecto. Lo que rompe es que
existan dos niveles que puedan discutir de quién es el UUID del jugador.

La salida fue **sacar el segundo nivel**: el lobby y el coliseo se mudaron al
overworld, lejísimos, y las dos dimensiones se borraron. Ahora «estás en el
lobby» es una caja de coordenadas, en el mod y en el datapack. Y **al que se
desconecta no se le toca nada**, que es la regla que quedó de todo esto.

### `restart` con el servidor apagado no lo prende

`mc.power("restart")` sobre un servidor que ya está en `offline` **no hace nada**: lo
deja apagado y el script sigue adelante como si hubiera arrancado. Pasó el
2026-09-22 después de apagarlo a mano para editar la config de la arena; el servidor
quedó caído y lo que se leyó del log era del arranque anterior.

Los scripts que suben algo miran el estado antes y usan `start` o `restart` según
corresponda. Y la verificación de después **tiene que esperar a que el estado sea
`running`**, no leer el log de una: si no, se lee el del arranque viejo y todo parece
bien.

### El servidor no se duerme, y por qué

`pause-when-empty-seconds` está en **0**, o sea apagado. Venía en 60: el servidor
vanilla deja de tickear cuando lleva un minuto sin nadie adentro, para no gastar
CPU al pedo.

El 2026-09-22 eso dejó un login colgado **ocho minutos**. En el log se ve el
silencio entero entre la conexión y el `logged in`, y después:

```
User 7a067f19... doesn't currently have data pre-loaded - denying login.
PEPE lost connection: Disconnected
PEPE left the game
PEPE joined the game          <- el joined DESPUÉS del left
handleDisconnection() called twice
```

LuckPerms precarga los permisos del que está entrando y los descarta si el login
tarda demasiado; cuando por fin entra, ya no los tiene y lo rechaza. Al jugador le
aparece un cartel rojo largo de error de permisos, y en el servidor queda un
**jugador fantasma**: `list` lo muestra adentro, `kick` no lo saca, y ocupa el
nombre así que el de verdad no puede entrar. Se limpia reiniciando.

Lo que **no** era, aunque lo parecía: generación de terreno. Las dos zonas nuevas
están a 500.000 del spawn y es lo primero que uno piensa, pero los archivos de
región guardan la hora de cada chunk y dicen que durante esos ocho minutos **no se
escribió ni un chunk**. Los 1.860 chunks que aparecen después son del fantasma
parado en el lobby, y son los que cualquier jugador carga al pisar terreno nuevo:
para entregar los 441 de `view-distance=10` hay que tocar unos 1.850 en las etapas
intermedias. `region.py` tiene la función `cuando()` para volver a preguntarle esto
al servidor.

Con 20 jugadores el servidor casi nunca queda un minuto vacío, así que esto se ve
sobre todo probando solo. Igual no vale la pena: lo que ahorra es CPU de un
servidor que no está haciendo nada.

### El borde del mundo, después de la mudanza

Afuera del borde el juego daña y empuja, así que con el lobby a 500.000 el borde
del overworld tuvo que irse a 30.000.000. Como en 26.1 **el borde es por
dimensión**, el Nether y el End conservan la pared de siempre.

El límite del survival no desapareció: lo hace `sdp:tick`, que devuelve al que se
mete en la banda que va de 15.020 a 400.000 del spawn. Más allá de los 400.000
están el lobby y el coliseo, y para llegar caminando hay que cruzar la banda
primero. `WORLD_BORDER_RADIUS` sigue siendo la única palanca, y
`configurar-borde.py` verifica que el datapack diga el mismo número.

**Los ids de los menús llevan namespace.** La perla abre `menu sdp:juegos`, no
`menu juegos`: sin el prefijo, Inventory Menu contesta *"the menu doesn't exist"*.
Es el mismo formato que usan los comandos de Melius y los botones de volver.

Por eso mismo **`hide-player-coords` quedó en `false`**. Prendido hace casi lo
mismo que el mod, pero los dos enganchan el mismo momento del login y el orden
entre mods no está definido: si EasyAuth mueve al jugador primero, el mod anota el
lobby como "donde estaba" y el `/survival` lo dejaría encerrado ahí. Una sola cosa
mueve al jugador, y es el mod.

El punto donde se cae está en `LobbyServidor` (0.5, 0.0, 0.5) y en `SPAWN` de
`subir-lobby.py`. Si se mueve, se mueve en los dos lados.

### El día que el lobby borró inventarios (2026-09-22)

Dos jugadores entraron y aparecieron **sin nada**. La causa, y la regla que sale
de acá:

> **Al jugador que se desconecta no se le toca nada.**

El mod tenía un `ServerPlayConnectionEvents.DISCONNECT` que le devolvía sus cosas
al que se iba desde el lobby, para que su archivo no quedara guardado adentro de
la dimensión del lobby. Ese evento **corre en el hilo de red**, no en el del servidor — en el
log se ve clarísimo, dice `[Netty Epoll IO #56]` en vez de `[Server thread]` — y
para cuando corre, el servidor **ya escribió el archivo del jugador**, vacío,
porque en el lobby está vacío. O sea que le devolvíamos el inventario a una copia
en memoria que ya nadie iba a guardar, y encima **borrábamos la copia buena del
JSON**. El jugador entraba sin nada y no quedaba de dónde sacarlo.

Ahora la copia se consume en **un solo lugar**, siempre con el jugador adentro del
juego y en el hilo del servidor: al entrar o al apretar SURVIVAL. Una sola puerta
de salida es lo que hace imposible perderlo y también duplicarlo.

Y hay un seguro más, puesto en la mudanza: **anotar nunca pisa una copia que ya
existe**. Si hay una copia sin devolver, lo que el jugador tenga encima no son sus
cosas —es la perla del lobby o un kit prestado del coliseo— así que anotarlo sería
cambiarle el equipo por eso. Si eso salta en el log, se arregla el orden de
`alEntrar`; no se saca el seguro.

Cómo encontrar los afectados si vuelve a pasar algo así: el mod dejaba una línea
por caso, y los logs viejos están comprimidos en `/logs/*.log.gz`. Fueron dos en
99 archivos de log.

**Lo que no se pudo recuperar:** Minecraft guarda solo dos copias del jugador,
`<uuid>.dat` y `<uuid>.dat_old`, y cada entrar-y-salir corre una sobre la otra. Al
mirarlas, las dos estaban ya vacías. El cofre de ender sí se salvó entero. Se
compensó a mano con equipo de netherita al máximo.

**Los respaldos, desde el 2026-09-22.** Son dos, porque ninguno alcanza solo:

- **El panel**, con el schedule `Respaldo diario` — todos los días a las 5:30 hace
  un backup del servidor entero. Ojo: el plan de Minehost permite **un solo
  backup**, así que cada uno pisa al anterior; sirve para volver de un desastre
  grande, no para buscar qué tenía alguien el martes.
- **`respaldar-jugadores.py`**, en esta PC — baja los `.dat` y `.dat_old` de todos
  y los JSON de nuestros mods, fechados, y conserva 45 días. Le pide al servidor un
  `save-all` antes de bajar, porque lo de los que están conectados vive en memoria
  y si no, justo los que están jugando quedan peor respaldados. Al final imprime
  cuánto tiene cada uno encima y en el ender: un inventario que era de 37 y hoy es
  de 0 salta a la vista sin abrir nada.

### Lo que el lobby tapa solo

- **No se rompe ni se pone nada.** Tres eventos de Fabric cancelan romper, usar y
  pegar en esa dimensión; los operadores quedan afuera para poder arreglar el
  mundo. Lo que **no** está tapado es tirar items al piso, que pide un mixin: si
  alguien vacía la mochila en el patio, los items se despawnean solos.
- **Al que no está autenticado no lo deja moverse** — eso lo sigue haciendo
  EasyAuth y no hay que tocarlo. Además es invulnerable y los bichos lo ignoran.
- **El que se cae vuelve.** Abajo del patio no hay nada, así que `sdp:tick` sube
  al patio a cualquiera que esté por debajo de y=−25 en esa dimensión.
- **Deja de cantar dónde vivís.** Antes, mientras escribías `/login`, la pantalla
  mostraba tu casa. Era lo último que faltaba para streamear tranquilo.

El `/login` en sí lo sufre cada vez menos gente: con el launcher no existe, y sin
launcher `session-timeout` está en 604.800 segundos, o sea que el que vuelve dentro
de la semana desde la misma IP entra derecho.

## El coliseo está lejos, no en otro mundo

Las peleas no son adentro del survival: el coliseo flota en el overworld a
**x 501.145→501.205, y 250→306, z 11.590→11.650**, o sea a medio millón de bloques
del spawn y por encima de las nubes. Así no se puede grifear, no ocupa chunks de
donde vive todo el mundo, y el lag del overworld poblado no se come las peleas.

Tuvo dos mudanzas. La primera (2026-09-22, a la mañana) lo sacó del survival y lo
metió en la dimensión `sdp:coliseo`; la segunda, el mismo día, lo trajo de vuelta
al overworld junto con el lobby, porque las dimensiones propias rompen el login.
Las dos las hizo `mudar-al-overworld.py` con `/clone from <mundo> ... to <mundo>`,
que **funciona entre dimensiones** (probado acá). Va por franjas porque un clone
tiene un tope de 32.768 bloques, y con las dos áreas en `forceload` porque si no el
comando contesta *"that position is not loaded"* y no copia nada.

La aritmética de la segunda mudanza es toda esta: **x + 500.000, y + 180, z igual**
para el coliseo, y **x + 500.000, y + 250, z igual** para el lobby. Las cajas de
origen no son a ojo: `region.py` lee los archivos de región y devuelve la caja
exacta de los bloques que no son aire.

Verificado bloque por bloque bajando las regiones de los dos lados y comparando con
la traslación aplicada: **21.049 bloques no-aire en el lobby y 32.323 en el
coliseo, los mismos de los dos lados, cero faltantes, cero sobrantes, cero
distintos.** (Cinco bloques de pasto que se volvieron tierra entre una medición y
la otra: eso lo hace el juego solo y el verificador lo sabe.)

**La altura salió de medir el destino, no de una corazonada:** el terreno del
overworld llega a y=88 donde va el lobby y a y=103 donde va el coliseo. Poniéndolos
arriba de 250 quedan 150 bloques de aire por debajo y no hubo que limpiar ni un
bloque con `/fill`.

Lo viejo sigue donde estaba, a propósito: el coliseo original en `x 1145, z 11590`
del overworld, y los chunks de las dimensiones borradas en
`/world/dimensions/sdp/`. Se borran cuando lo nuevo esté probado.

### Recorrer el coliseo, y por qué es de espectador

`/coliseo` (el botón **VER EL MAPA**) te deja de **espectador** arriba de la
cancha: volás, atravesás las paredes y no podés tocar ni romper nada, que es todo
lo que hace falta para darse una vuelta sin ensuciarla. Lo maneja
`mod-lobby/Mirador.java`.

Tres cosas que no son obvias:

- **De espectador no se puede usar la perla.** El juego no dispara el click derecho
  de un ítem para el que mira, así que el menú no abre. La única salida es escribir
  `/lobby`, y por eso el mod se lo dice al entrar, en amarillo.
- **Hay que encerrarlo.** De espectador se cruza el mundo entero volando y el
  coliseo está a 500.000 del spawn: el que sale derecho no vuelve nunca. Un tick lo
  devuelve a las gradas apenas cruza la caja.
- **Al salir siempre queda en survival.** La primera versión le devolvía el modo que
  tenía antes, para no romperle el creativo a los operadores, y estuvo mal: el que
  entraba a mirar desde el creativo volvía al lobby en creativo y **seguía volando**,
  apretaba SURVIVAL y entraba al mundo volando también, que es modo creativo de
  contrabando. Del lobby se sale a jugar, y jugando no se vuela. Además `soloMover` y
  `mandarAlLobby` pasan a survival a cualquiera que llegue al patio de espectador,
  que es la red abajo del trapecio por si aparece otro camino de salida.

La marca es la etiqueta `sdp_mirando`. Si alguien queda marcado **dentro del
lobby** —o sea, salió del coliseo sin pasar por `/lobby`— el tick le limpia la
marca en vez de traerlo de vuelta: traerlo sería mandarlo al coliseo veinte veces
por segundo mientras el mod del lobby lo devuelve al patio, que es exactamente el
ida y vuelta por tick que ya se pagó una vez con `execute in`.

El espectador de `/pvp ver` —el que mira un duelo, que maneja
`mod-duelos/Mirones.java`— tiene su propio límite: `noSeVayan()` lo devuelve arriba
de la cancha si se aleja más de 48 bloques de la caja de la arena. Con 48 sobra para
recorrer las gradas y todo el edificio (la cancha mide 37 de lado y el coliseo 61) y
no alcanza para irse a ningún lado. Son dos mods sueltos y por eso son dos límites,
pero los dos existen por el mismo motivo: a 500.000 del spawn, el que sale volando no
vuelve.

### La guarda del login pregunta por la CONEXIÓN

En el `JOIN` el mod difiere su trabajo al tick siguiente (`servidor.execute`), y ahí
chequea que el jugador no se haya ido en el medio. Ese chequeo **tiene que mirar la
conexión** (`handler.isAcceptingMessages()`).

La primera versión miraba si el jugador ya figuraba en la lista del servidor, y
rechazaba **todos** los logins: para cuando corre esa tarea el jugador todavía no
está anotado ahí —el `joined the game` sale después— así que daba "se fue" siempre y
nadie llegaba al lobby. Estuvo así medio día y el síntoma era raro de leer: la gente
aparecía donde se había desconectado en vez de en el patio, y el log decía
`se fue antes de que lo pudiera mandar al lobby` en cada entrada buena.

### La perla abre dos menús distintos

Un menú de cofre es un JSON fijo: no sabe dónde está parado el que lo abre. Lo
único que sabe eso es el mod, así que el botón **IR AL LOBBY** se resuelve
generando dos archivos —`sdp:juegos` y `sdp:juegos_afuera`— y eligiendo en
`Perla.abrirMenu`. Estando en el lobby, "IR AL LOBBY" no sería un botón: sería una
pregunta.

Hoy la perla existe en dos lugares, el lobby y el coliseo (`enLasNuestras`). En el
survival no la tenés, y de espectador no se puede usar.

### Armar una pelea a mano: la mesa

Hasta ahora al coliseo se entraba de una sola forma: anotarse en la lista de espera
y que el servidor te cruzara con quien apareciera. El compañero de un 2v2 salía por
orden de llegada y no se elegía.

La **mesa** es la otra forma: elegís vos a quién invitar, de tu lado y del otro.
Vive en `mod-duelos/Mesa.java` y se abre con `/pvp mesa 1v1` o `/pvp mesa 2v2`,
que es lo que corren los botones de **ARMAR UNA PELEA** en el menú del coliseo.

**El grupo y la mesa son la misma cosa**, y esa es la decisión que hace que todo
esto entre en una clase y no en dos:

- llenás **tu lado** y lo dejás ahí → es un grupo, y se anota junto a la cola;
- llenás **los dos lados** → es una pelea armada, y arranca sola.

Lo que el jugador aprende para una le sirve para la otra, y no hay dos sistemas de
invitaciones conviviendo.

**La pelea arranca sola cuando el último acepta.** No hay botón de empezar, y eso no
es una simplificación: la primera versión lo tenía y estaba roto de raíz. El botón lo
veía sólo el dueño, y su pantalla **no se enteraba** de que el otro había aceptado
—son dos menús distintos, cada uno con su copia dibujada— así que le quedaba "FALTA
GENTE" para siempre mientras el invitado veía "ya aceptaste". Los dos mirando una
pantalla que no se iba a mover nunca.

Arrancando sola, el estado vive en un solo lugar (`Mesa`) y las pantallas pasan a ser
lo que tienen que ser: una foto de algo que decide otro. Se pueden cerrar, se puede
estar en otro lado del mapa, y la pelea empieza igual. Lo hace `Mesas.tick`, y está
ahí y no en la pantalla a propósito: una pelea que sólo arranca si alguien tiene una
ventana abierta no arranca nunca.

### Las nueve formas de romper la mesa, y qué las tapa

| Lo que alguien va a intentar | Qué lo frena |
|---|---|
| Armar una mesa atrás de otra para spamear invitaciones | Una mesa por persona y **una por minuto** (`ESPERA_ENTRE_MESAS`) |
| Insistirle al que dijo que no, apretando su cara sin parar | No se puede reinvitar al mismo por **30 segundos** (`ESPERA_REINVITAR`) |
| Subir la apuesta a 999.999 con el botón | Tope de **1.000 shards**, y nunca más de lo que el dueño tiene |
| Apostar shards que no tiene ninguno de los dos | Se verifica **al arrancar**, no sólo al invitar: entre medio se los pudo gastar en la tienda |
| Invitar a alguien que está peleando en el survival | La marca `sdp_combate` lo deja fuera, igual que en `/pvp` |
| Meterse a una mesa estando en la cola o en otra pelea | `porQueNo` lo apaga en la lista, con el motivo escrito debajo de la cara |
| Que la pelea arranque con la arena ocupada | Espera y avisa **una sola vez**; arranca cuando termina la de ahora |
| Aceptar y desconectarse antes de que arranque | El tick libera el lugar y le avisa al dueño |
| Dejar una mesa colgada para siempre | Se cae sola a los tres minutos, y si el dueño se va se deshace entera |

El aviso de "por qué no arranca" pasa por `hayQueAvisar`, que sólo habla cuando el
motivo **cambia**: esto se pregunta veinte veces por segundo y sin ese filtro "la
arena está ocupada" serían mil doscientas líneas de chat por minuto.

Y al que se **desconecta** se le libera el lugar y se le avisa al dueño.

### Las pantallas que dibuja el mod

Los doce menús del servidor los dibuja Inventory Menu leyendo JSON del datapack, y
están perfectos para lo que son: **botones fijos**. Pero un JSON no sabe quién está
conectado, y para elegir a una persona hay que mostrar una lista que cambia cada
minuto.

Por eso `mod-duelos` tiene ahora sus propias pantallas, y **conviven** con las del
datapack en vez de reemplazarlas: en el mod están sólo las que muestran gente.

- `Pantalla.java` es la base: un contenedor de 9×N donde **nada se puede mover**
  (`clicked` no llama nunca al `super`, que es lo que evita que el primero que abra
  el menú se lleve las cabezas al inventario).
- `ElegirJugador.java` es la lista de caras, paginada, y debajo de cada una dice
  **si se puede elegir y por qué no**: que está peleando, que ya está en la mesa,
  que está armando otra. Mostrar a todos y después decirle que no al que clickeó es
  peor que no mostrarlos.
- `PantallaMesa.java` es la mesa: los dos lados, quién aceptó y quién falta.

Las caras se ven de verdad aunque el servidor sea **offline-mode** porque está
`skinrestorer` instalado. Sin ese mod serían todas Steve y la lista no se podría
leer de un vistazo, que es justo para lo que existe.

En 26.1 la firma es `clicked(int, int, ContainerInput, Player)` y no la de
`ClickType` de las versiones viejas, y `ResolvableProfile` es abstracta: se arma con
`ResolvableProfile.createResolved(perfil)`. Las dos salieron de mirar con `javap`
cómo lo hace EconomyCraft, que ya tiene sus menús andando en esta versión, en vez de
descubrirlas a fuerza de compilar.

### La cola se acuerda de quién vino con quién

`Cola.java` guardaba una lista de nombres sueltos, y alcanzaba mientras el compañero
saliera por orden de llegada. Desde que el dúo se arma a mano, **cada anotación es un
grupo** que entra junta del mismo lado; un anotado solo es un grupo de uno, así que
no hay dos caminos.

Al armar la pelea se toma un lado y después el otro, y si el segundo no se puede
completar **el primero vuelve a la lista**: sacar media pelea de la cola dejaría a un
dúo esperando para siempre sin figurar en ningún lado. Un grupo que no entra en el
cupo se saltea en vez de cortarse.

### La caja de la arena llega hasta abajo del piso

La arena va de **y=250 a 280**, y el piso de la cancha está en 256: hay seis bloques
de caja **por debajo** del piso, y no son decoración.

Con dinamita se vuela el piso. Cuando la caja empezaba justo en el piso, el que caía
por el agujero salía de la caja, y el control que devuelve al que se va —`Duelo.
adentroDeLaArena`, que corre cada tick— lo mandaba **al borde de abajo**, que ahora
era aire: caía otra vez, y otra, y otra. El jugador quedaba temblando en el aire
hasta que terminaba la pelea. Pasó en un 1v1 de verdad.

Dos cosas lo tapan, y hacen falta las dos:

- la caja baja hasta la base del coliseo, así romper el piso no te saca de la arena
  **y lo que se rompe ahí abajo también se restaura** al terminar;
- al que igual se va **por abajo** se lo manda a su esquina, que siempre tiene piso,
  en vez de al borde. Devolver al borde a alguien que está cayendo es devolverlo
  cayendo.

### Dónde aparece cada uno en un 2v2

Los dos puntos marcados con la varita definen el **eje** de la pelea —uno enfrente
del otro— y los compañeros se reparten sobre la **perpendicular** a ese eje, a tres
bloques uno de otro. Eso es lo que hace que un 2v2 se lea desde las gradas como un
2v2: dos de un lado, dos del otro.

Antes el primero de cada lado iba a su punto y los compañeros caían **al azar** por
el ring. Se veía mal y el que aparecía de espaldas arrancaba perdiendo.

Sale de la geometría y no de puntos marcados a mano a propósito: marcar seis lugares
por lado en cada cancha son doce clicks y una planilla, y el día que la cancha cambie
hay que rehacerlos. Así, **marcar los dos de siempre alcanza para todos los modos**, y
el mismo coliseo sirve para 1v1, 2v2 y equipo contra equipo sin tocar nada.

El tope de gente por lado es **6**, que no es un número inventado: es el que ya tiene
`mod-equipos` (`Registro.TOPE`), así que un equipo entero entra siempre.

### Los textos de los menús

Los escribe `generar-menus.py` y los lee **gente que entra por primera vez**, así
que la regla es que no pueden explicar el servidor *desde los cambios que le
hicimos*. "Aparecés donde lo dejaste" o "ya no es en el survival" solo se entienden
si viviste la versión anterior; al que recién llega no le dicen nada. Dicen qué se
hace adentro, en dos renglones, y terminan en el triangulito (`e.ENTRAR`), que es
lo único que hay que entender para apretar.

El menú **LOS KITS** (`sdp:coliseo_kits`) muestra las ocho formas de pelear, para
mirar y no para elegir: el kit sale al azar y les toca el mismo a los dos, que es
lo que hace pareja la pelea. Los nombres y el "cómo se pelea" están escritos en
`mod-duelos/Kits.java` y copiados en el menú, y `kits_de_java()` compara las dos
listas al generar: si alguien agrega la novena clase en el mod, el generador falla
en vez de dejar un menú que miente.

Lo único animado que da Minecraft sin resource pack es `obfuscated`, que cambia los
caracteres veinte veces por segundo. `titilando()` lo pone en un adorno a los
costados y **nunca** en el texto que hay que leer: un título entero titilando no se
lee, y además el ancho baila y mueve todo lo que tiene al lado.

**No hay botón de ver la pelea.** Casi siempre no hay ninguna jugándose —hay una
sola arena, así que se juega de a una y el resto espera en la cola— y un botón que
la mayor parte del tiempo no hace nada ensucia el menú. El comando `/pvp ver` sigue
existiendo para el que mira mientras espera su turno.

### Cómo se pelea ahora

Se entra **desde el lobby**: perla → COLISEO → el modo → ANOTARME. No hace falta
retar a nadie por su nombre; uno se anota en la lista de espera y, cuando hay con
quién, la pelea arranca sola.

| Modo | Cuántos | Cómo se arma |
|---|---|---|
| **1v1** | 2 anotados | por orden de llegada |
| **2v2** | 4 anotados | los dos primeros contra los dos siguientes; el compañero no se elige |
| **Equipo vs equipo** | 2 equipos | junta a los anotados por su equipo de `/equipo` y los cruza |

Los equipos se leen del **scoreboard** y no del mod de equipos: son dos mods
sueltos que no se hablan, pero `/equipo` espeja cada equipo en uno del scoreboard
llamado `sdp_<nombre>`, y eso lo ve cualquiera.

Sigue habiendo **una pelea a la vez** — el coliseo es para mirar, y con tres duelos
simultáneos no habría a qué apostarle. Los retos de `/pvp <jugador>` tienen
prioridad sobre la lista de espera: un reto es entre dos que se pusieron de
acuerdo, y eso vale más que un turno.

### Bandos, y qué cambió por dentro

`Duelo` estaba escrito para dos jugadores. Ahora son **dos lados** ({@link Lado}),
y cada lado tiene un **nombre para mostrar**: en 1v1 es el del jugador, en 2v2
"PEPE y Chichón", y en equipos el nombre del equipo. Por eso los carteles siguen
recibiendo dos String como siempre y no hubo que reescribir las 860 líneas de
`Carteles`.

Lo que cambia con más de uno por lado:

- **El que cae no termina la pelea**: queda eliminado, pasa a espectador y mira
  cómo sigue. El lado pierde cuando se queda sin nadie en pie.
- **Entre compañeros no se pega.** El kit es el mismo para todos, así que un golpe
  mal dado adentro del propio bando decidiría la pelea.
- **La apuesta la pone cada uno** y el pozo se reparte entre los del lado que ganó.
- Si se acaban los cinco minutos, gana el lado con **más vida sumada**.

### Los espectadores

`/pvp ver` (o el botón VER LA PELEA del chat y del menú) te pone en **modo
espectador** sobre el centro de la arena: volás, atravesás paredes y mirás desde
donde quieras. No podés pegar, que te peguen ni tocar un bloque.

**Ya no hay un asiento marcado.** El comando `/pvp arena gradas` y la clase
`Gradas` se borraron: el espectador vuela, así que cada cancha nueva trae su punto
de mirada sin que nadie marque nada.

Se vuelve con `/pvp volver`, o solo cuando termina la pelea. Dónde estabas y en qué
modo de juego se escriben en `config/mirones-de-pepe.json` **antes** de moverte,
porque nadie puede quedar de espectador para siempre: también se devuelve al que se
desconecta mirando y al que estaba mirando cuando se cayó el servidor.

### Nadie queda guardado adentro del coliseo

Es la misma regla que en el lobby y por la misma razón: el que se conecta dentro de
una dimensión custom entra con el mapa vacío. Entonces el mod saca del coliseo,
**antes de que el servidor escriba el archivo del jugador**, a los tres casos:

- el que se va **peleando** (además recupera lo suyo en el acto, sin esperar a
  reconectarse),
- el que se va **mirando**,
- y el que se va desde el lobby, que ya lo hacía el mod del lobby.

### En el coliseo no hay bichos

Ni uno, en todo el mundo, haya pelea o no. Se borran **al aparecer** (`ENTITY_LOAD`
en el mod del lobby) en vez de barrer cada tick, y se respeta al invulnerable, que
es la marca que deja la varita sobre un animal que alguien quiso cuidar. Probado
invocando un zombi y una vaca adentro: ninguno de los dos llegó a existir.

La luz es **constante** (`ambient_light` en 1.0): el coliseo se ve igual de día que
de noche. La lluvia sí sigue la del overworld, porque la dimensión tiene cielo. Si
llega a molestar, se le saca el cielo y queda siempre seco.

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

### La netherita: crafteable o solo con shards, según el interruptor

**El interruptor es `RESTRICT_NETHERITE` en `web/.env.local`**, y se aplica con
`python servidor/configurar-netherite.py`.

| `RESTRICT_NETHERITE` | Qué pasa |
|---|---|
| `false` ← **hoy** | La netherita es normal: las doce recetas de smithing andan y se craftea minando. La tienda de shards la sigue vendiendo, así que comprarla es una forma más y no la única. |
| `true` | Las doce recetas quedan apagadas. La armadura, las armas y las herramientas de netherita salen de un solo lado: la tienda de shards, que las entrega con `give` y por lo tanto no pasa por ninguna receta. Es la asimetría que hace que el equipo de fin del juego se pague matando gente. |

Todo pasa por **un archivo**, el tag que las doce recetas usan como `addition`:

```
datapack/data/minecraft/tags/item/netherite_tool_materials.json
{"replace": true,  "values": []}   -> la etiqueta reemplaza a la de vanilla y queda vacía: las recetas se ignoran
{"replace": false, "values": []}   -> el archivo está pero no cambia nada: los tags se mezclan y vuelven las recetas
```

Apagar la restricción no es borrar el archivo — `subir-datapack.py` lo volvería a
dejar como estaba — sino dejarlo inerte con `replace: false`.

Son doce de trece — verificado abriendo el jar de 26.1: la que sobra es
`netherite_upgrade_smithing_template`, la de duplicar la plantilla, que sin
smithing no sirve para nada. Se recarga con `/reload`, no pide reinicio y no
toca el launcher. En el log quedan **doce WARN** que son la confirmación de que
funcionó, no un problema:

```
Recipe minecraft:netherite_axe_smithing can't be placed due to empty ingredients and will be ignored
```

Medido al apagar la restricción el 2026-09-08: `Loaded 1515 recipes` y **cero**
WARN de netherita, que es la confirmación de que las doce volvieron.

Por qué se eligió esta palanca y no las otras, con los números que había cuando se
construyó:

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

Son dos listas distintas y no tienen por qué coincidir. Al 2026-09-09 el
servidor tiene **21 jars** en `/mods` y el pack que baja el launcher tiene **17
entradas** (16 mods y el shader). **Los 8 que están en los dos lados son el mismo
archivo**: fabric-api, cloth-config, lithium, modernfix, sound-physics, voicechat,
el mod de acceso y el de los cuadros. Los otros trece del servidor no los tiene
nadie en su PC, y está bien: son `environment: server` o `*` sin nada de cliente.

Cómo se decide dónde va cada uno: se lee el `fabric.mod.json` del jar.

- `"environment": "client"` → **no lo carga el servidor**, ni aunque esté en
  `/mods`. Va solo en el pack (sodium, iris, zoomx…).
- `"environment": "*"` con entrypoint `main` y nada de `client` → va solo en el
  servidor (essential_commands, melius-commands, inventory-menu,
  styled-sidebars, skinrestorer, team-only-locator-bar).
- `"environment": "server"` → solo servidor, y ni siquiera se carga en un
  cliente (luckperms, el mod de equipos).
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

### Los cuadros

**My Photo Paintings** (`my_photo_paintings` 1.0.1, 99 KB), desde el 2026-09-09.
Se craftea el item "Photo Painting" con ocho palos y un papel, se lo usa contra una
pared y se abre el explorador de archivos de Windows para elegir un PNG, JPG o
JPEG; después se elige la medida, de 1x1 hasta 16x16 bloques, con una sugerencia
según la forma de la imagen.

Va en los dos lados y es el mismo archivo (`environment: *`): el jar está en
`/mods` y en el pack del launcher. Lo sube `subir-cuadros.py` del lado del
servidor y `npm run pack:jar` del lado del pack. **Es el único mod del pack que no
sale de Modrinth**, así que el archivo lo sirve nuestro backend desde
`/api/files/<sha1>`; el autor permite incluirlo en modpacks. Pide
`minecraft: "26.1"` **exacto**, o sea que el día que el servidor pase a 26.1.2 este
mod deja de cargar.

**Está puesto sabiendo que el mod es para un solo jugador**, y hay que decirlo acá
porque si no el próximo que lea esto va a pensar que está roto. Verificado en el
bytecode de 1.0.1, y el autor lo aclara en su propia página:

- `client/PhotoImageStorage` guarda el PNG llamando a `getSingleplayerServer()`, y
  tiene preparado el mensaje `"No singleplayer server found."`. En un servidor
  dedicado ese mundo no existe.
- Lo único que viaja por la red (`PlacePhotoPaintingPayload`) es
  `position; direction; width; height; imageId; cropMode; imageWidth; imageHeight`.
  **La imagen no viaja**: viaja un número que apunta a un archivo del disco del que
  la colgó.

O sea que acá adentro no se espera que los demás vean nada. Se eligió igual, a
sabiendas (Tobías, 2026-09-09).

**El que sí funciona en servidor es Custom Paintings**, que estuvo puesto unas
horas: las imágenes las elige el admin, viven en `/world/custompaintings/` y el
servidor se las manda a cada jugador cuando entra. Volver a él es
`npm run pack:mod -- custom-paintings-mod` del lado del launcher, subir su jar a
`/mods` y dejar el zip de imágenes en `/world/custompaintings/`. El tercero que
miramos, Immersive Paintings, es el único donde cada jugador cuelga lo suyo **y**
los demás lo ven, pero pide Minecraft 26.1.2 y nosotros estamos en 26.1.

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

## El coliseo: los duelos de `/pvp`

Desde el 2026-09-21 se puede retar a alguien a un duelo uno contra uno en una
arena marcada. Es el mod `mod-duelos/`, y se sube con `subir-duelos.py`.

    /pvp <jugador> [shards] [clase]   retarlo; al otro le llega un boton
    /pvp aceptar <jugador>     entrar a la arena contra el
    /pvp rechazar <jugador>    decirle que no
    /pvp rendirse              abandonar el duelo que estas peleando
    /pvp apostar <jug> <n>     ponerle shards a uno de los dos
    /pvp top                   los que mas duelos ganaron
    /pvp duelos                la ficha que explica todo
    /pvp arena ...             marcar y mirar la arena (operadores)
    /pvp arena zona <id> [alto] tomar una zona protegida como arena
    /pvp torneo ...            abrir, anotarse, arrancar y cancelar
    /pvp probar                probar el guardado del inventario (operadores)

`/pvp` **a secas no cambio**: lo sigue abriendo Melius con el menu de cofre
`sdp:pvp`. El mod registra solo los hijos, sin `executes` en la raiz, y Brigadier
los fusiona -- `CommandNode.addChild` pisa el `command` del nodo solo si el nuevo
trae uno, asi que con el nuestro en null el de Melius sobrevive sin importar cual
de los dos mods registre primero. Es lo que evita tener que elegir entre el menu
y el comando.

### Como es un duelo

Cuatro etapas, y el orden es lo que le da la forma de coliseo:

1. **Apuestas, 10 segundos.** Los dos ya estan parados en la arena, quietos y sin
   poderse tocar, con el kit puesto, mientras el resto mira y apuesta. Estan
   adentro y no afuera a proposito: la gente le apuesta a alguien que esta
   **viendo**, y no a un nombre en el chat.
2. **La cuenta.** 3, 2, 1 en pantalla, con un pim por numero cada vez mas agudo,
   y el PELEEN en verde grande. Recien ahi se sueltan.
3. **La pelea**, hasta cinco minutos. Termina cuando uno cae, se rinde o se
   desconecta; si se acaba el tiempo gana el que llego con mas vida, y si estan
   iguales es empate y vuelve todo para atras.
4. **El final, 5 segundos.** El ganador en pantalla y los fuegos artificiales, y
   despues se deshace todo: los inventarios vuelven, la arena vuelve, la barra de
   arriba se va.

Se pelea **una pelea a la vez**. Hay una sola arena y el coliseo es para mirar:
con tres duelos simultaneos no habria a que apostarle. Los demas hacen cola.

### El boton para ir a mirar: `/pvp ver`

El cartel que sale para todo el servidor cuando arranca un duelo trae, abajo de
los dos botones de apostar, un **[ VER LA PELEA ]** que te teletransporta a las
gradas del coliseo.

Donde caen se marca una sola vez, parado ahi:

    /pvp arena gradas      (operadores)

Se guarda **para donde estas mirando**, no solo donde estas parado. No es un
detalle: el que llega teletransportado aparece mirando para donde lo deja el
juego, y si eso es la pared del fondo lo primero que hace es girar sin entender
donde cayo. Se marca parado en la tribuna mirando la arena y todos los que llegan
ya estan mirando la pelea. Se guarda en el mismo archivo que la arena y **se
borra con ella**: un punto para mirar una pelea que no se pelea en ningun lado
mandaria gente a un descampado.

Si no hay gradas marcadas, el boton no sale. Uno que contesta "todavia no
marcaron las gradas" es peor que no tener boton, y el log lo avisa al arrancar.

Tres cosas frenan el `/pvp ver`, y las tres son la misma: **un teletransporte
gratis no puede ser una forma de zafar de algo.**

- **Estar en combate** no te deja ir. Sin esto, `/pvp ver` seria el mejor `/home`
  del servidor: cada vez que te acorralan, un click y aparecias entero del otro
  lado del mapa. Es la misma marca del datapack (`sdp_combate`) que ya tapa
  `/home`, `/spawn`, `/rtp` y `/tpa`.
- **El que esta peleando** tampoco: ya esta adentro de la arena, y salir de ahi
  seria abandonar la pelea sin perderla.
- **Tiene que haber un duelo ahora.** Si no, no hay nada que mirar y esto seria un
  viaje gratis a un punto fijo del mapa a cualquier hora.

### A la arena entra el que quiere, y la pared la pones vos

El mod **no saca a nadie de la arena**. Lo hacia, y estaba mal: el que queria
mirar de cerca salia volando dos bloques para atras cada vez que pisaba adentro
de la caja, y la caja es todo el coliseo, gradas incluidas.

Estar adentro no cambia la pelea, y eso ya estaba resuelto por otro lado:

- no puede pegarle a los que pelean ni ellos a el (`dejarPegar` cancela cualquier
  golpe en el que uno solo de los dos sea duelista, venga del lado que venga);
- no puede romper ni poner un bloque adentro de la arena mientras dura la pelea
  (`puedeTocarLaArena`).

Lo unico que puede hacer un espectador adentro es **ponerse en el medio**. Para
eso estan los bloques, que es lo que se ve y lo que se entiende: una pared de
`minecraft:barrier` en el borde del ring. Es invisible, se ve a traves, frena a
los jugadores, a los bichos y a las flechas, y solo la ve el que tiene una
barrier en la mano. Se pone una vez y queda:

    give <jugador> minecraft:barrier 64

Y queda **protegida sola**: la foto de la arena se saca antes de cada pelea y se
devuelve al terminar, asi que si alguien llegara a romper una, vuelve.

### Adentro de la arena no hay bichos

Haya duelo o no. Cada diez ticks —media vez por segundo— se barre la caja de la
arena y se van todos los hostiles. Un coliseo es un lugar grande y oscuro, o sea
una guarderia de zombis, y un duelo con un esqueleto tirando flechas desde las
gradas deja de decidir nada. Los phantoms son peores: bajan igual con la pelea
empezada, y bajan justo sobre el que hace rato que no duerme.

Se barre en vez de prohibirles aparecer porque aparecer no es el unico camino: el
phantom se genera arriba del techo y el zombi entra caminando desde una cueva de
al lado. Y aunque alguno llegue, a los que pelean no les pega: `dejarPegar` ya
cancela todo golpe que no venga del otro duelista.

Se van **solo los hostiles, y solo los que no estan protegidos**. Los caballos,
los lobos y las vacas que alguien haya dejado ahi son de alguien; y al que se
protegio a mano con la varita (click derecho con el palo de depuracion sobre el
bicho) no lo toca esto ni aunque sea un hostil. Hace falta preguntarlo: `discard()`
se lleva puesto al invulnerable igual, asi que sin el chequeo un esqueleto de
adorno protegido con la varita se borraria solo medio segundo despues de ponerlo.

### El duelo le gana al equipo

Dos companeros de `/equipo` no se podian pelear: entraban a la arena, sonaba el
PELEEN y los golpes no pasaban. Eran los dos mods diciendo cosas distintas sobre
el mismo golpe, y ganaba el que corre primero. Y el que corre primero es vanilla:
`ServerPlayer.hurtServer` pregunta `canHarmPlayer` —que es el `friendlyFire` del
equipo del scoreboard— y devuelve false **antes** de que el golpe llegue a
`LivingEntity.hurtServer`, que es donde Fabric tira el `ALLOW_DAMAGE` que usa el
mod de duelos. Ningun enganche nuestro llegaba a opinar. Pasa igual con las
flechas: el mismo metodo mira tambien al dueno de la flecha.

Manda el duelo, que es el que los dos pidieron: mientras dura la pelea se le
**abre el fuego amigo al equipo** y se le vuelve a cerrar al desarmar. Se toca el
equipo y no a los jugadores porque sacarlos del equipo les apagaria el color, el
`[TAG]` y la barra de arriba en el medio de su propia pelea. Mientras dura, el
resto del equipo tambien se puede pegar entre si: son como mucho cinco minutos,
hace falta que los dos que pelean sean del mismo equipo, y el que pega le tiene
que apuntar a un companero a proposito. Si el servidor se cae con el duelo
empezado, el equipo queda abierto hasta el arranque siguiente: ahi el mod de
equipos rearma el espejo desde `equipos-de-pepe.json` y lo vuelve a cerrar.

### Nadie muere de verdad

El golpe que mataria se cancela (`ServerLivingEntityEvents.ALLOW_DEATH`) y en su
lugar termina el duelo. **No es cosmetico.** Si la muerte pasara de verdad:

- el que pierde soltaria el kit prestado en el piso;
- el que gana cobraria los 10 shards de la kill, el 10% de la plata del muerto y
  todo el equipo, o sea que dos amigos turnandose serian una maquina de shards,
  que es exactamente por lo que se saco Simple Revive;
- y el muerto perderia de verdad lo suyo, que es lo contrario de lo que tiene que
  ser un duelo.

Cancelar la muerte **no le devuelve la vida**: el juego ya le puso la vida en
cero antes de preguntar (el evento es un `Redirect` sobre `isDeadOrDying` adentro
de `hurtServer`), asi que hay que sanarlo a mano en el mismo enganche. Sin eso
queda parado en cero corazones y se muere con el proximo golpe de cualquier cosa.

### El inventario, que es lo unico que no se puede perder

Adentro se juega con un kit prestado, asi que al empezar el duelo **se le vacia
el inventario de verdad a los dos**. La copia se escribe en
`config/duelos-de-pepe.json` **antes** de tocar nada, y se borra recien cuando ya
volvio a su dueno. Si el servidor se cae en el medio de una pelea (que en este
servidor pasa por RAM) al arrancar de nuevo las copias siguen ahi y cada uno
recupera lo suyo al conectarse, en el tick siguiente a entrar.

Lo unico que no vuelve de una caida son los efectos de pocion, que se guardan
solo en memoria. Es lo unico de la lista que se puede volver a tomar.

**`/pvp probar` prueba justamente eso, y no toca nada.** Guarda tu inventario
como lo guardaria un duelo, lo escribe, lo vuelve a leer y compara casillero por
casillero; contesta cuantos volvieron identicos y, si alguno no volvio, cual.
Correlo parado con lo mejor que tengas —elytra, shulkers llenas, netherita
encantada— porque asi queda probado contra items de verdad.

La prueba vive adentro del juego y no en un test de Gradle a proposito: en 26.1
los componentes de los items son **data-driven**, asi que fuera del servidor no
se puede ni siquiera construir un `ItemStack` (`Bootstrap.bootStrap()` registra
los 1.506 items pero deja sus componentes sin atar, y el constructor revienta con
`Components not bound yet`). Probar la ida y vuelta de verdad pide el servidor
de verdad.

Se recorre el inventario por indice y no por sus listas de adentro: en 26.1
`Inventory.getContainerSize()` cuenta los 36 casilleros **mas** la armadura y la
mano de atras, y `getItem(i)` sabe a cual de las dos partes va cada indice.
Recorrer `getNonEquipmentItems()` se comeria la armadura puesta.

### Los kits, y por que son al azar

Ocho clases: GLADIADOR, NETHERITA, CRISTALERO, ARQUERO, PERLERO, BOMBARDERO,
MAZAZO y CUERO. Se saca una al azar por duelo y **es la misma para los dos**,
cantidades incluidas: el kit se arma UNA vez y despues se copia. Armandolo dos
veces, uno podria salir con diez gapples y el otro con seis, y ahi ya no gana el
que pelea mejor.

**Las ocho llevan perlas**, de 3 a 5 en CUERO hasta 16 a 24 en PERLERO. La perla
no es el sabor de una clase, es lo que hace que el que arranca atras tenga una
carta para jugar; una clase sin perlas no es una clase distinta, es una clase
peor.

**La que lleva escudo lleva hacha.** GLADIADOR es la unica de las ocho con
escudo, y por eso la unica con hacha: el hacha es lo que deshabilita el escudo
por cinco segundos. Como los dos pelean con el mismo kit, sin hacha los dos se
tapan, no se sacan vida y el duelo lo termina el reloj en vez de la pelea. Si
alguna vez se agrega otra clase con escudo, va con hacha por la misma razon.

**Dos de las ocho llevan crystals**, que es con lo que se pelea de verdad en los
servidores grandes: se apoya obsidiana, se planta el crystal al lado del otro y se
le pega antes de que reaccione. CRISTALERO es la clase entera dedicada a eso —16 a
24 crystals, 64 de obsidiana y un hacha de netherita— y NETHERITA lleva unos pocos
como segunda herramienta, que es exactamente la combinacion de Donut. Las dos van
con armadura de netherita y Blast Protection IV: sin eso el primer crystal bien
puesto termina el duelo en dos segundos y no hay pelea, hay sorteo.

Que sea al azar es la mitad de la gracia: nadie se especializa en una sola forma
de pelear porque no sabe con que le va a tocar.

**Se puede pedir una**, igual: `/pvp <jugador> [shards] <clase>`. No rompe que
sea pareja, y por una razon concreta: los dos pelean con la misma clase de todas
formas, y al retado **le llega escrita en el reto** antes de apretar ACEPTAR. Si
no le gusta con que le proponen pelear, rechaza. Elegir la clase es proponer una
pelea, no imponerla.

Las llaves del torneo van siempre al azar: ahi nadie reto a nadie, asi que elegir
la clase seria darle a uno de los dos algo que el otro no pidio.

El orden de los argumentos importa en el codigo: los shards se registran antes
que la clase porque Brigadier prueba los argumentos en el orden en que se
registraron. Con el numero primero, `/pvp Fulano 50` entra por los shards y
`/pvp Fulano CUERO` falla el numero y cae en la clase. Al reves, `50` seria un
nombre de clase que no existe.

**Nada del kit se queda.** Cada item lleva la marca `sdp_duelo` en su
`custom_data`, y al terminar se borran los que hayan quedado tirados en la arena
y diez bloques alrededor. El margen es lo que importa: un jugador no puede salir
de la arena, pero si puede tirar un item por arriba de la pared, y esa es la
unica forma de que una pieza de netherita del kit termine en la economia. Los
items **sin** la marca no se tocan, asi que lo que alguien haya dejado tirado en
el coliseo antes de la pelea sigue ahi.

### La arena se marca con la misma varita, de dos formas

**La corta, con una zona protegida.** Es la que se usa, porque marcar una zona
con el click derecho ya se sabe hacer:

    click derecho en una esquina y en la opuesta   (la zona de siempre)
    parado adentro:  /pvp arena guardar [alto]
    /sz remove       parado adentro, para borrar la zona

**El tercer paso no es opcional**, y es lo unico incomodo de este camino:
adentro de una zona de Safe Zone nadie puede romper ni poner un bloque, y el
dueño de la zona es **inmune al daño de explosion mientras este parado adentro**
(`ClaimEntityProtection.shouldBlockExplosionDamage`). Con la zona puesta encima,
los kits de TNT y de crystals quedan de adorno y el dueño del coliseo gana todos
los duelos sin despeinarse. Borrar la zona **no borra la arena**: la arena ya
quedo guardada en `config/duelos-de-pepe.json` con sus coordenadas.

El mod avisa solo: lee `world/safe-zone/claims.json` y en `/pvp arena guardar` y
en `/pvp arena` dice en rojo si hay una zona encima, con su id. Safe Zone escribe
ese archivo en el momento en que se crea la zona —medido: una zona creada a las
13:44 ya estaba— asi que no hay que recargar nada.

**De una zona el alto lo pone el comando.** La proteccion de Safe Zone no mira la
altura (protege la columna entera de la roca madre al cielo), asi que casi todas
las zonas estan marcadas con las dos esquinas a la misma altura y de ahi no sale
el alto de un coliseo. Son 24 por defecto, contados desde un bloque abajo del
piso que se clickeo; `/pvp arena guardar 40` para otro. Tambien se puede nombrar
la zona sin estar parado adentro: `/pvp arena zona <id> [alto]`, con el id de
`/sz list`.

**La larga, sin crear ninguna zona:** el mismo palo de depuracion pero con el
click **izquierdo**, una esquina de abajo y la de arriba opuesta, y
`/pvp arena guardar`. Ahi el alto sale de los dos clicks y no hay zona que
borrar.

Los dos gestos conviven porque son distintos: el derecho es de Safe Zone y del
mod de la varita sobre un bicho, el izquierdo es este. Si fueran el mismo, marcar
una arena reclamaria un terreno sin querer, y el orden entre dos mods escuchando
el mismo evento no esta garantizado. En supervivencia el palo de depuracion no
rompe bloques por su cuenta (`DebugStickItem.canDestroyBlock` devuelve siempre
false), asi que cancelar el golpe no le saca ninguna funcion a nadie.

**La caja va ALTA.** De ahi adentro no se sale mientras se pelea, asi que si el
techo de la caja queda a la altura del piso, al primero que salte lo devuelve de
un tiron. Los dos puntos donde aparece cada uno salen solos de la caja (los
extremos del lado mas largo, sobre el primer piso firme buscando de abajo hacia
arriba) y se pisan a mano con `/pvp arena punto1` y `punto2`, parado donde uno
quiera que aparezcan.

El tope son **400.000 bloques** (por ejemplo 100 x 100 de piso por 40 de alto):
antes de cada pelea se saca una foto entera de la arena, y esa foto es un arreglo
con un bloque por casillero que vive en la RAM del servidor.

**La arena NO puede caer adentro de una zona protegida de Safe Zone**, y es el
unico error de marcado que no se ve hasta la primera pelea. Adentro de una zona
nadie puede romper ni poner un bloque, asi que los kits de TNT y de crystals
quedan de adorno; y peor, el dueño de la zona y sus trusted son **inmunes al
dano de explosion mientras esten parados adentro**
(ClaimEntityProtection.shouldBlockExplosionDamage), o sea que el dueño del
coliseo ganaria todos los duelos de crystals sin despeinarse. El mod lo avisa al
guardar la arena, pero no lo puede comprobar: las zonas las guarda Safe Zone en
world/safe-zone/claims.json y leerlas seria atarse al formato de otro mod.

### La arena vuelve a como estaba

Se puede romper todo, poner crystals y volar el piso con TNT. Al terminar se
rehace desde la foto, y se rehace **despues de cada pelea**, no al final del
torneo. Sin esto el coliseo dura una pelea.

La foto se saca al empezar cada duelo, asi que lo que alguien haya construido o
decorado ENTRE dos peleas ya esta adentro de la foto siguiente y se respeta. Lo
unico que se deshace es lo que cambio mientras se peleaba.

Son cuatro cosas, y hacen falta las cuatro para que de verdad quede igual:

1. **Los bloques**, con el estado exacto que tenian.
2. **Lo que los bloques tienen adentro.** Devolver el bloque y nada mas te deja
   el cofre de decoracion vacio, el cartel en blanco y el estandarte sin dibujo,
   que es justo lo que se nota. Se guarda el NBT de cada block entity de la caja
   —son cuatro o cinco en una arena— y al reponer el bloque se le vuelve a poner
   adentro con `BlockEntity.loadStatic`.
3. **La decoracion que no es un bloque** —los cuadros, los soportes de armadura,
   las vitrinas, algun bicho— se hace **intocable** mientras dura el duelo, en vez
   de guardarse y rehacerse. Es a proposito: rehacer una entidad es crear una
   entidad, y si algo sale mal en el medio quedan dos cuadros donde habia uno. Un
   coliseo con la decoracion duplicada es peor que uno con un cuadro roto. Se
   marca solo a los que no eran invulnerables ya, asi no se le pisa la proteccion
   a lo que haya marcado la varita.
4. **Lo que quedo tirado** se barre: flechas, crystals, TNT encendida, la
   experiencia, los bloques cayendo y todos los items que **no estaban antes de
   empezar**. Los que si estaban no se tocan — el PvP aca es libre y en todo el
   mundo, asi que alguien pudo morirse adentro del coliseo un minuto antes, y
   quitarle lo suyo por haber muerto en el lugar equivocado seria bastante peor
   que dejar el piso un poco sucio.

El punto 4 no es prolijidad: el bloque que alguien rompe del piso **vuelve a su
lugar igual**, asi que si su drop se quedara tirado, cada pelea fabricaria items
de la nada.

Lo unico que no vuelve es un cuadro colgado de una pared que voló: el juego lo
descuelga en cuanto le sacan el bloque de atras, antes de que nadie pueda
frenarlo. La pared vuelve, el cuadro no.

Se guarda la caja entera y no solo lo que cambia: guardar solo lo que cambia
pediria enterarse de cada bloque que se rompe y de cada uno que se pone, y hay
formas de cambiar un bloque que ningun evento avisa -- la explosion, el fuego que
se propaga, la arena que cae, el agua que corre. La foto entera no se puede
equivocar.

Al devolver se usa `UPDATE_CLIENTS` pelado, **sin avisarle a los vecinos**: si se
avisara, devolver la arena dispararia en cadena la arena que cae, el agua que
corre y las antorchas que se caen, y el final de la reposicion se pelearia con el
principio. Como se devuelve TODO al estado que tenia, el resultado ya es
consistente.

Lo que **no** se recupera es una caida del servidor con la pelea empezada: la
foto vive en memoria, asi que ahi el coliseo queda como haya quedado.

### Que no se meta nadie

Es lo que hace que una pelea sea una pelea y no una montonera. Tres candados
independientes:

- al que no esta peleando y se mete en la caja lo saca dos bloques afuera de la
  pared mas cercana, con un aviso cada dos segundos para no llenarle el chat al
  que salta en el borde;
- **el dano no pasa** ni de adentro para afuera ni de afuera para adentro: el de
  las gradas no le puede tirar una flecha al que va ganando, y el que pelea
  tampoco le pega a nadie de afuera. Antes de la cuenta y despues del final los
  dos son ademas intocables, porque ahi estan quietos y no se pueden defender;
- romper y poner bloques adentro de la caja es solo de los dos que pelean, asi
  nadie tapa un agujero desde afuera ni le abre el piso a uno.

Y para el otro lado: al que se va de la caja lo devuelve al borde por donde se
iba, y no al principio. Devolverlo al punto de aparicion seria regalarle la
posicion al otro.

### La marca de pelea, en los dos sentidos

Mientras dura el duelo los dos llevan `sdp_combate`, asi que no andan `/home`,
`/spawn`, `/rtp`, `/tpa` ni `/back`. No hubo que programar nada de eso: lo unico
que mira `modificadores/combate.json` es esa etiqueta.

**La etiqueta sola no alcanza, y esto costo entenderlo.** `sdp:tick` corre todos
los ticks `execute as @a[tag=sdp_combate,scores={sdp_combate=..0}] run function
sdp:combate_salir`, o sea que a cualquiera que tenga la etiqueta con el reloj en
cero se la saca al instante. Un jugador que ya peleo alguna vez tiene el objetivo
en cero, asi que poner solo la etiqueta la perdia en el tick siguiente y ademas
le tiraba el cartel de "saliste de la pelea" en la cara apenas entraba a la
arena. El mod pone la etiqueta **y** el reloj en 300, y lo repone una vez por
segundo.

Y al reves: **no se puede entrar a un duelo estando EN PELEA**. Ni retar ni
aceptar. Sin eso, la arena seria la forma perfecta de escaparse de una pelea de
verdad: adentro nadie de afuera te toca, y al salir estas curado y del otro lado
del mundo.

### Las apuestas son en shards, nunca en plata

No es una preferencia. La plata de EconomyCraft no vive en ningun scoreboard y
sus comandos devuelven exito aunque el jugador no tenga un peso (`eco
removemoney` y `pay` devuelven 1 siempre, ver mas arriba "El unico lector de
saldo"). Cobrarle una apuesta en plata a alguien sin saldo saldria bien y el
servidor regalaria la diferencia. El objetivo `Shards` se lee y se escribe con un
numero exacto.

Se cobran **al apostar** y no al final: cobrando al final, el que aposto 500 y
los gasto en la tienda mientras miraba la pelea pagaria con shards que ya no
tiene, y el objetivo se iria a negativo sin que nada se queje.

**Se paga a la par: le acertaste, cobras el doble de lo que pusiste.** Cada shard
del que erro le paga un shard al que acerto, y nada mas. Lo que del lado de los
que erraron no encuentra con quien cruzarse vuelve a quien lo puso, con su propio
cartel para que no se lea como un premio. Sin comision de la casa. Lo que la
division no da entero se lo lleva el que gano la pelea: tiene que ir a algun
lado, o los shards se irian evaporando de a uno por duelo.

Antes era el reparto de una rifa de carrera —todo lo de los que erraron repartido
entre los que acertaron, en proporcion— y en la primera pelea con apuestas de
verdad (2026-09-21, PEPE contra Felix1256) eso pago **65 shards por una apuesta de
10**, porque del otro lado habia uno solo que habia puesto todo lo que tenia. La
cuenta cerraba igual, pero con dos o tres apostadores las cuotas salen de
cualquier lado, y un shard vale diez minutos de juego o una kill. Una apuesta
tiene que pagar algo que el que la hace pueda calcular antes de hacerla.

Los shards de un duelo **no se crean ni se destruyen**: los que pone el que
pierde son exactamente los que cobra el que gana. Es la unica forma de meter
apuestas sin inflar la economia.

Si nadie le aposto al que gano, a los que erraron se les devuelve todo: perder
porque nadie mas jugo no es perder.

### El torneo

`/pvp torneo abrir <entrada>` (operadores), la gente se anota con `/pvp torneo
entrar`, y `/pvp torneo arrancar` arma la llave. Cada cruce es un duelo normal
(kit al azar, la misma arena, la gente apostando) y el campeon se lleva el pozo
entero de las entradas.

**Se encola una pelea por vez y no la ronda entera.** Encolar la ronda completa
suena mas prolijo y es una fuente de peleas fantasma: alguien se desconecta, su
llave desaparece de la cola y el torneo se queda esperando un resultado que no va
a llegar nunca. Al que se desconecta antes de que le toque no se lo saca de la
lista: se resuelve cuando le toca, y ahi el que estaba pasa de ronda sin pelear.

Funciona con cualquier cantidad de anotados y no solo con 4, 8 o 16: el que queda
desparejo en una ronda pasa derecho. Si se cancela el torneo, la entrada vuelve a
**todos** los que la pagaron y no solo a los que siguen en pie.

### Los dos objetivos nuevos del scoreboard

`sdp_duelos_g` y `sdp_duelos_p`, los ganados y los perdidos de cada uno. Los crea
el mod al arrancar si no estan, que es lo que hace que esto funcione solo en un
mundo recien hecho. Son de donde sale `/pvp top`.

## Los equipos, y la barra de arriba que era un radar

Desde el 2026-09-09 se puede armar equipo con `/equipo`, y **la barra de
waypoints de arriba del inventario pasó a mostrar solamente a los del propio
equipo**. Las dos cosas son la misma decisión y por eso están juntas acá.

### Qué pasaba con la barra

26.1 trae de fábrica la barra de waypoints (la gamerule `locator_bar`, que
arranca en `true`) y de fábrica **muestra a todos los jugadores del servidor**,
con un puntito que indica para dónde está cada uno. En un survival de PvP libre
donde matar paga 10 shards, el 10% de la plata del muerto y todo el equipo que
llevaba puesto, eso es un radar: nadie se esconde, nadie se escapa y salir a
buscar pelea deja de tener mérito. Es exactamente lo que hizo sacar los
minimapas de Xaero el 2026-09-06, y por el mismo motivo.

Se podía apagar entera con `gamerule locator_bar false`, y **no se hizo así**:
apagada no molesta a nadie pero tampoco le sirve a nadie. Con
**team-only-locator-bar** la misma barra queda mostrando únicamente a los del
mismo equipo del scoreboard, o sea que pasa a ser lo que hace que valga la pena
armar equipo. El que no tiene equipo no ve a nadie, que es lo que se pedía.

Si algún día igual se la quiere sin barra: `gamerule locator_bar false`. El mod
no molesta con la gamerule apagada, simplemente no hay barra que filtrar.

### Los dos mods, y por qué ninguno toca el launcher

| Jar | De dónde | Qué hace |
|---|---|---|
| `equipos-sobrinosdepepe-1.0.0.jar` | nuestro, `mod-equipos/` | el comando `/equipo` |
| `team-only-locator-bar-1.0.0+MC26.1.jar` | Modrinth, MIT, 5 KB | la barra muestra solo al equipo |

Los sube `python servidor/subir-equipos.py`, que reinicia si alguno era nuevo.
**A nadie hay que pasarle ningún jar**, ni siquiera a los que juegan sin el
launcher: el nuestro declara `environment: server`, y team-only-locator-bar
declara `environment: *` pero su único entrypoint es `main` y su único mixin es
sobre `WaypointTransmitter.doesSourceIgnoreReceiver`, que corre del lado del
servidor. Es el servidor el que decide a quién le manda cada punto de la barra:
si el que transmite y el que recibe no están en el mismo equipo, no se manda.

### Los comandos

    /equipo                      quiénes son y qué podés hacer
    /equipo crear <nombre>       armar uno, y sos el jefe
    /equipo invitar <jugador>    solo el jefe, y el otro tiene que estar conectado
    /equipo aceptar <equipo>     entrar, mientras la invitación no venza
    /equipo echar <jugador>      solo el jefe
    /equipo salir                irte

No piden ser operador: los registra nuestro mod sin `requires`, así que no hay
nada que darle al grupo `default` de LuckPerms ni ningún modificador de Melius
que escribir.

Dónde se anuncian, que es la mitad del trabajo: **EQUIPO** es una categoría del
menú principal (`/ayuda`), en el medio de la fila, y abre `sdp:equipo`, que tiene
el cartel de qué es un equipo y un botón por comando. La misma puerta está
repetida adentro de **PVP**, porque el que entra ahí a ver cómo se pelea tiene
que enterarse de que se puede armar equipo. Y en la lista del chat (`/comandos`)
los seis van en un grupo propio: un `/equipo` pelado no cuenta que se invita, que
se echa ni que hay un jefe. La invitación llega con un `[ ENTRAR ]` clickeable y **dura dos
minutos**; vive en memoria y no en disco, porque una invitación que sobrevive a
un reinicio es una invitación que nadie se acuerda de haber mandado.

Entran **seis por equipo**. El tope no es decoración: la barra muestra a los
compañeros, así que un equipo con medio servidor adentro es el radar de todos
contra todos que justamente se sacó.

### `/sz bichos`: una zona sin hostiles

Safe Zone no lo tiene: una zona suya es siempre lo mismo para todas. Lo agrega el
mod de la varita, se guarda en `config/zonas-de-pepe.json` por `claimId`, y se
pone parado adentro de la zona:

    /sz bichos      adentro no hay hostiles
    /sz modos       que zonas lo tienen puesto

Cuelga del `/sz` que ya existe porque es donde el que administra lo va a buscar.
Brigadier fusiona los hijos cuando dos mods registran el mismo literal, igual que
`/pvp` con Melius. **El `requires` de nuestra raiz esta copiado del de Safe Zone a
proposito**: de los dos nodos raiz sobrevive el del que registro primero, con SU
permiso, y el orden entre dos mods no lo decide nadie. Si el nuestro fuera mas
permisivo y ganara la carrera, todo el `/sz` —`reload`, `removeall`, `limits`— le
quedaria abierto a cualquiera. Con el mismo permiso —nivel 2, `GAMEMASTERS`— el
resultado es el mismo gane quien gane.

Son dos cosas: el hostil que aparece adentro se va en el mismo tick
(`ServerEntityEvents.ENTITY_LOAD`), y una vez por segundo se barre la zona entera
para el que entro caminando. La caja del barrido va de lo mas bajo a lo mas alto
del mundo porque una zona **es la columna entera**: su proteccion compara solo X
y Z. Se van solo los hostiles y solo los que nadie protegio con la varita:
`discard()` se lleva puesto al invulnerable igual.

### El coliseo adentro de una zona protegida

Safe Zone **no toca el daño entre jugadores**: adentro de una zona dos se siguen
pegando igual. Lo que cancela es poner y romper bloques al que no es dueño ni
trusted, y los kits de duelo son de poner bloques: obsidiana y crystals el
CRISTALERO, TNT el BOMBARDERO, y los ocho traen bloques para taparse. Con el
coliseo adentro del spawn protegido no se podia jugar ninguno.

**El agujero lo abre el duelo, no un comando.** `duelos/mixin/ClaimManagerMixin`
entra en `ClaimManager.canBuild` y contesta `ADMIN_BYPASS` cuando se dan las tres
condiciones juntas, que son las de `Duelos.dejaConstruir`:

- **hay un duelo ahora**, asi que entre pelea y pelea el coliseo esta tan
  protegido como el resto del spawn;
- **es uno de los dos que pelean**, asi que el de las gradas no rompe nada aunque
  este parado adentro;
- **es adentro de la caja de la arena**, que es lo unico que se fotografia antes
  del duelo y vuelve entero al terminar. Un bloque puesto un metro afuera de la
  caja se quedaria ahi para siempre, y desde adentro se llega: el brazo de un
  jugador pasa la pared.

Hubo antes un `/sz construir` que abria la zona entera para todos. Duro una
tarde, por lo obvio: adentro del coliseo eso dejaba que cualquiera picara la
estructura entre pelea y pelea.

**Por que `canBuild` y no `getClaimAt`.** `getClaimAt` seria mas corto y apagaria
la zona entera en ese punto, pero **no sabe quien pregunta**: recibe una posicion
y nada mas. `canBuild` recibe el jugador, que es justo lo que hay que mirar.

Lo que eso cuesta: las explosiones van por `getClaimAt` y no por `canBuild`, asi
que adentro de una zona la TNT y los crystals **siguen sin romper el piso**. Se
puede poner la TNT, se puede prender y le hace daño al otro igual, que es de lo
que se trata la pelea; lo unico que no pasa es el crater. Es el precio de que
nadie mas pueda tocar el coliseo, y es barato: la arena se devuelve entera al
terminar, asi que el crater duraba lo que duraba la pelea.

**Por que un mixin y no un evento nuestro.** Los eventos de Fabric se cancelan si
CUALQUIERA de los que escucha dice que no, y no hay forma de des-cancelar lo que
ya cancelo otro mod. Safe Zone dice que no antes de que lleguemos a opinar.

**Y hace falta un segundo mixin para que los crystals peguen.** Safe Zone decide
la inmunidad a explosiones con `ClaimEntityProtection.hasTrustedClaimAccess`, y
ese metodo no pregunta si sos dueño ni si sos trusted: pregunta **`canBuild(vos,
donde estas parado) != DENIED`**. Usa el permiso de construir como si fuera la
definicion de "este es de los de casa". Asi que el mismo mixin que deja a los
duelistas poner obsidiana los volvia inmunes a las explosiones: los end crystals
del CRISTALERO se plantaban, reventaban y no le hacian un rasguño a nadie. Lo
mismo la TNT del BOMBARDERO. `ClaimEntityProtectionMixin` le contesta que no al
que esta peleando adentro de la arena, y vuelve a ser lo que era.

Adentro de una zona protegida un duelo queda asi:

| | |
|---|---|
| poner y romper bloques, los dos que pelean | **si** (`ClaimManagerMixin`) |
| daño y empujon de las explosiones | **si** (`ClaimEntityProtectionMixin`) |
| que la explosion rompa el piso | **no** |

Lo ultimo porque eso Safe Zone lo decide por posicion y no por jugador, y
abrirlo dejaria que cualquiera volara el coliseo. La arena se devuelve entera al
terminar igual, asi que el crater duraba lo que duraba la pelea.

`PermissionResult.ADMIN_BYPASS` se busca **por reflexion**, el mismo camino que
usa el mod de acceso con EasyAuth: sin eso habria que tener el jar de Safe Zone
para compilar, y el dia que cambie de version no compilaria mas. Y si Safe Zone
no esta puesto, Mixin avisa en el log que no encontro la clase y sigue de largo,
que es lo que se quiere: los duelos tienen que andar en un servidor sin zonas.

### Zonas anidadas: no existen

`ClaimValidator.validate` recorre todas las zonas y rechaza la nueva si toca
cualquiera, con `TOO_CLOSE_TO_EXISTING_CLAIM`. No hay prioridad, no hay "la de
adentro manda" y **no hay excepcion para el administrador**: el unico chequeo con
bypass es el del limite de zonas. El `claimGapEnforced` en false solo saca los 10
bloques de separacion extra (`effectiveMinDistance()` devuelve 0); el
solapamiento se sigue rechazando.

O sea que una zona chica adentro de una grande no se puede hacer. Si el coliseo
tiene que quedar adentro del spawn, es **una sola zona** con `/sz construir`
puesto, o el spawn en varias zonas alrededor del coliseo. Y de todas formas van a
ser varias: `maxClaimWidth`/`maxClaimDepth` estan en 128.

### Lo que da un equipo, y lo que no

Da tres cosas, y las tres las hace el juego y no nosotros, porque cada equipo es
un **equipo del scoreboard** de vanilla llamado `sdp_<nombre en minúscula>`:

- **entre compañeros no se pega** (`friendlyFire` en `false`), salvo adentro de
  un duelo: ver "El duelo le gana al equipo" mas abajo;
- **el nombre va del color del equipo**, que es cómo en una pelea se ve de un
  vistazo quién es de quién. Se reparten ocho colores, el primero libre;
- **se ven en la barra de arriba**, que es lo de más arriba.

No da nada más, a propósito: no hay teletransporte al compañero, no hay chat de
equipo, no hay casas compartidas, y matar a un compañero (saliéndose del equipo
primero) sigue pagando exactamente lo mismo. El enfriamiento de diez minutos por
víctima de `sdp:pagar_kill` es el que ya impedía que dos amigos se maten en loop,
y los equipos no lo tocan.

### Dónde vive quién está con quién

La fuente de verdad es **`config/equipos-de-pepe.json`**, y no el scoreboard:

```json
{ "equipos": [ { "nombre": "LOSMONOS", "color": "aqua",
                 "jefe": "PEPE", "miembros": ["PEPE", "JUAN"] } ] }
```

Es al revés de lo que uno esperaría, y es por dos motivos. Un equipo del
scoreboard **no tiene dónde guardar quién es el jefe**, y sin jefe no hay a quién
pedirle permiso para invitar ni a quién echar a nadie. Y un archivo se abre desde
el panel y se entiende de una: si algo queda raro, se corrige ahí y se reinicia.

El scoreboard es un espejo que se rearma en cada cambio y al arrancar el
servidor. Al arrancar, además, **borra los equipos `sdp_*` que ya no están en el
archivo**, que es lo que pasa si se lo editó con el servidor apagado. Lo baja
`respaldar.py` a `servidor/equipos/`, y hasta que alguien arme el primer equipo
el archivo no existe.

### Las tres trampas de esto

- **`team option` no existe.** El comando es `team modify <equipo> friendlyFire
  <bool>`, y sirve para verificar sin adivinar: si contesta *"Nothing changed.
  Friendly fire is already disabled for that team"*, el mod lo dejó bien. Y ojo
  que las opciones de equipo siguen en camelCase (`friendlyFire`,
  `seeFriendlyInvisibles`, `nametagVisibility`) aunque las gamerules de 26.1 sean
  snake_case.
- **`team list` muestra el nombre de pantalla, no el id.** Un equipo que se llama
  `sdp_losmonos` aparece como `[LOSMONOS]`. Para verlo por su id hay que
  nombrarlo: `team list sdp_losmonos`.
- **Ya había un equipo en el servidor y no es nuestro.** `noTags` lo crea
  Server-Side Horror (está en su `CommonClass`) y adentro tiene un solo
  miembro, `MarsThePlanet_`, que es el jugador fantasma del mod. Por eso los
  nuestros van con el prefijo `sdp_`: sin él, alguien podía crear un equipo
  llamado igual y meterse ahí.

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
panel** para que llegue a los jugadores.

**Publicar el pack NO interrumpe a nadie.** El launcher pide el pack y sincroniza
los mods adentro del flujo de JUGAR (`PackAsync` y después `PackInstaller.
ApplyAsync`, en `HomeViewModel`), y no hay ningún vigilante en segundo plano: a
quien está jugando no le pasa nada y el jar nuevo le llega la próxima vez que
aprieta JUGAR. Y como este mod es `environment: client`, al servidor no le
importa el desfase. Acá decía que convenía publicar sin nadie conectado: no hace
falta.

### Sí se puede compilar en esta PC

Acá decía que no había ningún JDK 25 y que por eso el mod solo se podía rearmar
cambiándole el `precios.json` adentro del zip. Cambiar la clase **sí** se puede:
se baja un JDK 25 y se le pasa a Gradle por `JAVA_HOME`, sin instalar nada ni
tocar el proyecto.

```
curl -sL -o jdk25.zip https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip
cd mod-precios
JAVA_HOME=<donde se extrajo>/jdk-25.0.4.1+1 ./gradlew build
```

El `build.gradle` pide `options.release = 25` y el JDK 17 que hay instalado no
puede: los `.class` de 26.1 son formato 69, o sea Java 25.

### El precio adentro de un cofre

Hasta el 2026-09-07 el precio **no aparecía adentro de un cofre**, y eso era un
filtro puesto a propósito que se llevaba puesto el caso más útil. El mod tenía un
`esTuyo()` que solo mostraba el precio si el `ItemStack` era el mismo objeto que
alguno de los slots del inventario del jugador. Los items de un cofre son copias
aparte, así que nunca coincidían.

El filtro existía por una razón real: adentro del `/shop` de EconomyCraft nuestra
línea quedaba repetida, porque el mod ya escribe ahí cuánto sale y cuánto paga. Y
del lado del cliente **un cofre y el menú de una tienda son los dos un
`ChestMenu`**: no hay forma de distinguirlos.

La versión 1.7.0 lo cambia por un chequeo de contenido, `yaMuestraPrecio()`, que
no necesita distinguirlos: **si el tooltip ya tiene un `$` seguido de un dígito,
no se agrega nada.** El ancla es que `EconomyCraft.formatMoney` siempre arranca
con `"$"` (`return "$" + new DecimalFormat("#,##0", symbols).format(amount)`), así
que cualquier precio que dibuje el mod —en el `/shop`, en el `/ah`, en las
órdenes o en el `/eco admin`— lo trae.

Es mejor que filtrar por el título del menú: `MenuUiSupport.openMenu` recibe el
título como un `String` literal del código que lo llama, así que atarse a esos
textos se rompe en silencio en cuanto el mod cambia una palabra.

Los botones de nuestros propios menús siguen tapados por `esBoton()`, que lee la
marca `custom_data.sdp`. Lo único que se pierde: un item que alguien renombre con
algo como "$5" no muestra el precio.

Ahora el precio se ve en el inventario, en la mano y adentro de cualquier
contenedor de verdad: cofres, barriles, shulkers y el cofre de ender.

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
- **`GameProfile` es un record y `getName()` no existe**: en la authlib de 26.1
  el accesor es `name()`. Para el nombre de un jugador desde un mod conviene
  igual `getScoreboardName()`, que es el mismo nombre con el que trabajan los
  equipos y el scoreboard.
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
