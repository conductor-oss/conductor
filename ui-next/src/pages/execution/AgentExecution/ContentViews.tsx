/**
 * Read-only renderers shared by the execution detail panels: a JSON editor, a
 * Markdown block, and a `ContentView` that picks between them by value shape.
 */
import { Box, Typography } from "@mui/material";
import Editor from "@monaco-editor/react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { defaultEditorOptions, type EditorOptions } from "shared/editor";

const jsonViewerOptions: EditorOptions = {
  ...defaultEditorOptions,
  readOnly: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  lineNumbers: "off",
  folding: true,
  wordWrap: "on",
  fontSize: 12,
  renderLineHighlight: "none",
  overviewRulerLanes: 0,
  guides: { indentation: false },
};

/** Monaco JSON viewer. Fills its container, so give it a sized parent. */
export function JsonView({ src }: { src: unknown }) {
  const json = JSON.stringify(src, null, 2);
  return (
    <Editor
      height="100%"
      language="json"
      value={json}
      options={jsonViewerOptions}
      theme="vs"
    />
  );
}

/** Raw text or JSON, as-is. `maxHeight` makes tall content scroll instead of grow. */
export function PreformattedText({
  text,
  maxHeight,
}: {
  text: string;
  maxHeight?: number;
}) {
  return (
    <Box
      component="pre"
      sx={{
        m: 0,
        fontFamily: "monospace",
        fontSize: "0.8rem",
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        lineHeight: 1.6,
        maxHeight,
        overflow: maxHeight ? "auto" : undefined,
      }}
    >
      {text}
    </Box>
  );
}

function looksLikeMarkdown(text: string): boolean {
  return /^#{1,6}\s|\*\*[^*]+\*\*|^[-*]\s|\n#{1,6}\s|^\d+\.\s|^>\s/m.test(text);
}

export function MarkdownView({ content }: { content: string }) {
  return (
    <Box
      sx={{
        "& h1,& h2,& h3": {
          fontWeight: 700,
          mt: 1.5,
          mb: 0.75,
          lineHeight: 1.3,
        },
        "& h1": { fontSize: "1rem" },
        "& h2": { fontSize: "0.9rem" },
        "& h3": { fontSize: "0.85rem" },
        "& p": { my: 0.75, lineHeight: 1.6, fontSize: "0.875rem" },
        "& ul,& ol": { pl: 2.5, my: 0.5 },
        "& li": { fontSize: "0.875rem", lineHeight: 1.5 },
        "& code": {
          backgroundColor: "#f1f5f9",
          borderRadius: 0.5,
          px: 0.5,
          fontFamily: "monospace",
          fontSize: "0.8rem",
        },
        "& pre": {
          backgroundColor: "#f1f5f9",
          borderRadius: 1,
          p: 1.5,
          overflowX: "auto",
          my: 1,
          "& code": { backgroundColor: "transparent", p: 0 },
        },
        "& blockquote": {
          borderLeft: "3px solid",
          borderColor: "divider",
          pl: 1.5,
          my: 0.75,
          color: "text.secondary",
        },
        "& strong": { fontWeight: 700 },
        "& a": { color: "primary.main" },
        "& table": { borderCollapse: "collapse", width: "100%", my: 1 },
        "& th,& td": {
          border: "1px solid",
          borderColor: "divider",
          px: 1,
          py: 0.5,
          fontSize: "0.875rem",
        },
        "& th": { backgroundColor: "grey.50", fontWeight: 600 },
      }}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </Box>
  );
}

/** Renders any value: Markdown, preformatted text, or a JSON viewer. */
export function ContentView({
  value,
  label,
}: {
  value: unknown;
  label?: string;
}) {
  if (value == null) {
    return (
      <Typography
        variant="body2"
        color="text.disabled"
        sx={{ py: 2, textAlign: "center", fontSize: "0.875rem" }}
      >
        No {label ?? "data"}
      </Typography>
    );
  }
  if (typeof value === "string") {
    if (looksLikeMarkdown(value)) return <MarkdownView content={value} />;
    return <PreformattedText text={value} />;
  }
  // Object: wrap Monaco in fixed-height container (height="100%" requires flex parent,
  // which only the JSON tab provides — Input/Output tabs use block layout).
  return (
    <Box
      sx={{
        height: 400,
        border: "1px solid rgba(0,0,0,0.08)",
        borderRadius: 1,
        overflow: "hidden",
      }}
    >
      <JsonView src={value} />
    </Box>
  );
}
