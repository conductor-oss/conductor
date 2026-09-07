/**
 * Load ui-next env files into process.env for Playwright integration runs.
 *
 * Prefer `.env.local` (gitignored) for secrets like OPENAI_API_KEY. Existing
 * process.env values win so CI / shell exports still override.
 */

import { existsSync, readFileSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const UI_NEXT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

function applyEnvFile(filePath: string): void {
  if (!existsSync(filePath)) return;
  for (const raw of readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

/** Load `.env` then `.env.local` (local wins for keys not already set). */
export function loadIntegrationEnv(): void {
  applyEnvFile(resolve(UI_NEXT_ROOT, ".env"));
  applyEnvFile(resolve(UI_NEXT_ROOT, ".env.local"));
}
