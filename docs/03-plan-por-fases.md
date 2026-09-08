# 03 · Plan por fases

> Cuatro fases. Cada una termina en algo que se puede probar. Primero lo incierto: lanzar Minecraft 26.1 con Fabric y Java 25 desde código. Si eso no anda, no importa nada más.

| Fase | Qué queda funcionando | Estimación |
|---|---|---|
| **0 · Spike** | ✅ Listo. Una app de consola que instala todo en una carpeta aislada y te mete al server. | hecho |
| **1 · Backend** | ✅ Listo. Cuentas, aprobar, banear, whitelist automática, subir mods y publicar. | hecho |
| **2 · Launcher** | La interfaz: login, JUGAR, progreso, errores, auto-update. **Primer release.** | 4-5 días |
| **3 · Admin** | Pantallas MODS y USUARIOS dentro del launcher | 2 días |
| **4 · Acceso** | ✅ Listo. El servidor puede exigir el launcher, con un interruptor en el `.env`. | hecho |

Después: ✅ **fase 4**, el mod que hace que al servidor solo se entre con el launcher. Hecha el 2026-09-07, ver el final.

## Lo que necesito de vos

- [ ] Repo en GitHub. Monorepo: `launcher/`, `web/`, `docs/`.
- [ ] Cuenta en Vercel y en Neon (las dos gratis).
- [ ] **En Pterodactyl, cinco minutos:** probar crear una API key en `Cuenta → Credenciales de API`; ver qué muestra `Network` (cuántas allocations) y `Startup` (imagen de Java).
- [ ] Confirmar los nombres: `PEPE`, `Chichon`, `Titit0N`, `Luquitas1410`, `Felix_1256`.
- [ ] Cuánta RAM tienen las PCs de los jugadores, aproximado. Define el `-Xmx` por defecto.

---

## Fase 0 · Spike

**Objetivo:** probar que el núcleo funciona antes de escribir una sola pantalla.

`launcher/Spike/`, consola C# .NET 10 con CmlLib.Core:

1. Instala Minecraft 26.1 en `%LOCALAPPDATA%\SobrinosDePepe\game`: client.jar, las 75 librerías de Windows, los 4.750 assets y el runtime Java 25 de Mojang. Con reintentos y verificación de sha1.
2. Instala el perfil Fabric 0.19.5. Ojo con esto: hay que sacar el extractor de Java del perfil hijo, porque si no baja 150 MB de Java 8 que no sirven para nada.
3. Descarga los mods del pack verificando sha1 y sha512, y copia las configs.
4. Lanza el juego con sesión offline (`--username PEPE`, UUID derivado del nombre) y `--quickPlayMultiplayer sobrinosdepepe.minehost.pro`.
5. Loguea todo y captura la salida del juego.

**Listo cuando:** apareces en el server como PEPE, con tu inventario, desde una carpeta que no es `.minecraft`, y un amigo sin Java instalado repite el proceso en su PC.

---

## Fase 1 · Backend

`web/`, Next.js en Vercel con Postgres en Neon y Drizzle.

- Las cuatro tablas y sus migraciones.
- Registro, login, `/api/me`, `/api/pack`. Argon2id, token opaco de 30 días, username inmutable con casing exacto y único sin distinguir mayúsculas. Límite de intentos por IP configurado en Vercel.
- Las cinco cuentas actuales creadas antes de abrir el registro, con una contraseña que les pasás vos.
- `/api/admin/*`: listar usuarios, aprobar, banear, desbanear, contraseña temporal, subir `.jar`, quitar mods y publicar el pack.
- Integración con Pterodactyl: `whitelist add`, `whitelist remove`, `kick`. Primero el comando, después la base.
- Los `.jar` que sube el admin se guardan en la base y se sirven por `/api/files/<sha1>` solo a cuentas aprobadas.

**Probado:** 52 comprobaciones automáticas, incluida la subida de los 17 jars reales, su identificación por hash y la descarga verificada. Y el circuito completo contra el launcher: publicar una versión que saca un mod hace que el launcher lo borre en el arranque siguiente, en un segundo.

**Falta para ponerlo en producción:** la base en la nube y la clave del panel.

---

## Fase 2 · Launcher

`launcher/`, Avalonia 12 sobre el núcleo de la fase 0, empaquetado con Velopack.

- Pantallas: crear cuenta, iniciar sesión, Home con JUGAR y el punto de estado del server, progreso de descarga, error con "Copiar detalles". La de espera quedó solo para cuando el panel no contesta al registrarse.
- Instalación incremental: sincroniza `mods/` con el pack y borra lo que no esté; escribe `config/` y `options.txt` solo si no existen.
- Botón "Reparar instalación", que reusa el mismo código.
- Sesión cifrada con DPAPI. Chequeo de disco libre, de Windows 64 bits y de que no haya otro launcher abierto.
- `Setup.exe` con auto-update desde GitHub Releases.

**Listo cuando:** un amigo baja el instalador, se registra, aprieta JUGAR y entra. Y cuando publicás una versión nueva, se actualiza solo al abrir.

---

## Fase 3 · Admin dentro del launcher

- Botón ADMIN visible si la cuenta tiene rol admin, con dos pestañas.
- **USUARIOS**: lista con estado y fecha, buscador, botones aprobar, banear, desbanear y contraseña temporal.
- **MODS**: pack actual, subir `.jar` (arrastrando varios de una), quitar, y botón publicar. Al publicar, el backend te dice si algún jar hay que subirlo también al server.
- Créditos: la lista de mods con autor, licencia y link. Es obligatorio por las licencias de Simple Voice Chat y del shader Complementary.

**Listo cuando:** agregás un mod desde el launcher, publicás, y en otra PC el launcher lo descarga solo al abrir.

---

## Fase 4 · Al servidor se entra con el launcher ✅

Hecha el 2026-09-07. El detalle completo está en [`02-arquitectura.md`](02-arquitectura.md), punto 4.6.

El problema que resuelve, y por qué se hizo antes de lo previsto: el que no usa el launcher juega con los mods que le pasamos hace unos días. No están al día, y con el tiempo eso es un servidor donde cada uno tiene una versión distinta de las reglas. El launcher es lo único que garantiza que todos tengan lo mismo, así que ahora es obligatorio. De paso cierra la whitelist, que en modo offline filtra por nombre y no autentica.

Cómo quedó:

- `mod-acceso/`: un solo `.jar` que va en los dos lados. En el servidor le pide al cliente el permiso durante el login y desconecta con un cartel a quien no lo tenga; en el cliente lo contesta leyéndolo de la variable de entorno que le dejó el launcher.
- `GET /api/ticket`: el backend firma un permiso con `ACCESS_SECRET`, a nombre de quien lo pide y por doce horas.
- El launcher lo pide al apretar JUGAR, con el pack ya al día, y se lo pasa al juego.
- `servidor/subir-acceso.py`: sube el jar y la config al servidor y, si el candado queda puesto, echa a los que estén jugando con el motivo escrito.
- **El interruptor:** `REQUIRE_LAUNCHER` en el `.env`. En `false` el servidor revisa igual y anota en el log quién entró sin el launcher, pero no echa a nadie; en `true` no entra nadie sin él. El mod lee su config en cada intento de entrar, así que cambiarlo no reinicia el servidor.

Un cliente sin nuestros mods se delata solo: el protocolo de Minecraft obliga a contestar el pedido del servidor durante el login, y contesta "no entendí".

**Probado:** los ocho casos de firma entre el backend (Node) y el servidor (Java) — permiso vigente, vencido, con la firma cambiada, firmado con otro secreto, vacío, con basura adentro, de otra versión del formato y a nombre de otro jugador — más siete comprobaciones nuevas en `npm run test`, que ahora son 67.

## Cosas sueltas para cuando estén las fases

- Instalar SkinRestorer en el server, así nadie es Steve. Cada uno pide su skin por chat y no escribimos código.
- Sacar de la carpeta `mods` del server los jars que son solo de cliente. No rompen nada, pero ensucian el log.
- Donde compartas el link de descarga, escribir que Windows va a decir "Windows protegió tu PC" y que hay que ir a "Más información → Ejecutar de todas formas". Sin eso, con viewers, muchos no pasan de ahí.
