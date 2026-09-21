/**
 * Agrega un solo .jar al pack y publica la versión nueva.
 *   npm run pack:jar -- ../mod-precios/build/libs/precios-sobrinosdepepe-1.0.0.jar
 *   npm run pack:jar -- <ruta> --sin-publicar
 *
 * A diferencia de pack:publish, no toca los demás mods: ese script usa una
 * carpeta como fuente de verdad y quita del pack todo lo que no esté ahí, así
 * que para sumar uno solo hay que ir por acá.
 */
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { eq } from 'drizzle-orm';
import { createSession } from '../lib/auth';
import { db } from '../lib/db';
import { users } from '../lib/db/schema';

const ruta = process.argv[2];
// Cambiar un mod por otro es subir sin publicar y despues quitar el viejo con
// pack:quitar, que publica los dos cambios de una: cada publicacion saca del
// juego a quien este jugando.
const sinPublicar = process.argv.includes('--sin-publicar');
if (!ruta) {
  console.log('Falta el .jar. Ejemplo: npm run pack:jar -- ../mod-precios/build/libs/mod.jar');
  process.exit(1);
}

const admins = await db.select().from(users).where(eq(users.role, 'admin')).limit(1);
if (admins.length === 0) {
  console.log('No hay ninguna cuenta de administrador.');
  process.exit(1);
}
const token = await createSession(admins[0].id);

const form = new FormData();
form.append(
  'files',
  new File([new Uint8Array(readFileSync(ruta))], basename(ruta), { type: 'application/java-archive' }),
);

const upload = (await import('../app/api/admin/mods/upload/route')).POST;
const uploaded = (await (
  await upload(
    new Request('http://local/api/admin/mods/upload', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    }),
  )
).json()) as {
  added: { title: string; version: string; side: string; sizeKb: number; license: string }[];
  rejected: { filename: string; reason: string }[];
  error?: string;
};

if (uploaded.error) {
  console.log('Error:', uploaded.error);
  process.exit(1);
}
for (const bad of uploaded.rejected) {
  console.log(`RECHAZADO ${bad.filename}: ${bad.reason}`);
  process.exit(1);
}
for (const mod of uploaded.added) {
  console.log(`subido: ${mod.title} ${mod.version} (${mod.side}, ${mod.sizeKb} KB, ${mod.license})`);
}

if (sinPublicar) {
  console.log('Queda en el borrador, sin publicar (--sin-publicar).');
  process.exit(0);
}

const publish = (await import('../app/api/admin/pack/publish/route')).POST;
const release = (await (
  await publish(
    new Request('http://local/api/admin/pack/publish', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    }),
  )
).json()) as { version?: string; mods?: number; error?: string; problems?: string[] };

if (!release.version) {
  console.log(`No se publicó: ${release.error}`);
  for (const problem of release.problems ?? []) console.log(`  - ${problem}`);
  process.exit(1);
}

console.log(`Pack ${release.version} publicado con ${release.mods} mods.`);
process.exit(0);
