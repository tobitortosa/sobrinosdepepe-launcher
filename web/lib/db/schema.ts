import {
  bigint,
  customType,
  boolean,
  index,
  integer,
  jsonb,
  pgTable,
  serial,
  text,
  timestamp,
  uniqueIndex,
} from 'drizzle-orm/pg-core';

/** Postgres no tiene un tipo de bytes en drizzle-orm: se declara a mano. */
const bytea = customType<{ data: Buffer; default: false }>({
  dataType: () => 'bytea',
});

/**
 * Los .jar que sube el admin. Se guardan acá para que el launcher los pueda bajar.
 * El pack completo pesa unos 20 MB, así que entra de sobra en la base.
 */
export const modFiles = pgTable('mod_files', {
  sha1: text('sha1').primaryKey(),
  filename: text('filename').notNull(),
  size: bigint('size', { mode: 'number' }).notNull(),
  data: bytea('data').notNull(),
  uploadedAt: timestamp('uploaded_at', { withTimezone: true }).notNull().defaultNow(),
});

export const users = pgTable(
  'users',
  {
    id: serial('id').primaryKey(),
    /** El nombre con sus mayúsculas exactas: de acá sale el UUID del jugador en el server. */
    username: text('username').notNull(),
    /** En minúsculas, para que no existan a la vez PEPE y pepe. */
    usernameLower: text('username_lower').notNull(),
    passwordHash: text('password_hash').notNull(),
    /** Cuando es verdadero, la persona entra pero lo primero que hace es elegir su contraseña. */
    mustChangePassword: boolean('must_change_password').notNull().default(false),
    role: text('role').notNull().default('player'),
    status: text('status').notNull().default('pending'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    approvedAt: timestamp('approved_at', { withTimezone: true }),
    bannedAt: timestamp('banned_at', { withTimezone: true }),
  },
  (t) => [
    uniqueIndex('users_username_lower_idx').on(t.usernameLower),
    index('users_status_created_idx').on(t.status, t.createdAt),
  ],
);

export const sessions = pgTable(
  'sessions',
  {
    id: text('id').primaryKey(),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    /** Solo el SHA-256 del secreto; el token completo lo tiene el launcher. */
    secretHash: text('secret_hash').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  },
  (t) => [index('sessions_user_idx').on(t.userId)],
);

/** La lista de mods que el admin edita. Publicar toma una foto de esto. */
export const packMods = pgTable(
  'pack_mods',
  {
    id: serial('id').primaryKey(),
    projectId: text('project_id').notNull(),
    slug: text('slug').notNull(),
    title: text('title').notNull(),
    versionId: text('version_id').notNull(),
    versionNumber: text('version_number').notNull(),
    filename: text('filename').notNull(),
    url: text('url').notNull(),
    sha1: text('sha1').notNull(),
    sha512: text('sha512').notNull(),
    size: bigint('size', { mode: 'number' }).notNull(),
    /** "client", "server" o "both". */
    side: text('side').notNull(),
    license: text('license').notNull().default(''),
    pageUrl: text('page_url').notNull().default(''),
    /** "upload" si el archivo lo subió el admin, "modrinth" si se baja de su CDN. */
    source: text('source').notNull().default('upload'),
    /** "mod" va a la carpeta mods; "shader" va a shaderpacks. */
    kind: text('kind').notNull().default('mod'),
    requires: jsonb('requires')
      .$type<{ projectId: string; versionId: string | null }[]>()
      .notNull()
      .default([]),
    addedAt: timestamp('added_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('pack_mods_project_idx').on(t.projectId)],
);

/** Cada publicación del pack, con su contenido congelado. Es lo que sirve /api/pack. */
export const packReleases = pgTable('pack_releases', {
  id: serial('id').primaryKey(),
  version: text('version').notNull(),
  /** El pack completo tal como lo lee el launcher. */
  content: jsonb('content').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  published: boolean('published').notNull().default(true),
});

/**
 * La configuración del juego de cada cuenta: `options.txt` (las teclas, la
 * sensibilidad del mouse, el FOV, el volumen) y la carpeta `config/` entera (los
 * ajustes de Sodium, los shaders de Iris, el sonido).
 *
 * Va como un zip y no como cien filas porque no lo leemos nunca: el launcher lo
 * sube cuando se cierra el juego y lo baja cuando alguien entra en otra máquina.
 * Son unos 200 KB por cuenta.
 *
 * Es por cuenta y no por máquina, que es todo el punto: si la novia de Tobías
 * entra con su cuenta en la computadora de él, tiene sus propias teclas, y las de
 * él la esperan enteras cuando vuelve a entrar.
 */
export const userSettings = pgTable('user_settings', {
  userId: integer('user_id')
    .primaryKey()
    .references(() => users.id, { onDelete: 'cascade' }),
  data: bytea('data').notNull(),
  size: bigint('size', { mode: 'number' }).notNull(),
  /** El sha1 del zip, para no volver a subir lo mismo cuando nada cambió. */
  sha1: text('sha1').notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

/**
 * Los mods que una cuenta tiene además del pack. Los bytes viven en `mod_files`,
 * que es de donde ya se sirven los .jar que sube el admin.
 *
 * **Solo las cuentas admin pueden tener.** No es una comodidad: es lo único que
 * evita que esto sea la vieja `mods-propios.txt` con otro nombre. La lista de los
 * jars que sobreviven al borrado ya no la escribe el jugador en su PC —la decide
 * el backend mirando la cuenta—, así que un jugador no puede agregarse un xray ni
 * tocando el launcher.
 */
export const userMods = pgTable(
  'user_mods',
  {
    id: serial('id').primaryKey(),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    filename: text('filename').notNull(),
    sha1: text('sha1').notNull(),
    size: bigint('size', { mode: 'number' }).notNull(),
    /** Lo que dice el .jar de sí mismo, para mostrarlo en el panel. */
    title: text('title').notNull().default(''),
    versionNumber: text('version_number').notNull().default(''),
    addedAt: timestamp('added_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [uniqueIndex('user_mods_user_sha1_idx').on(t.userId, t.sha1)],
);

export type User = typeof users.$inferSelect;
export type PackMod = typeof packMods.$inferSelect;
export type UserMod = typeof userMods.$inferSelect;
