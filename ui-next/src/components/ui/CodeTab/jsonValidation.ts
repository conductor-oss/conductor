export type ProblemSeverity = "error" | "warn" | "info";

export type Problem = {
  sev: ProblemSeverity;
  /** 1-based line the problem is anchored to. */
  line: number;
  msg: string;
};

export type ValidationResult<T = Record<string, any>> = {
  /** The parsed document, or null when the text is not valid JSON. */
  parsed: T | null;
  problems: Problem[];
};

export type JsonValidator<T = Record<string, any>> = (
  text: string,
) => ValidationResult<T>;

export type ValidationStatus = {
  kind: "invalid" | "error" | "warn" | "valid";
  label: string;
};

export type CheckContext = {
  /**
   * 1-based line of the first line mentioning `"key"`, or 1 when none does.
   * Coarse, but good enough for gutter markers without a full JSON AST.
   */
  lineOf: (key: string) => number;
  /** Shorthand for pushing a problem anchored with `lineOf(key)`. */
  report: (sev: ProblemSeverity, key: string, msg: string) => void;
};

const parseErrorLine = (text: string, message: string) => {
  const lineMatch = /line (\d+)/.exec(message);
  if (lineMatch) return Number(lineMatch[1]);
  const posMatch = /position (\d+)/.exec(message);
  if (posMatch) return text.slice(0, Number(posMatch[1])).split("\n").length;
  return 1;
};

/** Parses JSON, turning a syntax error into a single anchored problem. */
export const parseJson = (text: string): ValidationResult<unknown> => {
  try {
    return { parsed: JSON.parse(text), problems: [] };
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    return {
      parsed: null,
      problems: [
        {
          sev: "error",
          line: parseErrorLine(text, message),
          msg:
            "Invalid JSON — " +
            message.replace(/^.*?:\s*/, "").replace(/ in JSON.*$/, ""),
        },
      ],
    };
  }
};

/**
 * Builds a Code tab validator: parses the text, requires a top-level object,
 * then hands it to `check` to report domain problems. Pass no `check` for a
 * plain "is this a JSON object" validator.
 */
export const createJsonValidator =
  <T extends Record<string, any> = Record<string, any>>(
    check?: (data: T, ctx: CheckContext) => void,
    { documentName = "Document" }: { documentName?: string } = {},
  ): JsonValidator<T> =>
  (text) => {
    const result = parseJson(text);
    if (!result.parsed && result.problems.length) {
      return { parsed: null, problems: result.problems };
    }
    const data = result.parsed;
    if (data === null || typeof data !== "object" || Array.isArray(data)) {
      return {
        parsed: null,
        problems: [
          {
            sev: "error",
            line: 1,
            msg: `${documentName} must be a JSON object.`,
          },
        ],
      };
    }

    const lines = text.split("\n");
    const lineOf = (key: string) => {
      const i = lines.findIndex((l) => l.includes(`"${key}"`));
      return i < 0 ? 1 : i + 1;
    };
    const problems: Problem[] = [];
    check?.(data as T, {
      lineOf,
      report: (sev, key, msg) => problems.push({ sev, line: lineOf(key), msg }),
    });
    return { parsed: data as T, problems };
  };

export const summarizeProblems = ({
  parsed,
  problems,
}: ValidationResult<unknown>): ValidationStatus => {
  const errors = problems.filter((p) => p.sev === "error").length;
  const warns = problems.filter((p) => p.sev === "warn").length;
  const plural = (n: number, word: string) => `${n} ${word}${n > 1 ? "s" : ""}`;

  if (!parsed) return { kind: "invalid", label: "Invalid JSON" };
  if (errors) {
    return {
      kind: "error",
      label:
        plural(errors, "error") +
        (warns ? `, ${plural(warns, "warning")}` : ""),
    };
  }
  if (warns) return { kind: "warn", label: plural(warns, "warning") };
  return { kind: "valid", label: "Valid" };
};
