/**
 * Prueba el flujo completo del backend contra una base Postgres embebida, sin tocar
 * nada de producción y sin depender de Neon ni del panel de Minehost.
 *
 * Cubre: registrarse, iniciar sesión, que una cuenta pendiente no pueda descargar el
 * pack, aprobar, banear, desbanear, contraseña temporal, agregar y quitar mods,
 * publicar el pack y los chequeos que impiden publicar un pack roto.
 *
 *   npx tsx scripts/test-flow.mts
 */
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
import { drizzle } from 'drizzle-orm/pglite';
import * as schema from '../lib/db/schema';

// --- Preparar la base y engancharla antes de que se importe cualquier módulo que la use.
const pg = new PGlite();
const database = drizzle(pg, { schema });

// Aplica todas las migraciones que haya, en orden.
const { readdirSync: listDir } = await import('node:fs');
for (const file of listDir('drizzle').filter((f) => f.endsWith('.sql')).sort()) {
  const sql = readFileSync(`drizzle/${file}`, 'utf8');
  for (const statement of sql.split('--> statement-breakpoint')) {
    const trimmed = statement.trim();
    if (trimmed) await pg.exec(trimmed);
  }
}

// El cliente de lib/db es un proxy sobre globalThis.db: alcanza con dejarlo puesto.
(globalThis as unknown as { db: unknown }).db = database;

// --- Interceptar las llamadas al panel de Minehost a nivel de red, así se comprueban
// los comandos exactos que se le mandan al servidor sin tocar el código de producción.
process.env.PTERODACTYL_KEY ??= 'ptlc_de_prueba';
const PANEL = process.env.PTERODACTYL_URL ?? 'https://pterodactyl.minehost.com.ar';

const commands: string[] = [];
const archivos = new Map<string, string>();
let panelOffline = false;
const realFetch = globalThis.fetch;

globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
  if (!url.startsWith(PANEL)) return realFetch(input as never, init);

  if (panelOffline) return new Response('', { status: 502 });

  if (url.endsWith('/command')) {
    const body = JSON.parse(String(init?.body ?? '{}')) as { command?: string };
    if (body.command) commands.push(body.command);
    return new Response(null, { status: 204 });
  }

  if (url.includes('/files/write')) {
    const ruta = decodeURIComponent(new URL(url).searchParams.get('file') ?? '');
    archivos.set(ruta, String(init?.body ?? ''));
    return new Response(null, { status: 204 });
  }

  if (url.endsWith('/resources')) {
    return Response.json({ attributes: { current_state: 'running' } });
  }

  return new Response('', { status: 404 });
}) as typeof fetch;

// --- Cargar las rutas después de haber preparado todo.
const register = (await import('../app/api/auth/register/route')).POST;
const login = (await import('../app/api/auth/login/route')).POST;
const me = (await import('../app/api/me/route')).GET;
const packRoute = (await import('../app/api/pack/route')).GET;
const listUsers = (await import('../app/api/admin/users/route')).GET;
const approve = (await import('../app/api/admin/users/[id]/approve/route')).POST;
const ban = (await import('../app/api/admin/users/[id]/ban/route')).POST;
const unban = (await import('../app/api/admin/users/[id]/unban/route')).POST;
const resetPassword = (await import('../app/api/admin/users/[id]/password/route')).POST;
const modsRoute = await import('../app/api/admin/mods/route');
const publish = (await import('../app/api/admin/pack/publish/route')).POST;
const upload = (await import('../app/api/admin/mods/upload/route')).POST;
const fileRoute = (await import('../app/api/files/[sha1]/route')).GET;

// --- Utilidades mínimas de test.
let passed = 0;
const failures: string[] = [];

function check(name: string, condition: boolean, detail = '') {
  if (condition) {
    passed++;
    console.log(`  ok    ${name}`);
  } else {
    failures.push(`${name}${detail ? ` — ${detail}` : ''}`);
    console.log(`  FALLA ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

function post(url: string, body?: unknown, token?: string) {
  return new Request(`http://test${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

function get(url: string, token?: string) {
  return new Request(`http://test${url}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

const params = (id: number) => ({ params: Promise.resolve({ id: String(id) }) });

/** Los nombres que quedaron en la whitelist que el backend le escribió al servidor. */
function enLaWhitelist(): string[] {
  const crudo = archivos.get('/whitelist.json');
  if (!crudo) return [];
  return (JSON.parse(crudo) as { name: string; uuid: string }[]).map((e) => e.name);
}

async function body<T>(response: Response): Promise<T> {
  return (await response.json()) as T;
}

// ---------------------------------------------------------------- Cuentas
console.log('\nCuentas');

let response: Response = await register(post('/api/auth/register', { username: 'PEPE', password: 'secreto1' }));
const admin = await body<{ token: string; user: { status: string } }>(response);
check('se puede registrar', response.status === 201, `status ${response.status}`);
check('la cuenta nueva queda activa sola', admin.user.status === 'active', admin.user.status);
check('y entra en la whitelist en el mismo pedido', enLaWhitelist().includes('PEPE'));
check('con el identificador de offline y no el de Mojang',
  (JSON.parse(archivos.get('/whitelist.json') ?? '[]') as { uuid: string }[])[0].uuid
    === '7a067f19-b48d-3c6e-9039-8f37f64def1f');

// Convertir a PEPE en admin activo, como haría el script de siembra.
const { eq } = await import('drizzle-orm');
async function idOf(username: string): Promise<number> {
  const rows = await database.select({ id: schema.users.id }).from(schema.users).where(eq(schema.users.username, username));
  return rows[0].id;
}
await database
  .update(schema.users)
  .set({ role: 'admin', status: 'active' })
  .where(eq(schema.users.username, 'PEPE'));

response = await register(post('/api/auth/register', { username: 'pepe', password: 'otraclave' }));
check('no se puede repetir el nombre con otras mayúsculas', response.status === 409);

response = await register(post('/api/auth/register', { username: 'ab', password: 'secreto1' }));
check('rechaza un nombre demasiado corto', response.status === 400);

response = await register(post('/api/auth/register', { username: 'con espacio', password: 'secreto1' }));
check('rechaza un nombre con espacios', response.status === 400);

response = await register(post('/api/auth/register', { username: 'Viewer_01', password: 'clave123' }));
const viewer = await body<{ token: string }>(response);

// Con el panel caído la cuenta se crea igual pero vuelve a quedar pendiente: el
// servidor la rechazaría, así que es preferible la pantalla de espera antes que un
// JUGAR que no funciona.
panelOffline = true;
response = await register(post('/api/auth/register', { username: 'SinPanel', password: 'clave123' }));
const sinPanel = await body<{ token: string; user: { status: string } }>(response);
check('si el panel no contesta, la cuenta nueva queda pendiente', sinPanel.user.status === 'pending',
  sinPanel.user.status);
check('y no se cuela en la whitelist', !enLaWhitelist().includes('SinPanel'));
panelOffline = false;

response = await login(post('/api/auth/login', { username: 'PEPE', password: 'malísima' }));
check('no entra con la contraseña incorrecta', response.status === 401);

response = await login(post('/api/auth/login', { username: 'pEpE', password: 'secreto1' }));
const adminLogin = await body<{ token: string; user: { role: string } }>(response);
check('entra sin importar las mayúsculas del nombre', response.status === 200);
check('el rol viaja en el login', adminLogin.user.role === 'admin');

const adminToken = adminLogin.token;

// Con el panel de vuelta, volver a entrar destraba la cuenta sin que nadie apruebe.
response = await login(post('/api/auth/login', { username: 'SinPanel', password: 'clave123' }));
const reintento = await body<{ user: { status: string } }>(response);
check('volver a entrar destraba la cuenta que quedó pendiente', reintento.user.status === 'active',
  reintento.user.status);
check('y ahí sí entra en la whitelist', enLaWhitelist().includes('SinPanel'));

// Se la vuelve a dejar pendiente para las pruebas de más abajo, que necesitan una.
await database.update(schema.users).set({ status: 'pending', approvedAt: null })
  .where(eq(schema.users.usernameLower, 'sinpanel'));

response = await me(get('/api/me', viewer.token));
check('una sesión válida se identifica', response.status === 200);

response = await me(get('/api/me', 'basura.inventada'));
check('un token inventado no sirve', response.status === 401);

// ---------------------------------------------------------------- Permisos
console.log('\nPermisos');

response = await listUsers(get('/api/admin/users', viewer.token));
check('un jugador no entra al panel de admin', response.status === 403);

const viewerId = await idOf('Viewer_01');
const adminId = await idOf('PEPE');
const sinPanelId = await idOf('SinPanel');

response = await listUsers(get('/api/admin/users', adminToken));
const list = await body<{ users: { username: string; status: string }[] }>(response);
check('el admin ve la lista de cuentas', response.status === 200 && list.users.length === 3, `vio ${list.users.length}`);
check('las pendientes aparecen primero', list.users[0].status === 'pending');

response = await packRoute(get('/api/pack', sinPanel.token));
check('una cuenta pendiente no descarga el pack', response.status === 403);

response = await packRoute(get('/api/pack', viewer.token));
check('la que se registró con el panel arriba sí lo descarga', response.status !== 403, `status ${response.status}`);

// ---------------------------------------------------------------- Aprobar y banear
console.log('\nAprobar y banear');

// Aprobar a mano sigue existiendo para las que quedaron pendientes por una caída.
response = await approve(post(`/api/admin/users/${sinPanelId}/approve`, undefined, adminToken), params(sinPanelId));
check('aprobar responde bien', response.status === 200, `status ${response.status}`);
check('aprobar mete el nombre en la whitelist', enLaWhitelist().includes('SinPanel'));
check('y recarga la whitelist en el servidor', commands.includes('whitelist reload'));

panelOffline = true;
response = await ban(post(`/api/admin/users/${viewerId}/ban`, { reason: 'prueba' }, adminToken), params(viewerId));
const offlineError = await body<{ error: string }>(response);
check('si el servidor está apagado, banear falla', response.status === 502);
check('el mensaje lo dice y aclara que no cambió nada', offlineError.error.includes('apagado') && offlineError.error.includes('No se cambió nada'));

let rows = await database.select().from(schema.users).where(eq(schema.users.id, viewerId));
check('la cuenta sigue activa después de la falla', rows[0].status === 'active');
panelOffline = false;

response = await ban(post(`/api/admin/users/${viewerId}/ban`, { reason: 'prueba' }, adminToken), params(viewerId));
check('banear responde bien', response.status === 200);
check('banear saca de la whitelist', !enLaWhitelist().includes('Viewer_01'));

response = await me(get('/api/me', viewer.token));
check('al banear se corta su sesión', response.status === 401 || response.status === 403);

response = await ban(post(`/api/admin/users/${adminId}/ban`, undefined, adminToken), params(adminId));
check('no se puede banear al admin', response.status === 400);

response = await unban(post(`/api/admin/users/${viewerId}/unban`, undefined, adminToken), params(viewerId));
check('desbanear responde bien', response.status === 200);

response = await resetPassword(post(`/api/admin/users/${viewerId}/password`, undefined, adminToken), params(viewerId));
const temporary = await body<{ password: string }>(response);
check('genera una contraseña temporal', response.status === 200 && temporary.password.length >= 6);

response = await login(post('/api/auth/login', { username: 'Viewer_01', password: temporary.password }));
check('la contraseña temporal funciona', response.status === 200);

// ---------------------------------------------------------------- Mods y pack
console.log('\nMods y pack (consulta Modrinth de verdad)');

response = await modsRoute.POST(post('/api/admin/mods', { reference: 'https://modrinth.com/mod/lithium' }, adminToken));
const added = await body<{ mod: { slug: string; sha1: string; side: string } }>(response);
check('agrega un mod desde un link', response.status === 200 && added.mod.slug === 'lithium', `status ${response.status}`);
check('resuelve el hash sin que nadie lo tipee', /^[0-9a-f]{40}$/.test(added.mod.sha1));

response = await modsRoute.POST(post('/api/admin/mods', { reference: 'sodium' }, adminToken));
const sodium = await body<{ mod: { side: string } }>(response);
check('detecta que Sodium es solo de cliente', sodium.mod.side === 'client');

response = await modsRoute.POST(post('/api/admin/mods', { reference: 'no-existe-este-mod-xyz' }, adminToken));
check('avisa si el mod no existe', response.status === 404);

response = await modsRoute.GET(get('/api/admin/mods', adminToken));
const draft = await body<{ mods: unknown[]; hasUnpublishedChanges: boolean }>(response);
check('el pack en edición tiene los dos mods', draft.mods.length === 2);
check('marca que hay cambios sin publicar', draft.hasUnpublishedChanges);

// Iris pide una versión de Sodium que no existe para 26.1: el pack no tiene que publicarse.
response = await modsRoute.POST(post('/api/admin/mods', { reference: 'iris' }, adminToken));
const iris = await body<{ warnings: string[] }>(response);
check('avisa que Iris pide otra versión de Sodium', iris.warnings.length > 0, JSON.stringify(iris.warnings));

response = await publish(post('/api/admin/pack/publish', undefined, adminToken));
const blocked = await body<{ problems?: string[] }>(response);
check('no publica un pack incompatible', response.status === 409, `status ${response.status}`);
check('explica por qué no publicó', (blocked.problems?.length ?? 0) > 0, JSON.stringify(blocked.problems));

response = await modsRoute.DELETE(
  new Request('http://test/api/admin/mods?projectId=YL57xq9U', {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${adminToken}` },
  }),
);
check('quita un mod del pack', response.status === 200);

response = await publish(post('/api/admin/pack/publish', undefined, adminToken));
const released = await body<{ version: string; mods: number }>(response);
check('publica cuando el pack está sano', response.status === 200, `status ${response.status}`);
check('la primera versión es la 1.0.0', released.version === '1.0.0');

response = await packRoute(get('/api/pack', temporary ? (await body<{ token: string }>(await login(post('/api/auth/login', { username: 'Viewer_01', password: temporary.password })))).token : ''));
const payload = await body<{ packVersion: string; minecraft: string; mods: { slug: string }[] }>(response);
check('una cuenta activa descarga el pack', response.status === 200);
check('el pack trae la versión de Minecraft', payload.minecraft === '26.1');
check('el pack trae los mods', payload.mods.length === 2);

response = await publish(post('/api/admin/pack/publish', undefined, adminToken));
const second = await body<{ version: string }>(response);
check('la siguiente publicación sube el número', second.version === '1.0.1');

// ---------------------------------------------------------------- Subir .jar
console.log('\nSubir archivos .jar (los de la instalación real)');

const { readdirSync } = await import('node:fs');
const { join } = await import('node:path');

const MODS_DIR = 'C:/Users/Tobi/AppData/Roaming/.minecraft/mods';
let jars: string[] = [];
try {
  jars = readdirSync(MODS_DIR).filter((f) => f.endsWith('.jar'));
} catch {
  console.log('  (se salta: no encontré la carpeta de mods de referencia)');
}

if (jars.length > 0) {
  function formWith(names: string[]): Request {
    const data = new FormData();
    for (const name of names) {
      const path = join(MODS_DIR, name);
      const bytes = readFileSync(path);
      data.append('files', new File([new Uint8Array(bytes)], name, { type: 'application/java-archive' }));
    }
    return new Request('http://test/api/admin/mods/upload', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminToken}` },
      body: data,
    });
  }

  // Un mod conocido por Modrinth y otro cualquiera, en la misma subida.
  const someJars = jars.slice(0, 3);
  response = await upload(formWith(someJars));
  const uploaded = await body<{
    added: { title: string; version: string; side: string; recognised: boolean; sizeKb: number }[];
    rejected: { filename: string; reason: string }[];
  }>(response);

  check('acepta varios .jar de una', response.status === 200 && uploaded.added.length === someJars.length,
    `subidos ${uploaded.added.length}/${someJars.length} ${JSON.stringify(uploaded.rejected)}`);
  check('averigua el nombre y la versión de cada uno',
    uploaded.added.every((m) => m.title.length > 0 && m.version.length > 0),
    JSON.stringify(uploaded.added.map((m) => `${m.title} ${m.version}`)));
  check('reconoce los que están en Modrinth', uploaded.added.some((m) => m.recognised));
  check('sabe si el mod es de cliente o de servidor',
    uploaded.added.every((m) => ['client', 'server', 'both'].includes(m.side)));

  // Un archivo que no es un mod.
  const notAJar = new FormData();
  notAJar.append('files', new File([new Uint8Array([1, 2, 3])], 'cualquiera.jar'));
  response = await upload(new Request('http://test/api/admin/mods/upload', {
    method: 'POST',
    headers: { Authorization: `Bearer ${adminToken}` },
    body: notAJar,
  }));
  const bad = await body<{ added: unknown[]; rejected: { reason: string }[] }>(response);
  check('rechaza un archivo que no es un mod de Fabric',
    bad.added.length === 0 && bad.rejected.length === 1, JSON.stringify(bad));

  // Publicar y descargar de verdad uno de los archivos subidos.
  response = await publish(post('/api/admin/pack/publish', undefined, adminToken));
  check('publica el pack con archivos propios', response.status === 200, `status ${response.status}`);

  const viewerToken = (await body<{ token: string }>(
    await login(post('/api/auth/login', { username: 'Viewer_01', password: temporary.password })),
  )).token;

  response = await packRoute(get('/api/pack', viewerToken));
  const withUploads = await body<{ mods: { url: string; sha1: string; filename: string }[] }>(response);
  const own = withUploads.mods.find((m) => m.url.includes('/api/files/'));
  check('el pack apunta a los archivos propios con dirección completa',
    Boolean(own && own.url.startsWith('http')), own?.url ?? 'ninguno');

  if (own) {
    response = await fileRoute(get(`/api/files/${own.sha1}`, viewerToken), {
      params: Promise.resolve({ sha1: own.sha1 }),
    });
    const downloaded = Buffer.from(await response.arrayBuffer());
    const { createHash } = await import('node:crypto');
    check('se puede descargar el archivo subido', response.status === 200);
    check('lo descargado coincide con el hash publicado',
      createHash('sha1').update(downloaded).digest('hex') === own.sha1);

    response = await fileRoute(get(`/api/files/${own.sha1}`), { params: Promise.resolve({ sha1: own.sha1 }) });
    check('sin sesión no se puede descargar', response.status === 401);
  }
}

// ---------------------------------------------------------------- Resultado
console.log(`\n${passed} comprobaciones pasaron, ${failures.length} fallaron.`);
if (failures.length > 0) {
  for (const failure of failures) console.log(`  - ${failure}`);
  process.exit(1);
}
console.log('Comandos enviados al servidor durante la prueba:');
for (const command of commands) console.log(`  ${command}`);
process.exit(0);
