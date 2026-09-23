# Mudanza del lobby y el coliseo al overworld

> **HECHA el 2026-09-22.** Este documento queda como el encargo y el diagnóstico;
> lo que hay hoy está en `servidor/LEEME.md`. En dos líneas: el lobby quedó en
> `x=500.000, y=250, z=0` y el coliseo en `x=501.145, y=250, z=11.590`, los dos en
> el overworld y por encima de las nubes. Se eligió la opción 1 del borde: el
> overworld pasó a 30.000.000 y el límite del survival lo hace `sdp:tick`. No hubo
> que limpiar terreno con `/fill`: el destino se midió antes y el terreno no pasa
> de y=103. La copia se verificó bloque por bloque y dio idéntica.

Este documento es el encargo para hacer la mudanza. Está escrito para que alguien
que no vio nada de lo anterior pueda hacerla sin romper nada.

---

## Quién sos y cómo trabajar

Trabajás con Tobías, dev TypeScript, argentino. Hablale de **vos**, en español
rioplatense, sin rodeos. Quiere cosas **simples y funcionando**, no arquitecturas.
Cortá el alcance: nada "por si acaso".

**Cuidá los tokens**: nada de abrir veinte archivos porque sí, y nada de disparar
subagentes en paralelo sin avisarle antes.

**Verificá todo contra el servidor de verdad.** Este proyecto ya se comió tres bugs
que parecían imposibles y todos se resolvieron mirando el log, no razonando.

---

## El proyecto

Servidor de Minecraft privado **SOBRINOS DE PEPE**, para unos 20 amigos.

| | |
|---|---|
| Dirección | `sobrinosdepepe.com` |
| Servidor | **Fabric 26.1.2** (los clientes van en 26.1, mismo protocolo) |
| Hosting | Minehost, panel **Pterodactyl** (`pterodactyl.minehost.com.ar`) |
| RAM | 5 GB, y **se queda sin RAM cada tanto** |
| Modo | **offline-mode**: el UUID sale del nombre, `OfflinePlayer:<nombre>` v3 |
| Mundo | el overworld vive en `/world/dimensions/minecraft/overworld/` |
| Jugadores | `/world/players/data/<uuid>.dat` (¡no es `playerdata`!) |

Todo se maneja desde `servidor/*.py` con la API del panel. El helper es
`servidor/mc.py`: `mc.cmd()`, `mc.read()`, `mc.write()`, `mc.upload()`, `mc.ls()`,
`mc.delete()`, `mc.power()`, `mc.call()`. Las credenciales salen de
`web/.env.local`, que no está en el repo.

Los mods propios se compilan con **JDK 25**, que en esta PC solo existe adentro del
runtime del launcher:

```
cd mod-lobby
JAVA_HOME="/c/Users/Tobi/AppData/Local/SobrinosDePepe/game/runtime/windows-x64/java-runtime-epsilon" ./gradlew build
```

Los mappings son **Mojang oficiales**, no yarn, y en 26.x cambiaron nombres:
`Identifier` (no `ResourceLocation`), `ResourceKey.identifier()` (no `.location()`),
`entity.entityTags()` (no `getTags()`), `quien.level().getServer()`,
`jugador.getName().getString()`, `nameAndId()` para `isOp`.

---

## Qué hay hecho hoy

**`mod-lobby`** — al entrar, todos caen en un lobby con el inventario vacío y una
perla en la mano; la perla abre un menú de cofre; desde ahí eligen SURVIVAL (vuelven
exacto a donde estaban, con todo) o COLISEO. Guarda el inventario completo en
`config/lobby-de-pepe.json` **antes** de vaciarlo.

**`mod-duelos`** — el coliseo: 1v1, 2v2 y equipo contra equipo, con lista de espera
que cruza a los que se anotan, kit prestado al azar igual para todos, apuestas en
shards, espectadores en modo espectador que vuelven a donde estaban, y la arena que
se restaura sola al terminar.

**Otros mods propios**: `mod-acceso` (entrar con el launcher), `mod-varita` (zonas),
`mod-equipos` (`/equipo`), `mod-precios`, `mod-cofres`.

**Menús**: son menús de cofre de Inventory Menu, generados por
`servidor/generar-menus.py` y servidos desde el datapack. **Los ids llevan
namespace**: `menu sdp:juegos`, no `menu juegos`.

---

## Por qué se muda: el problema

Hoy el lobby es la dimensión `sdp:lobby` y el coliseo es `sdp:coliseo`. **Eso no se
puede sostener.**

Al conectarse un jugador que estuvo en una dimensión propia, el servidor lo agrega
dos veces al mundo:

```
Force-added player with duplicate UUID 7a067f19-...
UUID of added entity already exists: ServerPlayer['PEPE'/531, x=0.50, y=0.00, z=0.50]
```

La segunda alta se rechaza, el jugador **nunca entra de verdad al nivel** y el
cliente se queda en "Cargando el terreno…" para siempre, o con el mapa vacío.

Se intentaron las tres variantes posibles y **todas fallan**:

1. Sacarlo del lobby en `ServerPlayConnectionEvents.DISCONNECT` → ese evento corre
   en el **hilo de red** (`[Netty Epoll IO #56]`) y **después** de que el servidor
   guardó el archivo del jugador. **Esto borró el inventario de dos jugadores de
   verdad.**
2. No tocarlo al salir → queda guardado adentro de la dimensión → duplicado al
   reconectar.
3. Sacarlo en `ServerPlayerEvents.LEAVE` (hilo del servidor, antes del guardado) →
   el teleport durante la desconexión **igual** deja el UUID colgado en el otro
   nivel → duplicado.

Diagnóstico: `Force-added player` aparece **solo en el login**, nunca en un
teleport. Los `/tp` y `/tpa` entre dimensiones funcionan bien. Lo que rompe es que
existan dos niveles que puedan discutir de quién es el UUID del jugador.

**Los servidores grandes (Hypixel, DonutSMP) no usan dimensiones: usan un proxy con
un servidor por modo.** Eso pide más RAM de la que hay. La salida para esta escala
es meter todo en el overworld.

---

## El encargo

**Mudar el lobby y el coliseo al overworld**, lejísimos del spawn, y que todo se
sienta exactamente igual que ahora.

Tobías lo pidió así:

> "la idea es que funcione todo perfecto como está en cada lugar pero ahora en el
> overworld, el coliseo no uses el que ya tenemos, mové el nuevo a un lugar bien
> lejano. Que se juegue y se sienta como está ahora pero siempre todo adentro del
> overworld. Entrás al lobby, es indestructible, no hay mobs, y después tenés la
> ender pearl para ir a donde quieras."

Al terminar, **las dimensiones `sdp:lobby` y `sdp:coliseo` dejan de existir** y con
ellas se caen todos los parches que existían solo para sostenerlas.

---

## Decisiones técnicas, con sus trampas

### Dónde ponerlos

Tobías pidió "500.000 bloques o más, y bien alto". Ojo con las dos cosas:

- **"Bien alto" no existe**: el overworld va de y=−64 a y=320 y no se puede
  cambiar. La separación tiene que ser horizontal.
- **El world border hoy es de 30.000 de ancho (15.000 de radio)** y lo pone
  `servidor/configurar-borde.py` desde `WORLD_BORDER_RADIUS` en `web/.env.local`.
  **Afuera del borde el juego daña y empuja al jugador**, así que no se puede
  poner el lobby afuera y listo.

Hay que elegir una y decírselo:

1. **Agrandar el borde y reemplazarlo por uno blando** (recomendada): el borde de
   verdad pasa a 30.000.000 (sin límite práctico), el lobby va a ~500.000 y el
   coliseo a ~520.000, y el límite del survival se hace con el datapack: al que se
   aleja más de 15.000 del spawn **y no está en las cajas del lobby o del coliseo**,
   se lo devuelve. Conserva lo que el borde daba y habilita las zonas remotas.
2. **Dejarlos dentro del borde**, a ~14.000 del spawn. No se toca nada del borde.
   Alguien podría llegar volando en unos minutos, pero no puede romper nada porque
   la zona está protegida.

### Traer las construcciones

Con `/clone from <mundo> <esquina> <esquina> to <mundo> <destino>`, que **funciona
entre dimensiones** (probado en este servidor). Recetas que ya funcionaron:

- Va **por franjas**: un clone no puede pasar de 32.768 bloques.
- Con `force`, porque origen y destino pueden compartir coordenadas.
- Las dos áreas tienen que estar con **`forceload`** antes, o el comando contesta
  "that position is not loaded" y no copia nada. Sacar el forceload después.
- El servidor contesta `Successfully cloned N block(s)` — ojo que el filtro del log
  busque esa frase con minúscula.

**Verificá la copia bloque por bloque**, no confíes: bajá los archivos de región de
origen y destino y compará. La mudanza del coliseo dio **32.112 bloques no-aire en
los dos, cero faltantes, cero sobrantes, cero distintos**. Hay parsers de NBT y de
región ya escritos en `servidor/mudar-coliseo.py` y en el historial del proyecto.

### Lo que el overworld trae y las dimensiones no tenían

En una dimensión vacía, el lobby flotaba en la nada. En el overworld hay que tapar
cuatro cosas a mano:

1. **Terreno alrededor**: en el destino va a haber montañas, cuevas y agua. O se
   limpia con `/fill` de aire una caja generosa alrededor, o se elige una altura
   donde no moleste. El lobby tiene que verse como ahora.
2. **Mobs**: el mod hoy borra los `Mob` que aparecen en la dimensión del coliseo
   (`ServerEntityEvents.ENTITY_LOAD`). Eso pasa a ser **por caja de coordenadas**:
   si el bicho aparece adentro de la caja del lobby o del coliseo, se descarta.
   Respetá al invulnerable, que es la marca que deja la varita sobre un animal que
   alguien quiso cuidar.
3. **Protección**: hoy "no se rompe nada" se decide por dimensión. Pasa a decidirse
   por caja. Los operadores quedan afuera de la protección.
4. **Luz y clima**: la dimensión del lobby tenía `ambient_light` en 1.0 y nunca se
   hacía de noche. En el overworld va a haber noche y lluvia. Si queda feo, se
   resuelve con iluminación puesta a mano en la construcción.

### El código que hay que tocar

- `mod-lobby/LobbyServidor.java`: las constantes `LOBBY` y `COLISEO` (hoy
  `ResourceKey` de dimensión) pasan a ser **cajas de coordenadas en el overworld**.
  `enLobby()`, `enColiseo()` y `enLasNuestras()` pasan a preguntar por posición.
- **Sacá el `ServerPlayerEvents.LEAVE`**: con todo en el overworld ya no hace falta
  mover a nadie al desconectarse, y **tocar al jugador en la desconexión es lo que
  borró inventarios**. Que no vuelva nunca.
- El teleport de entrada puede quedarse en `servidor.execute(...)` (tick siguiente),
  que no molesta.
- `mod-duelos`: la arena se guarda en `config/duelos-de-pepe.json` con un campo
  `mundo`. Pasa a `minecraft:overworld` con las coordenadas nuevas.
- Datapack: la función `sdp:tick` tiene una línea que sube al patio al que se cae
  del lobby. Hoy pregunta la dimensión con `if dimension`, jugador por jugador
  (**ojo: `execute in <dim>` NO filtra el selector por dimensión**; escrito así
  agarraba a los que estaban bajo tierra en el survival y los devolvía al lobby
  veinte veces por segundo). Con todo en el overworld, eso pasa a mirar la caja.
- Borrar `data/sdp/dimension/` y `data/sdp/dimension_type/` del datapack.

---

## Reglas que se pagaron caro. No las rompas

1. **Al jugador que se desconecta no se le toca nada.** Ni inventario, ni teleport,
   ni borrarle su copia guardada. El `DISCONNECT` corre en el hilo de red y después
   del guardado. Esto borró inventarios de verdad el 2026-09-22.
2. **El estado guardado se consume en un solo lugar**, con el jugador adentro del
   juego y en el hilo del servidor. Una sola puerta de salida hace imposible
   perderlo *y* duplicarlo.
3. **Si el datapack no parsea, el servidor no se cae: se cuelga en "starting"** para
   siempre, y un `restart` lo deja colgado en "stopping". La única salida es
   `mc.power("kill")` y después `start`.
4. **Las dimensiones y los mods se leen al arrancar**: con `/reload` no alcanza.
   Los menús y las funciones del datapack sí se recargan con `/reload`.
5. **Antes de cualquier cosa grande, corré `servidor/respaldar-jugadores.py`.** Baja
   los `.dat` de todos y avisa a quién le quedó el inventario vacío. El panel hace
   un backup diario a las 5:30, pero el plan **permite un solo backup** y cada uno
   pisa al anterior.
6. **Fijate si hay alguien conectado antes de reiniciar** (`mc.cmd("list")` y leer
   el log). Si hay gente, avisá por chat con `tellraw` y esperá.

---

## Cómo saber que salió bien

Verificá vos lo que se pueda desde el servidor, y pedile a Tobías lo que no:

- [ ] El log no dice `Force-added player` ni `UUID of added entity already exists`
      para ningún jugador. **Esta es la prueba de que la mudanza sirvió.**
- [ ] La copia quedó idéntica: mismo conteo de bloques no-aire en origen y destino.
- [ ] `sdp:lobby` y `sdp:coliseo` ya no aparecen en `Possible world ids` del log.
- [ ] Entrar → caer en el lobby con la perla → SURVIVAL → aparecer donde estabas con
      todo tu inventario.
- [ ] **Salir estando en el lobby y volver a entrar.** Antes eso dejaba el mapa
      vacío; ahora tiene que entrar normal.
- [ ] En el lobby no se rompe ni se pone nada, y no aparecen bichos.
- [ ] El menú de la perla abre, y COLISEO lleva al coliseo.
- [ ] Una pelea 1v1 de punta a punta: dos se anotan, pelean, y los dos vuelven con
      lo suyo.

---

## Archivos que vas a tocar

```
mod-lobby/src/main/java/pe/sobrinosdepepe/lobby/
    LobbyServidor.java    las cajas, las protecciones, los eventos
    ComandoLobby.java     /lobby /survival /coliseo
    Vuelta.java           el registro de lo guardado (no lo toques, funciona)
    Guardado.java         la foto del jugador (no lo toques, funciona)
    Perla.java            el ítem del menú
mod-duelos/src/main/java/pe/sobrinosdepepe/duelos/
    Duelo.java Duelos.java Cola.java Mirones.java Arena.java Registro.java
servidor/
    mudar-coliseo.py      el mudador que ya funcionó, como modelo
    subir-lobby.py        sube mundo + mod + config, y verifica
    subir-duelos.py       sube el jar de duelos
    generar-menus.py      los menús de cofre
    configurar-borde.py   el world border
    respaldar-jugadores.py  respaldo antes de tocar nada
    datapack/data/sdp/    funciones, menús, dimensiones (estas se borran)
servidor/LEEME.md         la documentación del servidor, mantenela al día
```

El mundo del lobby original está en `lobby-e2451/lobby1 - Copy/` (64×64 bloques, un
patio a la altura 0 rodeado de edificios). El coliseo está hoy en `sdp:coliseo`, en
x 1145→1205, y 70→135, z 11590→11650, y su copia original sigue en el overworld en
esas mismas coordenadas.
