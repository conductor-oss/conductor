/** Package roots supplied by the host app (see package.json peerDependencies). Lib build must not bundle these. */
export const LIB_PEER_ROOTS = [
  "react",
  "react-dom",
  "react-router",
  "react-router-dom",
  "react-router-use-location-state",
  "@emotion/react",
  "@emotion/styled",
  "@mui/material",
  "@mui/icons-material",
  "@mui/system",
  "@mui/x-date-pickers",
  "@jsonforms/core",
  "@jsonforms/material-renderers",
  "@jsonforms/react",
  "@monaco-editor/react",
  "monaco-editor",
  "@dnd-kit/core",
  "@dnd-kit/sortable",
  "react-hook-form",
  "@hookform/resolvers",
  "yup",
  "styled-components",
] as const;

export function isLibPeerExternal(id: string): boolean {
  for (const root of LIB_PEER_ROOTS) {
    if (id === root || id.startsWith(`${root}/`)) {
      return true;
    }
  }
  return false;
}
