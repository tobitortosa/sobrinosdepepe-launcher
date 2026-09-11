# 02 · Arquitectura

> Versión recortada al alcance que pidió Tobías el 2026-09-03: launcher básico, panel de admin adentro del launcher, sin backups, sin modo degradado, sin planes B.
> Los datos técnicos con su fuente están en [`05-hallazgos-verificados.md`](05-hallazgos-verificados.md).

## 1. Qué hace

Un launcher de Windows para el server "SOBRINOS DE PEPE". El viewer lo baja, se crea la cuenta, Tobías la aprueba, aprieta **JUGAR** y entra al server con Minecraft 26.1, Fabric, Java 25 y los mods correctos. Con la cuenta de admin, el mismo launcher muestra cuatro pantallas más: **USUARIOS**, **MODS**, **SERVIDOR** y **MIS MODS**.

Todo lo que es de Mojang, Fabric o de los autores de mods se descarga desde sus servidores oficiales a la PC del jugador. Nosotros solo publicamos la lista con los hashes.

## 2. Pantallas

### Del jugador

```
┌──────────────────────────────────┐   ┌──────────────────────────────────┐
│           SOBRINOS DE PEPE             │   │           SOBRINOS DE PEPE             │
│                                  │   │                                  │
│  Usuario                         │   │        Hola, PEPE                │
│  ┌────────────────────────────┐  │   │                                  │
│  │ PEPE                       │  │   │   ┌────────────────────────┐     │
│  └────────────────────────────┘  │   │   │        JUGAR           │     │
│  Contraseña                      │   │   └────────────────────────┘     │
│  ┌────────────────────────────┐  │   │                                  │
│  │ ••••••••                   │  │   │       Servidor ● online          │
│  └────────────────────────────┘  │   │                                  │
│                                  │   │   Cerrar sesión · Ver logs       │
│      [ INICIAR SESIÓN ]          │   │   Créditos                       │
│                                  │   │                                  │
│       Crear una cuenta           │   │            [ ADMIN ]  ← solo vos │
└──────────────────────────────────┘   └──────────────────────────────────┘
```

Más dos estados: **pendiente de aprobación** ("Tu cuenta espera aprobación" + botón "Volver a comprobar") y **progreso de instalación** (barra en MB + detalle + cancelar). Si algo falla: mensaje concreto y botón "Copiar detalles", que copia el error y el log del juego para que te lo peguen.

### Del admin (misma app, cuenta con rol admin)

```
┌──────────────────────────────────────────────────┐
│  ADMIN                          MODS │ USUARIOS  │
├──────────────────────────────────────────────────┤
│  Pack v1.0.0 · Minecraft 26.1 · Fabric 0.19.5    │
│                                                  │
│  sodium                 0.8.9      cliente   [x] │
│  iris                   1.10.9     cliente   [x] │
│  fabric-api             0.155.2    ambos     [x] │
│  simple-voice-chat      2.6.22     ambos     [x] │
│  …                                               │
│                                                  │
│  [ + Subir .jar ]    (arrastrá los archivos)     │
│                                                  │
│  Cambios sin publicar: 2                         │
│              [ PUBLICAR PACK v1.0.1 ]            │
└──────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│  ADMIN                          MODS │ USUARIOS  │
├──────────────────────────────────────────────────┤
│  Buscar: [______________]                        │
│                                                  │
│  Pepito98      🟡 pendiente   03/09   [Aprobar]  │
│  PEPE          🟢 activo      02/09   [Banear]   │
│  Troll123      🔴 baneado     01/09   [Desbanear]│
│                                                  │
└──────────────────────────────────────────────────┘
```

## 3. Componentes

```
┌─────────────────────────┐        ┌────────────────────────────┐
│  LAUNCHER (Windows)     │ HTTPS  │  BACKEND                   │
│  C# .NET 10 · Avalonia  │───────▶│  Next.js en Vercel         │
│  CmlLib.Core · Velopack │        │  Postgres en Neon          │
│  · pantallas jugador    │        │  API key de Pterodactyl    │
│  · pantallas admin      │        │  (solo acá)                │
└──┬──────────────────────┘        └─────────────┬──────────────┘
   │                                             │ whitelist add/remove, kick
   │ descargas directas                          ▼
   │ Mojang · Fabric · Modrinth        ┌────────────────────────┐
   │ GitHub Releases (auto-update)     │  SERVER (Minehost)     │
   └── juego ─────────────────────────▶│  Fabric 26.1           │
                                       │  Pterodactyl dbd3f1e9  │
                                       └────────────────────────┘
```

| Pieza | Tecnología |
|---|---|
| Launcher | C# / .NET 10 · Avalonia 12 · CmlLib.Core 4.0.6 · Velopack 1.2 |
| Backend | Next.js en **`https://sobrinosdepepe.com`** (Vercel) · Postgres en Neon · Drizzle · Argon2id |
| Pack | lista de archivos con hashes, URL y lado cliente/servidor; la forma es compatible con el índice de un `.mrpack` de Modrinth |
| Server | Fabric 26.1 en Minehost, panel Pterodactyl |
| Mods propios | Java 25 con Fabric Loom: `mod-acceso` (solo se entra con el launcher) y `mod-precios` (el precio en la descripción de cada item) |
| Updates del launcher | GitHub Releases |

**Por qué el backend existe** aunque el panel esté en el launcher: la clave del panel de Minehost da control total del server (archivos, consola, apagarlo). Si viviera dentro del `.exe`, cualquiera que lo descompile la saca. Entonces el launcher admin llama a `/api/admin/*` y el backend es el único que habla con Pterodactyl.

## 4. Flujos

### 4.1 Instalar y actualizar el juego

1. `GET /api/pack` → la lista de mods publicada, con su hash, su tamaño y de dónde bajar cada uno.
2. Con CmlLib: Minecraft 26.1 (client.jar, 75 librerías de Windows, 4.750 assets) y el runtime **Java 25** de Mojang; después el perfil **Fabric 0.19.5**.
3. Mods: compara el sha1 de cada archivo local con el de la lista; baja lo que falta, verifica sha1 y sha512, escribe a un archivo temporal y recién entonces lo renombra. **Borra de `mods/` todo jar que no esté en la lista** (si no, quitar un mod del pack no lo quita de las PCs y el juego crashea).
   Los archivos que subió el admin salen de nuestro backend y van con la credencial de la sesión. Esa credencial se manda solo a nuestro dominio: a Mojang, a Fabric y a Modrinth no se les manda nada.
4. Configs: `config/` y `options.txt` se escriben **solo si no existen**, y después los pisa la configuración de la cuenta (4.7). El pack pone los valores por defecto; los keybinds y la sensibilidad del mouse de cada uno vienen de su cuenta.
5. Escribe `servers.dat` con "SOBRINOS DE PEPE" si no existe.

Primera instalación: ~665 MB en ~5.200 archivos, 3-5 minutos con buena conexión. Después, solo el delta.

Dos cosas que no son fallbacks y se quedan: **reintentar** una descarga que se cortó (con 5.200 archivos, que falle alguna es lo normal) y **verificar el hash** (un archivo cortado no da error de red, da un crash de Java incomprensible).

### 4.2 JUGAR

1. Chequea el pack; si cambió, actualiza.
2. Ping de estado: resuelve el SRV `_minecraft._tcp.sobrinosdepepe.minehost.pro` → `sv36.minehost.pro:25445` y muestra el punto verde o rojo.
3. Trae la configuración de la cuenta y la deja en la carpeta del juego (4.7).
4. Pide el permiso de entrada a `GET /api/ticket`, con el pack ya al día (4.6).
5. Lanza el juego con el Java privado, los argumentos que trae el JSON de Mojang, sesión offline (`--username PEPE`, UUID v3 de `OfflinePlayer:PEPE`), el permiso en `SOBRINOSDEPEPE_TICKET` y `--quickPlayMultiplayer sobrinosdepepe.minehost.pro`, que lo mete directo al server sin pasar por el menú.
6. Captura la salida del juego y `latest.log` para el botón "Copiar detalles".
7. Cuando el juego se cierra, guarda la configuración en la cuenta (4.7).

### 4.3 Cuentas

```
Se registra → activo y en la whitelist, en el mismo pedido (2026-09-07). Queda pendiente solo si el panel no contestó, y se reintenta al volver a iniciar sesión.
                                          │
                                          └─ vos baneás → banned → whitelist remove + kick
```

- Username: `^[A-Za-z0-9_]{3,16}$`, único sin distinguir mayúsculas, **inmutable**. En modo offline el UUID sale del nombre exacto: si un viewer se registra como `PEPE` antes que PEPE, se queda con su inventario y no hay vuelta atrás. Por eso las cinco cuentas actuales se crean antes de abrir el registro.
- Sin email. Si alguien olvida la contraseña, le ponés una temporal desde el panel.
- Contraseñas con Argon2id. Sesión: token opaco de 30 días guardado cifrado con DPAPI. Al banear, se borran sus sesiones.
- Límite de intentos por IP en registro y login: configuración de Vercel, no código.

### 4.4 Banear y aprobar

El backend ejecuta en el server, vía la API de Pterodactyl: `whitelist add <nombre>` al aprobar, y `whitelist remove <nombre>` + `kick <nombre>` al banear. El `kick` hace falta porque quitar de la whitelist no echa a quien ya está conectado.

**El orden importa: primero el comando en el server, después la base de datos.** Si fuera al revés y Pterodactyl fallara, quedaría una cuenta marcada como activa que no está en la whitelist: el jugador aprieta JUGAR, el server lo rechaza y nadie entiende por qué. Con este orden, si falla no se cambió nada.

Si el server está apagado, la API responde con error y el panel te dice: **"El servidor está apagado. Prendelo y volvé a apretar Aprobar. No se cambió nada."** No hay cola ni reintento silencioso.

Mientras una cuenta está pendiente, `GET /api/pack` le responde 403: ni siquiera puede descargar el juego. La pantalla de espera consulta su estado cada 20 segundos y pasa sola al Home cuando se destraba. Desde el 2026-09-07 el registro aprueba solo, así que a esa pantalla solo se llega si el panel de Minehost no contestó justo en ese momento.

**Cómo se cierra de verdad (2026-09-07):** en modo offline la whitelist filtra por nombre, no autentica: alguien con TLauncher que sepa el nombre de un jugador aprobado entraría igual. Lo que lo cierra es el permiso de entrada de 4.6, que se construyó: sin el launcher no se entra. Un permiso ya emitido vive doce horas y no se puede revocar, pero eso no salva a un baneado: banear también lo saca de la whitelist, y el permiso no es una llave para pasarla por arriba.

#### 4.5 Gestionar mods desde el launcher

1. Elegís los `.jar` que querés en el pack y los subís. Podés arrastrar varios de una.
2. El backend guarda cada archivo y averigua solo lo demás: calcula los hashes, y para saber el nombre, la versión y si el mod es de cliente o de servidor primero le pregunta a Modrinth por el hash del archivo; si no lo conoce, lee la ficha que todo mod de Fabric lleva adentro. Vos no tipeás nada.
3. Quitar un mod es apretar la cruz. El archivo se borra cuando ningún mod lo usa.
4. **Publicar pack**: el backend revisa que las descargas estén disponibles, sube el número de versión y lo deja publicado.
5. Si algún mod va también en el servidor, el panel te imprime qué jar subir por SFTP. Eso queda manual: pasa poco y son dos clics.

Los archivos quedan guardados en la base de datos. El pack completo pesa unos 20 MB, así que entra de sobra y no hace falta ningún servicio extra de almacenamiento.

También se puede agregar un mod pegando su link de Modrinth, y en ese caso el launcher lo descarga del servidor de Modrinth en vez del nuestro. Sirve para los mods grandes.

**Lo que ve el jugador cuando publicás.** Al apretar JUGAR, el launcher pide el pack, compara con lo que tiene y aplica la diferencia: baja lo que falta, borra lo que sacaste y arranca. Cuando no cambió nada tarda uno o dos segundos. Probado: publicar una versión que saca un mod hace que el launcher lo borre solo en el arranque siguiente.

Todo se verifica en cada arranque a propósito. Es rápido cuando no cambió nada, y hace que una instalación a medias se arregle sola en vez de terminar en un crash que nadie puede diagnosticar.

### 4.6 Al servidor se entra con el launcher

El servidor puede exigir que el juego se haya abierto con el launcher. El motivo original no era la seguridad: era que el launcher es lo único que garantiza que los mods y las versiones estén al día, y un servidor donde la mitad juega con otra versión de las cosas no se puede sostener. Desde el 2026-09-11 es además la puerta por donde el servidor se entera de qué mods trae cada uno.

**Es un candado con interruptor: `REQUIRE_LAUNCHER` en el `.env`.** Hoy está **puesto**.

| `REQUIRE_LAUNCHER` | Qué hace el servidor |
|---|---|
| `false` | Entra cualquiera, como siempre. Igual revisa y **anota en el log quién entró sin el launcher** (`PEPE entra SIN el launcher (cliente sin el mod). El candado esta abierto`), y se lo avisa por chat a los ops que estén jugando. Sirve para ver quién falta antes de cerrar. |
| `true` | No entra nadie sin el launcher: ve un cartel con la dirección de la página. Salvo los nombres de `LAUNCHER_EXEMPT`. |

**`LAUNCHER_EXEMPT` es la lista de los que entran igual con el candado puesto**, separados por coma: los amigos que prefieren su propio launcher. Entran derecho, sin cartel y sin permiso, y en el log queda `Fulano entra sin el launcher: esta en la lista`. Los nombres van **exactos**, con sus mayúsculas, porque el UUID de un jugador offline se calcula a partir del nombre: `TititON` y `Titit0N` son dos personas distintas para el servidor.

Y ahí está el límite de esa lista, que conviene tener escrito: **en offline, exceptuar un nombre es exceptuar a cualquiera que lo sepa escribir.** Tener la cuenta creada en nuestra base evita que otro se registre con ese nombre en el launcher, pero no evita que alguien entre con TLauncher usándolo. Por eso la lista se escribe a mano, es corta, y el que esté ahí igual tiene que estar en la whitelist del servidor.

Se cambia en `web/.env.local` y se aplica con `python servidor/subir-acceso.py`. **El mod lee su config en cada intento de entrar, no una sola vez al arrancar**, así que prender o apagar el candado, o tocar la lista, no reinicia el servidor ni saca a nadie del juego. Lo único que pide reinicio es un `.jar` nuevo, porque Fabric carga los mods una sola vez.

Cuando el archivo de config falta o no se puede leer, el mod **exige** el launcher: es el lado que no deja entrar por error a quien no corresponde. Abrir el servidor entero tiene que estar escrito a mano en el archivo.

Cómo funciona, de punta a punta:

1. El jugador aprieta **JUGAR**. El launcher deja el pack al día y recién entonces pide `GET /api/ticket`.
2. El backend firma un permiso con `ACCESS_SECRET`: `1:PEPE:1757260000:<hmac>` — versión, nombre, cuándo vence y la firma. Dura **doce horas** y no se guarda en ninguna tabla.
3. El launcher arranca el juego con el permiso en la variable de entorno `SOBRINOSDEPEPE_TICKET`. No es un archivo ni un argumento: un archivo se copia junto con la carpeta de mods, y los argumentos se ven en el administrador de tareas y terminan pegados en los reportes de error.
4. Mientras el jugador entra, el mod `accesodepepe` del servidor le manda un pedido por el canal `sobrinosdepepe:acceso`. El mismo mod del lado del cliente contesta dos cosas: el permiso y **la lista de los mods que tiene cargados**.
5. El servidor revisa la firma con el secreto que tiene en `config/acceso-de-pepe.json`. No consulta al backend: valida solo. Con el candado abierto hace la misma revisión y solo la escribe en el log.

Los cinco carteles que puede ver quien no entra, cada uno con la dirección de la página:

| Qué pasó | Qué ve |
|---|---|
| Entró con TLauncher o cualquier Minecraft sin nuestros mods | "Este servidor se juega con el launcher. Descargalo acá…" |
| Tiene los mods (le pasamos la carpeta) pero abrió el juego por su cuenta | "Abriste el juego sin el launcher. Cerrá Minecraft y entrá con el botón JUGAR" |
| Su permiso venció, o dejó el juego abierto medio día | "Tu permiso de entrada venció. Volvé a apretar JUGAR" |
| El permiso no verifica, o es de otro jugador | "Tu permiso de entrada no es válido" |
| Tiene cargado un cliente de trampas | "Tenés cargado un mod que acá no se puede usar: meteor-client. Sacalo de la carpeta mods…" |

**La lista de mods.** El cliente manda los nombres de los mods que Fabric cargó de verdad (solo los de arriba de todo: los cuarenta módulos que Fabric API trae adentro no cuentan). El servidor hace dos cosas con eso:

- **Los escribe en el log**, en una línea por login: `Los mods de PEPE: accesodepepe,fabric-api,iris,lithium,sodium,…`. Es la forma de saber con qué juega cada uno sin preguntarle.
- **Busca los que no van** (`Tramposos.java`): Meteor, Wurst, los xray, Baritone, freecam y compañía. Al que tenga uno no lo deja entrar, le dice cuál es, y le avisa por chat a los ops que estén jugando. Se revisa **antes que el permiso y antes que la lista de exentos**: un cliente de trampas no entra ni con el permiso en la mano ni por ser amigo.

Es una lista de prohibidos y no de permitidos a propósito. La de permitidos sería más fuerte, pero tendría que salir del pack publicado, y el día que se publica un pack nuevo el servidor se quedaría con la lista vieja y echaría a todos los que ya se actualizaron. Una lista de prohibidos nunca hace eso: lo peor que puede pasar es que no reconozca un mod nuevo, y para eso está el log.

**Por qué el cliente sin el mod se delata solo.** El pedido del servidor viaja durante el login, y el protocolo de Minecraft obliga a contestarlo: un cliente que no conoce el canal responde "no entendí", y eso alcanza para saber que no tiene nuestros mods. No hace falta que el jugador coopere.

**Por qué el nombre va adentro de la firma.** Si no estuviera, el permiso de uno serviría para cualquiera: te lo paso por Discord y entrás con TLauncher. Con el nombre firmado, el servidor lo compara con el del login y un permiso prestado no sirve.

**El link no está escrito en ningún lado del código.** Sale de `SITE_URL` en el `.env` del backend, y de ahí lo copia `servidor/subir-acceso.py` a la config del servidor. El día que compremos un dominio se cambia en un solo lugar y se vuelve a subir.

**Límite honesto:** el dueño de un permiso puede usar el suyo para entrar con su propio nombre desde otro launcher, si se toma el trabajo de copiar el mod y armar la variable de entorno. Y la lista de mods la manda el cliente, así que el que se tome el trabajo de tocar nuestro mod para que mienta, pasa. Las dos cosas tienen el mismo techo y no hay forma de subirlo: **no existe manera de que el servidor sepa qué hay del otro lado sin preguntarle al otro lado.** Por eso lo que de verdad frena las trampas no vive acá, sino adentro del servidor, donde el cliente no llega: el anti-xray y el anticheat de movimiento (4.9).

**Lo que hay que subir cuando cambia.** El mod es un solo `.jar` que va en los dos lados: al cliente por el pack (`npm run pack:jar`) y al servidor por `python servidor/subir-acceso.py`, que además le escribe la config, echa a los que estén jugando con el motivo escrito y reinicia. El orden importa y está en el encabezado de ese script: primero las variables en Vercel, después el pack, después el launcher nuevo, y el servidor al final. Al revés, no entra nadie.

### 4.7 La configuración viaja con la cuenta

Las teclas, la sensibilidad del mouse, el FOV, el volumen y los ajustes de los mods se guardan en la cuenta, no en la computadora. El launcher los baja antes de abrir el juego y los sube cuando el juego se cierra, que es cuando Minecraft termina de escribir sus archivos.

Es `options.txt` más la carpeta `config/` entera, en un zip de unos 200 KB. Quedan afuera tres archivos que son de la máquina y no de la persona: `sodium-fingerprint.json` (es de la placa de video), `usercache.json` y `servers.dat` (lo escribe el propio launcher en cada instalación).

Para qué sirve, en concreto: el que se sienta en otra computadora se encuentra sus propias teclas, y el que presta la suya las recupera enteras cuando vuelve a entrar. Si la novia de Tobías entra con su cuenta en la máquina de él, juega con lo suyo; cuando él vuelve a entrar, lo suyo está esperándolo.

Tres detalles que hacen que funcione:

- **El zip sale siempre igual.** Las entradas van ordenadas por nombre y con la misma fecha, así que el mismo contenido da el mismo sha1. De eso depende no escribir en la base ni pisar archivos cuando en realidad no cambió nada.
- **Hay un marcador local** (`settings.json`, en la raíz del launcher) que dice de quién son los ajustes que están en la carpeta y con qué hash se sincronizaron. Sin eso no habría forma de saber si lo que está en disco es de esta cuenta o de la anterior.
- **Al aplicar se borra de `config/` lo que el zip no trae.** Si no, el que entra en una máquina prestada se queda con los ajustes del dueño mezclados con los suyos. Lo que se borra son configs de mods: si falta alguna, el mod la vuelve a escribir con sus valores por defecto.

Que esto falle no puede impedir jugar: se avisa en la línea de estado y se sigue con la configuración que haya en la máquina. Jugar con las teclas de otro es molesto; no poder entrar porque no se pudo sincronizar un archivo de ajustes es peor. Al revés sí se avisa fuerte: si no se pudo **guardar**, en otra máquina no va a encontrar lo que acaba de cambiar.

### 4.8 Los mods propios del admin

Una cuenta puede tener mods además del pack. Se administran en la pestaña **MIS MODS** del panel, se instalan solos en cualquier computadora donde entre esa cuenta, y no se publican: nadie más los recibe.

**Solo las cuentas admin pueden tener, y eso lo decide el backend.** No es una comodidad: es lo único que separa esto de la vieja `mods-propios.txt` —un archivo de texto en la PC del jugador donde se podía nombrar cualquier jar para que la sincronización no lo borrara, un xray incluido—. Ahora la lista de lo que sobrevive al borrado no la escribe el jugador en su máquina: la decide el backend mirando la cuenta. Un jugador puede tocar el launcher todo lo que quiera y no se va a poder agregar nada, porque `POST /api/my-mods` le contesta 403 y `GET` le contesta la lista vacía.

El launcher los suma a la lista del pack antes de sincronizar, así que se bajan con el hash verificado como todo lo demás y la limpieza de la carpeta no los borra. Del lado del servidor no cambia nada: el mod de acceso sigue mirando la lista de mods de todos los que entran (4.6), y un cliente de trampas no entra por ser del admin.

Hoy ahí vive uno solo: **NoWheel**, que desactiva el cambio de slot con la rueda del mouse.

### 4.9 Las trampas

Todo lo de 4.6 depende de que el cliente diga la verdad. Lo de acá no: vive entero adentro del servidor y no hay cliente que lo esquive. Se instala y se actualiza con `python servidor/subir-antitrampas.py`, que baja las versiones fijas verificando el hash.

| Trampa | Qué la frena | Cómo |
|---|---|---|
| **Xray** (ver dónde hay diamantes o netherita a través de la piedra) | **Anti Xray** de DrexHD, el anti-xray de Paper portado a Fabric | El servidor no le manda al cliente los minerales que están tapados: le manda piedra. Un xray no puede mostrar lo que nunca le llegó. Overworld en modo 3, nether en modo 1 (con la netherita), en `config/antixray.toml` |
| **Vuelo, speed, reach, killaura, nofall, timer** | **GrimAC** | Simula el movimiento de cada jugador y compara con lo que dice el cliente. No los "detecta": los deshace, devolviendo al jugador a donde tenía que estar y anulando el golpe imposible |
| **Meteor, Wurst, xray, Baritone y demás, en la carpeta `mods`** | El mod de acceso (4.6) | Lo mira al entrar. Es el más débil de los tres, porque la lista la manda el cliente |

**Los castigos de Grim** están en `config/GrimAC/punishments.yml`. De fábrica solo avisa; le agregamos el kick a los cuatro grupos que importan, con números altos a propósito (Simulation 250, Knockback 40, Reach 30, Misc 60): Grim ya frena el truco en el momento, así que el kick es para el que insiste, y de paso deja margen para que una mala conexión no eche a nadie. Las alertas le llegan por chat al que tenga el permiso `grim.alerts`, que con LuckPerms ya tiene PEPE.

**Por qué el servidor está en 26.1.2 y los jugadores en 26.1.** Grim no carga en 26.1 pelado: su soporte de la familia 26 arranca en 26.1.2. Se subió solo el servidor, y nadie tuvo que tocar nada, porque **26.1, 26.1.1 y 26.1.2 hablan el mismo protocolo (775)**: el cliente de 26.1 entra a un servidor 26.1.2 sin enterarse. Lo único que se cayó en el camino fue *My Photo Paintings*, que estaba clavado en `"minecraft": "26.1"` exacto, así que salió del servidor y del pack.

**Lo que Grim no tiene acá son sus comandos** (`/grim ...`). Avisa al arrancar con un `Grim will run without commands enabled!`. Es un problema de empaquetado de ellos, explicado en el encabezado de `subir-antitrampas.py`; las detecciones, los setbacks, las alertas y los castigos funcionan igual.

## 5. Carpetas en la PC del jugador

```
%LOCALAPPDATA%\SobrinosDePepeLauncher\   ← la app (Velopack)
%LOCALAPPDATA%\SobrinosDePepe\
├── game\      versions· libraries· assets· runtime(Java 25)· mods· config· logs
├── pack\      el .mrpack actual
├── session.dat
└── launcher.log
```

Sin permisos de administrador. No toca `%APPDATA%\.minecraft`, así que TLauncher les sigue funcionando. Requiere Windows 64 bits (Mojang no publica Java 25 para 32) y ~1,5 GB libres, que el launcher chequea antes de empezar.

### Nada de mods propios

`mods\` y `shaderpacks\` son carpetas **del pack**, no del jugador: `ModSynchronizer`
borra todo `.jar` que no esté en la lista publicada. Eso es a propósito y no es
paranoia — un jar que sobrevive a un cambio de pack crashea el juego al arrancar, y
el jugador no tiene forma de diagnosticarlo.

Hasta el 2026-09-11 hubo una excepción: un `mods-propios.txt` en la raíz donde se
podían nombrar los jars que la sincronización no tocaba, para el que quería un mod
de cliente propio. **Se quitó**, porque era también la única forma de dejar un xray
o un Meteor adentro de la carpeta y que el launcher no lo borrara: apretar JUGAR
limpiaba todo menos justo eso. El mod que quiera estar en las PC de todos entra por
el pack, que es donde el admin lo mira antes (`npm run pack:mod`).

## 6. Base de datos

| Tabla | Columnas |
|---|---|
| `users` | id, username (casing exacto), username_lower (único), password_hash, role (player/admin), status (pending/active/banned), created_at, approved_at, banned_at |
| `sessions` | id, user_id, secret_hash, expires_at |
| `pack_mods` | el pack que estás editando: project_id, version_id, title, version_number, filename, url, sha1, sha512, size, side (client/server/both), license, page_url, source (upload/modrinth) |
| `pack_releases` | cada publicación con su contenido congelado: version, content, created_at |
| `mod_files` | los `.jar` que subiste: sha1, filename, size, data |
| `user_settings` | la configuración del juego de cada cuenta, como un zip: user_id, data, size, sha1, updated_at |
| `user_mods` | los mods que una cuenta tiene además del pack: user_id, filename, sha1, size, title, version_number. Solo las cuentas admin pueden tener |

El UUID offline no se guarda: se calcula del nombre cuando hace falta, y la whitelist del server se maneja por nombre. Un campo menos que se puede desincronizar.

## 7. API

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| POST | `/api/auth/register` | público | crea la cuenta, la activa y la mete en la whitelist |
| POST | `/api/auth/login` | público | devuelve token de sesión, rol y estado |
| GET | `/api/me` | sesión | estado de la cuenta |
| GET | `/api/pack` | cuenta activa | el pack a instalar; una cuenta pendiente recibe 403 |
| GET | `/api/ticket` | cuenta activa | el permiso para entrar al servidor, firmado y por doce horas. Se emite siempre, con el candado abierto también: así prenderlo es un cambio de un solo lado |
| GET | `/api/admin/users` | admin | lista con búsqueda |
| POST | `/api/admin/users/:id/approve` | admin | activa + `whitelist add` |
| POST | `/api/admin/users/:id/ban` | admin | marca baneado + `whitelist remove` + `kick` + borra sesiones |
| POST | `/api/admin/users/:id/unban` | admin | reactiva + `whitelist add` |
| POST | `/api/admin/users/:id/password` | admin | pone una contraseña temporal |
| GET | `/api/admin/mods` | admin | pack actual |
| POST | `/api/admin/mods/upload` | admin | sube uno o varios `.jar` |
| POST | `/api/admin/mods` | admin | agrega un mod por su link de Modrinth |
| GET | `/api/admin/mods/search` | admin | busca en Modrinth |
| DELETE | `/api/admin/mods` | admin | lo saca del pack |
| GET | `/api/files/:sha1` | cuenta activa | descarga un `.jar` subido |
| GET | `/api/settings` | cuenta activa | la configuración guardada, como zip; 204 si todavía no guardó ninguna |
| PUT | `/api/settings` | cuenta activa | la guarda. El backend no mira adentro del zip |
| GET | `/api/my-mods` | cuenta activa | sus mods propios; a quien no es admin le llega la lista vacía |
| POST | `/api/my-mods` | **admin** | sube uno o varios `.jar` a su cuenta |
| DELETE | `/api/my-mods?sha1=` | **admin** | lo saca de su cuenta |
| POST | `/api/admin/pack/publish` | admin | arma y publica el `.mrpack` |
| GET | `/api/admin/server` | admin | estado del server |

## 8. Secretos

| Qué | Dónde |
|---|---|
| API key de Pterodactyl | variable de entorno del backend, y en ningún otro lugar |
| `ACCESS_SECRET`, con el que se firman los permisos de entrada | variable de entorno del backend y `config/acceso-de-pepe.json` en el servidor. En el launcher no está: el launcher recibe permisos, no los emite |
| `REQUIRE_LAUNCHER` | no es un secreto, pero vive al lado: es el interruptor del candado, y se aplica al servidor con `servidor/subir-acceso.py` |
| Hashes de contraseñas | Postgres |
| Token de sesión del jugador | su PC, cifrado con DPAPI |
| El launcher | no tiene ningún secreto |

En el server: `white-list=true`, `enforce-whitelist=true` y **ningún jugador con OP** (en Java Edition los OP saltan la whitelist).

## 9. Versiones

El pack fija Minecraft **26.1** (la del server, protocolo 775) y Fabric Loader **0.19.5**. Nunca "la última". Los mods se fijan por versión exacta y los elegís vos cuando los agregás. Si algún día actualizás el server, se actualiza el pack a mano y listo.

## 10. Distribución

**El backend no se despliega solo al pushear.** El proyecto de Vercel no está conectado al repositorio: se publica a mano, desde `web/`, con `npx vercel --prod`. Dos cosas que importan de eso: despliega **lo que está en el disco**, no el último commit, así que conviene tener todo commiteado antes; y la dirección de producción es `https://sobrinosdepepe.com`, que es a la que le pega el launcher desde la versión 1.16.0. `sobrinosdepepe.vercel.app` sigue funcionando y es lo que usan los launchers viejos hasta que se actualizan solos. `www.sobrinosdepepe.com` **no** está dado de alta: no tiene certificado y no responde.

`Setup.exe` de Velopack en GitHub Releases, sin firma de código. La primera vez Windows muestra "Windows protegió tu PC": la página de descarga lo explica con las capturas de pantalla paso por paso. Después el launcher se actualiza solo y el aviso no vuelve a aparecer.

### Desinstalar

Desde "Aplicaciones instaladas" de Windows, como cualquier programa. La entrada la crea
Velopack apuntando a su propio `Update.exe`, que desinstala sin mostrar nada; el launcher
la reescribe en cada arranque para que apunte a sí mismo con `--uninstall` y aparezca la
pantalla de despedida: qué se borra, botón, barra de progreso y "listo, no quedó nada".

El reparto es: la pantalla borra lo pesado (`game`, `pack`, la sesión y el log) mientras lo
cuenta, y al cerrar le pasa la posta a Velopack, que se borra a sí mismo, los accesos
directos y la entrada del registro. Un `cmd` suelto, fuera de la carpeta, remata la raíz por
si quedó algo. No se puede hacer al revés: lo primero que hace Velopack es matar los
procesos que corren desde esa carpeta.

`QuietUninstallString` queda como la dejó Velopack, para las herramientas que desinstalan
sin intervención. Ese camino también limpia: Velopack avisa antes de borrar
(`OnBeforeUninstallFastCallback`) y ahí se hace la misma limpieza sin pantalla.

## 11. Créditos

Lista de mods con autor, licencia y link dentro del launcher. Es obligatorio: Simple Voice Chat es de derechos reservados y el shader Complementary pide crédito visible. Sin monetizar el launcher ni el server. (Xaero's Minimap y World Map también lo pedían; salieron del pack el 2026-09-06.)

## 12. Posicionamiento

El launcher descarga el juego desde los servidores de Mojang a la PC del jugador y no toca la autenticación oficial. No redistribuye archivos de Mojang ni de mods. Es un instalador de modpack con control de acceso para un server privado, sin logos de Mojang y sin publicitar jugar sin comprar el juego. Cada jugador es responsable de su licencia.
