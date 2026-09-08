# 04 · Decisiones y lo que falta

## Decidido

| Tema | Decisión |
|---|---|
| Launcher | C# / .NET 10 · Avalonia 12 · CmlLib.Core · Velopack. La librería ya parsea 26.1, instala Fabric y baja el Java 25; probado en la PC de Tobías. |
| Backend | Next.js en Vercel · Postgres en Neon · Drizzle · Argon2id. Costo cero. |
| Panel de admin | **Dentro del launcher**, con una cuenta de rol admin. Las acciones las ejecuta el backend, que es el único que tiene la clave de Pterodactyl. |
| Alta de cuentas | Registro abierto para viewers. Quedan pendientes hasta que Tobías las aprueba. Sin email: si alguien olvida la contraseña, se le pone una temporal desde el panel. |
| Banear | Marca la cuenta y saca al jugador de la whitelist, más un `kick` si está conectado. |
| Versión | Minecraft 26.1 y Fabric Loader 0.19.5, fijos. Los mods se fijan por versión exacta. |
| Pack | Formato `.mrpack` de Modrinth. Los mods se descargan de Modrinth, nunca se rehostean. |
| Java | El runtime de Mojang, Java 25, dentro de la carpeta del launcher. Da igual qué Java tenga el jugador. |
| Firma de código | Sin firma. Se avisa del cartel de SmartScreen donde se comparta el link. |
| Skins | SkinRestorer instalado en el server. Cada uno pide la suya por chat. Cero código nuestro. |
| Shaders | Fuera del pack. |
| MapLink | Fuera del pack: su configuración ignora este server. |
| Entrar solo con el launcher | Construido el 2026-09-07 (fase 4) y **queda apagado**: `REQUIRE_LAUNCHER=false`. El servidor anota en el log quién entra sin el launcher, pero no echa a nadie. Se prende cambiando esa variable y corriendo `servidor/subir-acceso.py`, sin reiniciar ni sacar a nadie del juego. La idea es cerrarlo cuando todos hayan abierto el launcher al menos una vez. |
| Xaero's Minimap y World Map | Fuera del pack y del servidor desde el 2026-09-06. El minimapa trae radar de jugadores, y con PvP libre en todo el mundo eso es saber siempre dónde está cada uno. |

## Fuera de alcance, por pedido explícito

Backups del mundo, monitoreo de las URLs del pack, modo degradado para jugar sin backend, registro de IPs, auditoría de acciones de admin, códigos de invitación y de reset, anti-multicuenta, login de Microsoft, planes B de stack y alternativas de cualquier tipo.

## Lo que falta

**Contestada la pregunta de fondo.** El ban saca de la whitelist y marca la cuenta, pero por sí solo no impedía entrar: en modo offline la whitelist filtra por nombre y no autentica. La fase 4 se construyó el 2026-09-07 y lo cierra, con la particularidad de que el corte es un interruptor y no una decisión de una sola vez: hoy está en `false` y el servidor solo anota quién entra sin el launcher.

**Hay que encender la whitelist antes de que sirva aprobar y banear.** Hoy el servidor tiene `white-list=false`, así que la lista existe pero no filtra a nadie. El orden correcto es: meter a los cinco jugadores actuales, después poner `white-list=true` y `enforce-whitelist=true`. Si se enciende antes de agregarlos, quedan todos afuera. Hace falta que Tobías lo apruebe porque cambia quién puede entrar al servidor.

**Datos que necesito de vos:**

1. La cadena de conexión de la base (Neon). Es lo único que falta para poner el backend en producción.
2. Cuánta RAM tienen las PCs de los jugadores, aproximado. Define el `-Xmx` por defecto: 3, 4 o 6 GB.
3. ¿Cuántos viewers esperás? Con cientos de registros, aprobar de a uno a mano se hace pesado.
4. Cuando subís un mod desde el panel, ¿se publica al toque para todos, o querés probarlo vos primero y después apretar publicar?

**Resuelto:** la clave del panel ya está y funciona (probada de solo lectura contra el servidor). Los cinco nombres se confirmaron en los logs del juego. La voz quedó andando en el puerto 25446.
