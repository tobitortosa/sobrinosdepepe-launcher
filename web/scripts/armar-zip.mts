/**
 * Arma el .zip de mods sueltos que se baja de la página, y lo deja en public/.
 *
 *   npm run mods:zip
 *
 * Sale del pack publicado y no de una carpeta de una PC, que es lo que lo mantiene
 * honesto: adentro va exactamente lo mismo que instala el launcher, sin los mods
 * que el admin tenga puestos de más.
 *
 * Lo único que se saca es el mod de acceso: es el que pide el permiso del launcher
 * y afuera de él no tiene nada que hacer.
 *
 * Cada archivo va a la carpeta que dice el pack (`mods` o `shaderpacks`), así el
 * jugador descomprime adentro de .minecraft y ya está.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { eq } from 'drizzle-orm';
import { zipSync } from 'fflate';
import { db } from '../lib/db';
import { modFiles } from '../lib/db/schema';
import { MODRINTH_USER_AGENT } from '../lib/env';
import { latestRelease } from '../lib/pack';

const FUERA = ['acceso-sobrinosdepepe'];
const DESTINO = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'SobrinosDePepe-mods.zip');

const release = await latestRelease();
if (!release) {
  console.log('No hay ninguna versión del pack publicada.');
  process.exit(1);
}

const entradas: Record<string, Uint8Array> = {};
let bytes = 0;

for (const mod of release.content.mods) {
  if (FUERA.some((p) => mod.filename.startsWith(p))) {
    console.log(`  (fuera) ${mod.filename}`);
    continue;
  }

  let datos: Uint8Array;
  if (mod.url.startsWith('/api/files/')) {
    // Lo subió el admin: vive en la base, no hace falta pasar por HTTP.
    const filas = await db.select().from(modFiles).where(eq(modFiles.sha1, mod.sha1)).limit(1);
    if (!filas[0]) {
      console.log(`Falta el archivo de ${mod.filename} (sha1 ${mod.sha1}).`);
      process.exit(1);
    }
    datos = new Uint8Array(filas[0].data);
  } else {
    const r = await fetch(mod.url, { headers: { 'User-Agent': MODRINTH_USER_AGENT } });
    if (!r.ok) {
      console.log(`No se pudo bajar ${mod.filename}: ${r.status}`);
      process.exit(1);
    }
    datos = new Uint8Array(await r.arrayBuffer());
  }

  entradas[`${mod.folder}/${mod.filename}`] = datos;
  bytes += datos.length;
  console.log(`  ${mod.folder}/${mod.filename}`);
}

// Sin comprimir: los .jar y los shaders ya son zips adentro, así que comprimirlos
// otra vez tarda y no achica nada.
const zip = zipSync(entradas, { level: 0 });
mkdirSync(dirname(DESTINO), { recursive: true });
writeFileSync(DESTINO, zip);

console.log(
  `\n${Object.keys(entradas).length} archivos · ${(bytes / 1024 / 1024).toFixed(1)} MB` +
    ` · zip de ${(zip.length / 1024 / 1024).toFixed(1)} MB`,
);
console.log(`pack ${release.version} → public/SobrinosDePepe-mods.zip`);
process.exit(0);
