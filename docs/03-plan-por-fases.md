# 03 · Plan por fases

> Cuatro fases. Cada una termina en algo que se puede probar. Primero lo incierto: lanzar Minecraft 26.1 con Fabric y Java 25 desde código. Si eso no anda, no importa nada más.

| Fase | Qué queda funcionando | Estimación |
|---|---|---|
| **0 · Spike** | ✅ Listo. Una app de consola que instala todo en una carpeta aislada y te mete al server. | hecho |
| **1 · Backend** | ✅ Listo. Cuentas, aprobar, banear, whitelist automática, subir mods y publicar. | hecho |
| **2 · Launcher** | La interfaz: login, JUGAR, progreso, errores, auto-update. **Primer release.** | 4-5 días |
| **3 · Admin** | Pantallas MODS y USUARIOS dentro del launcher | 2 días |

Después, si hace falta: mod de verificación en el servidor (fase 4, opcional, ver el final).

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

## Fase 4 · Opcional: verificación real en el server

No entra en el alcance de ahora. Queda escrito para cuando haga falta.

El problema que resuelve: en modo offline la whitelist filtra por nombre, no autentica. Alguien con TLauncher que sepa el nombre de un jugador aprobado entra igual, así que el ban no lo frena de verdad. Con cinco amigos da lo mismo. Con viewers de stream, el día que aparezca un troll, esto es lo que lo cierra.

Cómo se hace: un mod en el servidor que durante el login le pide al cliente un token que emitió el backend, lo valida y desconecta a quien no lo tenga; más un mod chico en el cliente que lo manda. Verifiqué que la API de red de Fabric para esto existe en 26.1. Son unas 400 líneas de Java, más el toolchain (JDK 25, Gradle, Loom) y subirlo por SFTP.

Cuánto cuesta: dos o tres días, y el corte es duro. El día que se active, todos tienen que usar el launcher: con TLauncher ya nadie entra.

## Cosas sueltas para cuando estén las fases

- Instalar SkinRestorer en el server, así nadie es Steve. Cada uno pide su skin por chat y no escribimos código.
- Sacar de la carpeta `mods` del server los jars que son solo de cliente. No rompen nada, pero ensucian el log.
- Donde compartas el link de descarga, escribir que Windows va a decir "Windows protegió tu PC" y que hay que ir a "Más información → Ejecutar de todas formas". Sin eso, con viewers, muchos no pasan de ahí.
