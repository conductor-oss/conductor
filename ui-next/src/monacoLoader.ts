import { loader } from "@monaco-editor/react";

import packageJson from "../package.json";

// @monaco-editor/loader defaults to a hardcoded CDN version (independent of
// our package.json) unless explicitly configured. Point it at the version we
// actually depend on, read straight from package.json, so bumping the
// `monaco-editor` dependency there is enough to change what loads at runtime.
// (We can't read monaco-editor's own package.json directly - its `exports`
// map blocks any subpath other than the ones it explicitly lists.)
//
// This lives in its own side-effect module rather than in main.tsx because
// library consumers have their own entry point and never load main.tsx. Both
// main.tsx and index.ts import this so the OSS app and the published lib
// agree on the version.
const monacoEditorVersionRange =
  packageJson.devDependencies["monaco-editor"] ??
  packageJson.peerDependencies["monaco-editor"];
const monacoEditorCdnVersion = monacoEditorVersionRange.replace(/^[^\d]*/, "");

loader.config({
  paths: {
    vs: `https://cdn.jsdelivr.net/npm/monaco-editor@${monacoEditorCdnVersion}/min/vs`,
  },
});
