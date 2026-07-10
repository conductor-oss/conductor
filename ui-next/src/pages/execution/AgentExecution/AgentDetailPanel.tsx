/**
 * AgentDetailPanel — right-hand panel matching Conductor's task detail style.
 * Tabs: Summary | Input | Output | JSON
 */
import { useState, useRef, useEffect, type ReactNode } from "react";
import {
  Box,
  Paper,
  Typography,
  IconButton,
  Select,
  MenuItem,
} from "@mui/material";
import { X as CloseIcon, ArrowRight, Scissors } from "@phosphor-icons/react";
import { Tab, Tabs } from "components";
import Editor from "@monaco-editor/react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  EventType,
  TaskAttempt,
} from "./types";
import {
  formatTokens,
  formatDuration,
  getModelIconPath,
} from "./agentExecutionUtils";
import { toolCategoryForPanel } from "utils/agentTaskCategory";

// ─── Types ────────────────────────────────────────────────────────────────────

export interface DetailNodeData {
  kind:
    | "llm"
    | "tool"
    | "handoff"
    | "subagent"
    | "output"
    | "error"
    | "start"
    | "group";
  label: string;
  status: AgentStatus;
  event?: AgentEvent;
  subAgentRun?: AgentRunData;
  /** For group kind */
  groupType?: "agents" | "tools";
  groupAgents?: AgentRunData[];
  groupEvents?: AgentEvent[];
}

// ─── Tab keys ────────────────────────────────────────────────────────────────

const SUMMARY_TAB = "summary";
const INPUT_TAB = "input";
const OUTPUT_TAB = "output";
const JSON_TAB = "json";

// ─── JSON editor viewer (Monaco, fills available height) ─────────────────────

function JsonView({ src }: { src: unknown }) {
  const json = JSON.stringify(src, null, 2);
  return (
    <Editor
      height="100%"
      language="json"
      value={json}
      options={
        {
          readOnly: true,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          lineNumbers: "off",
          folding: true,
          wordWrap: "on",
          fontSize: 12,
          renderLineHighlight: "none",
          overviewRulerLanes: 0,
          renderIndentGuides: false,
        } as any
      }
      theme="vs"
    />
  );
}

// ─── Markdown renderer ────────────────────────────────────────────────────────

function looksLikeMarkdown(text: string): boolean {
  return /^#{1,6}\s|\*\*[^*]+\*\*|^[-*]\s|\n#{1,6}\s|^\d+\.\s|^>\s/m.test(text);
}

function MarkdownView({ content }: { content: string }) {
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

// ─── Smart content renderer (text/markdown/JSON) ──────────────────────────────

function ContentView({ value, label }: { value: unknown; label?: string }) {
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
        }}
      >
        {value}
      </Box>
    );
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

// ─── Summary key-value row (matches Conductor's KeyValueTable style) ──────────

function SummaryRow({ label, value }: { label: string; value: ReactNode }) {
  if (value == null || value === "" || value === undefined) return null;
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "flex-start",
        px: 2,
        py: 1,
        borderBottom: "1px solid #e5e7eb",
        gap: 2,
      }}
    >
      <Typography
        sx={{
          color: "#6b7280",
          fontSize: "0.875rem",
          minWidth: 148,
          flexShrink: 0,
          lineHeight: 1.7,
        }}
      >
        {label}
      </Typography>
      <Box
        sx={{
          fontSize: "0.875rem",
          color: "#111827",
          wordBreak: "break-word",
          lineHeight: 1.7,
        }}
      >
        {value}
      </Box>
    </Box>
  );
}

function SummaryTable({ children }: { children: ReactNode }) {
  return (
    <Box
      sx={{
        border: "1px solid #e5e7eb",
        borderRadius: 1,
        overflow: "hidden",
        mx: 2,
        my: 1.5,
      }}
    >
      {children}
    </Box>
  );
}

// ─── Status pill chip (matches Conductor's status badge) ──────────────────────

function StatusChip({ status }: { status: AgentStatus }) {
  const cfg = {
    [AgentStatus.COMPLETED]: {
      bg: "#d4edda",
      color: "#155724",
      label: "COMPLETED",
    },
    [AgentStatus.FAILED]: { bg: "#f8d7da", color: "#721c24", label: "FAILED" },
    [AgentStatus.RUNNING]: {
      bg: "#fff3cd",
      color: "#856404",
      label: "RUNNING",
    },
    [AgentStatus.WAITING]: {
      bg: "#fff3cd",
      color: "#856404",
      label: "WAITING",
    },
  }[status] ?? { bg: "#e9ecef", color: "#495057", label: status.toUpperCase() };
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontSize: "0.75rem",
        fontWeight: 600,
        padding: "2px 10px",
        borderRadius: 20,
        letterSpacing: "0.02em",
      }}
    >
      {cfg.label}
    </span>
  );
}

// Keep old name as alias for backward compat within this file
const StatusBadgeInline = StatusChip;

// ─── Model display with provider icon ─────────────────────────────────────────

function ModelValue({ model }: { model: string }) {
  const icon = getModelIconPath(model);
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
      {icon && (
        <img
          src={icon}
          style={{ width: 14, height: 14, objectFit: "contain", flexShrink: 0 }}
          alt=""
        />
      )}
      {model}
    </Box>
  );
}

// ─── Context condensation banner ──────────────────────────────────────────────

function CondensationBanner({
  info,
}: {
  info: NonNullable<AgentEvent["condensationInfo"]>;
}) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "flex-start",
        gap: 1,
        mx: 2,
        my: 1,
        px: 1.25,
        py: 0.75,
        borderRadius: 1,
        backgroundColor: "#e1f5fe",
        border: "1px dashed #0288d1",
        color: "#0277bd",
      }}
    >
      <Scissors size={14} style={{ flexShrink: 0, marginTop: 2 }} />
      <Box>
        <Typography
          sx={{ fontWeight: 700, fontSize: "0.75rem", lineHeight: 1.4 }}
        >
          Context condensed before this call
        </Typography>
        <Typography
          sx={{ fontSize: "0.72rem", lineHeight: 1.5, opacity: 0.85 }}
        >
          {info.messagesBefore} → {info.messagesAfter} messages &nbsp;·&nbsp;
          {info.exchangesCondensed} exchange
          {info.exchangesCondensed !== 1 ? "s" : ""} summarized &nbsp;·&nbsp;
          {info.trigger}
        </Typography>
      </Box>
    </Box>
  );
}

// ─── Agent definition section ─────────────────────────────────────────────────

function getItemName(t: unknown, fallback = "[item]"): string {
  if (typeof t === "string") return t;
  if (t && typeof t === "object") {
    const o = t as Record<string, unknown>;
    const n = o.name ?? o._worker_ref ?? (o.function as any)?.name;
    if (typeof n === "string" && n) return n;
  }
  return fallback;
}

function TagList({
  items,
  color,
  bg,
  border,
}: {
  items: string[];
  color: string;
  bg: string;
  border: string;
}) {
  return (
    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
      {items.slice(0, 12).map((name, i) => (
        <Box
          key={i}
          sx={{
            px: 0.75,
            py: 0.25,
            borderRadius: 0.5,
            backgroundColor: bg,
            border: `1px solid ${border}`,
            fontSize: "0.72rem",
            color,
          }}
        >
          {name}
        </Box>
      ))}
      {items.length > 12 && (
        <Box
          sx={{
            fontSize: "0.72rem",
            color: "text.secondary",
            alignSelf: "center",
          }}
        >
          +{items.length - 12} more
        </Box>
      )}
    </Box>
  );
}

const TOOL_TYPE_BADGE: Record<
  string,
  { label: string; color: string; bg: string; border: string }
> = {
  tool: { label: "@tool", color: "#0369a1", bg: "#e0f2fe", border: "#7dd3fc" },
  http: { label: "HTTP", color: "#6b7280", bg: "#f3f4f6", border: "#d1d5db" },
  mcp: { label: "MCP", color: "#7c3aed", bg: "#ede9fe", border: "#c4b5fd" },
  rag: { label: "RAG", color: "#0f766e", bg: "#ccfbf1", border: "#99f6e4" },
};

function getToolDescription(t: Record<string, unknown>): string | undefined {
  const d = t.description ?? (t.function as any)?.description;
  return typeof d === "string" && d ? d : undefined;
}

function ToolList({
  tools,
  category,
}: {
  tools: Array<Record<string, unknown>>;
  category: string;
}) {
  const badge = TOOL_TYPE_BADGE[category] ?? TOOL_TYPE_BADGE.tool;
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
      {tools.slice(0, 20).map((t, i) => {
        const name = getItemName(t);
        const desc = getToolDescription(t);
        return (
          <Box
            key={i}
            sx={{
              display: "flex",
              alignItems: "flex-start",
              gap: 1,
              px: 1,
              py: 0.75,
              borderRadius: 1,
              border: `1px solid ${badge.border}`,
              backgroundColor: badge.bg,
            }}
          >
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
                <Typography
                  sx={{
                    fontWeight: 600,
                    fontSize: "0.78rem",
                    color: badge.color,
                    wordBreak: "break-word",
                  }}
                >
                  {name}
                </Typography>
              </Box>
              {desc && (
                <Typography
                  sx={{
                    fontSize: "0.7rem",
                    color: "text.secondary",
                    lineHeight: 1.4,
                    mt: 0.25,
                  }}
                >
                  {desc.length > 120 ? desc.slice(0, 120) + "…" : desc}
                </Typography>
              )}
            </Box>
            <Box
              sx={{
                flexShrink: 0,
                px: 0.75,
                py: 0.15,
                borderRadius: 0.5,
                backgroundColor: "#fff",
                border: `1px solid ${badge.border}`,
                fontSize: "0.62rem",
                fontWeight: 700,
                color: badge.color,
                letterSpacing: "0.03em",
              }}
            >
              {badge.label}
            </Box>
          </Box>
        );
      })}
      {tools.length > 20 && (
        <Box sx={{ fontSize: "0.72rem", color: "text.secondary", pl: 1 }}>
          +{tools.length - 20} more
        </Box>
      )}
    </Box>
  );
}

/** Categorise a tool entry by its toolType field — delegates to shared utility */
const toolCategory = toolCategoryForPanel;

/** Compact card for an agent_tool entry (shows model + nested tools + instructions).
 *  Expandable on click to show full instructions and all nested tools. */
function AgentToolCard({ tool }: { tool: Record<string, unknown> }) {
  const [expanded, setExpanded] = useState(false);
  const name = getItemName(tool);
  const agentConfig = (tool.config as any)?.agentConfig as
    | Record<string, unknown>
    | undefined;
  const model = (agentConfig?.model ?? tool.model) as string | undefined;
  const nestedTools = (agentConfig?.tools ?? tool.tools) as
    | Array<unknown>
    | undefined;
  const instructions = agentConfig?.instructions as string | undefined;
  const iconPath = getModelIconPath(model);

  const maxInstr = expanded ? Infinity : 100;
  const maxTools = expanded ? Infinity : 8;

  return (
    <Box
      onClick={() => setExpanded((e) => !e)}
      sx={{
        border: "2px solid #93c5fd",
        borderRadius: 1,
        backgroundColor: "#f8f9ff",
        p: 1,
        mb: 0.5,
        cursor: "pointer",
        transition: "border-color 0.15s, box-shadow 0.15s",
        "&:hover": {
          borderColor: "#4969e4",
          boxShadow: "0 1px 4px rgba(73,105,228,0.15)",
        },
      }}
    >
      {/* Name + model + badge */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, mb: 0.25 }}>
        {iconPath && (
          <img
            src={iconPath}
            style={{
              width: 13,
              height: 13,
              objectFit: "contain",
              flexShrink: 0,
            }}
            alt=""
          />
        )}
        <Typography
          sx={{
            fontWeight: 600,
            fontSize: "0.78rem",
            color: "#4969e4",
            flex: 1,
          }}
        >
          {name}
        </Typography>
        {model && (
          <Typography
            sx={{ fontSize: "0.68rem", color: "text.disabled", flexShrink: 0 }}
          >
            {model}
          </Typography>
        )}
        <Box
          sx={{
            flexShrink: 0,
            px: 0.75,
            py: 0.15,
            borderRadius: 0.5,
            backgroundColor: "#e8eeff",
            border: "1px solid #93c5fd",
            fontSize: "0.62rem",
            fontWeight: 700,
            color: "#3d5fc0",
            letterSpacing: "0.03em",
          }}
        >
          AGENT
        </Box>
      </Box>
      {/* Instructions snippet */}
      {instructions && (
        <Typography
          sx={{
            fontSize: "0.7rem",
            color: "text.secondary",
            lineHeight: 1.4,
            mb: nestedTools?.length ? 0.5 : 0,
          }}
        >
          {instructions.length > maxInstr
            ? instructions.slice(0, maxInstr) + "…"
            : instructions}
        </Typography>
      )}
      {/* Nested tools */}
      {nestedTools && nestedTools.length > 0 && (
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.4 }}>
          {nestedTools.slice(0, maxTools).map((nt, i) => (
            <Box
              key={i}
              sx={{
                px: 0.5,
                py: 0.15,
                borderRadius: 0.5,
                backgroundColor: "#e8eeff",
                border: "1px solid #c7d2fc",
                fontSize: "0.66rem",
                color: "#6366f1",
              }}
            >
              {getItemName(nt)}
            </Box>
          ))}
          {!expanded && nestedTools.length > 8 && (
            <Box
              sx={{
                fontSize: "0.66rem",
                color: "text.disabled",
                alignSelf: "center",
              }}
            >
              +{nestedTools.length - 8}
            </Box>
          )}
        </Box>
      )}
      {/* Expand hint */}
      <Typography
        sx={{
          fontSize: "0.62rem",
          color: "text.disabled",
          mt: 0.5,
          textAlign: "right",
        }}
      >
        {expanded ? "click to collapse" : "click to expand"}
      </Typography>
    </Box>
  );
}

/** Extract guardrail function name from an input/output guardrail entry */
function guardrailFnName(g: unknown): string {
  if (!g || typeof g !== "object") return getItemName(g);
  const obj = g as Record<string, unknown>;
  const fn = obj.guardrail_function as Record<string, unknown> | undefined;
  if (fn) return getItemName(fn);
  return (obj.name as string) ?? getItemName(g);
}

/** Small inline pipeline chip */
function PipelineChip({
  label,
  color,
  bg,
  border,
}: {
  label: string;
  color: string;
  bg: string;
  border: string;
}) {
  return (
    <Box
      sx={{
        px: 0.75,
        py: 0.25,
        borderRadius: 0.5,
        backgroundColor: bg,
        border: `1px solid ${border}`,
        fontSize: "0.72rem",
        color,
        whiteSpace: "nowrap",
      }}
    >
      {label}
    </Box>
  );
}

function GuardrailPipeline({
  inputGrs,
  outputGrs,
}: {
  inputGrs: string[];
  outputGrs: string[];
}) {
  const arrow = (
    <Box
      sx={{
        color: "#9ca3af",
        fontSize: "0.72rem",
        lineHeight: 1,
        flexShrink: 0,
      }}
    >
      →
    </Box>
  );
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: 0.5,
        rowGap: 0.75,
      }}
    >
      {inputGrs.map((name, i) => (
        <Box
          key={`ip-${i}`}
          sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
        >
          <PipelineChip
            label={name}
            color="#7c3aed"
            bg="#faf5ff"
            border="#e9d5ff"
          />
          {arrow}
        </Box>
      ))}
      <PipelineChip
        label="LLM Call"
        color="#374151"
        bg="#f3f4f6"
        border="#d1d5db"
      />
      {outputGrs.map((name, i) => (
        <Box
          key={`op-${i}`}
          sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
        >
          {arrow}
          <PipelineChip
            label={name}
            color="#0369a1"
            bg="#e0f2fe"
            border="#bae6fd"
          />
        </Box>
      ))}
    </Box>
  );
}

function AgentDefSection({ agentDef }: { agentDef: Record<string, unknown> }) {
  const rawInstr = agentDef.instructions ?? agentDef.description;
  const instructions = typeof rawInstr === "string" ? rawInstr : undefined;
  const dynamicInstr =
    rawInstr && typeof rawInstr === "object"
      ? (rawInstr as Record<string, unknown>)
      : undefined;
  const allTools =
    (agentDef.tools as Array<Record<string, unknown>> | undefined) ?? [];
  const defModel = agentDef.model as string | undefined;

  // Read guardrails from both old and new field names
  const inputGuardrails =
    (agentDef.input_guardrails as Array<unknown> | undefined) ?? [];
  const outputGuardrails =
    (agentDef.output_guardrails as Array<unknown> | undefined) ?? [];
  const legacyGuardrails =
    (agentDef.guardrails as Array<unknown> | undefined) ?? [];

  // Split tools by category
  const agentTools = allTools.filter((t) => toolCategory(t) === "agent");
  const regularTools = allTools.filter((t) => toolCategory(t) === "tool");
  const guardrailTools = allTools.filter(
    (t) => toolCategory(t) === "guardrail",
  );
  const httpTools = allTools.filter((t) => toolCategory(t) === "http");
  const mcpTools = allTools.filter((t) => toolCategory(t) === "mcp");
  const ragTools = allTools.filter((t) => toolCategory(t) === "rag");

  const inputGrNames = inputGuardrails.map((g) => guardrailFnName(g));
  const outputGrNames = outputGuardrails.map((g) => guardrailFnName(g));
  const legacyGrNames = [
    ...guardrailTools.map((g) => getItemName(g)),
    ...legacyGuardrails.map((g) => getItemName(g)),
  ];
  const hasGuardrailPipeline =
    inputGrNames.length > 0 || outputGrNames.length > 0;

  const hasContent =
    instructions ||
    dynamicInstr ||
    allTools.length ||
    hasGuardrailPipeline ||
    legacyGrNames.length > 0 ||
    defModel;
  if (!hasContent) return null;

  return (
    <Box sx={{ borderTop: "1px solid rgba(0,0,0,0.06)", mt: 1 }}>
      <Typography
        sx={{
          px: 3,
          pt: 1.5,
          pb: 0.5,
          fontSize: "0.65rem",
          fontWeight: 600,
          color: "#4969e4",
          textTransform: "uppercase",
          letterSpacing: "0.06em",
        }}
      >
        Agent Definition
      </Typography>

      {defModel && (
        <SummaryRow
          label="Configured model"
          value={<ModelValue model={defModel} />}
        />
      )}

      {/* Guardrail pipeline: |ip gr| → |LLM| → |op gr| */}
      {hasGuardrailPipeline && (
        <SummaryRow
          label="Guardrail pipeline"
          value={
            <GuardrailPipeline
              inputGrs={inputGrNames}
              outputGrs={outputGrNames}
            />
          }
        />
      )}

      {/* Agent tools — each shown as a mini card */}
      {agentTools.length > 0 && (
        <SummaryRow
          label={`Agents (${agentTools.length})`}
          value={
            <Box>
              {agentTools.map((t, i) => (
                <AgentToolCard key={i} tool={t} />
              ))}
            </Box>
          }
        />
      )}

      {/* Regular tools (worker, tool, simple) */}
      {regularTools.length > 0 && (
        <SummaryRow
          label={`Tools (${regularTools.length})`}
          value={<ToolList tools={regularTools} category="tool" />}
        />
      )}

      {/* HTTP tools */}
      {httpTools.length > 0 && (
        <SummaryRow
          label={`HTTP (${httpTools.length})`}
          value={<ToolList tools={httpTools} category="http" />}
        />
      )}

      {/* MCP tools */}
      {mcpTools.length > 0 && (
        <SummaryRow
          label={`MCP (${mcpTools.length})`}
          value={<ToolList tools={mcpTools} category="mcp" />}
        />
      )}

      {/* RAG tools */}
      {ragTools.length > 0 && (
        <SummaryRow
          label={`RAG (${ragTools.length})`}
          value={<ToolList tools={ragTools} category="rag" />}
        />
      )}

      {/* Legacy guardrails (from tools list with toolType="guardrail" or agentDef.guardrails) */}
      {legacyGrNames.length > 0 && !hasGuardrailPipeline && (
        <SummaryRow
          label={`Guardrails (${legacyGrNames.length})`}
          value={
            <TagList
              items={legacyGrNames}
              color="#0369a1"
              bg="#e0f2fe"
              border="#bae6fd"
            />
          }
        />
      )}

      {/* Instructions */}
      {instructions && (
        <SummaryRow
          label="Instructions"
          value={
            <Box
              component="pre"
              sx={{
                m: 0,
                fontSize: "0.78rem",
                fontFamily: "inherit",
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
                color: "text.secondary",
                lineHeight: 1.5,
                maxHeight: 180,
                overflowY: "auto",
              }}
            >
              {instructions}
            </Box>
          }
        />
      )}
      {/* Dynamic instructions (worker ref) */}
      {!instructions && dynamicInstr && (
        <SummaryRow
          label="Instructions"
          value={
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 0.75,
                fontSize: "0.78rem",
                color: "text.secondary",
              }}
            >
              <Box
                sx={{
                  px: 0.75,
                  py: 0.25,
                  borderRadius: 0.5,
                  backgroundColor: "#f0f4ff",
                  border: "1px solid #d1d9f5",
                  fontSize: "0.72rem",
                  color: "#4969e4",
                }}
              >
                {getItemName(dynamicInstr)}
              </Box>
              <span style={{ fontStyle: "italic", fontSize: "0.72rem" }}>
                {typeof dynamicInstr.description === "string"
                  ? dynamicInstr.description
                  : "dynamic"}
              </span>
            </Box>
          }
        />
      )}
    </Box>
  );
}

// ─── Parallel run selector ────────────────────────────────────────────────────

type WindowItem =
  | { type: "chip"; idx: number }
  | { type: "gap"; from: number; to: number };

function buildWindow(total: number, sel: number): WindowItem[] {
  if (total <= 10)
    return Array.from({ length: total }, (_, i) => ({
      type: "chip" as const,
      idx: i,
    }));
  const visible = new Set(
    [
      0,
      1,
      total - 2,
      total - 1,
      Math.max(0, sel - 2),
      Math.max(0, sel - 1),
      sel,
      Math.min(total - 1, sel + 1),
      Math.min(total - 1, sel + 2),
    ].filter((i) => i >= 0 && i < total),
  );
  const sorted = Array.from(visible).sort((a, b) => a - b);
  const result: WindowItem[] = [];
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] > sorted[i - 1] + 1)
      result.push({ type: "gap", from: sorted[i - 1] + 1, to: sorted[i] - 1 });
    result.push({ type: "chip", idx: sorted[i] });
  }
  return result;
}

interface RunBarProps {
  count: number;
  statuses: AgentStatus[];
  selected: number;
  onSelect: (i: number) => void;
  labels: string[];
}

function RunBar({ count, statuses, selected, onSelect, labels }: RunBarProps) {
  const items = buildWindow(count, selected);
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 0.25,
        flexWrap: "wrap",
      }}
    >
      {items.map((item) => {
        if (item.type === "chip") {
          const { idx } = item;
          const st = statuses[idx];
          const color =
            st === AgentStatus.FAILED
              ? "#DD2222"
              : st === AgentStatus.RUNNING
                ? "#f59e0b"
                : "#40BA56";
          const active = idx === selected;
          return (
            <Box
              key={idx}
              component="button"
              onClick={() => onSelect(idx)}
              sx={{
                appearance: "none",
                fontFamily: "inherit",
                cursor: "pointer",
                minWidth: 32,
                height: 24,
                px: 0.5,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                backgroundColor: "#fff",
                color: active ? color : "#858585",
                border: `1px solid ${active ? color : "#DDDDDD"}`,
                borderRadius: "3px",
                fontSize: "0.65rem",
                fontWeight: active ? 700 : 500,
                transition: "all 0.1s",
                outline: "none",
                "&:hover": {
                  borderColor: color,
                  color: color,
                  backgroundColor: `${color}0d`,
                },
              }}
            >
              {idx + 1}
            </Box>
          );
        }
        // Gap → dropdown listing all runs in the gap
        const { from, to } = item;
        const gapItems = Array.from(
          { length: to - from + 1 },
          (_, k) => from + k,
        );
        return (
          <Select
            key={`gap-${from}`}
            value=""
            displayEmpty
            renderValue={() => "···"}
            onChange={(e) => onSelect(Number(e.target.value))}
            size="small"
            variant="outlined"
            sx={{
              height: 24,
              minWidth: 36,
              "& .MuiSelect-select": {
                py: 0,
                px: 0.75,
                fontSize: "0.65rem",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "#858585",
              },
              "& .MuiOutlinedInput-notchedOutline": { borderColor: "#DDDDDD" },
              "& .MuiSelect-icon": { display: "none" },
            }}
          >
            {gapItems.map((idx) => {
              const st = statuses[idx];
              const dot =
                st === AgentStatus.FAILED
                  ? "#DD2222"
                  : st === AgentStatus.RUNNING
                    ? "#f59e0b"
                    : "#40BA56";
              return (
                <MenuItem
                  key={idx}
                  value={idx}
                  sx={{ fontSize: "0.75rem", gap: 1 }}
                >
                  <Box
                    sx={{
                      width: 6,
                      height: 6,
                      borderRadius: "50%",
                      backgroundColor: dot,
                      flexShrink: 0,
                    }}
                  />
                  {labels[idx] ?? `Run ${idx + 1}`}
                </MenuItem>
              );
            })}
          </Select>
        );
      })}
    </Box>
  );
}

// ─── Group detail panel (parallel agents / tool calls) ────────────────────────

function GroupDetailPanel({
  node,
  onDrillIn,
}: {
  node: DetailNodeData;
  onDrillIn?: (run: AgentRunData) => void;
}) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const isAgents = node.groupType === "agents";
  const agents = node.groupAgents ?? [];
  const events = node.groupEvents ?? [];
  const count = isAgents ? agents.length : events.length;

  const statuses: AgentStatus[] = isAgents
    ? agents.map((a) => a.status)
    : events.map((e) =>
        e.success === true
          ? AgentStatus.COMPLETED
          : e.success === false
            ? AgentStatus.FAILED
            : AgentStatus.RUNNING,
      );

  const labels: string[] = isAgents
    ? agents.map((a, i) => `${a.agentName} #${i + 1}`)
    : events.map((e, i) => `${e.toolName ?? "tool"} #${i + 1}`);

  const completed = statuses.filter((s) => s === AgentStatus.COMPLETED).length;
  const failed = statuses.filter((s) => s === AgentStatus.FAILED).length;
  const running = count - completed - failed;

  const selAgent = isAgents ? agents[selectedIdx] : undefined;
  const selEvent = !isAgents ? events[selectedIdx] : undefined;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      {/* Stat bar */}
      <Box
        sx={{
          px: 2,
          py: 1,
          display: "flex",
          alignItems: "center",
          gap: 2,
          borderBottom: "1px solid #e5e7eb",
          flexShrink: 0,
        }}
      >
        <Typography sx={{ fontSize: "0.75rem", color: "text.secondary" }}>
          {count} {isAgents ? "agents" : "calls"}
        </Typography>
        {completed > 0 && (
          <Typography
            sx={{ fontSize: "0.75rem", color: "#40BA56", fontWeight: 600 }}
          >
            {completed} ✓
          </Typography>
        )}
        {failed > 0 && (
          <Typography
            sx={{ fontSize: "0.75rem", color: "#DD2222", fontWeight: 600 }}
          >
            {failed} ✗
          </Typography>
        )}
        {running > 0 && (
          <Typography
            sx={{ fontSize: "0.75rem", color: "#f59e0b", fontWeight: 600 }}
          >
            {running} ⟳
          </Typography>
        )}
      </Box>

      {/* Run selector */}
      <Box
        sx={{
          px: 2,
          py: 1.25,
          borderBottom: "1px solid #e5e7eb",
          flexShrink: 0,
        }}
      >
        <RunBar
          count={count}
          statuses={statuses}
          selected={selectedIdx}
          onSelect={setSelectedIdx}
          labels={labels}
        />
      </Box>

      {/* Selected run detail */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: "auto",
          scrollbarWidth: "none",
          "&::-webkit-scrollbar": { display: "none" },
        }}
      >
        {selAgent && (
          <Box>
            <SummaryTable>
              <SummaryRow label="Agent" value={selAgent.agentName} />
              {selAgent.model && (
                <SummaryRow
                  label="Model"
                  value={<ModelValue model={selAgent.model} />}
                />
              )}
              <SummaryRow
                label="Status"
                value={<StatusBadgeInline status={selAgent.status} />}
              />
              {selAgent.totalDurationMs > 0 && (
                <SummaryRow
                  label="Duration"
                  value={formatDuration(selAgent.totalDurationMs)}
                />
              )}
              {selAgent.totalTokens.promptTokens +
                selAgent.totalTokens.completionTokens >
                0 && (
                <>
                  <SummaryRow
                    label="Prompt tokens"
                    value={formatTokens(selAgent.totalTokens.promptTokens)}
                  />
                  <SummaryRow
                    label="Completion tokens"
                    value={formatTokens(selAgent.totalTokens.completionTokens)}
                  />
                  <SummaryRow
                    label="Total tokens"
                    value={formatTokens(
                      selAgent.totalTokens.promptTokens +
                        selAgent.totalTokens.completionTokens,
                    )}
                  />
                </>
              )}
              {selAgent.turns.length > 0 && (
                <SummaryRow label="Turns" value={selAgent.turns.length} />
              )}
              {selAgent.finishReason && (
                <SummaryRow
                  label="Finish reason"
                  value={selAgent.finishReason.toUpperCase()}
                />
              )}
              {selAgent.failureReason && (
                <SummaryRow
                  label="Failure reason"
                  value={
                    <span style={{ color: "#DC2626" }}>
                      {selAgent.failureReason}
                    </span>
                  }
                />
              )}
            </SummaryTable>
            {/* Input */}
            {selAgent.input && (
              <Box
                sx={{
                  mx: 2,
                  mb: 1.5,
                  mt: 1.5,
                  border: "1px solid #e5e7eb",
                  borderRadius: 1,
                  overflow: "hidden",
                }}
              >
                <Box
                  sx={{
                    px: 2,
                    py: 0.75,
                    borderBottom: "1px solid #e5e7eb",
                    backgroundColor: "#f9fafb",
                  }}
                >
                  <Typography
                    sx={{
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#6b7280",
                    }}
                  >
                    Input
                  </Typography>
                </Box>
                <Box sx={{ px: 2, py: 1 }}>
                  <ContentView value={selAgent.input} label="input" />
                </Box>
              </Box>
            )}
            {/* Output */}
            {selAgent.output && (
              <Box
                sx={{
                  mx: 2,
                  mb: 1.5,
                  border: "1px solid #e5e7eb",
                  borderRadius: 1,
                  overflow: "hidden",
                }}
              >
                <Box
                  sx={{
                    px: 2,
                    py: 0.75,
                    borderBottom: "1px solid #e5e7eb",
                    backgroundColor: "#f9fafb",
                  }}
                >
                  <Typography
                    sx={{
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#6b7280",
                    }}
                  >
                    Output
                  </Typography>
                </Box>
                <Box sx={{ px: 2, py: 1 }}>
                  <ContentView value={selAgent.output} label="output" />
                </Box>
              </Box>
            )}
            {onDrillIn && selAgent.subWorkflowId && (
              <Box sx={{ px: 2, py: 1 }}>
                <Box
                  onClick={() => onDrillIn(selAgent)}
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 0.75,
                    px: 1.5,
                    py: 0.75,
                    borderRadius: 1,
                    cursor: "pointer",
                    fontSize: "0.8rem",
                    fontWeight: 500,
                    color: "#fff",
                    backgroundColor: "#4969e4",
                    "&:hover": { backgroundColor: "#3858d6" },
                  }}
                >
                  View full execution <ArrowRight size={14} />
                </Box>
              </Box>
            )}
          </Box>
        )}
        {selEvent && (
          <Box>
            <SummaryTable>
              <SummaryRow label="Tool" value={selEvent.toolName ?? "tool"} />
              <SummaryRow
                label="Status"
                value={<StatusBadgeInline status={statuses[selectedIdx]} />}
              />
              {selEvent.durationMs ? (
                <SummaryRow
                  label="Duration"
                  value={formatDuration(selEvent.durationMs)}
                />
              ) : null}
              {selEvent.tokens &&
                selEvent.tokens.promptTokens +
                  selEvent.tokens.completionTokens >
                  0 && (
                  <SummaryRow
                    label="Tokens"
                    value={formatTokens(
                      selEvent.tokens.promptTokens +
                        selEvent.tokens.completionTokens,
                    )}
                  />
                )}
              {selEvent.taskMeta?.workerId && (
                <SummaryRow label="Worker" value={selEvent.taskMeta.workerId} />
              )}
              {selEvent.taskMeta?.reasonForIncompletion && (
                <SummaryRow
                  label="Failure"
                  value={
                    <span style={{ color: "#DC2626" }}>
                      {selEvent.taskMeta.reasonForIncompletion}
                    </span>
                  }
                />
              )}
            </SummaryTable>
            {selEvent.toolArgs != null && (
              <Box
                sx={{
                  mx: 2,
                  mb: 1.5,
                  mt: 1.5,
                  border: "1px solid #e5e7eb",
                  borderRadius: 1,
                  overflow: "hidden",
                }}
              >
                <Box
                  sx={{
                    px: 2,
                    py: 0.75,
                    borderBottom: "1px solid #e5e7eb",
                    backgroundColor: "#f9fafb",
                  }}
                >
                  <Typography
                    sx={{
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#6b7280",
                    }}
                  >
                    Input
                  </Typography>
                </Box>
                <Box sx={{ height: 200 }}>
                  <JsonView src={selEvent.toolArgs} />
                </Box>
              </Box>
            )}
            {selEvent.result != null && (
              <Box
                sx={{
                  mx: 2,
                  mb: 1.5,
                  border: "1px solid #e5e7eb",
                  borderRadius: 1,
                  overflow: "hidden",
                }}
              >
                <Box
                  sx={{
                    px: 2,
                    py: 0.75,
                    borderBottom: "1px solid #e5e7eb",
                    backgroundColor: "#f9fafb",
                  }}
                >
                  <Typography
                    sx={{
                      fontSize: "0.75rem",
                      fontWeight: 600,
                      color: "#6b7280",
                    }}
                  >
                    Output
                  </Typography>
                </Box>
                <Box sx={{ height: 200 }}>
                  <JsonView src={selEvent.result} />
                </Box>
              </Box>
            )}
          </Box>
        )}
      </Box>
    </Box>
  );
}

// ─── Lazy-fetch sub-agent definition (model + agentDef) ──────────────────────

interface SubAgentFetchResult {
  model?: string;
  agentDef?: Record<string, unknown>;
}

function useSubAgentDef(run: AgentRunData | undefined): {
  data: SubAgentFetchResult | null;
  loading: boolean;
} {
  const [data, setData] = useState<SubAgentFetchResult | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Already have definition data or no sub-workflow to fetch
    if (!run?.subWorkflowId || run.agentDef || run.model) {
      setData(null);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    fetch(`/api/agent/executions/${run.subWorkflowId}/full`)
      .then((r) => (r.ok ? r.json() : null))
      .then((exec: any) => {
        if (!exec || cancelled) return;
        const agentDef = exec.workflowDefinition?.metadata?.agentDef as
          | Record<string, unknown>
          | undefined;
        const model = (exec.tasks as any[] | undefined)?.find(
          (t: any) => t.taskType === "LLM_CHAT_COMPLETE",
        )?.inputData?.model as string | undefined;
        if (!cancelled) setData({ model, agentDef });
      })
      .catch(() => {
        if (!cancelled) setData(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [run?.subWorkflowId, run?.agentDef, run?.model]);

  return { data, loading };
}

// ─── Summary tab content per node kind ──────────────────────────────────────

function SummaryContent({
  node,
  onDrillIn,
}: {
  node: DetailNodeData;
  onDrillIn?: (run: AgentRunData) => void;
}) {
  const ev = node.event;

  // Lazy-load sub-agent definition for subagent/start nodes that don't have it yet
  const agentRun =
    node.kind === "subagent" || node.kind === "start"
      ? node.subAgentRun
      : undefined;
  const { data: fetchedDef, loading: defLoading } = useSubAgentDef(agentRun);

  if (node.kind === "start" && node.subAgentRun) {
    const run = node.subAgentRun;
    const effectiveModel = run.model ?? fetchedDef?.model;
    const effectiveAgentDef = run.agentDef ?? fetchedDef?.agentDef;
    const pt = run.totalTokens.promptTokens;
    const ct = run.totalTokens.completionTokens;
    return (
      <Box>
        <SummaryTable>
          <SummaryRow label="Agent" value={run.agentName} />
          {effectiveModel && (
            <SummaryRow
              label="Model"
              value={<ModelValue model={effectiveModel} />}
            />
          )}
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={run.status} />}
          />
          {run.totalDurationMs > 0 && (
            <SummaryRow
              label="Duration"
              value={formatDuration(run.totalDurationMs)}
            />
          )}
          {pt + ct > 0 && (
            <SummaryRow label="Total tokens" value={formatTokens(pt + ct)} />
          )}
          {pt > 0 && (
            <SummaryRow label="Prompt tokens" value={formatTokens(pt)} />
          )}
          {ct > 0 && (
            <SummaryRow label="Completion tokens" value={formatTokens(ct)} />
          )}
          {run.finishReason && (
            <SummaryRow
              label="Finish reason"
              value={run.finishReason.toUpperCase()}
            />
          )}
        </SummaryTable>
        {onDrillIn && run.subWorkflowId && (
          <Box sx={{ px: 2, py: 1 }}>
            <Box
              onClick={() => onDrillIn(run)}
              sx={{
                display: "inline-flex",
                alignItems: "center",
                gap: 0.75,
                px: 1.5,
                py: 0.75,
                borderRadius: 1,
                cursor: "pointer",
                fontSize: "0.8rem",
                fontWeight: 500,
                color: "#fff",
                backgroundColor: "#4969e4",
                transition: "all 0.15s",
                "&:hover": { backgroundColor: "#3858d6" },
              }}
            >
              View full execution <ArrowRight size={14} />
            </Box>
          </Box>
        )}
        {defLoading && !effectiveAgentDef && (
          <Box
            sx={{ px: 3, py: 1.5, color: "text.disabled", fontSize: "0.75rem" }}
          >
            Loading agent definition…
          </Box>
        )}
        {effectiveAgentDef && <AgentDefSection agentDef={effectiveAgentDef} />}
      </Box>
    );
  }

  if (node.kind === "llm") {
    const tok = ev?.tokens;
    return (
      <Box>
        {ev?.condensationInfo && (
          <CondensationBanner info={ev.condensationInfo} />
        )}
        <SummaryTable>
          <SummaryRow label="Kind" value="LLM Call" />
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={node.status} />}
          />
          {ev?.toolName && (
            <SummaryRow
              label="Model"
              value={<ModelValue model={ev.toolName} />}
            />
          )}
          {ev?.baseUrl && <SummaryRow label="Base URL" value={ev.baseUrl} />}
          {tok && tok.promptTokens + tok.completionTokens > 0 && (
            <SummaryRow
              label="Total tokens"
              value={formatTokens(tok.promptTokens + tok.completionTokens)}
            />
          )}
          {tok && tok.promptTokens > 0 && (
            <SummaryRow
              label="Prompt tokens"
              value={formatTokens(tok.promptTokens)}
            />
          )}
          {tok && tok.completionTokens > 0 && (
            <SummaryRow
              label="Completion tokens"
              value={formatTokens(tok.completionTokens)}
            />
          )}
          {ev?.durationMs && (
            <SummaryRow
              label="Duration"
              value={formatDuration(ev.durationMs)}
            />
          )}
        </SummaryTable>
      </Box>
    );
  }

  if (node.kind === "tool") {
    const meta = ev?.taskMeta;
    const fmt = (ts?: number) =>
      ts
        ? new Date(ts)
            .toLocaleString(undefined, {
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit",
              second: "2-digit",
            })
            .replace(",", "")
        : undefined;
    return (
      <Box>
        <SummaryTable>
          {meta?.taskType && (
            <SummaryRow label="Task type" value={meta.taskType} />
          )}
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={node.status} />}
          />
          <SummaryRow label="Task name" value={ev?.toolName ?? node.label} />
          {meta?.referenceTaskName && (
            <SummaryRow label="Task reference" value={meta.referenceTaskName} />
          )}
          {meta?.taskId && (
            <SummaryRow label="Task execution id" value={meta.taskId} />
          )}
          {meta?.retryCount != null && (
            <SummaryRow label="Retry count" value={String(meta.retryCount)} />
          )}
          {meta?.totalAttempts != null && meta.totalAttempts > 1 && (
            <SummaryRow
              label="Total attempts"
              value={String(meta.totalAttempts)}
            />
          )}
          {fmt(meta?.scheduledTime) && (
            <SummaryRow
              label="Scheduled time"
              value={fmt(meta?.scheduledTime)!}
            />
          )}
          {fmt(meta?.startTime) && (
            <SummaryRow label="Start time" value={fmt(meta?.startTime)!} />
          )}
          {fmt(meta?.endTime) && (
            <SummaryRow label="End time" value={fmt(meta?.endTime)!} />
          )}
          {ev?.durationMs ? (
            <SummaryRow
              label="Duration"
              value={formatDuration(ev.durationMs)}
            />
          ) : null}
          {meta?.workerId && (
            <SummaryRow label="Worker" value={meta.workerId} />
          )}
          {meta?.pollCount != null && (
            <SummaryRow label="Poll count" value={String(meta.pollCount)} />
          )}
          {meta?.seq != null && (
            <SummaryRow label="Sequence" value={String(meta.seq)} />
          )}
          {meta?.queueWaitTime != null && (
            <SummaryRow
              label="Queue wait time"
              value={String(meta.queueWaitTime)}
            />
          )}
          {meta?.reasonForIncompletion && (
            <SummaryRow
              label="Failure reason"
              value={
                <span style={{ color: "#DC2626" }}>
                  {meta.reasonForIncompletion}
                </span>
              }
            />
          )}
        </SummaryTable>
      </Box>
    );
  }

  if (node.kind === "handoff") {
    return (
      <Box>
        <SummaryTable>
          <SummaryRow
            label="Target agent"
            value={ev?.targetAgent ?? node.label}
          />
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={node.status} />}
          />
        </SummaryTable>
      </Box>
    );
  }

  // Guardrail event (GUARDRAIL_PASS/FAIL — not a gate)
  if (
    (node.kind === "output" || node.kind === "error") &&
    (ev?.type === EventType.GUARDRAIL_PASS ||
      ev?.type === EventType.GUARDRAIL_FAIL) &&
    ev?.toolName !== "gate"
  ) {
    const passed = ev.type === EventType.GUARDRAIL_PASS;
    const grDetail = ev.detail as Record<string, unknown> | undefined;
    const grOutput = grDetail?.output as Record<string, unknown> | undefined;
    const reason = (grOutput?.message ??
      (grOutput?.output_info as any)?.reason ??
      ev.summary) as string;
    const meta = ev.taskMeta;
    const fmt = (ts?: number) =>
      ts
        ? new Date(ts)
            .toLocaleString(undefined, {
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit",
              second: "2-digit",
            })
            .replace(",", "")
        : undefined;
    return (
      <Box>
        <Box
          sx={{
            mx: 2,
            my: 1,
            px: 1.25,
            py: 0.75,
            borderRadius: 1,
            backgroundColor: passed ? "#f0fdf4" : "#fff5f5",
            border: `1px solid ${passed ? "#86efac" : "#fca5a5"}`,
          }}
        >
          <Typography
            sx={{
              fontWeight: 700,
              fontSize: "0.8rem",
              color: passed ? "#16a34a" : "#dc2626",
            }}
          >
            {passed ? "Guardrail passed" : "Guardrail triggered"}
          </Typography>
          {reason && (
            <Typography
              sx={{
                fontSize: "0.75rem",
                color: passed ? "#166534" : "#991b1b",
                mt: 0.25,
              }}
            >
              {reason}
            </Typography>
          )}
        </Box>
        <SummaryTable>
          <SummaryRow label="Guardrail" value={ev.toolName ?? node.label} />
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={node.status} />}
          />
          {grOutput?.passed != null && (
            <SummaryRow label="Passed" value={String(grOutput.passed)} />
          )}
          {grOutput?.guardrail_name != null && (
            <SummaryRow label="Name" value={String(grOutput.guardrail_name)} />
          )}
          {grOutput?.on_fail != null && (
            <SummaryRow label="On fail" value={String(grOutput.on_fail)} />
          )}
          {grOutput?.fixed_output != null && (
            <SummaryRow
              label="Fixed output"
              value={String(grOutput.fixed_output)}
            />
          )}
          {ev.durationMs ? (
            <SummaryRow
              label="Duration"
              value={formatDuration(ev.durationMs)}
            />
          ) : null}
          {meta?.taskType && (
            <SummaryRow label="Task type" value={meta.taskType} />
          )}
          {meta?.referenceTaskName && (
            <SummaryRow label="Task reference" value={meta.referenceTaskName} />
          )}
          {meta?.taskId && (
            <SummaryRow label="Task execution id" value={meta.taskId} />
          )}
          {fmt(meta?.startTime) && (
            <SummaryRow label="Start time" value={fmt(meta?.startTime)!} />
          )}
          {fmt(meta?.endTime) && (
            <SummaryRow label="End time" value={fmt(meta?.endTime)!} />
          )}
          {meta?.workerId && (
            <SummaryRow label="Worker" value={meta.workerId} />
          )}
        </SummaryTable>
      </Box>
    );
  }

  // Gate event (GUARDRAIL_PASS/FAIL with toolName === "gate")
  if (
    (node.kind === "output" || node.kind === "error") &&
    ev?.toolName === "gate"
  ) {
    const gateDetail = ev.detail as Record<string, unknown> | undefined;
    const gateCfg = gateDetail?.gate as Record<string, unknown> | undefined;
    const decision = gateDetail?.decision as string | undefined;
    const stopped = decision === "stop";
    return (
      <Box>
        <Box
          sx={{
            mx: 2,
            my: 1,
            px: 1.25,
            py: 0.75,
            borderRadius: 1,
            backgroundColor: stopped ? "#fff5f5" : "#f0fdf4",
            border: `1px solid ${stopped ? "#fca5a5" : "#86efac"}`,
          }}
        >
          <Typography
            sx={{
              fontWeight: 700,
              fontSize: "0.8rem",
              color: stopped ? "#dc2626" : "#16a34a",
            }}
          >
            {stopped
              ? "Gate triggered — chain stopped"
              : "Gate passed — chain continues"}
          </Typography>
        </Box>
        <SummaryTable>
          <SummaryRow
            label="Decision"
            value={
              <span
                style={{
                  fontWeight: 600,
                  color: stopped ? "#dc2626" : "#16a34a",
                }}
              >
                {decision ?? "continue"}
              </span>
            }
          />
          {gateCfg?.type != null && (
            <SummaryRow label="Gate type" value={String(gateCfg.type)} />
          )}
          {gateCfg?.text != null && (
            <SummaryRow
              label="Sentinel text"
              value={
                <Box
                  component="code"
                  sx={{
                    fontSize: "0.78rem",
                    backgroundColor: "#f1f5f9",
                    px: 0.5,
                    py: 0.15,
                    borderRadius: 0.5,
                    fontFamily: "monospace",
                  }}
                >
                  {String(gateCfg.text)}
                </Box>
              }
            />
          )}
          {gateCfg?.caseSensitive != null && (
            <SummaryRow
              label="Case sensitive"
              value={String(gateCfg.caseSensitive)}
            />
          )}
          {ev.durationMs && (
            <SummaryRow
              label="Duration"
              value={formatDuration(ev.durationMs)}
            />
          )}
        </SummaryTable>
      </Box>
    );
  }

  if (node.kind === "subagent" && node.subAgentRun) {
    const run = node.subAgentRun;
    const effectiveModel = run.model ?? fetchedDef?.model;
    const effectiveAgentDef = run.agentDef ?? fetchedDef?.agentDef;
    const pt = run.totalTokens.promptTokens;
    const ct = run.totalTokens.completionTokens;
    return (
      <Box>
        <SummaryTable>
          <SummaryRow label="Agent" value={run.agentName} />
          {effectiveModel && (
            <SummaryRow
              label="Model"
              value={<ModelValue model={effectiveModel} />}
            />
          )}
          <SummaryRow
            label="Status"
            value={<StatusBadgeInline status={run.status} />}
          />
          {run.totalDurationMs > 0 && (
            <SummaryRow
              label="Duration"
              value={formatDuration(run.totalDurationMs)}
            />
          )}
          {pt + ct > 0 && (
            <SummaryRow label="Total tokens" value={formatTokens(pt + ct)} />
          )}
          {pt > 0 && (
            <SummaryRow label="Prompt tokens" value={formatTokens(pt)} />
          )}
          {ct > 0 && (
            <SummaryRow label="Completion tokens" value={formatTokens(ct)} />
          )}
          {run.failureReason && (
            <SummaryRow
              label="Failure reason"
              value={
                <span style={{ color: "#DC2626" }}>{run.failureReason}</span>
              }
            />
          )}
        </SummaryTable>
        {onDrillIn && run.subWorkflowId && (
          <Box sx={{ px: 2, py: 1 }}>
            <Box
              onClick={() => onDrillIn(run)}
              sx={{
                display: "inline-flex",
                alignItems: "center",
                gap: 0.75,
                px: 1.5,
                py: 0.75,
                borderRadius: 1,
                cursor: "pointer",
                fontSize: "0.8rem",
                fontWeight: 500,
                color: "#fff",
                backgroundColor: "#4969e4",
                transition: "all 0.15s",
                "&:hover": { backgroundColor: "#3858d6" },
              }}
            >
              View full execution <ArrowRight size={14} />
            </Box>
          </Box>
        )}
        {defLoading && !effectiveAgentDef && (
          <Box
            sx={{ px: 3, py: 1.5, color: "text.disabled", fontSize: "0.75rem" }}
          >
            Loading agent definition…
          </Box>
        )}
        {effectiveAgentDef && <AgentDefSection agentDef={effectiveAgentDef} />}
      </Box>
    );
  }

  // output / error / fallback
  return (
    <Box>
      <SummaryTable>
        <SummaryRow label="Kind" value={node.kind} />
        <SummaryRow
          label="Status"
          value={<StatusBadgeInline status={node.status} />}
        />
        {ev?.summary && <SummaryRow label="Summary" value={ev.summary} />}
      </SummaryTable>
    </Box>
  );
}

// ─── Resolve input value for a node ─────────────────────────────────────────

function resolveInput(node: DetailNodeData): unknown {
  if (node.kind === "start" && node.subAgentRun) return node.subAgentRun.input;
  if (node.kind === "subagent" && node.subAgentRun)
    return node.subAgentRun.input;
  const detail = node.event?.detail as any;
  if (detail && typeof detail === "object" && "input" in detail)
    return detail.input;
  // Fallback: toolArgs is always the raw input for TOOL_CALL events
  if (node.event?.toolArgs != null) return node.event.toolArgs;
  return null;
}

function resolveOutput(node: DetailNodeData): unknown {
  if (node.kind === "start" && node.subAgentRun) return node.subAgentRun.output;
  if (node.kind === "subagent" && node.subAgentRun)
    return node.subAgentRun.output;
  const detail = node.event?.detail as any;
  if (detail && typeof detail === "object" && "output" in detail)
    return detail.output;
  // DONE and MESSAGE both carry the text content directly as detail
  if (
    node.event?.type === EventType.DONE ||
    node.event?.type === EventType.MESSAGE
  )
    return detail;
  // Fallback: result field carries tool output
  if (node.event?.result != null) return node.event.result;
  return null;
}

function resolveJsonData(node: DetailNodeData): unknown {
  if (node.kind === "start" && node.subAgentRun) {
    const r = node.subAgentRun;
    return {
      agentName: r.agentName,
      model: r.model,
      status: r.status,
      tokens: r.totalTokens,
      durationMs: r.totalDurationMs,
      finishReason: r.finishReason,
      input: r.input,
      output: r.output,
    };
  }
  if (node.kind === "subagent" && node.subAgentRun) {
    const r = node.subAgentRun;
    return {
      agentName: r.agentName,
      model: r.model,
      status: r.status,
      tokens: r.totalTokens,
      durationMs: r.totalDurationMs,
      finishReason: r.finishReason,
      input: r.input,
      output: r.output,
    };
  }
  // Return the full raw event object — no key extraction (Output tab does that)
  return node.event ?? null;
}

// ─── Main panel ───────────────────────────────────────────────────────────────

/** Build a DetailNodeData with taskMeta overridden from a past attempt */
function buildAttemptNode(
  node: DetailNodeData,
  attempt: TaskAttempt,
): DetailNodeData {
  const statusMap: Record<string, AgentStatus> = {
    COMPLETED: AgentStatus.COMPLETED,
    FAILED: AgentStatus.FAILED,
    TIMED_OUT: AgentStatus.FAILED,
    IN_PROGRESS: AgentStatus.RUNNING,
  };
  return {
    ...node,
    status: statusMap[attempt.status] ?? AgentStatus.RUNNING,
    event: node.event
      ? {
          ...node.event,
          durationMs: attempt.durationMs,
          success: attempt.status === "COMPLETED",
          taskMeta: {
            ...node.event.taskMeta,
            taskId: attempt.taskId,
            retryCount: attempt.retryCount,
            startTime: attempt.startTime,
            endTime: attempt.endTime,
            workerId: attempt.workerId,
            reasonForIncompletion: attempt.reasonForIncompletion,
          },
        }
      : undefined,
  };
}

interface AgentDetailPanelProps {
  node: DetailNodeData;
  onClose: () => void;
  onDrillIn?: (run: AgentRunData) => void;
}

const KIND_DISPLAY: Record<DetailNodeData["kind"], string> = {
  start: "Agent",
  subagent: "Sub-agent",
  llm: "LLM Call",
  tool: "Tool Call",
  handoff: "Handoff",
  output: "Output",
  error: "Error",
  group: "Parallel Group",
};

export function AgentDetailPanel({
  node,
  onClose,
  onDrillIn,
}: AgentDetailPanelProps) {
  const [tab, setTab] = useState(SUMMARY_TAB);
  const allAttempts = node.event?.taskMeta?.allAttempts;
  const hasMultipleAttempts = allAttempts != null && allAttempts.length > 1;
  // Default to the latest attempt (last in the array, which is sorted ascending by retryCount)
  const [selectedAttemptIdx, setSelectedAttemptIdx] = useState(
    hasMultipleAttempts ? allAttempts.length - 1 : 0,
  );
  // Must be declared before any early returns to satisfy the Rules of Hooks
  const prevNodeId = useRef(node.label + node.kind);

  // Reset tab to summary and attempt selection when the non-group node changes
  if (node.kind !== "group" && prevNodeId.current !== node.label + node.kind) {
    prevNodeId.current = node.label + node.kind;
    setTab(SUMMARY_TAB);
    setSelectedAttemptIdx(hasMultipleAttempts ? allAttempts.length - 1 : 0);
  }

  // Group nodes get their own dedicated layout (no tabs)
  if (node.kind === "group") {
    return (
      <Paper
        square
        elevation={0}
        sx={{
          height: "100%",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          borderLeft: "1px solid",
          borderColor: "divider",
          backgroundColor: "#fff",
        }}
      >
        <Box
          sx={{
            px: 2.5,
            pt: 2,
            pb: 1.5,
            borderBottom: "1px solid",
            borderColor: "divider",
            flexShrink: 0,
          }}
        >
          <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
            <IconButton
              size="small"
              onClick={onClose}
              sx={{
                width: 28,
                height: 28,
                color: "text.primary",
                flexShrink: 0,
                mt: 0.25,
              }}
            >
              <CloseIcon size={16} />
            </IconButton>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  gap: 1,
                  flexWrap: "wrap",
                  mb: 0.5,
                }}
              >
                <Typography
                  sx={{
                    fontWeight: 700,
                    fontSize: "1rem",
                    lineHeight: 1.3,
                    color: "text.primary",
                    wordBreak: "break-word",
                  }}
                >
                  {node.label}
                </Typography>
                <StatusChip status={node.status} />
              </Box>
              <Typography
                sx={{
                  fontSize: "0.7rem",
                  color: "text.secondary",
                  fontWeight: 600,
                  letterSpacing: "0.04em",
                  textTransform: "uppercase",
                }}
              >
                {node.groupType === "agents"
                  ? "Parallel Agents"
                  : "Parallel Tool Calls"}
              </Typography>
            </Box>
          </Box>
        </Box>
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            overflow: "hidden",
            display: "flex",
            flexDirection: "column",
          }}
        >
          <GroupDetailPanel node={node} onDrillIn={onDrillIn} />
        </Box>
      </Paper>
    );
  }

  // When viewing a past attempt, override input/output with that attempt's data
  const selectedAttempt: TaskAttempt | null =
    hasMultipleAttempts && selectedAttemptIdx < allAttempts.length - 1
      ? allAttempts[selectedAttemptIdx]
      : null;

  const inputValue = selectedAttempt
    ? (selectedAttempt.inputData ?? null)
    : resolveInput(node);
  const outputValue = selectedAttempt
    ? selectedAttempt.status === "FAILED"
      ? (selectedAttempt.reasonForIncompletion ?? null)
      : (selectedAttempt.outputData ?? null)
    : resolveOutput(node);
  const jsonData = selectedAttempt
    ? { ...selectedAttempt }
    : resolveJsonData(node);

  const isAgentNode = node.kind === "start" || node.kind === "subagent";
  const hasInput = inputValue != null;
  const hasOutput = outputValue != null || isAgentNode;

  return (
    <Paper
      square
      elevation={0}
      sx={{
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        borderLeft: "1px solid",
        borderColor: "divider",
        backgroundColor: "#fff",
      }}
    >
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <Box
        sx={{
          px: 2.5,
          pt: 2,
          pb: 1.5,
          borderBottom: "1px solid",
          borderColor: "divider",
          flexShrink: 0,
          backgroundColor: "#fff",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
          <IconButton
            size="small"
            onClick={onClose}
            sx={{
              width: 28,
              height: 28,
              color: "text.primary",
              flexShrink: 0,
              mt: 0.25,
            }}
          >
            <CloseIcon size={16} />
          </IconButton>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            {/* Name + status badge inline */}
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1,
                flexWrap: "wrap",
                mb: 0.5,
              }}
            >
              <Typography
                sx={{
                  fontWeight: 700,
                  fontSize: "1rem",
                  lineHeight: 1.3,
                  color: "text.primary",
                  wordBreak: "break-word",
                }}
              >
                {node.label}
              </Typography>
              <StatusChip status={node.status} />
            </Box>
            {/* Kind label below */}
            <Typography
              sx={{
                fontSize: "0.7rem",
                color: "text.secondary",
                fontWeight: 600,
                letterSpacing: "0.04em",
                textTransform: "uppercase",
              }}
            >
              {KIND_DISPLAY[node.kind]}
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* ── Attempt selector (shown when task has multiple retry attempts) ── */}
      {hasMultipleAttempts && (
        <Box
          sx={{
            px: 2.5,
            py: 1,
            borderBottom: "1px solid",
            borderColor: "divider",
            display: "flex",
            alignItems: "center",
            gap: 1.5,
            backgroundColor: "#fffbeb",
            flexShrink: 0,
          }}
        >
          <Typography
            sx={{
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "#92400e",
              whiteSpace: "nowrap",
            }}
          >
            Attempt
          </Typography>
          <Select
            size="small"
            value={selectedAttemptIdx}
            onChange={(e) => setSelectedAttemptIdx(Number(e.target.value))}
            sx={{
              fontSize: "0.75rem",
              height: 28,
              "& .MuiSelect-select": { py: 0.25, px: 1 },
              minWidth: 200,
            }}
          >
            {allAttempts.map((attempt, idx) => {
              const statusEmoji =
                attempt.status === "COMPLETED"
                  ? "\u2705"
                  : attempt.status === "FAILED" ||
                      attempt.status === "TIMED_OUT"
                    ? "\u274C"
                    : attempt.status === "IN_PROGRESS"
                      ? "\u23F3"
                      : "\u2022";
              const isLatest = idx === allAttempts.length - 1;
              return (
                <MenuItem
                  key={attempt.taskId}
                  value={idx}
                  sx={{ fontSize: "0.75rem" }}
                >
                  {statusEmoji} Attempt #{attempt.retryCount + 1}
                  {isLatest ? " (latest)" : ""}
                  {" \u2014 "}
                  {attempt.taskId.slice(0, 8)}
                </MenuItem>
              );
            })}
          </Select>
        </Box>
      )}

      {/* ── Tabs ──────────────────────────────────────────────────────── */}
      <Box sx={{ flexShrink: 0 }}>
        <Tabs
          value={tab}
          contextual
          variant="scrollable"
          scrollButtons="auto"
          style={{ marginBottom: 0 }}
        >
          {[
            <Tab
              key="summary"
              label="Summary"
              value={SUMMARY_TAB}
              onClick={() => setTab(SUMMARY_TAB)}
            />,
            hasInput ? (
              <Tab
                key="input"
                label="Input"
                value={INPUT_TAB}
                onClick={() => setTab(INPUT_TAB)}
              />
            ) : null,
            hasOutput ? (
              <Tab
                key="output"
                label="Output"
                value={OUTPUT_TAB}
                onClick={() => setTab(OUTPUT_TAB)}
              />
            ) : null,
            <Tab
              key="json"
              label="JSON"
              value={JSON_TAB}
              onClick={() => setTab(JSON_TAB)}
            />,
          ].filter(Boolean)}
        </Tabs>
      </Box>

      {/* ── Content ───────────────────────────────────────────────────── */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          display: tab === SUMMARY_TAB ? "block" : "flex",
          flexDirection: "column",
          overflowY: tab === SUMMARY_TAB ? "auto" : "hidden",
          scrollbarWidth: "none",
          "&::-webkit-scrollbar": { display: "none" },
        }}
      >
        {tab === SUMMARY_TAB && (
          <SummaryContent
            node={
              selectedAttempt ? buildAttemptNode(node, selectedAttempt) : node
            }
            onDrillIn={onDrillIn}
          />
        )}
        {tab === INPUT_TAB && (
          <>
            <Box
              sx={{
                px: 2.5,
                py: 1.5,
                borderBottom: "1px solid",
                borderColor: "divider",
                flexShrink: 0,
              }}
            >
              <Typography sx={{ fontWeight: 700, fontSize: "0.875rem" }}>
                Task input
              </Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0 }}>
              {inputValue == null ? (
                <Typography variant="body2" color="text.disabled" sx={{ p: 2 }}>
                  No input
                </Typography>
              ) : typeof inputValue === "string" ? (
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    p: 2,
                    fontFamily: "monospace",
                    fontSize: "0.8rem",
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                    overflowY: "auto",
                    height: "100%",
                  }}
                >
                  {inputValue}
                </Box>
              ) : (
                <JsonView src={inputValue} />
              )}
            </Box>
          </>
        )}
        {tab === OUTPUT_TAB && (
          <>
            <Box
              sx={{
                px: 2.5,
                py: 1.5,
                borderBottom: "1px solid",
                borderColor: "divider",
                flexShrink: 0,
              }}
            >
              <Typography sx={{ fontWeight: 700, fontSize: "0.875rem" }}>
                Task output
              </Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0 }}>
              {outputValue == null ? (
                <Typography variant="body2" color="text.disabled" sx={{ p: 2 }}>
                  No output
                </Typography>
              ) : typeof outputValue === "string" ? (
                <Box
                  component="pre"
                  sx={{
                    m: 0,
                    p: 2,
                    fontFamily: "monospace",
                    fontSize: "0.8rem",
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                    overflowY: "auto",
                    height: "100%",
                  }}
                >
                  {outputValue}
                </Box>
              ) : (
                <JsonView src={outputValue} />
              )}
            </Box>
          </>
        )}
        {tab === JSON_TAB && (
          <>
            <Box
              sx={{
                px: 2.5,
                py: 1.5,
                borderBottom: "1px solid",
                borderColor: "divider",
                flexShrink: 0,
              }}
            >
              <Typography sx={{ fontWeight: 700, fontSize: "0.875rem" }}>
                Task Execution JSON
              </Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0 }}>
              <JsonView src={jsonData} />
            </Box>
          </>
        )}
      </Box>
    </Paper>
  );
}

export default AgentDetailPanel;
