import { env, MODRINTH_USER_AGENT } from './env';

/**
 * Modrinth es de donde salen los mods. El launcher los descarga directo del CDN de
 * Modrinth: no rehosteamos nada, porque varios mods del pack no lo permiten
 * (Simple Voice Chat es de derechos reservados y el shader Complementary lo
 * prohíbe explícitamente).
 *
 * Los términos de uso piden un User-Agent que identifique la aplicación.
 * El límite es de 300 pedidos por minuto.
 */
const API = 'https://api.modrinth.com/v2';

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    headers: { 'User-Agent': MODRINTH_USER_AGENT },
    cache: 'no-store',
  });
  if (!response.ok) throw new Error(`Modrinth respondió ${response.status} en ${path}`);
  return (await response.json()) as T;
}

export type ModrinthProject = {
  id: string;
  slug: string;
  title: string;
  description: string;
  icon_url: string | null;
  client_side: 'required' | 'optional' | 'unsupported';
  server_side: 'required' | 'optional' | 'unsupported';
  license: { id: string };
  project_type: string;
};

export type ModrinthVersion = {
  id: string;
  project_id: string;
  version_number: string;
  version_type: string;
  game_versions: string[];
  loaders: string[];
  date_published: string;
  dependencies: { project_id: string | null; version_id: string | null; dependency_type: string }[];
  files: {
    filename: string;
    url: string;
    size: number;
    primary: boolean;
    hashes: { sha1: string; sha512: string };
  }[];
};

export type SearchHit = {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  icon_url: string | null;
  downloads: number;
  license: string;
  client_side: string;
  server_side: string;
};

/** Busca mods compatibles con la versión y el loader del pack. */
export async function search(query: string, limit = 20): Promise<SearchHit[]> {
  const facets = JSON.stringify([
    ['loaders:fabric'],
    [`versions:${env.minecraftVersion}`],
    ['project_type:mod'],
  ]);
  const url = `/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(facets)}&limit=${limit}`;
  const body = await get<{ hits: SearchHit[] }>(url);
  return body.hits;
}

/**
 * Busca un archivo por su hash. Sirve para reconocer un .jar que el admin sube:
 * si Modrinth lo conoce, sale gratis el nombre, la versión, la licencia y el lado.
 */
export async function byHash(sha1: string): Promise<ModrinthVersion | null> {
  const response = await fetch(`${API}/version_file/${sha1}?algorithm=sha1`, {
    headers: { 'User-Agent': MODRINTH_USER_AGENT },
    cache: 'no-store',
  });
  if (response.status === 404) return null;
  if (!response.ok) return null;
  return (await response.json()) as ModrinthVersion;
}

export function project(idOrSlug: string): Promise<ModrinthProject> {
  return get<ModrinthProject>(`/project/${encodeURIComponent(idOrSlug)}`);
}

/**
 * Versiones del proyecto que sirven para nuestra versión de Minecraft.
 * Los mods se publican para "fabric" y los shaderpacks para "iris", así que el
 * loader depende de qué se esté agregando.
 */
export function versions(idOrSlug: string, loader = 'fabric'): Promise<ModrinthVersion[]> {
  const games = encodeURIComponent(JSON.stringify([env.minecraftVersion]));
  const loaders = encodeURIComponent(JSON.stringify([loader]));
  return get<ModrinthVersion[]>(
    `/project/${encodeURIComponent(idOrSlug)}/version?game_versions=${games}&loaders=${loaders}`,
  );
}

/** Un shaderpack se publica como project_type "shader". */
export function isShader(project: ModrinthProject): boolean {
  return project.project_type === 'shader';
}

export function version(versionId: string): Promise<ModrinthVersion> {
  return get<ModrinthVersion>(`/version/${encodeURIComponent(versionId)}`);
}

/** Saca el slug de un link pegado, o devuelve el texto tal cual si ya es un slug o id. */
export function parseReference(input: string): string {
  const match = input.match(/modrinth\.com\/(?:mod|shader|datapack|resourcepack|plugin)\/([^/?#]+)/i);
  return (match?.[1] ?? input).trim();
}

export function sideOf(project: ModrinthProject): 'client' | 'server' | 'both' {
  if (project.server_side === 'unsupported') return 'client';
  if (project.client_side === 'unsupported') return 'server';
  return 'both';
}

export function primaryFile(version: ModrinthVersion) {
  const file = version.files.find((f) => f.primary) ?? version.files[0];
  if (!file) throw new Error(`La versión ${version.id} no tiene archivos.`);
  return file;
}

export type Requirement = { projectId: string; versionId: string | null };

/**
 * Las dependencias obligatorias. Cuando Modrinth indica una versión concreta hay que
 * respetarla: Iris 1.11.3 pide Sodium 0.9.1, que no existe para Minecraft 26.1, así que
 * "la versión más nueva" de un mod puede romper el pack aunque la dependencia esté.
 */
export function requiredDependencies(version: ModrinthVersion): Requirement[] {
  return version.dependencies
    .filter((d) => d.dependency_type === 'required' && d.project_id)
    .map((d) => ({ projectId: d.project_id as string, versionId: d.version_id ?? null }));
}
