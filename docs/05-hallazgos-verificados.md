# 05 · Hallazgos verificados (referencia para implementar)

> Todo lo de acá fue comprobado el **2026-09-03** contra la fuente indicada. Los informes completos, con confianza por afirmación y notas, están en [`research/`](research/). Cuando algo diga "conocimiento previo" en esos informes, verificarlo antes de depender de ello.

## Minecraft 26.1 (Mojang)

| Dato | Valor | Fuente |
|---|---|---|
| Manifest de versiones | `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` · latest release **26.2** (2026-06-16), snapshot 26.3-pre-1 · existen 26.1 (2026-03-24), 26.1.1, 26.1.2 | piston-meta |
| JSON de 26.1 | `https://piston-meta.mojang.com/v1/packages/c27af223100c5a1a7c986b361170bc8bd71ff3e1/26.1.json` | piston-meta |
| Java | `javaVersion: { component: "java-runtime-epsilon", majorVersion: 25 }` | 26.1.json |
| client.jar | sha1 `191771837687b766537a8c4607cb6fad79c533a1`, 38.113.398 B | 26.1.json |
| Assets | índice `30`, 4.750 objetos, 456.427.629 B; se bajan de `https://resources.download.minecraft.net/<hash[0:2]>/<hash>` | 30.json |
| Libraries | 107 entradas; 75 aplican a Windows (70,6 MB, 25 son natives como jars normales con `rules`, **no hay que extraer nada**); `https://libraries.minecraft.net/<path>` | 26.1.json |
| `arguments` | claves `game`, `jvm` y **`default-user-jvm`** (`-Xms2G -Xmx4G -XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+UseStringDeduplication` + `-XX:+UseZGC` en Windows ≥ 10.0.17134). Obligatorios en Java 25: `--sun-misc-unsafe-memory-access=allow`, `--enable-native-access=ALL-UNNAMED`. **No existe** `--userType` en 26.1. | 26.1.json |
| Quick Play | `--quickPlayMultiplayer ${quickPlayMultiplayer}` con feature `is_quick_play_multiplayer` | 26.1.json (verificado localmente) |
| Protocolo | 26.1 / 26.1.1 / 26.1.2 = **775**; 26.2 = 776 | minecraft.wiki/w/Protocol_version + ping real |
| Runtime Java de Mojang | `https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json` → `windows-x64` / `java-runtime-epsilon` = **25.0.1** (JDK completo, 411 archivos, 105,1 MB raw / 80,8 MB lzma). No existe para windows-x86. | launchermeta |
| Alternativa Java | Temurin JRE 25 zip 58,5 MB: `https://api.adoptium.net/v3/assets/latest/25/hotspot?os=windows&architecture=x64&image_type=jre` | api.adoptium.net |
| Primera descarga | ≈ 665 MB / ≈ 5.200 archivos; 3-5 min a 50 Mbps con 8-16 conexiones | suma de los manifests |

## Fabric

| Dato | Valor | Fuente |
|---|---|---|
| Loaders para 26.1 | `https://meta.fabricmc.net/v2/versions/loader/26.1` · stable = **0.19.5**; 0.19.2 existe (no marcado stable) | meta.fabricmc.net |
| Perfil | `https://meta.fabricmc.net/v2/versions/loader/26.1/<loader>/profile/json` → `id fabric-loader-<loader>-26.1`, `inheritsFrom 26.1`, `mainClass net.fabricmc.loader.impl.launch.knot.KnotClient`, 7 libs (asm 9.9 ×5, sponge-mixin 0.17.2+mixin.0.8.7, fabric-loader), JVM arg `-DFabricMcEmu= net.minecraft.client.main.Main ` (con espacios, un solo argv) | meta.fabricmc.net |
| Intermediary | 26.1 **no está ofuscado**: intermediary `0.0.0`, Yarn discontinuado, no hay mappings que bajar | fabricmc.net/2026/03/14/261.html |
| Server jar | `https://meta.fabricmc.net/v2/versions/loader/26.1/0.19.5/1.1.2/server/jar` | meta.fabricmc.net |
| Toolchain mods | JDK 25 · Gradle 9.5.1 (≥ 9.1 para Java 25) · plugin `net.fabricmc.fabric-loom` **1.17** (sin remap; `implementation`, `jar`) · template `FabricMC/fabric-example-mod` rama 26.1 | docs.fabricmc.net/develop/loom, GitHub |
| Login networking (Fabric API 0.155.x, rama 26.1) | `ServerLoginNetworking.registerGlobalReceiver`, `ServerLoginConnectionEvents.QUERY_START`, `LoginSynchronizer.waitFor`, `PacketSender.disconnect`; cliente `ClientLoginNetworking.registerGlobalReceiver`; cliente sin mod ⇒ `understood=false` | github.com/FabricMC/fabric/tree/26.1/fabric-networking-api-v1 |
| Mods solo cliente en server | Fabric Loader los ignora si `fabric.mod.json` declara `environment: client` (log "environment disabled") | fabric-loader ModDiscoverer/ModResolver |
| Echar a alguien durante el login **con el motivo puesto** | Va con `ServerLoginPacketListenerImpl.disconnect(Component)`, que manda un `ClientboundLoginDisconnectPacket` y despues cierra. El `disconnect()` del `PacketSender` de Fabric llama derecho a `Connection.disconnect()`, que cierra el canal **sin mandar ningun paquete**: el servidor loguea el motivo igual, pero el jugador solo ve "Desconectado" pelado. Probado el 2026-09-07 contra un cliente de TLauncher: con el sender no se ve el cartel, con el handler si | bytecode de 26.1 y de fabric-networking-api-v1 6.3.1 (`javap -c`) |
| Nombre del que está entrando, sin mixins | `ServerLoginPacketListenerImpl.getUserName()` es público y devuelve `requestedUsername` con la dirección atrás ("PEPE (1.2.3.4:56789)"). El nombre es lo que va antes del primer espacio: un usuario válido no tiene espacios | bytecode de 26.1 (`javap -c`), verificado el 2026-09-07 |
| Un jar para cliente y servidor | `environment: "*"` con los entrypoints `server` (`DedicatedServerModInitializer`) y `client` separados: en un mundo de un jugador el de servidor no corre, así que el mod de acceso no se autoexpulsa | fabric-loader, entrypoints |
| Compilar los mods en esta PC | Gradle con Loom pide JVM 21+ y el Java del sistema es 17. El runtime que baja el launcher es un JDK completo (trae `javac`): `JAVA_HOME="C:\Users\<vos>\AppData\Local\SobrinosDePepe\game\runtime\windows-x64\java-runtime-epsilon" ./gradlew build` | verificado el 2026-09-07 |
| Tildes en los mods | javac usa el charset de Windows si no se le dice nada, y los carteles salen con basura en el servidor. `options.encoding = 'UTF-8'` en `build.gradle` | verificado el 2026-09-07: los bytes UTF-8 en el `.class` compilado |
| Subir un `.jar` por el panel | `GET /files/upload` da una URL firmada que apunta al nodo, y el archivo se manda ahí como `multipart/form-data` sin la clave de la API. `/files/write` no sirve: manda el cuerpo como texto y el jar queda corrupto | API client de Pterodactyl |

## Sesión offline

- UUID = **v3 (MD5) de `OfflinePlayer:<nombre>`**, sensible a mayúsculas. El server con `online-mode=false` **ignora** el UUID que manda el cliente y calcula este. `whitelist.json`, `ops.json` y `playerdata/` usan este UUID.
- Calculados para los cinco jugadores: `PEPE` → `7a067f19-b48d-3c6e-9039-8f37f64def1f` · `Chichon` → `5f1bb3e1-3a21-3c39-a409-e50842f7df31` · `Titit0N` → `7bc394ba-c092-3432-b0b3-57a16c894f33` · `Luquitas1410` → `9c4d6396-daf9-3c3c-9eb3-93fe87cb87b7` · `Felix_1256` → `abc27f34-f3db-3e4c-8807-6fe307d72d6c`.
- El UUID que muestra TLauncher (`5b071e1f-…`, v4) es local y no importa.
- `--accessToken` cualquier string no vacío; `--clientId`/`--xuid` vacíos. `sessionserver.mojang.com` devuelve 204 para UUIDs offline ⇒ sin skin.
- `CmlLib.MSession.CreateOfflineSession()` genera UUID **aleatorio**: hay que asignar el v3 a mano.

## Server y hosting

| Dato | Valor | Fuente |
|---|---|---|
| DNS | `sobrinosdepepe.minehost.pro` **sin A**; SRV `_minecraft._tcp.sobrinosdepepe.minehost.pro` → `sv36.minehost.pro:25445` (TTL 300), IP 45.235.98.223 (AS266777, Argentina, RTT ~4 ms) | nslookup |
| Estado (ping) | online · `26.1` · protocolo 775 · 0/20 · MOTD "A Minecraft Server" · favicon · sin `enforcesSecureChat` (coherente con offline) | Server List Ping propio |
| Panel | **Pterodactyl** `https://pterodactyl.minehost.com.ar/` (tema Arix); nodo `sv36` corre Wings (8080) y SFTP en **2022**; sin FTP (21), sin RCON (25575 cerrado) | curl/sockets |
| Client API | keys en `/account/api` (prefijo `ptlc_`) · `POST /api/client/servers/{id}/command` `{"command":"whitelist add PEPE"}` (502 si el server está apagado) · `GET .../resources` · `POST .../power` · `GET .../files/contents?file=/whitelist.json` · `POST .../files/write` · `/backups` · 256 req/min | routes/api-client.php de pterodactyl/panel |
| Server ID | `dbd3f1e9` (de la URL del panel) | Tobías |
| Puertos extra | No hay producto ni opción; KB 53: "los clientes no tienen acceso a configurar puertos" ⇒ nada entrante al server salvo la allocation principal | web.minehost.com.ar KB |
| Imagen Java | `ghcr.io/pterodactyl/yolks:java_25` existe; confirmar en Startup | pterodactyl/yolks |
| Política | prepago 30 días; suspensión al día 30; archivos borrados a los 6 días | KB 54 |
| Planes | ≈ ARS 2.500 / US$ 4 por GB/mes; slots ilimitados | web.minehost.com.ar |
| Whitelist | en offline-mode compara UUID offline derivado del nombre; `whitelist add <nombre>` funciona sin Mojang; los **OP saltan la whitelist** | minecraft.wiki/w/Whitelist.json |

## Modrinth

| Dato | Valor | Fuente |
|---|---|---|
| Uso permitido | Los Terms ("API Usage") conceden licencia revocable a descargar vía apps propias; exigen **User-Agent identificable** (`usuario/proyecto/1.0.0 (contacto)`) | modrinth.com/legal/terms, docs.modrinth.com/api |
| Rate limit | 300 req/min por IP (headers `X-Ratelimit-*`) | api.modrinth.com |
| Lookup por hash | `POST /v2/version_files` `{hashes, algorithm: sha1|sha512}`; `POST /v2/version_files/update` (solo para avisar) | docs.modrinth.com |
| CDN | `https://cdn.modrinth.com/data/<projectId>/versions/<versionId>/<archivo>` (usar `files[].url` de la API tal cual; `+` va como `%2B`); `Cache-Control: no-store`; permanencia no garantizada | HEAD real |
| .mrpack | zip con `modrinth.index.json`: `formatVersion 1`, `files[]` con `path`, `hashes {sha1, sha512}` (ambos obligatorios), `env {client, server}`, `downloads[]` (solo cdn.modrinth.com, github.com, raw.githubusercontent.com, gitlab.com), `fileSize`; `dependencies {minecraft, fabric-loader}`; `overrides/`, `client-overrides/`, `server-overrides/` | support.modrinth.com/…/8802351 |
| Complementary Unbound | project `R6NEzAwj`; r5.8.1 = version `VMHXIk50`, sha1 `af656a33be2cbd217ea08986ad2cdd76f6bbfe1c`, 546.928 B; r5.9 = `w6dISYOd` (2026-09-02). Licencia 1.6/1.7: **prohíbe rehostear** (1.2.d), prohíbe modificar el zip (1.2.c), exige crédito si viene activado por defecto (1.2.a) | zip descargado |
| Iris/Sodium | Iris 1.11.x y Reese's 2.2.x exigen Sodium 0.9.x, que **solo existe para 26.1.2**. Para 26.1: Iris 1.10.9 + Sodium 0.8.9 + Reese's 2.0.5 + Sodium Extra 0.8.7 es el máximo. Para 26.1.2: Sodium 0.9.1 (`vf7UgZpC`), Iris 1.11.3 (`5H9TsVy4`), Reese's 2.2.3 (`laVM31w1`), Sodium Extra 0.9.3 (`q7UnsNa0`), ZoomX 26.1.2 (`IObeQ9nK`) | fabric.mod.json de los jars |
| Xaero's | ARR con permiso para modpacks: crédito con link accesible si se distribuye fuera de CF/Modrinth; monetizar solo vía CF/Modrinth. **Histórico: salió del pack el 2026-09-06 por el radar de jugadores.** | descripción en Modrinth |
| Sodium | PolyForm Shield 1.0.0: distribuir permitido con aviso de licencia; prohíbe competir con Sodium | LICENSE.md |
| Simple Revive | también existe como **datapack** (`v3.0-mc26.1.x-datapack`) | Modrinth |
| Mods de auth para Fabric 26.1 | EasyAuth 3.4.4 (`https://cdn.modrinth.com/data/aZj58GfX/versions/hsNEMYXj/easyauth-mc26.1-3.4.4.jar`, MIT, `/register` `/login`); Authenticate; Serverauth; AuthMe es **Paper** (no sirve) | Modrinth |
| Skins | SkinRestorer 2.10.0+26.1 (`ghrZDhGW` / `aXzPUPsr`, MIT, server-side): `/skin set mojang|web|ely.by`, autoFetch configurable, MineSkin API key para URLs; CustomSkinLoader 15.0.1 (cliente); Fabric Tailor sin build 26.1 exacto | Modrinth, wiki de SkinRestorer |

## Librerías del launcher (C#)

| Paquete | Versión | Notas verificadas |
|---|---|---|
| CmlLib.Core | 4.0.6 (2025-09-24, netstandard2.0, MIT) | Parsea 26.1 (probado en tu PC); `FabricInstaller` incluido (`CmlLib.Core.ModLoaders.FabricMC`); instala Java de Mojang en `runtime/windows-x64/<component>`; **gotcha**: `InstallAsync(perfilFabric)` baja también `jre-legacy` (150 MB) porque el perfil hijo no declara `javaVersion` → instalar `26.1` y quitar `JavaFileExtractor` para el hijo; sin reintentos visibles en `ParallelGameInstaller` (issue #112) → envolver con retry propio |
| CmlLib.Core.Auth.Microsoft / XboxAuthNet.Game | 3.3.1 / 1.4.1 (2025-12) | Solo si algún día hay premium |
| Avalonia | 12.1.2 (2026-09-02, net10) · LTS 11.3.20 | Excluir `libSkiaSharp.pdb` (84 MB) y `libHarfBuzzSharp.pdb` (21 MB) del publish; self-contained carpeta ≈ 110 MB, single-file ≈ 50 MB |
| Velopack | 1.2.0 (2026-06-03; repo activo) · CLI `vpk` | `Setup.exe` per-user, deltas, `GithubSource` / `SimpleWebSource`; `--framework` solo si framework-dependent |
| .NET | 10.0.11, LTS hasta 2028-11-14 | Windows 11 **no** trae .NET 10: publicar self-contained |
| DnsClient (NuGet) | — | para resolver SRV |

## Librerías alternativas (TypeScript)

| Paquete | Versión | Notas |
|---|---|---|
| electron / electron-builder / electron-updater | 44.1.1 / 26.16.0 / 6.8.9 | Instalador NSIS hello-world **120 MB**; `electron-updater` hoy fail-open sin firma, **v28 será fail-closed** |
| @xmcl/installer / core / user | 6.3.2 / 2.16.1 / 4.4.2 (ago 2026) | Repo original archivado, el código vive en `Voxelum/x-minecraft-launcher`; workflows `createFabricInstallWorkflow`, `createJavaRuntimeInstallWorkflow`; sin soporte .mrpack en la lib |
| Tauri 2 | 2.11 | Requiere Rust + C++ build tools; sin lib de launcher madura; descartado |

## Backend

| Dato | Valor |
|---|---|
| Vercel | Sin runtime .NET; filesystem read-only (SQLite inviable); Hobby = no comercial, funciones región única (gru1 disponible), **cron 1/día**, 1 regla WAF de rate limit, 100 GB transferencia |
| Neon Free | 0,5 GB, scale-to-zero a 5 min, región `aws-sa-east-1`; proyectos inactivos 90 días borrables desde 2026-10-05 → backup semanal |
| Supabase Free | pausa a los 7 días de inactividad (peor) · Turso Free archiva a los 10 días · Render Free sin disco · Fly sin free tier · Railway Free US$ 1 crédito |
| Cloudflare Workers Free | 10 ms CPU y PBKDF2 ≤ 100k → hashing inseguro; D1 sin región Sudamérica |
| Argon2id | m=19456 KiB, t=2, p=1 (OWASP); `@node-rs/argon2` 2.2.0 con binarios win/linux |
| Tokens | opacos: id + secreto aleatorio, SHA-256 del secreto en DB, comparación constante (patrón Lucia) |
| Usernames Minecraft | 3-16 chars `[A-Za-z0-9_]`, únicos case-insensitive |
| DPAPI | .NET `System.Security.Cryptography.ProtectedData` (CurrentUser); Electron `safeStorage` |
| GitHub Releases | archivos < 2 GiB, sin límite de ancho de banda, `releases/latest/download/<asset>`; API 60 req/h sin token |
| raw.githubusercontent.com | cache 5 min y rate limit agresivo: no usarlo como CDN del manifest |
| Ley 25.326 | IPs = dato personal; aviso de privacidad, retención acotada, derechos de acceso/supresión; una base que no sea "uso exclusivamente personal" formalmente se inscribe en el RNBD |

## Firma y SmartScreen

- Azure Artifact Signing (ex Trusted Signing): individuos solo EE.UU./Canadá; organizaciones solo lista de países sin Argentina.
- Certificados: validez máxima 460 días desde 2026-03-01. Certum Open Source ≈ US$ 58 (persona física, repo público). SSL.com IV ≈ US$ 129/año + token FIPS. Sectigo OV ≈ US$ 219 (empresa).
- SmartScreen: sin firma la reputación arranca de cero en cada archivo nuevo; EV ya no saltea el aviso; Smart App Control (Win11) puede bloquear directamente.
- Historial de falsos positivos de Defender con `PublishSingleFile` + `IncludeNativeLibrariesForSelfExtract`: publicar en carpeta.

## EULA / posicionamiento

- EULA de Minecraft: prohíbe redistribuir el juego o sus archivos; permite herramientas y launchers "as long as they do not seem official"; usar mods "if you've bought Minecraft"; prohíbe dar acceso "in a way that is unfair or unreasonable".
- Login Microsoft para launchers de terceros: app en Entra ID + aprobación de Mojang (`https://aka.ms/mce-reviewappid`), sin SLA; sin ella `login_with_xbox` devuelve 403.
