import react from "@vitejs/plugin-react";
import { createHash } from "crypto";
import { readdirSync } from "fs";
import { dirname, resolve, sep } from "path";
import { fileURLToPath } from "url";
import { defineConfig, loadEnv, Plugin } from "vite";
import svgr from "vite-plugin-svgr";
import tsconfigPaths from "vite-tsconfig-paths";
import { vitePluginCspNonce } from "./vite-plugin-csp-nonce";
import { isLibPeerExternal } from "./vite.lib-peer-external";

const __dirname = dirname(fileURLToPath(import.meta.url));
const packageDir = __dirname;

// Plugin to inject build-time hash into context.js script tag
function contextJsHashPlugin(): Plugin {
  const buildHash = createHash("md5")
    .update(Date.now().toString())
    .update(process.pid?.toString() || "")
    .digest("hex")
    .substring(0, 8);

  return {
    name: "context-js-hash",
    transformIndexHtml(html) {
      return html.replace(
        /<script[^>]*src=["']\/context\.js[^"']*["'][^>]*><\/script>/i,
        `<script src="/context.js?v=${buildHash}"></script>`,
      );
    },
  };
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, packageDir);
  const BASE_URL = env.VITE_PUBLIC_URL || "/";

  // Library build mode — emits individual ES modules preserving the src/
  // directory structure so enterprise can consume pre-built dist/ files via
  // deep imports (e.g. "conductor-ui/pages/definition/...").
  if (mode === "lib") {
    const srcDir = resolve(__dirname, "src");
    const srcFiles = readdirSync(srcDir, { recursive: true })
      .map((entry) => entry.toString().split(sep).join("/"))
      .filter(
        (file) =>
          /\.tsx?$/.test(file) &&
          !file.includes(".test.") &&
          !file.includes(".spec.") &&
          !file.endsWith("setupTests.ts"),
      );

    const input = Object.fromEntries(
      srcFiles.map((file) => [
        file.replace(/\.[^.]+$/, ""),
        resolve(srcDir, file),
      ]),
    );

    // Bare-import roots from tsconfig paths — these resolve into src/ and
    // must NOT be externalized.  Everything else (node_modules) is external.
    const tsconfigPathRoots = [
      "commonServices",
      "components",
      "growthbook",
      "images",
      "pages",
      "plugins",
      "queryClient",
      "shared",
      "templates",
      "testData",
      "theme",
      "types",
      "useArrowNavigation",
      "utils",
    ];

    const isExternal = (id: string) => {
      if (id.startsWith(".") || id.startsWith("/")) return false;
      if (tsconfigPathRoots.some((r) => id === r || id.startsWith(`${r}/`)))
        return false;
      return true;
    };

    return {
      plugins: [react(), tsconfigPaths(), svgr()],
      build: {
        // Inline all assets as data URIs so the lib output is self-contained.
        // Without this, Vite emits asset references like "/assets/logo-xxxx.svg"
        // which only resolve within the OSS app's own build output.
        assetsInlineLimit: Infinity,
        // Vite strips CSS imports out of the emitted JS, so per-chunk CSS would
        // be orphaned — nothing in dist/ imports it. Collect it into one
        // dist/style.css that consumers import via "conductor-ui/styles.css".
        cssCodeSplit: false,
        rollupOptions: {
          input,
          external: isExternal,
          preserveEntrySignatures: "allow-extension",
          output: {
            dir: "dist",
            format: "es" as const,
            preserveModules: true,
            preserveModulesRoot: "src",
            entryFileNames: "[name].js",
            assetFileNames: (asset) => {
              const name = asset.names?.[0] ?? "";
              return name.endsWith(".css")
                ? "style.css"
                : "assets/[name]-[hash][extname]";
            },
          },
        },
        sourcemap: true,
      },
    };
  }

  // App build mode - creates standalone OSS application
  return {
    base: BASE_URL,
    resolve: {
      // Prefer TypeScript so extensionless imports (e.g. `components/Foo`) resolve to
      // `Foo.tsx` when both TS and JS variants could apply.
      extensions: [".mjs", ".js", ".mts", ".ts", ".tsx", ".jsx", ".json"],
    },
    plugins: [
      react(),
      tsconfigPaths(),
      svgr(),
      vitePluginCspNonce(),
      contextJsHashPlugin(),
    ],
    optimizeDeps: {
      include: [
        "@emotion/react",
        "@emotion/styled",
        "@mui/material",
        "@mui/system",
      ],
    },
    define: {
      "process.env": {},
    },
    preview: {
      port: 1234,
      // Mirror the dev-server proxy so `vite preview` (used by integration
      // tests) forwards API calls to the Conductor backend.
      // VITE_WF_SERVER can be set in the process environment at preview time
      // to override the .env file value (e.g. for CI or Playwright webServer).
      proxy: {
        "/api": {
          target:
            process.env.VITE_WF_SERVER ||
            env.VITE_WF_SERVER ||
            "http://localhost:8080",
          changeOrigin: true,
        },
        "/swagger-ui": {
          target:
            process.env.VITE_WF_SERVER ||
            env.VITE_WF_SERVER ||
            "http://localhost:8080",
          changeOrigin: true,
        },
        "/api-docs": {
          target:
            process.env.VITE_WF_SERVER ||
            env.VITE_WF_SERVER ||
            "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
    server: {
      port: 1234,
      proxy: {
        "/api": {
          target: env.VITE_WF_SERVER || "http://localhost:8080",
          changeOrigin: true,
        },
        "/swagger-ui": {
          target: env.VITE_WF_SERVER || "http://localhost:8080",
          changeOrigin: true,
        },
        "/api-docs": {
          target: env.VITE_WF_SERVER || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: "dist",
      sourcemap: !!process.env.E2E_COVERAGE,
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: "./src/setupTests.ts",
      include: ["src/**/*.test.{js,ts,jsx,tsx}"],
      coverage: {
        provider: "v8",
        reporter: ["text", "html", "lcov"],
        include: ["src/**/*.{ts,tsx}"],
        exclude: [
          "src/**/*.test.{ts,tsx}",
          "src/setupTests.ts",
          "src/main.tsx",
          "src/index.ts",
        ],
      },
      server: {
        deps: {
          // Force Vitest to process Monaco's ESM through its own pipeline
          // rather than trying to load browser-only bundles in jsdom.
          inline: ["monaco-editor"],
        },
      },
    },
  };
});
