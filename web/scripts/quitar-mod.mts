/**
 * Saca uno o varios mods del pack y publica UNA versión nueva.
 *   npm run pack:quitar -- xaerominimap xaeroworldmap
 *
 * Es el inverso de pack:jar. Los slugs son los del pack publicado; los lista
 * pack:check.
 *
 * Van todos juntos a propósito: cada publicación le saca del juego a quien esté
 * jugando (UpdateWatcher lo cierra y lo vuelve a abrir), así que sacar dos mods
 * en dos corridas es sacar a la gente dos veces.
 */
import { eq } from 'drizzle-orm';
import { createSession } from '../lib/auth';
import { db } from '../lib/db';
import { users } from '../lib/db/schema';
import { latestRelease } from '../lib/pack';

const slugs = process.argv.slice(2).filter((a) => !a.startsWith('-'));
if (slugs.length === 0) {
  console.log('Faltan los slugs. Ejemplo: npm run pack:quitar -- xaerominimap xaeroworldmap');
  process.exit(1);
}

const release = await latestRelease();
const mods = [];
for (const slug of slugs) {
  const mod = release?.content.mods.find((m) => m.slug === slug);
  if (!mod) {
    console.log(`No hay ningún "${slug}" en el pack publicado.`);
    process.exit(1);
  }
  mods.push(mod);
}

const admins = await db.select().from(users).where(eq(users.role, 'admin')).limit(1);
if (admins.length === 0) {
  console.log('No hay ninguna cuenta de administrador.');
  process.exit(1);
}
const headers = { Authorization: `Bearer ${await createSession(admins[0].id)}` };

const quitar = (await import('../app/api/admin/mods/route')).DELETE;
for (const mod of mods) {
  console.log(`sacando ${mod.slug} ${mod.versionNumber} (${mod.filename})`);
  const borrado = await (
    await quitar(
      new Request(`http://local/api/admin/mods?projectId=${encodeURIComponent(mod.projectId)}`, {
        method: 'DELETE',
        headers,
      }),
    )
  ).json();
  if (!borrado.removed) {
    console.log('No se pudo quitar:', JSON.stringify(borrado));
    process.exit(1);
  }
}

const publicar = (await import('../app/api/admin/pack/publish/route')).POST;
const nuevo = (await (
  await publicar(new Request('http://local/api/admin/pack/publish', { method: 'POST', headers }))
).json()) as { version?: string; mods?: number; error?: string; problems?: string[] };

if (!nuevo.version) {
  console.log(`No se publicó: ${nuevo.error}`);
  for (const problema of nuevo.problems ?? []) console.log(`  - ${problema}`);
  process.exit(1);
}
console.log(`Pack ${nuevo.version} publicado con ${nuevo.mods} mods.`);
process.exit(0);
