/**
 * Merges raw V8 coverage data (produced by e2e/coverage-fixture.ts) into an
 * Istanbul coverage report and enforces a minimum threshold.
 *
 * Run after Playwright tests:
 *   E2E_COVERAGE=true pnpm test:e2e:integration
 *   node scripts/playwright-coverage-report.mjs [--min <percent>]
 *
 * Outputs:
 *   playwright-coverage-report/  — HTML + LCOV report
 *   stdout                       — text summary + threshold verdict
 */

import { readFileSync, readdirSync, existsSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

import v8toIstanbul from "v8-to-istanbul";
import libCoverage from "istanbul-lib-coverage";
import libReport from "istanbul-lib-report";
import reports from "istanbul-reports";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, "..");
const COVERAGE_DIR = resolve(ROOT, ".playwright-coverage");
const DIST_DIR = resolve(ROOT, "dist");
const REPORT_DIR = resolve(ROOT, "playwright-coverage-report");

const args = process.argv.slice(2);
const minIdx = args.indexOf("--min");
const MIN_COVERAGE = minIdx !== -1 ? Number(args[minIdx + 1]) : 50;

async function main() {
  if (!existsSync(COVERAGE_DIR)) {
    console.error("No coverage data found at", COVERAGE_DIR);
    console.error(
      "Run tests with E2E_COVERAGE=true first:\n" +
        "  E2E_COVERAGE=true pnpm test:e2e:integration",
    );
    process.exit(1);
  }

  const coverageMap = libCoverage.createCoverageMap();
  const files = readdirSync(COVERAGE_DIR).filter((f) => f.endsWith(".json"));

  if (files.length === 0) {
    console.error("No coverage JSON files found in", COVERAGE_DIR);
    process.exit(1);
  }

  console.log(`Processing ${files.length} coverage file(s)…\n`);

  for (const file of files) {
    const entries = JSON.parse(
      readFileSync(resolve(COVERAGE_DIR, file), "utf8"),
    );

    for (const entry of entries) {
      // Only process app chunks served from /assets/; skip inline scripts,
      // browser extensions, analytics snippets, etc.
      if (!entry.url.includes("/assets/")) continue;

      const urlPath = new URL(entry.url).pathname;
      const filePath = resolve(DIST_DIR, urlPath.slice(1));

      if (!existsSync(filePath)) {
        continue;
      }

      try {
        const converter = v8toIstanbul(filePath, 0, {
          source: entry.source,
        });
        await converter.load();
        coverageMap.merge(converter.toIstanbul());
      } catch (e) {
        console.warn(`  skip ${entry.url}: ${e.message}`);
      }
    }
  }

  if (coverageMap.files().length === 0) {
    console.error(
      "No source files mapped. Ensure the build was run with source maps enabled:\n" +
        "  E2E_COVERAGE=true pnpm build",
    );
    process.exit(1);
  }

  // Generate reports
  const context = libReport.createContext({
    dir: REPORT_DIR,
    coverageMap,
    defaultSummarizer: "nested",
  });

  reports.create("text").execute(context);
  reports.create("html").execute(context);
  reports.create("lcov").execute(context);

  // Threshold check
  const summary = coverageMap.getCoverageSummary();
  const metrics = {
    Statements: summary.statements.pct,
    Branches: summary.branches.pct,
    Functions: summary.functions.pct,
    Lines: summary.lines.pct,
  };

  console.log(`\nCoverage thresholds (minimum ${MIN_COVERAGE}%):`);
  let pass = true;
  for (const [label, pct] of Object.entries(metrics)) {
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
