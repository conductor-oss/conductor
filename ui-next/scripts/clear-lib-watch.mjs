/**
 * Removes a stale `.lib-watch` marker left by build:lib:watch.
 * Run at the start of one-shot `build:lib` so enterprise doesn't think watch is active.
 */
import { existsSync, unlinkSync } from "fs";
import { dirname, resolve } from "path";
import { fileURLToPath } from "url";

const markerPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../.lib-watch",
);

if (existsSync(markerPath)) {
  unlinkSync(markerPath);
}
