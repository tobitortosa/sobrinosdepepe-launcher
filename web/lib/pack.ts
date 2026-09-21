import { desc, eq, inArray } from 'drizzle-orm';
import { db } from './db';
import { modFiles, packMods, packReleases, type PackMod } from './db/schema';
import { env } from './env';

/**
 * El pack tal como lo lee el launcher. La forma es compatible con el índice de un
 * .mrpack de Modrinth (ruta, hashes sha1 y sha512, tamaño y lado de cada archivo),
 * así que si algún día hace falta empaquetarlo como .mrpack no hay que cambiar nada.
 */
export type PackPayload = {
  packVersion: string;
  minecraft: string;
  fabricLoader: string;
  server: { name: string; address: string };
  mods: {
    projectId: string;
    slug: string;
    title: string;
    versionId: string;
    versionNumber: string;
    filename: string;
    url: string;
    sha1: string;
    sha512: string;
    size: number;
    side: string;
    kind: string;
    folder: string;
    license: string;
    pageUrl: string;
    /**
     * Los ids de Modrinth de los mods que este necesita, y nada más. El launcher no
     * resuelve dependencias: eso pasa acá, en validate(), antes de publicar, y para
     * eso están las filas de la base, que sí guardan la versión pedida.
     *
     * Va como lista de textos porque así lo lee el launcher (`List<string>`), y un
     * launcher ya instalado no se puede cambiar: mandarle objetos rompe la pantalla
     * de JUGAR con un error de JSON y deja a todos afuera. Pasó con el pack 1.0.21.
     */
    requires: string[];
  }[];
};

export function toPayload(mods: PackMod[], version: string): PackPayload {
  return {
    packVersion: version,
    minecraft: env.minecraftVersion,
    fabricLoader: env.fabricLoader,
    server: { name: env.serverName, address: env.serverAddress },
    mods: mods
      .slice()
      .sort((a, b) => a.slug.localeCompare(b.slug))
      .map((m) => ({
        projectId: m.projectId,
        slug: m.slug,
        title: m.title,
        versionId: m.versionId,
        versionNumber: m.versionNumber,
        filename: m.filename,
        url: m.url,
        sha1: m.sha1,
        sha512: m.sha512,
        size: m.size,
        side: m.side,
        kind: m.kind,
        folder: m.kind === 'shader' ? 'shaderpacks' : m.kind === 'resourcepack' ? 'resourcepacks' : 'mods',
        license: m.license,
        pageUrl: m.pageUrl,
        requires: m.requires.map((dependencia) => dependencia.projectId),
      })),
  };
}

export async function draftMods(): Promise<PackMod[]> {
  return db.select().from(packMods).orderBy(packMods.slug);
}

export async function latestRelease(): Promise<{ version: string; content: PackPayload } | null> {
  const rows = await db
    .select()
    .from(packReleases)
    .where(eq(packReleases.published, true))
    .orderBy(desc(packReleases.createdAt))
    .limit(1);
  const row = rows[0];
  return row ? { version: row.version, content: row.content as PackPayload } : null;
}

export function nextVersion(current: string | null): string {
  if (!current) return '1.0.0';
  const parts = current.split('.').map((n) => Number.parseInt(n, 10) || 0);
  while (parts.length < 3) parts.push(0);
  parts[2] += 1;
  return parts.join('.');
}

export type PackProblem = { kind: 'missing-dependency' | 'wrong-version' | 'bad-url'; detail: string };

/**
 * Chequeos antes de publicar. El importante es el de dependencias: agregar Iris sin
 * Sodium hace que el juego no arranque, y el jugador solo ve un crash.
 */
export async function validate(mods: PackMod[]): Promise<PackProblem[]> {
  const problems: PackProblem[] = [];
  const byProject = new Map(mods.map((m) => [m.projectId, m]));

  for (const mod of mods) {
    for (const dependency of mod.requires) {
      const installed = byProject.get(dependency.projectId);
      if (!installed) {
        problems.push({
          kind: 'missing-dependency',
          detail: `${mod.title} necesita otro mod que no está en el pack (proyecto ${dependency.projectId}).`,
        });
        continue;
      }
      if (dependency.versionId && installed.versionId !== dependency.versionId) {
        problems.push({
          kind: 'wrong-version',
          detail: `${mod.title} ${mod.versionNumber} necesita una versión distinta de ${installed.title}: el pack tiene ${installed.versionNumber}.`,
        });
      }
    }
  }

  // Los archivos propios se comprueban en la base; los de Modrinth, con un pedido HEAD.
  const own = mods.filter((m) => m.url.startsWith('/'));
  const stored = own.length
    ? new Set(
        (
          await db
            .select({ sha1: modFiles.sha1 })
            .from(modFiles)
            .where(inArray(modFiles.sha1, own.map((m) => m.sha1)))
        ).map((r) => r.sha1),
      )
    : new Set<string>();

  const checks = await Promise.all(
    mods.map(async (mod) => {
      if (mod.url.startsWith('/')) {
        return stored.has(mod.sha1) ? null : `Falta el archivo de ${mod.filename}: subilo de nuevo.`;
      }
      try {
        const response = await fetch(mod.url, { method: 'HEAD', cache: 'no-store' });
        return response.ok ? null : `${mod.filename} ya no está disponible (HTTP ${response.status}).`;
      } catch {
        return `No se pudo verificar la descarga de ${mod.filename}.`;
      }
    }),
  );

  for (const problem of checks) {
    if (problem) problems.push({ kind: 'bad-url', detail: problem });
  }

  return problems;
}

/** Los jars que además van en el servidor. Un shaderpack nunca va al servidor. */
export function serverSideMods(mods: PackMod[]): PackMod[] {
  return mods.filter((m) => m.kind === 'mod' && (m.side === 'server' || m.side === 'both'));
}
