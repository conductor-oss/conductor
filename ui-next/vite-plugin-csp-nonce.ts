import type { Plugin } from "vite";

const NONCE_VALUE = "tpsHAxwU5x0csoIuLNs2vg==";

/**
 * Vite plugin to add CSP nonces to all script tags in the built HTML
 */
export function vitePluginCspNonce(): Plugin {
  return {
    name: "vite-plugin-csp-nonce",
    enforce: "post",
    transformIndexHtml(html) {
      // Add nonce to all script tags that don't already have one
      return html.replace(
        /<script(?![^>]*\snonce=)([^>]*)>/gi,
        `<script nonce="${NONCE_VALUE}"$1>`,
      );
    },
  };
}
