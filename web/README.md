# Backend de SOBRINOS DE PEPE

Cuentas, aprobación, baneos y el pack de mods. Es lo único que habla con el panel de Minehost: la clave de Pterodactyl vive acá y nunca viaja al launcher.

## Puesta en marcha

1. **Base de datos.** Crear un proyecto en [Neon](https://neon.com) (gratis, elegir la región de São Paulo) y copiar la cadena de conexión.
2. **Clave del panel.** En `https://pterodactyl.minehost.com.ar` ir a `Cuenta → Credenciales de API` y crear una. Empieza con `ptlc_`.
3. **Configuración.** Copiar `.env.example` a `.env.local` y completar `DATABASE_URL` y `PTERODACTYL_KEY`.
4. **Crear las tablas.**

   ```bash
   npm run db:push
   ```

5. **Crear las cuentas que ya juegan**, antes de abrir el registro. El script imprime la contraseña de cada uno para que se la pases.

   ```bash
   npm run db:seed
   ```

   Esto importa: en modo offline el servidor calcula el UUID del jugador a partir de su nombre exacto. Si un viewer se registra como `PEPE` antes que PEPE, se queda con su inventario y no hay vuelta atrás.

6. **Levantar en local o publicar.**

   ```bash
   npm run dev
   ```

   Para producción, importar el repo en Vercel, poner las mismas variables de entorno y elegir la región `gru1` (São Paulo), que es la más cercana a Argentina.

## Comandos

| Comando | Qué hace |
|---|---|
| `npm run dev` | servidor de desarrollo |
| `npm run build` | compila |
| `npm run typecheck` | revisa tipos |
| `npm test` | prueba el flujo completo contra una base embebida, sin tocar Neon ni el panel |
| `npm run serve:test` | levanta el backend entero en tu PC, con una base en memoria, los mods de tu `.minecraft` ya subidos y el pack publicado; sirve para probar el launcher sin tener nada configurado |
| `npm run check:modrinth` | comprueba en vivo la integración con Modrinth |
| `npm run db:push` | aplica el esquema a la base |
| `npm run db:generate` | genera el SQL de migración |
| `npm run db:seed` | crea la cuenta de admin y las de los jugadores actuales |

## API

Todo lo del launcher va con `Authorization: Bearer <token>`. El token sale de registrarse o iniciar sesión y dura 30 días.

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/auth/register` | cualquiera | crea la cuenta, la activa y la mete en la whitelist |
| POST | `/api/auth/login` | cualquiera | devuelve el token, el estado y el rol; reintenta activar una cuenta que quedó pendiente |
| POST | `/api/auth/logout` | con sesión | cierra la sesión |
| GET | `/api/me` | con sesión | estado de la cuenta; la pantalla de espera consulta esto |
| GET | `/api/pack` | cuenta activa | el pack a instalar; una cuenta pendiente recibe 403 |
| GET | `/api/admin/users?q=` | admin | lista de cuentas, pendientes primero |
| POST | `/api/admin/users/:id/approve` | admin | activa la cuenta y la agrega a la whitelist |
| POST | `/api/admin/users/:id/ban` | admin | la marca baneada, la saca de la whitelist, echa al jugador y corta sus sesiones |
| POST | `/api/admin/users/:id/unban` | admin | la reactiva y la vuelve a la whitelist |
| POST | `/api/admin/users/:id/password` | admin | genera una contraseña temporal |
| GET | `/api/admin/mods` | admin | el pack en edición y si hay cambios sin publicar |
| GET | `/api/admin/mods/search?q=` | admin | busca en Modrinth, ya filtrado por versión y loader |
| PATCH | `/api/admin/mods` | admin | versiones disponibles de un mod |
| POST | `/api/admin/mods/upload` | admin | sube uno o varios `.jar` (campo `files`, multipart) |
| POST | `/api/admin/mods` | admin | agrega un mod por link de Modrinth, slug o id |
| DELETE | `/api/admin/mods?projectId=` | admin | lo quita del pack |
| POST | `/api/admin/pack/publish` | admin | valida y publica una versión nueva |
| GET | `/api/admin/server` | admin | estado del servidor según el panel |
| GET | `/api/files/:sha1` | cuenta activa | descarga un `.jar` subido |

## Decisiones que conviene conocer antes de tocar el código

**Primero el servidor, después la base.** Aprobar y banear ejecutan el comando en el servidor y solo entonces escriben en la base. Al revés, si el panel falla quedaría una cuenta marcada como aprobada que el servidor rechaza, y nadie entendería por qué. Cuando el panel falla, la respuesta lo dice y aclara que no se cambió nada.

**Registrarse aprueba solo.** Quien se registra queda activo y en la whitelist en el mismo pedido: aprieta JUGAR y entra. El único filtro que queda es quién tiene el instalador, así que el link del launcher es la puerta del servidor.

Ahí el orden se invierte, y por un motivo: la whitelist se arma leyendo las cuentas activas, así que la cuenta tiene que existir en la base para poder escribirla. Si el panel no contesta se deshace y la cuenta queda pendiente, que es el estado honesto — el servidor la rechazaría igual. Se reintenta en el próximo inicio de sesión, así que cerrar y abrir el launcher la destraba sin que nadie apruebe nada. El botón de aprobar del panel sigue estando para ese caso.

**Los mods se suben como archivo.** El admin elige los `.jar` y el backend hace el resto: calcula los hashes, guarda el archivo y averigua el nombre, la versión y si el mod es de cliente o de servidor. Para eso primero le pregunta a Modrinth por el hash del archivo; si no lo conoce, lee la ficha que todo mod de Fabric lleva adentro. Los archivos quedan en la base: el pack completo pesa unos 20 MB.

**El launcher manda su credencial solo a nuestro dominio.** Los `.jar` propios se sirven únicamente a cuentas aprobadas, así que el launcher se identifica al bajarlos. Esa credencial no viaja a Mojang, ni a Fabric, ni a Modrinth.

**El nombre no se puede cambiar.** Es la identidad del jugador en el servidor. Cambiarlo equivale a perder el inventario.

**Publicar valida antes.** Se revisa que no falte una dependencia obligatoria y que la versión que pide cada mod esté en el pack. Iris 1.11.3, por ejemplo, pide una versión de Sodium que no existe para Minecraft 26.1: publicarlo dejaría a todos con un juego que no arranca. Si algo está mal, no se publica y se explica qué pasa.

**El launcher no tiene secretos.** La clave del panel da control total del servidor. Todo lo de admin pasa por `/api/admin/*`, que exige rol de administrador, y el rol se verifica acá, no en la interfaz.
