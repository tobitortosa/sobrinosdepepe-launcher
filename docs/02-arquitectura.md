# 02 · Arquitectura

> Versión recortada al alcance que pidió Tobías el 2026-09-03: launcher básico, panel de admin adentro del launcher, sin backups, sin modo degradado, sin planes B.
> Los datos técnicos con su fuente están en [`05-hallazgos-verificados.md`](05-hallazgos-verificados.md).

## 1. Qué hace

Un launcher de Windows para el server "SOBRINOS DE PEPE". El viewer lo baja, se crea la cuenta, Tobías la aprueba, aprieta **JUGAR** y entra al server con Minecraft 26.1, Fabric, Java 25 y los mods correctos. Con la cuenta de admin, el mismo launcher muestra dos pantallas más: **MODS** y **USUARIOS**.

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
| Backend | Next.js en Vercel · Postgres en Neon · Drizzle · Argon2id |
| Pack | lista de archivos con hashes, URL y lado cliente/servidor; la forma es compatible con el índice de un `.mrpack` de Modrinth |
| Server | Fabric 26.1 en Minehost, panel Pterodactyl |
| Updates del launcher | GitHub Releases |

**Por qué el backend existe** aunque el panel esté en el launcher: la clave del panel de Minehost da control total del server (archivos, consola, apagarlo). Si viviera dentro del `.exe`, cualquiera que lo descompile la saca. Entonces el launcher admin llama a `/api/admin/*` y el backend es el único que habla con Pterodactyl.

## 4. Flujos

### 4.1 Instalar y actualizar el juego

1. `GET /api/pack` → la lista de mods publicada, con su hash, su tamaño y de dónde bajar cada uno.
2. Con CmlLib: Minecraft 26.1 (client.jar, 75 librerías de Windows, 4.750 assets) y el runtime **Java 25** de Mojang; después el perfil **Fabric 0.19.5**.
3. Mods: compara el sha1 de cada archivo local con el de la lista; baja lo que falta, verifica sha1 y sha512, escribe a un archivo temporal y recién entonces lo renombra. **Borra de `mods/` todo jar que no esté en la lista** (si no, quitar un mod del pack no lo quita de las PCs y el juego crashea).
   Los archivos que subió el admin salen de nuestro backend y van con la credencial de la sesión. Esa credencial se manda solo a nuestro dominio: a Mojang, a Fabric y a Modrinth no se les manda nada.
4. Configs: `config/` y `options.txt` se escriben **solo si no existen**. No se pisan nunca: ahí están los keybinds, los waypoints y la sensibilidad del mouse de cada uno.
5. Escribe `servers.dat` con "SOBRINOS DE PEPE" si no existe.

Primera instalación: ~665 MB en ~5.200 archivos, 3-5 minutos con buena conexión. Después, solo el delta.

Dos cosas que no son fallbacks y se quedan: **reintentar** una descarga que se cortó (con 5.200 archivos, que falle alguna es lo normal) y **verificar el hash** (un archivo cortado no da error de red, da un crash de Java incomprensible).

### 4.2 JUGAR

1. Chequea el pack; si cambió, actualiza.
2. Ping de estado: resuelve el SRV `_minecraft._tcp.sobrinosdepepe.minehost.pro` → `sv36.minehost.pro:25445` y muestra el punto verde o rojo.
3. Lanza el juego con el Java privado, los argumentos que trae el JSON de Mojang, sesión offline (`--username PEPE`, UUID v3 de `OfflinePlayer:PEPE`) y `--quickPlayMultiplayer sobrinosdepepe.minehost.pro`, que lo mete directo al server sin pasar por el menú.
4. Captura la salida del juego y `latest.log` para el botón "Copiar detalles".

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

**Límite honesto, para que lo tengas presente:** en modo offline la whitelist filtra por nombre, no autentica. Alguien con TLauncher que sepa el nombre de un jugador aprobado entra igual. Con cinco amigos no importa; con viewers de stream, el día que a alguien le den ganas de romper, el ban no lo frena. Lo que lo cierra es un mod en el servidor que valide un token del launcher, y está diseñado en la fase 4 como opcional. No lo construimos ahora.

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

## 6. Base de datos

| Tabla | Columnas |
|---|---|
| `users` | id, username (casing exacto), username_lower (único), password_hash, role (player/admin), status (pending/active/banned), created_at, approved_at, banned_at |
| `sessions` | id, user_id, secret_hash, expires_at |
| `pack_mods` | el pack que estás editando: project_id, version_id, title, version_number, filename, url, sha1, sha512, size, side (client/server/both), license, page_url, source (upload/modrinth) |
| `pack_releases` | cada publicación con su contenido congelado: version, content, created_at |
| `mod_files` | los `.jar` que subiste: sha1, filename, size, data |

El UUID offline no se guarda: se calcula del nombre cuando hace falta, y la whitelist del server se maneja por nombre. Un campo menos que se puede desincronizar.

## 7. API

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| POST | `/api/auth/register` | público | crea la cuenta, la activa y la mete en la whitelist |
| POST | `/api/auth/login` | público | devuelve token de sesión, rol y estado |
| GET | `/api/me` | sesión | estado de la cuenta |
| GET | `/api/pack` | cuenta activa | el pack a instalar; una cuenta pendiente recibe 403 |
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
| POST | `/api/admin/pack/publish` | admin | arma y publica el `.mrpack` |
| GET | `/api/admin/server` | admin | estado del server |

## 8. Secretos

| Qué | Dónde |
|---|---|
| API key de Pterodactyl | variable de entorno del backend, y en ningún otro lugar |
| Hashes de contraseñas | Postgres |
| Token de sesión del jugador | su PC, cifrado con DPAPI |
| El launcher | no tiene ningún secreto |

En el server: `white-list=true`, `enforce-whitelist=true` y **ningún jugador con OP** (en Java Edition los OP saltan la whitelist).

## 9. Versiones

El pack fija Minecraft **26.1** (la del server, protocolo 775) y Fabric Loader **0.19.5**. Nunca "la última". Los mods se fijan por versión exacta y los elegís vos cuando los agregás. Si algún día actualizás el server, se actualiza el pack a mano y listo.

## 10. Distribución

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
