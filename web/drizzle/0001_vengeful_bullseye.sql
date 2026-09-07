ALTER TABLE "pack_mods" ADD COLUMN "kind" text DEFAULT 'mod' NOT NULL;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "must_change_password" boolean DEFAULT false NOT NULL;