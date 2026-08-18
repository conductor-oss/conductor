/**
 * Merges raw V8 coverage data (produced by e2e/coverage-fixture.ts) into an
 * Istanbul coverage report scoped to ui-next/src, and enforces a minimum
 * threshold.
 *
 * Important: Playwright only records files that were *loaded in the browser*.
 * This script:
 *   1. Drops node_modules / non-src paths (which previously inflated results)
 *   2. Omits unit-test fixtures, type-only modules, re-export barrels, and
 *      known unreachable/orphan OSS surfaces from the denominator
 *   3. Adds every remaining src file that never loaded as 0% coverage
 *   4. Prints a clear "never loaded" list so gaps are obvious
 *
 * Default threshold: 90% statements / lines / files touched.
 * Run after Playwright tests:
 *   E2E_COVERAGE=true pnpm test:e2e:integration
 *   node scripts/playwright-coverage-report.mjs [--min <percent>]
 *
 * Outputs:
 *   playwright-coverage-report/  — HTML + LCOV report
 *   playwright-coverage-report/never-loaded.txt
 *   stdout                       — summary + untested files + threshold verdict
 */

import {
  readFileSync,
  readdirSync,
  existsSync,
  writeFileSync,
  mkdirSync,
  statSync,
} from "fs";
import { resolve, dirname, join, relative, sep } from "path";
import { fileURLToPath } from "url";

import v8toIstanbul from "v8-to-istanbul";
import libCoverage from "istanbul-lib-coverage";
import libReport from "istanbul-lib-report";
import reports from "istanbul-reports";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, "..");
const SRC_DIR = resolve(ROOT, "src");
const COVERAGE_DIR = resolve(ROOT, ".playwright-coverage");
const DIST_DIR = resolve(ROOT, "dist");
const REPORT_DIR = resolve(ROOT, "playwright-coverage-report");

const args = process.argv.slice(2);
const minIdx = args.indexOf("--min");
const MIN_COVERAGE = minIdx !== -1 ? Number(args[minIdx + 1]) : 90;

const SRC_PREFIX = SRC_DIR.endsWith(sep) ? SRC_DIR : SRC_DIR + sep;

function isProjectSrc(filePath) {
  const normalized = resolve(filePath.split("?")[0]);
  return normalized.startsWith(SRC_PREFIX);
}

function toPosixRelative(absPath) {
  return relative(ROOT, absPath).split(sep).join("/");
}

/**
 * Files that never ship in the browser bundle (unit-test fixtures, type-only
 * modules, re-export barrels). Including them as 0% never-loaded rows tanks
 * statement % — e.g. sampleExecutions.js alone is ~50k lines.
 */
const COVERAGE_DENOM_EXCLUDE_NAMES = new Set([
  "setupTests.ts",
  "sampleExecutions.js",
  "diagramTests.js",
  "testDiagrams.js",
  "layoutTestData.js",
  "testUtils.js",
  "test-utils.tsx",
  // Type-only / erase-at-compile — not executed in the browser.
  "types.ts",
  // Side-effect-free re-export barrels — not a useful E2E signal.
  "index.ts",
]);

/** Icons actually imported by CardIcon / iconsForTaskTypes (runtime). */
const TASK_CARD_ICONS_IN_USE = new Set([
  "ForkJoinIcon.tsx",
  "Sendgrid.tsx",
  "HttpPoll.tsx",
  "Json.tsx",
  "Worker.tsx",
  "Simple.tsx",
  "LlmTextComplete.tsx",
  "LlmGenerateEmbeddings.tsx",
  "LlmGetEmbeddings.tsx",
  "LlmStoreEmbeddings.jsx",
  "LlmSearchIndex.tsx",
  "LlmIndexDocument.tsx",
  "GetDocument.tsx",
  "LlmIndexText.tsx",
  "QueryProcessor.tsx",
  "OpsGenie.tsx",
  "UpdateTaskIcon.tsx",
  "UpdateSecret.tsx",
  "LlmChatComplete.tsx",
  "MCPIcon.tsx",
]);

function shouldExcludeFromCoverageDenom(absPath) {
  const rel = toPosixRelative(absPath);
  const name = rel.split("/").pop() ?? "";
  if (COVERAGE_DENOM_EXCLUDE_NAMES.has(name)) return true;
  if (rel.includes("/__tests__/")) return true;
  if (/\.(test|spec)\.(ts|tsx|js|jsx)$/.test(name)) return true;
  // Type-only ambient / declaration modules — never execute in the browser.
  if (name.endsWith(".d.ts")) return true;
  if (rel.startsWith("src/types/")) return true;
  // Growthbook is feature-flagged / not exercised on the default OSS e2e stack.
  if (rel.startsWith("src/growthbook/")) return true;
  // Enterprise RBAC helpers — not wired in OSS routes.
  if (rel.endsWith("/utils/useGetGroups.ts")) return true;
  if (rel.endsWith("/utils/useGetUsers.ts")) return true;
  // Enterprise / plugin-only surfaces not mounted in OSS routes.
  if (rel.includes("/features/charts/")) return true;
  if (rel.includes("/SubjectSelector/")) return true;
  if (rel.endsWith("/RoleTagChip.tsx")) return true;
  if (rel.endsWith("/ShareWorkflowDialog.tsx")) return true;
  // `/taskExecs` is not registered in OSS routes.tsx.
  if (rel.includes("/pages/executions/Task/")) return true;
  if (rel.endsWith("/pages/executions/TaskSearch.tsx")) return true;
  // Playground / get-started (feature-flagged, not on default OSS stack).
  if (rel.includes("/getStartedSample/")) return true;
  // Legacy TaskCard icons superseded by Phosphor / the in-use set above.
  if (rel.includes("/TaskCard/icons/") && !TASK_CARD_ICONS_IN_USE.has(name)) {
    return true;
  }
  // Orphan modules with no import path in the app.
  const orphans = [
    "/pages/execution/state/sampleExecutions.js",
    "/testData/diagramTests.js",
    "/components/features/flow/nodes/index.js",
  ];
  if (orphans.some((o) => rel.endsWith(o) || rel.includes(o))) return true;
  return false;
}

/** Walk src/ for app source files included in the E2E coverage denominator. */
function listSrcFiles(dir = SRC_DIR, acc = []) {
  for (const name of readdirSync(dir)) {
    const abs = join(dir, name);
    if (statSync(abs).isDirectory()) {
      listSrcFiles(abs, acc);
      continue;
    }
    if (!/\.(ts|tsx|js|jsx)$/.test(name)) continue;
    if (shouldExcludeFromCoverageDenom(abs)) continue;
    acc.push(abs);
  }
  return acc;
}

/**
 * Build istanbul file coverage with every non-empty source line marked
 * uncovered (hit count 0). Used for src files that never loaded in e2e.
 */
function uncoveredFileCoverage(absPath) {
  const content = readFileSync(absPath, "utf8");
  const lines = content.split(/\r?\n/);
  const statementMap = {};
  const s = {};
  let id = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) continue;
    if (
      trimmed.startsWith("//") ||
      trimmed.startsWith("/*") ||
      trimmed.startsWith("*") ||
      trimmed.startsWith("*/")
    ) {
      continue;
    }
    const key = String(id++);
    statementMap[key] = {
      start: { line: i + 1, column: 0 },
      end: { line: i + 1, column: Math.max(line.length, 1) },
    };
    s[key] = 0;
  }

  // Avoid 0/0 → 100% for empty/comment-only files.
  if (id === 0) {
    statementMap["0"] = {
      start: { line: 1, column: 0 },
      end: { line: 1, column: 1 },
    };
    s["0"] = 0;
  }

  return {
    path: absPath,
    statementMap,
    fnMap: {},
    branchMap: {},
    s,
    f: {},
    b: {},
  };
}

function printSection(title, files, limit = 40) {
  console.log(`\n${title} (${files.length}):`);
  if (files.length === 0) {
    console.log("  (none)");
    return;
  }
  const shown = files.slice(0, limit);
  for (const f of shown) {
    console.log(`  ${f}`);
  }
  if (files.length > limit) {
    console.log(`  … and ${files.length - limit} more`);
  }
}

async function main() {
  if (!existsSync(COVERAGE_DIR)) {
    console.error("No coverage data found at", COVERAGE_DIR);
    console.error(
      "Run tests with E2E_COVERAGE=true first:\n" +
        "  E2E_COVERAGE=true pnpm test:e2e:integration",
    );
    process.exit(1);
  }

  const rawFiles = readdirSync(COVERAGE_DIR).filter((f) => f.endsWith(".json"));
  if (rawFiles.length === 0) {
    console.error("No coverage JSON files found in", COVERAGE_DIR);
    process.exit(1);
  }

  console.log(`Processing ${rawFiles.length} coverage file(s)…`);

  // Only keep project src/ paths — node_modules previously dominated the report
  // and made "100%" look like the whole app was tested.
  const coverageMap = libCoverage.createCoverageMap();
  let skippedNonSrc = 0;
  const startedAt = Date.now();

  for (let i = 0; i < rawFiles.length; i++) {
    const file = rawFiles[i];
    if (i === 0 || (i + 1) % 25 === 0 || i + 1 === rawFiles.length) {
      const elapsedSec = ((Date.now() - startedAt) / 1000).toFixed(0);
      console.log(`  ${i + 1}/${rawFiles.length} (${elapsedSec}s) — ${file}`);
    }

    const entries = JSON.parse(
      readFileSync(resolve(COVERAGE_DIR, file), "utf8"),
    );

    for (const entry of entries) {
      if (!entry.url.includes("/assets/")) continue;

      const urlPath = new URL(entry.url).pathname;
      const filePath = resolve(DIST_DIR, urlPath.slice(1));
      if (!existsSync(filePath)) continue;

      try {
        const converter = v8toIstanbul(filePath, 0, {
          source: entry.source,
        });
        await converter.load();

        const istanbul = converter.toIstanbul();
        const srcOnly = {};
        for (const [key, data] of Object.entries(istanbul)) {
          const cleanKey = key.split("?")[0];
          if (!isProjectSrc(cleanKey)) {
            skippedNonSrc += 1;
            continue;
          }
          if (shouldExcludeFromCoverageDenom(cleanKey)) {
            continue;
          }
          srcOnly[cleanKey] = { ...data, path: cleanKey };
        }
        if (Object.keys(srcOnly).length > 0) {
          coverageMap.merge(srcOnly);
        }
      } catch (e) {
        console.warn(`  skip ${entry.url}: ${e.message}`);
      }
    }
  }

  const allSrcFiles = listSrcFiles().sort();
  const loadedAbs = new Set(
    coverageMap.files().map((f) => resolve(f.split("?")[0])),
  );

  const neverLoadedAbs = allSrcFiles.filter((f) => !loadedAbs.has(f));
  const loadedAbsList = allSrcFiles.filter((f) => loadedAbs.has(f));

  // Files that loaded but have 0 statement hits (rare with coarse maps, but useful).
  const loadedUncovered = [];
  const loadedPartial = [];
  const loadedFull = [];
  for (const abs of loadedAbsList) {
    const fc = coverageMap.fileCoverageFor(abs);
    const { pct, covered, total } = fc.toSummary().statements;
    const rel = toPosixRelative(abs);
    if (total === 0 || covered === 0) {
      loadedUncovered.push(rel);
    } else if (pct < 100) {
      loadedPartial.push(`${rel} (${pct}%)`);
    } else {
      loadedFull.push(rel);
    }
  }

  // Treat never-loaded src files as fully uncovered in the istanbul map so
  // HTML/LCOV and the headline % reflect the whole project, not just visited pages.
  for (const abs of neverLoadedAbs) {
    coverageMap.addFileCoverage(uncoveredFileCoverage(abs));
  }

  if (coverageMap.files().length === 0) {
    console.error(
      "No source files mapped. Ensure the build was run with source maps enabled:\n" +
        "  E2E_COVERAGE=true pnpm build",
    );
    process.exit(1);
  }

  mkdirSync(REPORT_DIR, { recursive: true });

  const neverLoadedRel = neverLoadedAbs.map(toPosixRelative);
  writeFileSync(
    resolve(REPORT_DIR, "never-loaded.txt"),
    neverLoadedRel.join("\n") + (neverLoadedRel.length ? "\n" : ""),
    "utf8",
  );

  // Generate reports
  const context = libReport.createContext({
    dir: REPORT_DIR,
    coverageMap,
    defaultSummarizer: "nested",
  });

  reports.create("text").execute(context);
  reports.create("html").execute(context);
  reports.create("lcov").execute(context);

  const fileTouchPct =
    allSrcFiles.length === 0
      ? 0
      : (100 * loadedAbsList.length) / allSrcFiles.length;

  console.log("\n═══════════════════════════════════════════════════════════");
  console.log("E2E coverage is scoped to ui-next/src (node_modules excluded)");
  console.log("═══════════════════════════════════════════════════════════");
  console.log(`Source files:     ${allSrcFiles.length} total`);
  console.log(
    `Loaded in e2e:    ${loadedAbsList.length} (${fileTouchPct.toFixed(1)}% of src files)`,
  );
  console.log(`Never loaded:     ${neverLoadedAbs.length}`);
  console.log(
    `Loaded @ 100%:    ${loadedFull.length}  (often just module evaluation — not deep test coverage)`,
  );
  console.log(`Loaded partial:   ${loadedPartial.length}`);
  console.log(`Loaded @ 0%:      ${loadedUncovered.length}`);
  if (skippedNonSrc > 0) {
    console.log(
      `Ignored non-src:  ${skippedNonSrc} source-map entries (node_modules, etc.)`,
    );
  }

  printSection(
    "Never loaded during e2e — not exercised by these tests",
    neverLoadedRel,
  );
  printSection("Loaded but partially covered", loadedPartial);
  printSection("Loaded but 0% statement hits", loadedUncovered);

  console.log(
    `\nWrote never-loaded list → ${toPosixRelative(resolve(REPORT_DIR, "never-loaded.txt"))}`,
  );
  console.log(
    `HTML report           → ${toPosixRelative(REPORT_DIR)}/index.html`,
  );

  // Threshold check on project-wide statement coverage (includes never-loaded as 0%).
  const summary = coverageMap.getCoverageSummary();
  const metrics = {
    Statements: summary.statements.pct,
    Branches: summary.branches.pct,
    Functions: summary.functions.pct,
    Lines: summary.lines.pct,
    "Files touched": Number(fileTouchPct.toFixed(1)),
  };

  console.log(`\nCoverage thresholds (minimum ${MIN_COVERAGE}%):`);
  let pass = true;
  for (const [label, pct] of Object.entries(metrics)) {
    // Branches/functions are often empty with Vite production source maps —
    // only enforce metrics that have a real denominator (or file touch).
    if (label === "Branches" && summary.branches.total === 0) {
      console.log(`  – ${label}: n/a (no branch data in source maps)`);
      continue;
    }
    if (label === "Functions" && summary.functions.total === 0) {
      console.log(`  – ${label}: n/a (no function data in source maps)`);
      continue;
    }
    const ok = pct >= MIN_COVERAGE;
    if (!ok) pass = false;
    console.log(`  ${ok ? "✓" : "✗"} ${label}: ${pct}%`);
  }

  if (!pass) {
    console.error(`\nCoverage below ${MIN_COVERAGE}% threshold — failing.\n`);
    process.exit(1);
  }

  console.log(`\nAll coverage thresholds met.\n`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
