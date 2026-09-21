/**
 * Agrega uno o varios mods del catálogo de Modrinth y publica UNA versión nueva.
 *   npm run pack:mod -- custom-paintings-mod
 *   npm run pack:mod -- custom-paintings-mod --sin-publicar
 *
 * Es el inverso de pack:quitar. Va por Modrinth y no por archivo: así el launcher
 * descarga de su CDN y no rehosteamos nada. A diferencia de pack:publish, no toca
 * los demás mods; a diferencia de pack:shader, publica.
 *
 * Van todos juntos a propósito: cada publicación le saca del juego a quien esté
 * jugando (el launcher cierra el juego y lo vuelve a abrir), así que agregar dos
 * mods en dos corridas es sacar a la gente dos veces. Por lo mismo está
 * --sin-publicar: cambiar un mod por otro es agregar sin publicar y después
 * quitar el viejo con pack:quitar, que publica los dos cambios juntos.
 */
import { eq } from 'drizzle-orm';
import { createSession } from '../lib/auth';
import { db } from '../lib/db';
import { users } from '../lib/db/schema';

const referencias = process.argv.slice(2).filter((a) => !a.startsWith('-'));
const sinPublicar = process.argv.includes('--sin-publicar');
if (referencias.length === 0) {
  console.log('Faltan los mods. Ejemplo: npm run pack:mod -- signed-paintings');
  process.exit(1);
}

const admins = await db.select().from(users).where(eq(users.role, 'admin')).limit(1);
if (admins.length === 0) {
  console.log('No hay ninguna cuenta de administrador.');
  process.exit(1);
}
const headers = {
  Authorization: `Bearer ${await createSession(admins[0].id)}`,
  'Content-Type': 'application/json',
};

const agregar = (await import('../app/api/admin/mods/route')).POST;
for (const reference of referencias) {
  const cuerpo = (await (
    await agregar(
      new Request('http://local/api/admin/mods', {
        method: 'POST',
        headers,
        body: JSON.stringify({ reference }),
      }),
    )
  ).json()) as {
    mod?: { title: string; versionNumber: string; filename: string; side: string; kind: string; license: string };
    warnings?: string[];
    note?: string;
    error?: string;
  };

  if (!cuerpo.mod) {
    console.log(`No se pudo agregar ${reference}: ${cuerpo.error}`);
    process.exit(1);
  }

  const m = cuerpo.mod;
  console.log(`agregado ${m.title} ${m.versionNumber} (${m.filename})`);
  console.log(`  ${m.kind} · lado ${m.side} · licencia ${m.license}`);
  if (cuerpo.note) console.log(`  ${cuerpo.note}`);
  for (const aviso of cuerpo.warnings ?? []) console.log(`  OJO: ${aviso}`);
}

if (sinPublicar) {
  console.log('Queda en el borrador, sin publicar (--sin-publicar).');
  process.exit(0);
}

const publicar = (await import('../app/api/admin/pack/publish/route')).POST;
const nuevo = (await (
  await publicar(new Request('http://local/api/admin/pack/publish', { method: 'POST', headers }))
).json()) as { version?: string; mods?: number; serverSide?: string[]; error?: string; problems?: string[] };

if (!nuevo.version) {
  console.log(`No se publicó: ${nuevo.error}`);
  for (const problema of nuevo.problems ?? []) console.log(`  - ${problema}`);
  process.exit(1);
}
console.log(`Pack ${nuevo.version} publicado con ${nuevo.mods} mods.`);
if (nuevo.serverSide?.length) {
  console.log('\nEstos también van en el servidor:');
  for (const archivo of nuevo.serverSide) console.log(`  ${archivo}`);
}
process.exit(0);
