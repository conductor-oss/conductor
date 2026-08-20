/**
 * PromptPreview — reader-friendly view of the messages an LLM task was sent:
 * instructions as Markdown, embedded payloads in a JSON viewer, long messages
 * collapsed. The Input tab remains the source of truth for the raw payload.
 *
 * TODO: experimental. Reassess once users have exercised the tab, and remove it
 * if the plain Input and JSON views prove sufficient.
 */
import { useMemo, useState } from "react";
import { Box, Button, Typography } from "@mui/material";
import { ContentView, JsonView, MarkdownView } from "./ContentViews";
import {
  buildPromptEntries,
  describePrompt,
  reflowInstructions,
  type PromptEntry,
  type StructuredContent,
} from "./promptPreviewModel";

/**
 * Left border colour per role, so turns are scannable at a glance. Built with
 * `Object.assign` onto a null-prototype object (rather than an object
 * literal) so a role string pulled from the persisted payload — e.g.
 * `role: "constructor"` — can never resolve to `Object.prototype.constructor`
 * instead of a real accent or the fallback.
 */
const ROLE_ACCENTS: Record<string, string> = Object.assign(
  Object.create(null) as Record<string, string>,
  {
    system: "#7c3aed",
    user: "#2563eb",
    assistant: "#059669",
  },
);
const FALLBACK_ROLE_ACCENT = "#64748b";

/**
 * Resolves a persisted role string to its accent colour, falling back for any
 * role not in `ROLE_ACCENTS` — including prototype-polluting values like
 * `"constructor"` or `"__proto__"`, since the record has no prototype for
 * those to resolve against. Intentionally not exported: this file exports
 * components only, so that Vite's fast refresh keeps working.
 */
function roleAccent(role: string): string {
  return ROLE_ACCENTS[role] ?? FALLBACK_ROLE_ACCENT;
}
/** Characters shown before a message collapses behind "Show full message". */
const COLLAPSED_LENGTH = 1200;
/**
 * Below this many pretty-printed lines, a structured payload renders as plain
 * preformatted text instead of mounting Monaco. Monaco's folding and virtual
 * scrolling only earn their cost on substantial blobs; a trivial payload like
 * `{"ok": true}` is cheaper and better-sized as plain text than pinned inside
 * a fixed-height editor.
 */
const PAYLOAD_LINE_THRESHOLD = 15;
/** Monaco viewer height bounds (px) for payloads at/above the line threshold. */
const PAYLOAD_VIEWER_MIN_HEIGHT = 120;
const PAYLOAD_VIEWER_MAX_HEIGHT = 420;
/** Approximate px per line at Monaco's 12px font, used to size the viewer to content. */
const PAYLOAD_VIEWER_LINE_HEIGHT = 18;
/** Extra vertical chrome (padding, scrollbar margin) added on top of raw line height. */
const PAYLOAD_VIEWER_CHROME = 24;
const EMPTY_MESSAGE = "(empty message)";

export function PromptPreview({ input }: { input: unknown }) {
  const entries = useMemo(() => buildPromptEntries(input), [input]);

  return (
    <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto", p: 2.5 }}>
      <Typography sx={{ fontSize: "0.75rem", color: "text.secondary", mb: 2 }}>
        {describePrompt(entries)}
      </Typography>
      {entries.map((entry, index) => (
        <PromptEntryCard
          key={`${index}:${entry.role}:${entry.length}`}
          entry={entry}
        />
      ))}
    </Box>
  );
}

function PromptEntryCard({ entry }: { entry: PromptEntry }) {
  const isInstructions = entry.kind === "instructions";

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderLeft: "4px solid",
        borderLeftColor: roleAccent(entry.role),
        borderRadius: 1,
        mb: 1.5,
        overflow: "hidden",
      }}
    >
      <CardHeader
        label={isInstructions ? "Instructions" : entry.role}
        length={entry.length}
      />
      <Box sx={{ px: 1.5, py: 1 }}>
        {entry.structured ? (
          // Not collapsible: a partial payload would not be valid JSON.
          <StructuredContentView content={entry.structured} />
        ) : (
          <CollapsibleText text={entry.text} asInstructions={isInstructions} />
        )}
      </Box>
    </Box>
  );
}

function CardHeader({ label, length }: { label: string; length: number }) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        px: 1.5,
        py: 0.75,
        backgroundColor: "#f8fafc",
        borderBottom: "1px solid",
        borderColor: "divider",
      }}
    >
      <Typography
        sx={{
          fontSize: "0.7rem",
          fontWeight: 700,
          letterSpacing: "0.06em",
          textTransform: "uppercase",
        }}
      >
        {label}
      </Typography>
      <Typography sx={{ fontSize: "0.7rem", color: "text.secondary" }}>
        {length.toLocaleString()} characters
      </Typography>
    </Box>
  );
}

/**
 * Messages of any role can be long — recalled context and tool results arrive
 * as user turns and grow just as large as instructions — so any of them may
 * collapse, each card tracking its own state.
 */
function CollapsibleText({
  text,
  asInstructions,
}: {
  text: string;
  asInstructions: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const isCollapsible = text.length > COLLAPSED_LENGTH;
  const shown =
    isCollapsible && !expanded ? `${text.slice(0, COLLAPSED_LENGTH)}\n…` : text;

  return (
    <>
      {asInstructions ? (
        <MarkdownView content={reflowInstructions(shown) || EMPTY_MESSAGE} />
      ) : (
        <ContentView value={shown || EMPTY_MESSAGE} />
      )}
      {isCollapsible && (
        <Button
          size="small"
          aria-expanded={expanded}
          onClick={() => setExpanded((current) => !current)}
          sx={{ mt: 1, textTransform: "none" }}
        >
          {expanded
            ? "Show less"
            : asInstructions
              ? "Show full instruction"
              : "Show full message"}
        </Button>
      )}
    </>
  );
}

function StructuredContentView({ content }: { content: StructuredContent }) {
  const json = useMemo(
    () => JSON.stringify(content.payload, null, 2),
    [content.payload],
  );
  const lineCount = useMemo(() => json.split("\n").length, [json]);
  const isSmallPayload = lineCount < PAYLOAD_LINE_THRESHOLD;
  const viewerHeight = Math.min(
    PAYLOAD_VIEWER_MAX_HEIGHT,
    Math.max(
      PAYLOAD_VIEWER_MIN_HEIGHT,
      lineCount * PAYLOAD_VIEWER_LINE_HEIGHT + PAYLOAD_VIEWER_CHROME,
    ),
  );

  return (
    <>
      {content.leading ? <ContentView value={content.leading} /> : null}
      <Typography
        sx={{
          fontSize: "0.75rem",
          fontWeight: 700,
          mt: content.leading ? 2 : 0,
          mb: 0.75,
        }}
      >
        Structured data
      </Typography>
      {isSmallPayload ? (
        <Box
          component="pre"
          sx={{
            m: 0,
            fontFamily: "monospace",
            fontSize: "0.8rem",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
            lineHeight: 1.6,
          }}
        >
          {json}
        </Box>
      ) : (
        <Box
          sx={{
            height: viewerHeight,
            border: "1px solid rgba(0,0,0,0.08)",
            borderRadius: 1,
            overflow: "hidden",
          }}
        >
          <JsonView src={content.payload} />
        </Box>
      )}
      {content.trailing ? (
        <Box sx={{ mt: 1.5 }}>
          <ContentView value={content.trailing} />
        </Box>
      ) : null}
    </>
  );
}
