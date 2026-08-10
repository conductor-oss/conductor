/**
 * Playwright global teardown for integration tests.
 *
 * Stops the Docker Compose stack only if global-setup.ts started it
 * (indicated by the presence of the sentinel file).  If a developer had a
 * backend already running before the tests, this does nothing so they don't
 * lose their running server.
 */

import { execSync } from "child_process";
import { existsSync, unlinkSync } from "fs";
import { resolve, dirname } from "path";
import { tmpdir } from "os";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const COMPOSE_FILE = resolve(
  __dirname,
  "../../../docker/docker-compose-ui-e2e.yaml",
);

const COMPOSE_PROJECT = "conductor-ui-e2e";
const SENTINEL = resolve(tmpdir(), "conductor-ui-e2e-docker-started");

const compose = (args: string) =>
  `docker compose -p ${COMPOSE_PROJECT} -f "${COMPOSE_FILE}" ${args}`;

export default async function globalTeardown(): Promise<void> {
  if (!existsSync(SENTINEL)) {
    // We did not start Docker — leave it alone.
    return;
  }

  try {
    unlinkSync(SENTINEL);
  } catch {
    // Ignore — sentinel cleanup is best-effort.
  }

  if (process.env.SKIP_DOCKER_TEARDOWN === "true") {
    console.log("SKIP_DOCKER_TEARDOWN=true — leaving backend running");
    return;
  }

  console.log("Stopping Conductor backend ...");
  try {
    execSync(compose("down"), { stdio: "inherit" });
    console.log("Conductor backend stopped");
  } catch (err) {
    console.warn("Failed to stop Conductor backend:", err);
  }
}
