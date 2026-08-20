/**
 * PromptPreview — the messages an LLM task was sent, rendered for reading.
 * The Input tab keeps the raw payload.
 */
import { useMemo, useState } from "react";
import { Box, Button, Typography } from "@mui/material";
import { ContentView, MarkdownView, PreformattedText } from "./ContentViews";
import {
  buildPromptEntries,
  describePrompt,
  type PromptEntry,
  type StructuredContent,
} from "./promptPreviewModel";

// A Map, not an object literal: a persisted role like "constructor" must not
// resolve against Object.prototype.
const ROLE_ACCENTS = new Map([
  ["system", "#7c3aed"],
  ["user", "#2563eb"],
  ["assistant", "#059669"],
]);
const FALLBACK_ROLE_ACCENT = "#64748b";

/** Characters shown before a message collapses behind "Show full message". */
const COLLAPSED_LENGTH = 1200;
const PAYLOAD_MAX_HEIGHT = 420;
const EMPTY_MESSAGE = "(empty message)";

export function PromptPreview({ input }: { input: unknown }) {
  const entries = useMemo(() => buildPromptEntries(input), [input]);
  const summary = describePrompt(entries);

  return (
    <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto", p: 2.5 }}>
      {summary ? (
        <Typography
          sx={{ fontSize: "0.75rem", color: "text.secondary", mb: 2 }}
        >
          {summary}
        </Typography>
      ) : null}
      {entries.map((entry, index) => (
        // Content in the key: a card whose message changed remounts, so its
        // expanded state resets.
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
        borderLeftColor: ROLE_ACCENTS.get(entry.role) ?? FALLBACK_ROLE_ACCENT,
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
        <MarkdownView content={shown || EMPTY_MESSAGE} />
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
      <PreformattedText text={json} maxHeight={PAYLOAD_MAX_HEIGHT} />
      {content.trailing ? (
        <Box sx={{ mt: 1.5 }}>
          <ContentView value={content.trailing} />
        </Box>
      ) : null}
    </>
  );
}
