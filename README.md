# SOBRINOS DE PEPE Launcher

Launcher de Windows para el server de Minecraft "SOBRINOS DE PEPE" (`sobrinosdepepe.minehost.pro`, Fabric 26.1). El viewer lo baja, se crea la cuenta, aprieta **JUGAR** y entra al server con la versión, el Java y los mods correctos. Con la cuenta de admin, el mismo launcher gestiona los mods y las cuentas.

**Estado:** fases 0 y 1 listas y probadas. El instalador entra al server con el inventario intacto, y el backend maneja cuentas, subida de mods y publicación del pack. Desde el 2026-09-07 el server no deja entrar a quien no usa el launcher (fase 4), salvo los nombres de `LAUNCHER_EXEMPT`. Desde el 2026-09-11 tiene además anti-xray y anticheat de movimiento, y el server corre 26.1.2 (los jugadores siguen en 26.1: es el mismo protocolo). Falta la interfaz (fase 2) y el panel de admin dentro del launcher (fase 3).

## Documentos

| Doc | Para qué |
|---|---|
| [`docs/01-entorno-de-referencia.md`](docs/01-entorno-de-referencia.md) | Qué hay exactamente en la PC de referencia y en el server. Fuente de verdad del pack. |
| [`docs/02-arquitectura.md`](docs/02-arquitectura.md) | Pantallas, componentes, flujos, base de datos, API, secretos. |
| [`docs/03-plan-por-fases.md`](docs/03-plan-por-fases.md) | Las cuatro fases con su criterio de "listo". |
| [`docs/04-decisiones-y-preguntas.md`](docs/04-decisiones-y-preguntas.md) | Lo decidido, lo que quedó fuera de alcance y lo que falta. |
| [`docs/05-hallazgos-verificados.md`](docs/05-hallazgos-verificados.md) | Datos técnicos con su fuente: URLs, versiones, límites y trampas. |
| [`docs/research/`](docs/research/) | Los informes completos de la investigación, con nivel de confianza por afirmación. |
| [`reference/pack-inventory.json`](reference/pack-inventory.json) | Inventario de la instalación de referencia: hashes, IDs de Modrinth, UUIDs offline. |

## Código

```
web/                           backend y página de descarga (Next.js)
├── lib/                       env · db · auth · username · pterodactyl · modrinth · pack · api
├── app/api/                   16 rutas: cuentas, pack, permiso de entrada y admin
├── scripts/                   seed · test-flow · serve-test · check-modrinth
└── drizzle/                   migraciones

mod-acceso/                    el mod que hace que al servidor se entre solo con el launcher
├── AccesoServidor             pide el permiso durante el login y echa a quien no lo tenga
├── AccesoCliente              lo contesta, y le dice al server que mods tiene cargados
├── Tramposos                  los clientes de trampas que no entran (Meteor, xray, Wurst)
├── Ticket                     el formato del permiso y su firma
├── ConfigAcceso               el secreto y el link, de config/acceso-de-pepe.json
└── Carteles                   lo que ve el que no entra, con el link para bajarse el launcher

mod-precios/                   muestra en cada item cuanta plata paga el servidor por el

mod-equipos/                   los equipos: /equipo crear, invitar, aceptar, echar y salir
├── EquiposServidor            registra el comando y carga los equipos al arrancar
├── Registro                   quien esta con quien, en config/equipos-de-pepe.json,
│                              espejado en equipos del scoreboard (sin fuego amigo,
│                              y la barra de arriba muestra solo a los companeros)
├── Invitaciones               las que estan esperando respuesta, dos minutos
├── ComandoEquipo              /equipo y sus cinco subcomandos
└── Carteles                   lo que el jugador lee, con los colores del servidor

launcher/
├── pack.json                  copia del pack publicado; solo la usa la CLI de la fase 0
└── src/
    ├── SobrinosDePepe.Core/        instalación y arranque del juego
    │   ├── LauncherPaths      dónde vive todo
    │   ├── Pack               modelo del pack
    │   ├── OfflineIdentity    UUID derivado del nombre, igual al que calcula el server
    │   ├── HashedDownloader   descarga con reintentos y verificación de hash
    │   ├── ModSynchronizer    deja mods/ igual al pack, borra lo que sobra
    │   │                      sin excepciones: mods/ queda igual al pack
    │   ├── GameSetup          Minecraft + Java 25 + perfil de Fabric
    │   ├── GameRunner         arranca el juego y captura su salida
    │   ├── ServerStatus       resuelve el SRV y pregunta si el server está online
    │   ├── LauncherApi        cuentas y pack contra el backend
    │   ├── SessionStore       token guardado cifrado con la cuenta de Windows
    │   ├── PackInstaller      deja la instalación igual al pack publicado
    │   ├── ConfigSeeder       configs solo si no existen
    │   └── Uninstall          borra todo lo que dejó el launcher, sin residuos
    └── SobrinosDePepe.Spike/       app de consola de la fase 0
```

Probar la instalación completa sin abrir el juego:

```
cd launcher
dotnet run --project src/SobrinosDePepe.Spike -- PEPE --no-launch
```

Instalar y entrar al server:

```
dotnet run --project src/SobrinosDePepe.Spike -- PEPE
```

Se instala en `%LOCALAPPDATA%\SobrinosDePepe`. No toca `%APPDATA%\.minecraft`, así que TLauncher sigue funcionando.

Probar el circuito completo en tu PC, sin configurar nada (base en memoria, tus mods ya subidos):

```
cd web && npm run serve:test
```

y en otra terminal:

```
cd launcher
dotnet run --project src/SobrinosDePepe.Spike -- --api http://127.0.0.1:3100 --user PEPE --pass test1234
```

Backend: ver [`web/README.md`](web/README.md) para la puesta en marcha real.

## Las reglas del proyecto

1. El jugador no configura nada: crear cuenta y JUGAR. La cuenta se activa sola al registrarse; el único filtro es quién tiene el instalador.
2. Instalación aislada. Nunca se toca la instalación de TLauncher.
3. Minecraft, Fabric, Java y los mods se descargan de sus servidores oficiales a la PC del jugador. Nunca rehosteamos nada.
4. El launcher no contiene ningún secreto. Descompilarlo no da más poder que ser un usuario.
   El permiso de entrada al servidor lo firma el backend y el launcher solo lo transporta.
5. La identidad en el server es el nombre exacto: los usernames son inmutables y respetan mayúsculas.
6. Todo archivo descargado se verifica por hash.
7. Las versiones son fijas y las elige el admin. Nunca "la última".
8. El panel de admin vive dentro del launcher, pero las acciones las ejecuta el backend.
9. Si algo falla, mensaje claro. No hay caminos alternativos.
10. Sin monetizar el launcher ni el server.
11. **Al servidor se entra con el launcher, cuando el candado está puesto.** Es lo que
    garantiza que todos tengan los mismos mods y la misma versión, y es por donde el
    server se entera de qué mods trae cada uno. El interruptor es `REQUIRE_LAUNCHER`
    en el `.env`: en `true` quien abre Minecraft por otro lado no entra y ve un cartel
    con la dirección de la página; en `false` entra igual, pero el servidor lo anota.
    `LAUNCHER_EXEMPT` es la lista corta de nombres que entran igual con el candado
    puesto. Se aplica con `python servidor/subir-acceso.py`. Cómo funciona: `docs/02`, 4.6.
12. **Las trampas se frenan del lado del servidor, no del cliente.** Todo lo que le
    preguntamos al cliente lo puede contestar con mentiras; el anti-xray y el anticheat
    viven adentro del servidor, donde el cliente no llega. Se instalan con
    `python servidor/subir-antitrampas.py`. Qué frena cada uno: `docs/02`, 4.7.
