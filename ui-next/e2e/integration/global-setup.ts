/**
 * Playwright global setup for integration tests.
 *
 * Responsibilities:
 *  1. Check whether the Conductor backend is already running.
 *  2. If not, start it via docker-compose-ui-e2e.yaml (requires the
 *     conductor:server image to be built locally first).
 *  3. Wait until the /health endpoint returns 200.
 *  4. Write a sentinel file so global-teardown knows whether to stop Docker.
 */

import { execSync } from "child_process";
import { existsSync, writeFileSync, mkdirSync } from "fs";
import { resolve, dirname } from "path";
import { tmpdir } from "os";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const BACKEND_URL = process.env.CONDUCTOR_SERVER_URL ?? "http://localhost:8000";
const HEALTH_URL = `${BACKEND_URL}/health`;
const SKIP_DOCKER = process.env.SKIP_DOCKER === "true";

// Path to the Docker Compose file (relative to repo root, which is two levels
// above ui-next/).
const COMPOSE_FILE = resolve(
  __dirname,
  "../../../docker/docker-compose-ui-e2e.yaml",
);

// Explicit project name so this stack is fully isolated from any other
// docker-compose stacks in the docker/ directory (which share the same
// default project name and would otherwise collide on port 8000).
const COMPOSE_PROJECT = "conductor-ui-e2e";

// Sentinel file written by setup and read by teardown.
const SENTINEL = resolve(tmpdir(), "conductor-ui-e2e-docker-started");

const POLL_MS = 3_000;
// 8 minutes: first-run cold JVM start + Liquibase migrations can be slow.
const TIMEOUT_MS = 8 * 60 * 1_000;

async function isHealthy(): Promise<boolean> {
  try {
    const res = await fetch(HEALTH_URL, { signal: AbortSignal.timeout(3_000) });
    return res.ok;
  } catch {
    return false;
  }
}

async function waitForBackend(): Promise<void> {
  const deadline = Date.now() + TIMEOUT_MS;
  process.stdout.write("Waiting for Conductor backend");
  while (Date.now() < deadline) {
    if (await isHealthy()) {
      process.stdout.write(" ready\n");
      return;
    }
    process.stdout.write(".");
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
  process.stdout.write(" timed out\n");
  throw new Error(
    `Conductor backend did not become healthy within ${TIMEOUT_MS / 1000}s.\n` +
      `Check docker logs: ${compose("logs conductor-server")}`,
  );
}

function dockerImageExists(): boolean {
  try {
    execSync("docker image inspect conductor:server", { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

const compose = (args: string) =>
  `docker compose -p ${COMPOSE_PROJECT} -f "${COMPOSE_FILE}" ${args}`;

/** Build the conductor:server image via docker compose.
 *  Docker's layer cache makes this fast (~30s) after the first run. */
function buildDockerImage(): void {
  console.log(
    "Building conductor:server image — first run takes ~5–10 min, " +
      "subsequent runs use Docker layer cache and finish in ~30s ...",
  );
  execSync(compose("build conductor-server"), { stdio: "inherit" });
}

export default async function globalSetup(): Promise<void> {
  if (SKIP_DOCKER) {
    console.log("SKIP_DOCKER=true — assuming backend is already running");
    if (!(await isHealthy())) {
      throw new Error(
        `SKIP_DOCKER=true but backend is not healthy at ${HEALTH_URL}`,
      );
    }
    console.log(`Backend healthy at ${BACKEND_URL}`);
    return;
  }

  // If someone already has a backend running locally, reuse it.
  if (await isHealthy()) {
    console.log(`Conductor backend already running at ${BACKEND_URL}`);
    return;
  }

  // Build the image if it doesn't already exist locally.
  if (!dockerImageExists()) {
    buildDockerImage();
  }

  console.log(`Starting Conductor backend (project: ${COMPOSE_PROJECT}) ...`);
  execSync(compose("up -d"), { stdio: "inherit" });

  // Record that we started Docker so teardown can shut it down.
  mkdirSync(tmpdir(), { recursive: true });
  writeFileSync(SENTINEL, "1", "utf8");

  await waitForBackend();
  console.log(`Conductor backend healthy at ${BACKEND_URL}`);
}
