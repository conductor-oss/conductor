/**
 * Wraps `vite build --mode lib --watch` and writes a `.lib-watch` marker so
 * enterprise conductor-ui can detect that OSS dist/ is being live-rebuilt.
 */
import { spawn } from "child_process";
import { existsSync, unlinkSync, writeFileSync } from "fs";
import { createRequire } from "module";
import { dirname, resolve } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageDir = resolve(__dirname, "..");
const markerPath = resolve(packageDir, ".lib-watch");

const require = createRequire(import.meta.url);
const viteBin = resolve(
  dirname(require.resolve("vite/package.json")),
  "bin/vite.js",
);

writeFileSync(markerPath, `${process.pid}\n`, "utf8");

const cleanup = () => {
  try {
    if (existsSync(markerPath)) unlinkSync(markerPath);
  } catch {
    // ignore
  }
};

const exitWithCleanup = (code = 0) => {
  cleanup();
  process.exit(code);
};

process.on("SIGINT", () => exitWithCleanup(130));
process.on("SIGTERM", () => exitWithCleanup(143));
process.on("exit", cleanup);

const child = spawn(
  process.execPath,
  [viteBin, "build", "--mode", "lib", "--watch"],
  {
    cwd: packageDir,
    stdio: "inherit",
    env: process.env,
  },
);

child.on("exit", (code, signal) => {
  cleanup();
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
