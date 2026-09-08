/**
 * Levanta el backend completo en un solo proceso, con una base en memoria y el panel
 * de Minehost simulado. Sirve para probar el launcher de punta a punta sin tener
 * todavía la base en la nube ni la clave del panel.
 *
 *   npx tsx scripts/serve-test.mts
 *
 * Deja listo: la cuenta PEPE como administrador, los mods de la instalación de
 * referencia ya subidos y el pack publicado. Después:
 *
 *   dotnet run --project src/SobrinosDePepe.Spike -- --api http://127.0.0.1:3100 --user PEPE --pass test1234
 */
import { createServer } from 'node:http';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { PGlite } from '@electric-sql/pglite';
import { drizzle } from 'drizzle-orm/pglite';
import { eq } from 'drizzle-orm';
import * as schema from '../lib/db/schema';

const PORT = Number(process.env.PORT ?? 3100);
const MODS_DIR = process.env.MODS_DIR ?? 'C:/Users/Tobi/AppData/Roaming/.minecraft/mods';
const ADMIN_PASSWORD = 'test1234';

// --- Base en memoria con el esquema aplicado.
const pg = new PGlite();
const database = drizzle(pg, { schema });
for (const file of readdirSync('drizzle').filter((f) => f.endsWith('.sql')).sort()) {
  for (const statement of readFileSync(`drizzle/${file}`, 'utf8').split('--> statement-breakpoint')) {
    const trimmed = statement.trim();
    if (trimmed) await pg.exec(trimmed);
  }
}
(globalThis as unknown as { db: unknown }).db = database;

// --- Panel de Minehost simulado: registra los comandos en vez de ejecutarlos.
process.env.PTERODACTYL_KEY ??= 'ptlc_de_prueba';
// Con esto se firman los permisos de entrada. En la prueba no importa cuál sea, pero
// tiene que existir: el launcher pide uno cada vez que abre el juego.
process.env.ACCESS_SECRET ??= 'secreto-de-prueba';
const PANEL = process.env.PTERODACTYL_URL ?? 'https://pterodactyl.minehost.com.ar';
const commands: string[] = [];
const realFetch = globalThis.fetch;
globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
  if (!url.startsWith(PANEL)) return realFetch(input as never, init);
  if (url.endsWith('/command')) {
    const body = JSON.parse(String(init?.body ?? '{}')) as { command?: string };
    if (body.command) {
      commands.push(body.command);
      console.log(`  [servidor simulado] ${body.command}`);
    }
    return new Response(null, { status: 204 });
  }
  if (url.endsWith('/resources')) return Response.json({ attributes: { current_state: 'running' } });
  return new Response('', { status: 404 });
}) as typeof fetch;

// --- Rutas reales del backend.
const routes = {
  register: (await import('../app/api/auth/register/route')).POST,
  login: (await import('../app/api/auth/login/route')).POST,
  logout: (await import('../app/api/auth/logout/route')).POST,
  me: (await import('../app/api/me/route')).GET,
  pack: (await import('../app/api/pack/route')).GET,
  users: (await import('../app/api/admin/users/route')).GET,
  approve: (await import('../app/api/admin/users/[id]/approve/route')).POST,
  ban: (await import('../app/api/admin/users/[id]/ban/route')).POST,
  unban: (await import('../app/api/admin/users/[id]/unban/route')).POST,
  password: (await import('../app/api/admin/users/[id]/password/route')).POST,
  mods: await import('../app/api/admin/mods/route'),
  upload: (await import('../app/api/admin/mods/upload/route')).POST,
  search: (await import('../app/api/admin/mods/search/route')).GET,
  publish: (await import('../app/api/admin/pack/publish/route')).POST,
  server: (await import('../app/api/admin/server/route')).GET,
  file: (await import('../app/api/files/[sha1]/route')).GET,
  ticket: (await import('../app/api/ticket/route')).GET,
};

// --- Datos de arranque: admin, mods subidos y pack publicado.
const { hashPassword } = await import('../lib/auth');
await database.insert(schema.users).values({
  username: 'PEPE',
  usernameLower: 'pepe',
  passwordHash: await hashPassword(ADMIN_PASSWORD),
  role: 'admin',
  status: 'active',
  approvedAt: new Date(),
});

const token = await (async () => {
  const response = await routes.login(
    new Request('http://local/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'PEPE', password: ADMIN_PASSWORD }),
    }),
  );
  return ((await response.json()) as { token: string }).token;
})();

let jars: string[] = [];
try {
  jars = readdirSync(MODS_DIR).filter((f) => f.endsWith('.jar'));
} catch {
  console.log(`No encontré ${MODS_DIR}: el pack va a quedar vacío.`);
}

if (jars.length > 0) {
  const form = new FormData();
  for (const name of jars) {
    const bytes = readFileSync(join(MODS_DIR, name));
    form.append('files', new File([new Uint8Array(bytes)], name, { type: 'application/java-archive' }));
  }
  const response = await routes.upload(
    new Request('http://local/api/admin/mods/upload', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    }),
  );
  const uploaded = (await response.json()) as { added: unknown[]; rejected: unknown[] };
  console.log(`Mods subidos: ${uploaded.added.length}, rechazados: ${uploaded.rejected.length}`);

  const published = await routes.publish(
    new Request('http://local/api/admin/pack/publish', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    }),
  );
  const release = (await published.json()) as { version?: string; error?: string; problems?: string[] };
  console.log(
    release.version
      ? `Pack publicado: ${release.version}`
      : `No se pudo publicar: ${release.error} ${JSON.stringify(release.problems ?? [])}`,
  );
}

// --- Servidor HTTP que despacha a las rutas reales.
function toRequest(url: string, method: string, headers: Record<string, string>, body: Buffer) {
  return new Request(url, {
    method,
    headers,
    body: method === 'GET' || method === 'HEAD' ? undefined : new Uint8Array(body),
  });
}

createServer((incoming, outgoing) => {
  const chunks: Buffer[] = [];
  incoming.on('data', (chunk: Buffer) => chunks.push(chunk));
  incoming.on('end', async () => {
    const path = (incoming.url ?? '/').split('?')[0];
    const url = `http://127.0.0.1:${PORT}${incoming.url ?? '/'}`;
    const headers = Object.fromEntries(
      Object.entries(incoming.headers).map(([k, v]) => [k, Array.isArray(v) ? v.join(',') : (v ?? '')]),
    );
    const request = toRequest(url, incoming.method ?? 'GET', headers, Buffer.concat(chunks));

    const userMatch = path.match(/^\/api\/admin\/users\/(\d+)\/(approve|ban|unban|password)$/);
    const fileMatch = path.match(/^\/api\/files\/([0-9a-f]{40})$/);

    try {
      let response: Response;

      if (path === '/api/auth/register') response = await routes.register(request);
      else if (path === '/api/auth/login') response = await routes.login(request);
      else if (path === '/api/auth/logout') response = await routes.logout(request);
      else if (path === '/api/me') response = await routes.me(request);
      else if (path === '/api/pack') response = await routes.pack(request);
      else if (path === '/api/ticket') response = await routes.ticket(request);
      else if (path === '/api/admin/users') response = await routes.users(request);
      else if (path === '/api/admin/mods/search') response = await routes.search(request);
      else if (path === '/api/admin/mods/upload') response = await routes.upload(request);
      else if (path === '/api/admin/pack/publish') response = await routes.publish(request);
      else if (path === '/api/admin/server') response = await routes.server(request);
      else if (path === '/api/admin/mods') {
        const method = incoming.method ?? 'GET';
        response =
          method === 'POST'
            ? await routes.mods.POST(request)
            : method === 'PATCH'
              ? await routes.mods.PATCH(request)
              : method === 'DELETE'
                ? await routes.mods.DELETE(request)
                : await routes.mods.GET(request);
      } else if (userMatch) {
        const context = { params: Promise.resolve({ id: userMatch[1] }) };
        const action = userMatch[2] as 'approve' | 'ban' | 'unban' | 'password';
        response = await routes[action](request, context);
      } else if (fileMatch) {
        response = await routes.file(request, { params: Promise.resolve({ sha1: fileMatch[1] }) });
      } else {
        response = Response.json({ error: 'Ruta desconocida.' }, { status: 404 });
      }

      const payload = Buffer.from(await response.arrayBuffer());
      const outHeaders: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        outHeaders[key] = value;
      });
      outgoing.writeHead(response.status, outHeaders);
      outgoing.end(payload);
      console.log(`${incoming.method} ${path} -> ${response.status}`);
    } catch (error) {
      console.error(`${incoming.method} ${path} -> excepción`, error);
      outgoing.writeHead(500, { 'Content-Type': 'application/json' });
      outgoing.end(JSON.stringify({ error: 'Falló el servidor de prueba.' }));
    }
  });
}).listen(PORT, '127.0.0.1', () => {
  console.log(`\nBackend de prueba en http://127.0.0.1:${PORT}`);
  console.log(`Administrador: PEPE / ${ADMIN_PASSWORD}`);
  console.log('\nProbá el launcher con:');
  console.log(`  dotnet run --project src/SobrinosDePepe.Spike -- --api http://127.0.0.1:${PORT} --user PEPE --pass ${ADMIN_PASSWORD} --no-launch`);
});

async function idOf(username: string) {
  const rows = await database
    .select({ id: schema.users.id })
    .from(schema.users)
    .where(eq(schema.users.username, username));
  return rows[0]?.id;
}
void idOf;
