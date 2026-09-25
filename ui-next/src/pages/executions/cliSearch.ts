import type { BuildQueryOutput } from "./ApiSearchModalIntegration";

const shellQuote = (value: string) => `'${value.replaceAll("'", `'\\''`)}'`;

const formatCommand = (parts: string[]) => parts.join(" ");

const parseClauses = (query: string) =>
  query ? query.split(/\s+AND\s+/).map((clause) => clause.trim()) : [];

const addCommonNotes = (
  notes: string[],
  { start, sort }: BuildQueryOutput,
  defaultSort: string,
) => {
  if (start > 0) {
    notes.push(
      `# The CLI starts at offset 0; the UI selection starts at offset ${start}.`,
    );
  }
  if (sort !== defaultSort) {
    notes.push(`# The CLI uses ${defaultSort}; the UI selection uses ${sort}.`);
  }
};

export const buildWorkflowSearchCli = ({
  freeText,
  query,
  size,
  ...pagination
}: BuildQueryOutput) => {
  const args = ["conductor", "workflow", "search", "--count", String(size)];
  const unsupported: string[] = [];
  const notes: string[] = [];

  for (const clause of parseClauses(query)) {
    const workflow = clause.match(/^workflowType(?: IN \((.*)\)|='([^']*)')$/);
    const status = clause.match(/^status IN \((.*)\)$/);
    const startAfter = clause.match(/^startTime>(\d+)$/);
    const startBefore = clause.match(/^startTime<(\d+)$/);

    if (workflow) {
      args.push("--workflow", shellQuote(workflow[1] ?? workflow[2]));
    } else if (status) {
      args.push("--status", shellQuote(status[1]));
    } else if (startAfter) {
      args.push("--start-time-after", startAfter[1]);
    } else if (startBefore) {
      args.push("--start-time-before", startBefore[1]);
    } else {
      unsupported.push(clause);
    }
  }

  args.push("--json");
  if (freeText && freeText !== "*") args.push(shellQuote(freeText));

  addCommonNotes(
    notes,
    { freeText, query, size, ...pagination },
    "startTime:DESC",
  );
  if (unsupported.length) {
    notes.push(
      `# Not yet expressible with conductor workflow search: ${unsupported.join(
        " AND ",
      )}`,
    );
  }

  return [...notes, formatCommand(args)].join("\n");
};

export const buildSchedulerSearchCli = ({
  query,
  size,
  ...search
}: BuildQueryOutput) => {
  const args = ["conductor", "scheduler", "search", "--count", String(size)];
  const unsupported: string[] = [];
  const notes: string[] = [];

  for (const clause of parseClauses(query)) {
    const workflow = clause.match(/^workflowType IN \((.*)\)$/);
    const status = clause.match(/^status IN \((.*)\)$/);

    if (workflow) {
      args.push("--workflow", shellQuote(workflow[1]));
    } else if (status) {
      args.push("--status", shellQuote(status[1]));
    } else {
      unsupported.push(clause);
    }
  }

  addCommonNotes(notes, { query, size, ...search }, "startTime:DESC");
  if (unsupported.length) {
    notes.push(
      `# Not yet expressible with conductor scheduler search: ${unsupported.join(
        " AND ",
      )}`,
    );
  }

  return [...notes, formatCommand(args)].join("\n");
};

export const buildTaskSearchCli = () =>
  [
    "# Task execution search is not available in conductor-cli yet.",
    "# Use the cURL tab for the exact task search request.",
  ].join("\n");
