# 01 · Entorno de referencia (la PC de Tobías)

> Inventario tomado el 2026-09-03 de `C:\Users\Tobi\AppData\Roaming\.minecraft` (perfil de TLauncher "Fabric 26.1").
> Es la **fuente de verdad** del pack: lo que el launcher tiene que reproducir en cada PC.
> Versión legible por máquina, con hashes: [`reference/pack-inventory.json`](../reference/pack-inventory.json).

## 1. Juego, loader y Java

| Componente | Valor exacto | Fuente |
|---|---|---|
| Minecraft | **26.1** (asset index `30`, 456 MB de assets) | `versions/Fabric 26.1/Fabric 26.1.json` |
| Fabric Loader | **0.19.2** (sponge-mixin 0.17.2+mixin.0.8.7, ASM 9.9, desde `maven.fabricmc.net`) | idem + `logs/latest.log` |
| Fabric API | **0.155.2+26.1.2** | `mods/` |
| Java requerido | **Java 25** (`javaVersion: { component: "java-runtime-epsilon", majorVersion: 25 }`) | version JSON y log (`Compatibility level set to JAVA_25`) |
| Runtime usado hoy | `runtime/java-runtime-epsilon/windows/java-runtime-epsilon` (el JRE de Mojang, instalado por TLauncher) | `.minecraft/runtime` |
| Main class | `net.fabricmc.loader.impl.launch.knot.KnotClient` | version JSON |
| client.jar | sha1 `191771837687b766537a8c4607cb6fad79c533a1`, 38 MB, `piston-data.mojang.com` | version JSON |
| Libraries | 114 entradas (LWJGL 3.4.1 con natives windows/x64/arm64/x86) | version JSON |
| Tamaño en disco | assets 447 MB · libraries 112 MB · mods 16 MB · client 38 MB | `du` |

Notas:
- El Java 17 del sistema (`C:\Program Files\...\javapath`) **no sirve** para 26.1. El launcher debe traer su propio Java 25.
- El version JSON es el formato moderno (`arguments.jvm` / `arguments.game` con reglas por OS). Incluye `--clientId ${clientid}` y `--xuid ${auth_xuid}`, que en sesión offline van vacíos.

## 2. Mods (16) — todos identificados en Modrinth por hash SHA-1

> **Foto del 2026-09-03.** Es el inventario de la PC de referencia, no el pack de
> hoy. Lo que cambió desde entonces: salieron `player-revive` (2026-09-05) y
> `xaerominimap` / `xaeroworldmap` (2026-09-06, por el radar de jugadores),
> `maplink` nunca entró, y entraron el shader Complementary y el mod propio de
> precios. El pack vivo se mira con `cd web && npm run pack:check`; el porqué de
> cada baja está en [`servidor/LEEME.md`](../servidor/LEEME.md).


| Archivo en `mods/` | Mod (slug Modrinth) | Versión real | Licencia | Cliente / Servidor |
|---|---|---|---|---|
| `fabric-api-0.155.2+26.1.2.jar` | fabric-api | 0.155.2+26.1.2 | Apache-2.0 | optional / optional |
| `cloth-config-26.1.154.jar` | cloth-config | 26.1.154 | LGPL-3.0 | optional / optional |
| `sodium-fabric-0.8.9+mc26.1.1.jar` | sodium | 0.8.9 | Polyform Shield 1.0.0 | required / **unsupported** |
| `iris-fabric-1.11.2+mc26.1.2.jar` ⚠ | iris | **1.10.9** (el archivo está renombrado) | LGPL-3.0 | required / **unsupported** |
| `lithium-fabric-0.24.7+mc26.1.2.jar` | lithium | 0.24.7 | LGPL-3.0 | optional / optional |
| `modernfix-5.27.19-build.1.jar` | modernfix-mvus | 5.27.19 | LGPL-3.0 | optional / optional |
| `sodium-extra-fabric-0.8.6+mc26.1.1.jar` ⚠ | sodium-extra | **0.8.7** (renombrado) | LGPL-3.0 | required / **unsupported** |
| `reeses-sodium-options-fabric-2.2.3+mc26.1.2.jar` ⚠ | reeses-sodium-options | **2.0.5** (renombrado) | MIT | required / **unsupported** |
| `xaerominimap-fabric-26.1.2-26.4.2.jar` | xaeros-minimap | 26.4.2 | All Rights Reserved | required / optional |
| `xaeroworldmap-fabric-26.1.2-1.45.0.jar` | xaeros-world-map | 1.45.0 | All Rights Reserved | required / optional |
| `maplink-fabric-4.5.0-26.1.jar` | maplink | 4.5.0 | GPL-3.0 | required / unsupported (su config ignora nuestro server: hoy no aporta nada) |
| `sound-physics-remastered-fabric-1.5.1+26.1.2.jar` | sound-physics-remastered | 1.5.1 | GPL-3.0 | required / optional |
| `player-revive-v3.0-mc26.1.x.jar` | player-revive ("Simple Revive") | v3.0 | CC-BY-4.0 | optional / **required en el server** |
| `zoomx-26.1.jar` | zoomx | 26.1 | MIT | required / unsupported |
| `voicechat-fabric-2.6.22+26.1.2.jar` (agregado el 2026-09-03, también en el server) | simple-voice-chat | 2.6.22 | All Rights Reserved | optional / optional (para que haya voz tiene que estar en **ambos** lados; usa un puerto **UDP** propio) |
| `nowheel-fabric-1.4.0+mc26.1.jar` (agregado el 2026-09-03) | nowheel | 1.4.0 | MIT | solo cliente |
| `ukulib-fabric-2.0.0+26.1.jar` (dependencia de nowheel) | ukulib | 2.0.0 | MPL-2.0 | solo cliente |

Conclusiones:
- **Ningún mod hay que rehostear**: todos tienen URL directa en `cdn.modrinth.com` (ver JSON). El manifest referencia esas URLs y verifica hash.
- ⚠ Tres archivos tienen nombre distinto a su contenido real (iris, sodium-extra, reeses). El manifest debe basarse en **hash**, nunca en nombre de archivo.
- Del lado del **servidor** hacen falta: Fabric Loader, Fabric API, Simple Revive (obligatorio), Simple Voice Chat (para la voz) y, opcionalmente, Lithium/ModernFix/Sound Physics/Xaero's. Sodium, Iris, Sodium Extra, Reese's, ZoomX y MapLink son **solo cliente**: Fabric Loader los ignora en un server dedicado (declaran `environment: client`), así que no rompen nada, pero conviene no subirlos.
- Estado real del server (según Tobías, 2026-09-03, visto en el gestor de archivos del panel): tiene **los mismos jars que el cliente** más `voicechat`. Pendiente: confirmarlo con las primeras líneas del log de arranque del server.
- `cavedweller-1.3.0.jar` (en `Downloads`) **no se usa** en ningún lado.

## 3. Configs que forman parte del pack

35 archivos en `config/` (hashes en el JSON). Los relevantes:
- `iris.properties`: `enableShaders=true`, `shaderPack=ComplementaryUnbound_r5.8.1` → hoy los shaders están **activados por defecto**. Decisión pendiente: si el launcher los deja opcionales, este archivo se genera según la elección del usuario.
- `sodium-options.json`, `sodium-extra-options.json`: ajustes de rendimiento (razonables como default).
- `maplink/*.json5`, `sound_physics_remastered/*.properties`, `xaero/*`, `xaerohud.txt`: defaults de los mods.
- `lithium.properties`, `modernfix-mixins.properties`, `sodium-mixins.properties`, `sodium-extra.properties`: vacíos/por defecto (no hace falta distribuirlos).
- `sodium-fingerprint.json`: es por máquina; **no** distribuir.

`options.txt` (no es config de mod, es del juego): `lang:es_es`, `fullscreen:true`, `renderDistance:12`, `guiScale:3`, `maxFps:240`, `resourcePacks:["vanilla"]`. Candidato a "defaults de primera ejecución" (idioma español), sin sobrescribir en cada actualización.

## 4. Shaders y resource packs

- `shaderpacks/ComplementaryUnbound_r5.8.1/` (carpeta extraída, 2 MB) + `ComplementaryUnbound_r5.8.1.zip.txt` con los ajustes elegidos (bloom off, shadowDistance 128, etc.).
- `resourcepacks/`: vacío. `server-resource-packs/`: vacío (el server no manda resource pack).

## 5. El servidor

| Dato | Valor |
|---|---|
| Dirección que usan los jugadores | `sobrinosdepepe.minehost.pro` (nombre en la lista: "SOBRINOS DE PEPE") |
| Resolución real | registro **SRV** `_minecraft._tcp.sobrinosdepepe.minehost.pro` → `sv36.minehost.pro:25445` (IP 45.235.98.223). No hay registro A: un ping de estado tiene que resolver SRV primero. |
| Estado al 2026-09-03 | online · versión `26.1` · protocolo 775 · 0/20 jugadores · MOTD por defecto "A Minecraft Server" · favicon presente · latencia ~58 ms |
| Modo | **online-mode=false** (offline). Evidencia: la cuenta TLauncher "free" `PEPE` recibe 401 de Mojang y aun así entra al server. |
| Hosting | minehost.pro · panel **Pterodactyl** en `https://pterodactyl.minehost.com.ar/server/dbd3f1e9` (identificador de server `dbd3f1e9`), con consola y gestor de archivos (`/mods` editable) |
| Jugadores vistos | `PEPE` (Tobías), `Chichon`, `Titit0N`, `Luquitas1410`, `Felix_1256` (los cinco salen del historial de comandos y del chat en los logs) |

Implicancias:
- En offline-mode el UUID de cada jugador se deriva del nombre (`OfflinePlayer:<nombre>`, UUID v3). Los inventarios y avances del server están atados a **esos nombres exactos**: el nuevo sistema de cuentas tiene que respetarlos (ver preguntas abiertas).
- La whitelist vanilla en offline-mode solo mira el nombre. Cualquiera con un launcher "cracked" y un nombre válido entra. Eso hay que cerrarlo del lado del server.
- Que el panel sea **Pterodactyl** abre una vía limpia para la integración: Pterodactyl expone una **Client API** con API keys personales (`Cuenta → API Credentials`) que permite enviar comandos a la consola (`POST /api/client/servers/dbd3f1e9/command`, por ejemplo `whitelist add <nombre>`), leer estado y recursos (`/resources`), y leer/escribir archivos (`/files/...`). La API key la guarda **solo el backend**, nunca el launcher. Además Pterodactyl da acceso **SFTP** para subir un mod server-side propio.
- **Voz: funcionando.** El 2026-09-03 la voz no conectaba: el mod estaba bien en las dos puntas y el server le entregaba al cliente la dirección `45.235.98.223:24454`, pero el cliente reintentaba autenticar hasta rendirse porque ese puerto UDP no era alcanzable desde afuera. Tobías cambió el puerto en el panel de Minehost y quedó andando.
  Para el futuro: el puerto de la voz vive en `config/voicechat/voicechat-server.properties` del server, es **UDP** e independiente del puerto TCP del juego. La documentación del mod desaconseja reusar el puerto del juego, porque ese UDP lo usa la consulta de estado y puede colgar el server (https://modrepo.de/minecraft/voicechat/wiki/server_config). Si alguna vez Minehost migra el server de nodo, hay que revisar que el puerto siga abierto.

## 5.b Configuración real del servidor (leída por la API del panel el 2026-09-03)

| Dato | Valor |
|---|---|
| Plan | "Minecraft 5gb": 5120 MB de RAM, nodo `sv36`. Arranca con `-Xms2048M -Xmx5120M`. |
| Estado al momento de leer | encendido, usando 2011 MB |
| Allocations | **dos**: `sv36.minehost.pro:25445` (principal, el juego) y `sv36.minehost.pro:25446` (la que abrió Tobías para la voz). El panel no permite crear más (`allocations: 0`). |
| Voz | `config/voicechat/voicechat-server.properties`: `port=25446`, `bind_address=0.0.0.0`, `max_voice_distance=48`, `force_voice_chat=false`. Funcionando. |
| Backups | cupo de **1** |
| Imagen de Java | el egg ofrece hasta `java_25`; el arranque usa `@unix_args.txt`, que es el formato del server de Fabric |
| `online-mode` | `false` |
| **`white-list`** | **`false`** · `enforce-whitelist=false` |
| `enable-query` | `true`, `query.port=25445` |
| `max-players` | 20 · `difficulty=easy` · `pvp` por defecto · `view-distance=10` · `simulation-distance=10` |
| `enforce-secure-profile` | `false` |
| Mundo | `world` |

Dos cosas que salen de acá y afectan al plan:

**La whitelist está apagada.** Todo el diseño de aprobar y banear se apoya en ella: mientras `white-list=false`, agregar o quitar a alguien de la lista no cambia nada y cualquiera con el nombre que quiera entra. Antes de encenderla hay que meter a los cinco jugadores actuales, porque si no quedan todos afuera. Conviene además poner `enforce-whitelist=true`, que es lo que expulsa a quien saques de la lista sin esperar a que se desconecte.

**`enable-query=true` en el puerto del juego.** Esto confirma por qué no había que reusar el puerto 25445 para la voz: ese UDP ya lo usa la consulta de estado. La allocation aparte (25446) era el camino correcto.

## 5.c Mods que tiene el servidor

> **Foto del 2026-09-03.** Al 2026-09-06 son 14 jars: se movieron a
> `/mods-apagados` los cinco de solo cliente, `tl_skin_cape`, `maplink`,
> `player-revive` y los dos de Xaero. La lista al día está en
> [`servidor/LEEME.md`](../servidor/LEEME.md).

16 jars en `/mods`. Comparado con el cliente:

- **Los que corresponden**: fabric-api, cloth-config, lithium, modernfix, player-revive, sound-physics, voicechat, xaerominimap, xaeroworldmap.
- **Solo de cliente, cargados de más**: sodium, iris, sodium-extra, reeses-sodium-options, zoomx, maplink. Fabric los ignora en un servidor dedicado porque declaran `environment: client`, así que no rompen nada, pero ensucian el log. Se pueden borrar cuando haya un rato.
- **`tl_skin_cape_fabric_26.1_26.1.2-1.39.jar`**: el mod de skins y capas de TLauncher, del lado del servidor. Es de dónde salen las skins hoy. No está en Modrinth y es propio de TLauncher, así que el launcher nuevo no lo va a distribuir: la salida limpia es SkinRestorer (MIT) en el servidor.
- Falta `nowheel` y `ukulib` del lado del servidor, pero son solo de cliente: está bien que no estén.

## 6. Cosas que hoy dependen de TLauncher y van a desaparecer

- **Skins**: TLauncher inyecta el mod `tlskincape 1.39` (aparece en el log, no está en `mods/`). Sin TLauncher, en offline-mode nadie ve skins. Hace falta una solución (mod server-side de skins o skins por cuenta en nuestro backend).
- Cuenta "free" y su UUID offline `5b071e1f-3a11-4f28-9c68-a6321cadc031`: es simplemente `uuid3("OfflinePlayer:PEPE")`, cualquier launcher lo reproduce.

## 7. Herramientas en la PC de Tobías (para elegir stack)

- Windows 11 · .NET SDK 10.0.400 y 6.0 · Visual Studio 2026 (v18) · VS Code
- Node 24.11 · npm 11 · Python 3.11 · git 2.52
- Sin Docker ni GitHub CLI
- Experiencia principal: TypeScript (Expo/React Native + backend Next.js en `zeluxmobile` / `zeluxlogistics`)
- Intentos previos visibles en `Downloads`: Prism Launcher 11.0.3, JDK 17, Fabric installer 1.1.2, `fabric-server-launch.jar`, `AuthMe-6.0.0-Paper.jar` (plugin de **Paper**, no funciona en un server Fabric).
