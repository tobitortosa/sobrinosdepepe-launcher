import { createHash } from 'node:crypto';
import { and, eq } from 'drizzle-orm';
import { fail, ok, requireActiveUser } from '@/lib/api';
import { db } from '@/lib/db';
import { modFiles, userMods } from '@/lib/db/schema';
import { looksLikeJar, readJar } from '@/lib/jar';

/**
 * Los mods que una cuenta tiene además del pack.
 *
 * **Subir y borrar es solo para las cuentas admin.** No es una comodidad: es lo
 * único que separa esto de la vieja `mods-propios.txt`, que era un archivo de
 * texto en la PC del jugador donde se podía nombrar cualquier jar para que la
 * sincronización no lo borrara —un xray incluido—. Ahora la lista de lo que
 * sobrevive al borrado la decide el backend mirando la cuenta, así que un jugador
 * no puede agregarse nada ni tocando el launcher.
 *
 * Leer la lista sí puede cualquiera, y tiene que poder: el launcher la pide en
 * cada JUGAR para saber qué bajar, y a una cuenta que no es admin le llega vacía.
 *
 * Los bytes van a `mod_files`, que es de donde ya se sirven los .jar del pack, así
 * que se bajan por `/api/files/<sha1>` con el hash verificado como todo lo demás.
 */

const MAX_BYTES = 64 * 1024 * 1024;

export async function GET(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;

  const filas = await db
    .select()
    .from(userMods)
    .where(eq(userMods.userId, guard.user.id))
    .orderBy(userMods.filename);

  // La dirección va completa, como en /api/pack: el launcher descarga por URL y no
  // sabe pegarle un origen adelante.
  const origin = new URL(request.url).origin;

  return ok({
    mods: filas.map((fila) => ({
      filename: fila.filename,
      sha1: fila.sha1,
      size: fila.size,
      title: fila.title,
      versionNumber: fila.versionNumber,
      url: `${origin}/api/files/${fila.sha1}`,
    })),
  });
}

export async function POST(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;
  if (guard.user.role !== 'admin') {
    return fail('Los mods propios son solo para la cuenta de admin.', 403);
  }

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return fail('No llegaron archivos.');
  }

  const subidos = form.getAll('files').filter((entry): entry is File => entry instanceof File);
  if (subidos.length === 0) return fail('No llegaron archivos.');

  const agregados: unknown[] = [];
  const rechazados: { filename: string; reason: string }[] = [];

  for (const subido of subidos) {
    if (!looksLikeJar(subido.name)) {
      rechazados.push({ filename: subido.name, reason: 'No es un mod (.jar).' });
      continue;
    }
    if (subido.size > MAX_BYTES) {
      rechazados.push({ filename: subido.name, reason: 'El archivo es demasiado grande.' });
      continue;
    }

    const bytes = new Uint8Array(await subido.arrayBuffer());
    const adentro = readJar(bytes);
    if (!adentro) {
      rechazados.push({
        filename: subido.name,
        reason: 'No parece un mod de Fabric: no tiene fabric.mod.json adentro.',
      });
      continue;
    }

    // Un mod de servidor no tiene nada que hacer en la carpeta de un jugador, y
    // ponerlo ahí no lo instala en el servidor: solo rompe el juego al arrancar.
    if (adentro.side === 'server') {
      rechazados.push({
        filename: subido.name,
        reason: 'Es un mod de servidor. Este es para tu juego, no para el servidor.',
      });
      continue;
    }

    const buffer = Buffer.from(bytes);
    const sha1 = createHash('sha1').update(buffer).digest('hex');

    await db
      .insert(modFiles)
      .values({ sha1, filename: subido.name, size: buffer.byteLength, data: buffer })
      .onConflictDoNothing();

    const fila = {
      userId: guard.user.id,
      filename: subido.name,
      sha1,
      size: buffer.byteLength,
      title: adentro.name || subido.name,
      versionNumber: adentro.version,
    };

    await db
      .insert(userMods)
      .values(fila)
      .onConflictDoUpdate({ target: [userMods.userId, userMods.sha1], set: fila });

    agregados.push({
      filename: fila.filename,
      title: fila.title,
      versionNumber: fila.versionNumber,
      sizeKb: Math.round(fila.size / 1024),
    });
  }

  return ok({ added: agregados, rejected: rechazados });
}

export async function DELETE(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;
  if (guard.user.role !== 'admin') {
    return fail('Los mods propios son solo para la cuenta de admin.', 403);
  }

  const sha1 = new URL(request.url).searchParams.get('sha1') ?? '';
  if (!/^[0-9a-f]{40}$/.test(sha1)) return fail('Falta el archivo que hay que sacar.');

  // Se borra la fila, no el archivo: los bytes viven en mod_files, que es de donde
  // también se sirve el pack, y ahí puede estar el mismo jar por otro motivo.
  const borradas = await db
    .delete(userMods)
    .where(and(eq(userMods.userId, guard.user.id), eq(userMods.sha1, sha1)))
    .returning({ filename: userMods.filename });

  if (borradas.length === 0) return fail('Ese mod no está en tu lista.', 404);
  return ok({ removed: borradas[0].filename });
}
