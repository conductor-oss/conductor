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
import { AgentDefinitionDetails } from "components/features/agents/AgentSnapshotDetails";
import Editor from "@monaco-editor/react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  EventType,
  TaskAttempt,
} from "./types";
import {
  formatTokens,
  formatDuration,
  getModelIconPath,
  transformWorkflowExecutionToAgentRun,
} from "./agentExecutionUtils";
import { WorkflowExecution } from "types/Execution";

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
  strategy?: AgentStrategy;
}

// ─── Tab keys ────────────────────────────────────────────────────────────────

const SUMMARY_TAB = "summary";
const INPUT_TAB = "input";
const OUTPUT_TAB = "output";
const JSON_TAB = "json";
const FORMATTED_OUTPUT_TAB = "formatted";
const RAW_OUTPUT_TAB = "raw";

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

function formattedOutput(value: unknown): unknown {
  if (typeof value !== "object" || value == null || Array.isArray(value)) {
    return value;
  }
  const record = value as Record<string, unknown>;
  for (const key of ["result", "output", "answer", "message", "content"]) {
    if (typeof record[key] === "string") return record[key];
  }
  return value;
}

/** Keeps human-readable Markdown/text and the exact execution payload together. */
function OutputView({ value }: { value: unknown }) {
  const [view, setView] = useState(FORMATTED_OUTPUT_TAB);
  return (
    <Box
      sx={{ display: "flex", flex: 1, minHeight: 0, flexDirection: "column" }}
    >
      <Tabs value={view} contextual style={{ marginBottom: 0 }}>
        <Tab
          label="Formatted"
          value={FORMATTED_OUTPUT_TAB}
          onClick={() => setView(FORMATTED_OUTPUT_TAB)}
        />
        <Tab
          label="JSON"
          value={RAW_OUTPUT_TAB}
          onClick={() => setView(RAW_OUTPUT_TAB)}
        />
      </Tabs>
      <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto", p: 2 }}>
        {view === FORMATTED_OUTPUT_TAB ? (
          <ContentView
            value={formattedOutput(value)}
            label="output captured for this execution"
          />
        ) : value == null ? (
          <ContentView
            value={value}
            label="output captured for this execution"
          />
        ) : (
          <Box sx={{ height: 400 }}>
            <JsonView src={value} />
          </Box>
        )}
      </Box>
    </Box>
  );
}

interface FetchedAgentExecution {
  run: AgentRunData;
  rawExecution: WorkflowExecution;
}

const fetchedAgentExecutions = new Map<string, FetchedAgentExecution | null>();

function useAgentExecutionDetail(run: AgentRunData): {
  data: FetchedAgentExecution | null;
  loading: boolean;
} {
  const executionId = run.subWorkflowId;
  const [data, setData] = useState<FetchedAgentExecution | null>(
    executionId ? (fetchedAgentExecutions.get(executionId) ?? null) : null,
  );
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!executionId) {
      setData(null);
      setLoading(false);
      return;
    }
    if (fetchedAgentExecutions.has(executionId)) {
      setData(fetchedAgentExecutions.get(executionId) ?? null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setData(null);
    setLoading(true);
    fetch(`/api/agent/executions/${executionId}/full`)
      .then((response) => (response.ok ? response.json() : null))
      .then((rawExecution: WorkflowExecution | null) => {
        if (cancelled) return;
        const detail = rawExecution
          ? {
              run: transformWorkflowExecutionToAgentRun(rawExecution),
              rawExecution,
            }
          : null;
        fetchedAgentExecutions.set(executionId, detail);
        setData(detail);
      })
      .catch(() => {
        if (cancelled) return;
        fetchedAgentExecutions.set(executionId, null);
        setData(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [executionId]);

  return { data, loading };
}

function AgentInspector({
  run,
  invocationStrategy,
  onDrillIn,
}: {
  run: AgentRunData;
  invocationStrategy?: AgentStrategy;
  onDrillIn?: (run: AgentRunData) => void;
}) {
  const [tab, setTab] = useState(SUMMARY_TAB);
  const { data: fetched, loading } = useAgentExecutionDetail(run);
  const effectiveRun = fetched?.run ?? run;
  const input = effectiveRun.input ?? run.input;
  const output = effectiveRun.output ?? run.output;
  const definition = effectiveRun.agentDef ?? run.agentDef;
  const agentType = effectiveRun.agentType ?? run.agentType ?? "Unavailable";
  const strategy =
    (definition?.strategy as AgentStrategy | undefined) ??
    effectiveRun.strategy;
  const orchestratedBy = invocationStrategy ?? run.invocationStrategy;
  const promptTokens = effectiveRun.totalTokens.promptTokens;
  const completionTokens = effectiveRun.totalTokens.completionTokens;
  const rawJson = fetched?.rawExecution ?? {
    agentName: effectiveRun.agentName,
    agentType,
    strategy,
    status: effectiveRun.status,
    model: effectiveRun.model,
    input,
    output,
    agentDef: definition,
  };

  return (
    <Box
      sx={{ display: "flex", minHeight: 0, flex: 1, flexDirection: "column" }}
    >
      <Tabs
        value={tab}
        contextual
        variant="scrollable"
        scrollButtons="auto"
        style={{ marginBottom: 0 }}
      >
        <Tab
          label="Summary"
          value={SUMMARY_TAB}
          onClick={() => setTab(SUMMARY_TAB)}
        />
        <Tab
          label="Input"
          value={INPUT_TAB}
          onClick={() => setTab(INPUT_TAB)}
        />
        <Tab
          label="Output"
          value={OUTPUT_TAB}
          onClick={() => setTab(OUTPUT_TAB)}
        />
        <Tab
          label="Configuration"
          value="configuration"
          onClick={() => setTab("configuration")}
        />
        <Tab label="JSON" value={JSON_TAB} onClick={() => setTab(JSON_TAB)} />
      </Tabs>
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY:
            tab === SUMMARY_TAB || tab === "configuration" ? "auto" : "hidden",
          display:
            tab === SUMMARY_TAB || tab === "configuration" ? "block" : "flex",
          flexDirection: "column",
        }}
      >
        {tab === SUMMARY_TAB && (
          <Box>
            {loading && (
              <Typography
                sx={{
                  px: 2,
                  pt: 1.5,
                  fontSize: "0.75rem",
                  color: "text.secondary",
                }}
              >
                Loading full child execution details…
              </Typography>
            )}
            <SummaryTable>
              <SummaryRow label="Name" value={effectiveRun.agentName} />
              <SummaryRow label="Agent type" value={agentType} />
              <SummaryRow label="Strategy" value={strategy ?? "Unavailable"} />
              {orchestratedBy && orchestratedBy !== strategy && (
                <SummaryRow label="Invocation mode" value={orchestratedBy} />
              )}
              <SummaryRow
                label="Model"
                value={effectiveRun.model ?? "Unavailable"}
              />
              <SummaryRow
                label="Status"
                value={<StatusBadgeInline status={effectiveRun.status} />}
              />
              <SummaryRow
                label="Execution ID"
                value={effectiveRun.subWorkflowId ?? effectiveRun.id}
              />
              <SummaryRow
                label="Duration"
                value={formatDuration(effectiveRun.totalDurationMs)}
              />
              <SummaryRow
                label="Turns"
                value={String(effectiveRun.turns.length)}
              />
              <SummaryRow
                label="Prompt tokens"
                value={formatTokens(promptTokens)}
              />
              <SummaryRow
                label="Completion tokens"
                value={formatTokens(completionTokens)}
              />
              <SummaryRow
                label="Total tokens"
                value={formatTokens(effectiveRun.totalTokens.totalTokens)}
              />
              {effectiveRun.finishReason && (
                <SummaryRow
                  label="Finish reason"
                  value={effectiveRun.finishReason.toUpperCase()}
                />
              )}
              {effectiveRun.failureReason && (
                <SummaryRow
                  label="Failure reason"
                  value={
                    <span style={{ color: "#DC2626" }}>
                      {effectiveRun.failureReason}
                    </span>
                  }
                />
              )}
            </SummaryTable>
            {onDrillIn && effectiveRun.subWorkflowId && (
              <Box sx={{ px: 2, pb: 1 }}>
                <Box
                  onClick={() => onDrillIn(effectiveRun)}
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
        {tab === INPUT_TAB && (
          <Box sx={{ flex: 1, minHeight: 0, p: 2, overflowY: "auto" }}>
            <ContentView
              value={input}
              label="input captured for this execution"
            />
          </Box>
        )}
        {tab === OUTPUT_TAB && <OutputView value={output} />}
        {tab === "configuration" && (
          <Box sx={{ p: 2 }}>
            {loading && !definition ? (
              <Typography color="text.disabled" sx={{ fontSize: "0.875rem" }}>
                Loading agent configuration…
              </Typography>
            ) : definition ? (
              <AgentDefinitionDetails
                agentDef={definition}
                title="Agent configuration"
              />
            ) : (
              <Typography color="text.disabled" sx={{ fontSize: "0.875rem" }}>
                No agent configuration captured for this execution.
              </Typography>
            )}
          </Box>
        )}
        {tab === JSON_TAB && (
          <Box sx={{ flex: 1, minHeight: 0 }}>
            <JsonView src={rawJson} />
          </Box>
        )}
      </Box>
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
          <AgentInspector
            run={selAgent}
            invocationStrategy={node.strategy}
            onDrillIn={onDrillIn}
          />
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
        {effectiveAgentDef && (
          <Box sx={{ px: 2, py: 1 }}>
            <AgentDefinitionDetails agentDef={effectiveAgentDef} />
          </Box>
        )}
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
        {effectiveAgentDef && (
          <Box sx={{ px: 2, py: 1 }}>
            <AgentDefinitionDetails agentDef={effectiveAgentDef} />
          </Box>
        )}
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

  // Agent identity nodes share the same inspector whether they are the root
  // start node or a direct sub-agent node. Parallel groups render this same
  // inspector for their selected child above.
  if ((node.kind === "start" || node.kind === "subagent") && node.subAgentRun) {
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
              sx={{ width: 28, height: 28, mt: 0.25 }}
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
                    wordBreak: "break-word",
                  }}
                >
                  {node.subAgentRun.agentName}
                </Typography>
                <StatusChip status={node.subAgentRun.status} />
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
                {KIND_DISPLAY[node.kind]}
              </Typography>
            </Box>
          </Box>
        </Box>
        <AgentInspector
          run={node.subAgentRun}
          invocationStrategy={node.strategy}
          onDrillIn={onDrillIn}
        />
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
