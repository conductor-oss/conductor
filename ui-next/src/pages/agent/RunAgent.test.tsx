import { act, fireEvent, render, screen } from "@testing-library/react";
import RunAgent from "./RunAgent";

const navigate = vi.fn();
const startAgent = vi.fn();
const setAgentHistory = vi.fn();
const useFetch = vi.hoisted(() => vi.fn());
const useAiModelOptions = vi.hoisted(() => vi.fn());
const useLocalStorage = vi.hoisted(() => vi.fn());
const locationState = vi.hoisted(() => ({
  current: null as { agentName?: string; agentVersion?: number } | null,
}));
// Lets the tests drive onError, which is where the start failure is reported.
const actionOptions = vi.hoisted(() => ({
  current: null as { onError: (response: unknown) => Promise<void> } | null,
}));

vi.mock("react-router", () => ({
  useNavigate: () => navigate,
  useLocation: () => ({ state: locationState.current }),
}));

vi.mock("utils/query", () => ({
  useFetch: (...args: unknown[]) => useFetch(...args),
  useAction: (_path: unknown, _method: unknown, options: any) => {
    actionOptions.current = options;
    return { mutate: startAgent, isLoading: false };
  },
}));

vi.mock("utils", () => ({
  useLocalStorage: (...args: unknown[]) => useLocalStorage(...args),
}));

vi.mock("./hooks/useAiModelOptions", () => ({
  useAiModelOptions: (...args: unknown[]) => useAiModelOptions(...args),
}));

vi.mock("uuid", () => ({
  v4: () => "history-id-1",
}));

vi.mock("react-helmet", () => ({
  Helmet: ({ children }: any) => <>{children}</>,
}));

vi.mock("@mui/material", () => ({
  Alert: ({ children }: any) => <div>{children}</div>,
  Box: ({ children }: any) => <div>{children}</div>,
  Grid: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("components", () => ({
  Button: ({ children, onClick, disabled, id }: any) => (
    <button id={id} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  ),
  DataTable: ({ columns, data, noDataComponent, actions }: any) => (
    <div>
      {actions}
      {(!data || data.length === 0) && noDataComponent}
      {(data || []).map((row: any) => (
        <div key={row.id}>
          {columns.map((column: any) => (
            <div key={column.id}>
              {column.renderer
                ? column.renderer(row[column.name], row)
                : row[column.name]}
            </div>
          ))}
        </div>
      ))}
    </div>
  ),
  NavLink: ({ children, path }: any) => <a href={path}>{children}</a>,
  Paper: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("components/layout/SectionHeader", () => ({
  default: ({ title, actions }: any) => (
    <div>
      <h1>{title}</h1>
      {actions}
    </div>
  ),
}));

vi.mock("components/ui/layout/SectionContainer", () => ({
  default: ({ header, children }: any) => (
    <div>
      {header}
      {children}
    </div>
  ),
}));

vi.mock("components/icons/PlayIcon", () => ({ default: () => null }));
vi.mock("components/icons/ResetIcon", () => ({ default: () => null }));
vi.mock("components/icons/XCloseIcon", () => ({ default: () => null }));

vi.mock("components/ui/inputs/ConductorAutoComplete", () => ({
  ConductorAutoComplete: ({
    id,
    label,
    value,
    options,
    helperText,
    onChange,
    onInputChange,
  }: any) => (
    <div>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        aria-label={label}
        value={value ?? ""}
        data-options={JSON.stringify(options ?? [])}
        data-helper-text={helperText ?? ""}
        onChange={(event) => {
          onInputChange?.(event, event.target.value);
          onChange?.(event, event.target.value);
        }}
      />
      {helperText ? <span>{helperText}</span> : null}
    </div>
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ id, label, value, onTextInputChange }: any) => (
    <div>
      <label htmlFor={id}>{label}</label>
      <textarea
        id={id}
        aria-label={label}
        value={value ?? ""}
        onChange={(event) => onTextInputChange?.(event.target.value)}
      />
    </div>
  ),
}));

describe("RunAgent", () => {
  beforeEach(() => {
    navigate.mockReset();
    startAgent.mockReset();
    setAgentHistory.mockReset();
    useFetch.mockReset();
    useAiModelOptions.mockReset();
    useLocalStorage.mockReset();
    locationState.current = null;

    useLocalStorage.mockReturnValue([[], setAgentHistory]);
    useAiModelOptions.mockReturnValue([]);
    useFetch.mockReturnValue({ data: [{ name: "researcher", version: 2 }] });
  });

  it("shows the plain model helper text when no AI models are configured", () => {
    useAiModelOptions.mockReturnValue([]);

    render(<RunAgent />);

    const modelField = screen.getByLabelText("Model override (optional)");
    expect(modelField).toHaveAttribute("data-options", "[]");
    expect(
      screen.getByText(
        "Use the format provider/model, e.g. openai/gpt-4o. Applies to this execution only.",
      ),
    ).toBeInTheDocument();
  });

  describe("when the start request fails", () => {
    const fail = async (body: unknown) => {
      render(<RunAgent />);
      await act(async () => {
        await actionOptions.current?.onError({
          json: async () => {
            if (body instanceof Error) throw body;
            return body;
          },
        });
      });
    };

    it("reports the reason /agent/start gives, which it puts in `error`", async () => {
      await fail({
        error: "Invalid model format: 'gpt-4o'. Expected 'provider/model'.",
        status: 400,
      });

      expect(
        screen.getByText(
          "Invalid model format: 'gpt-4o'. Expected 'provider/model'.",
        ),
      ).toBeInTheDocument();
    });

    it("prefers `message` where the rest of the API keeps `error` for a code", async () => {
      await fail({
        message: "Token cannot be null or empty",
        error: "INVALID_TOKEN",
      });

      expect(
        screen.getByText("Token cannot be null or empty"),
      ).toBeInTheDocument();
      expect(screen.queryByText("INVALID_TOKEN")).not.toBeInTheDocument();
    });

    it("falls back when the body carries no reason", async () => {
      await fail({ status: 500 });

      expect(screen.getByText("Unable to start agent.")).toBeInTheDocument();
    });

    it("falls back when the body is not JSON", async () => {
      await fail(new Error("not json"));

      expect(screen.getByText("Unable to start agent.")).toBeInTheDocument();
    });
  });

  it("loads sorted provider/model options into the model override autocomplete", () => {
    useAiModelOptions.mockReturnValue([
      "anthropic/claude-sonnet",
      "openai/gpt-4o",
      "openai/gpt-5",
    ]);

    render(<RunAgent />);

    const modelField = screen.getByLabelText("Model override (optional)");
    expect(JSON.parse(modelField.getAttribute("data-options") ?? "[]")).toEqual(
      ["anthropic/claude-sonnet", "openai/gpt-4o", "openai/gpt-5"],
    );
    expect(
      screen.getByText(
        "Use the format provider/model, e.g. openai/gpt-4o. Applies to this execution only.",
      ),
    ).toBeInTheDocument();
  });

  it("preselects the agent from location state", () => {
    locationState.current = { agentName: "researcher", agentVersion: 2 };

    render(<RunAgent />);

    expect(screen.getByLabelText("Agent")).toHaveValue("researcher");
  });

  it("disables Run agent until both agent and prompt are set", () => {
    render(<RunAgent />);

    expect(screen.getByRole("button", { name: "Run agent" })).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Agent"), {
      target: { value: "researcher" },
    });
    expect(screen.getByRole("button", { name: "Run agent" })).toBeDisabled();

    fireEvent.change(screen.getByLabelText("Input text"), {
      target: { value: "Summarize this doc" },
    });
    expect(screen.getByRole("button", { name: "Run agent" })).toBeEnabled();
  });

  it("starts the agent with a selected model override", () => {
    useAiModelOptions.mockReturnValue(["openai/gpt-5"]);

    render(<RunAgent />);

    fireEvent.change(screen.getByLabelText("Agent"), {
      target: { value: "researcher" },
    });
    fireEvent.change(screen.getByLabelText("Model override (optional)"), {
      target: { value: "openai/gpt-5" },
    });
    fireEvent.change(screen.getByLabelText("Input text"), {
      target: { value: "Find citations" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Run agent" }));

    expect(startAgent).toHaveBeenCalledWith({
      body: JSON.stringify({
        name: "researcher",
        version: undefined,
        model: "openai/gpt-5",
        prompt: "Find citations",
      }),
    });
  });

  it("omits blank model from the start payload", () => {
    locationState.current = { agentName: "researcher", agentVersion: 3 };

    render(<RunAgent />);

    fireEvent.change(screen.getByLabelText("Input text"), {
      target: { value: "  go  " },
    });
    fireEvent.change(screen.getByLabelText("Model override (optional)"), {
      target: { value: "   " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Run agent" }));

    expect(startAgent).toHaveBeenCalledWith({
      body: JSON.stringify({
        name: "researcher",
        version: 3,
        model: undefined,
        prompt: "  go  ",
      }),
    });
  });

  it("resets agent, model, and prompt fields", () => {
    locationState.current = { agentName: "researcher", agentVersion: 2 };

    render(<RunAgent />);

    fireEvent.change(screen.getByLabelText("Model override (optional)"), {
      target: { value: "openai/gpt-5" },
    });
    fireEvent.change(screen.getByLabelText("Input text"), {
      target: { value: "hello" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Reset" }));

    expect(screen.getByLabelText("Agent")).toHaveValue("");
    expect(screen.getByLabelText("Model override (optional)")).toHaveValue("");
    expect(screen.getByLabelText("Input text")).toHaveValue("");
    expect(screen.getByRole("button", { name: "Run agent" })).toBeDisabled();
  });

  it("restores agent, model, and prompt from run history", () => {
    useLocalStorage.mockReturnValue([
      [
        {
          id: "h1",
          agentName: "summarizer",
          model: "anthropic/claude-sonnet",
          prompt: "Summarize yesterday",
          executionId: "exec-1",
          executionTime: 1_700_000_000_000,
        },
      ],
      setAgentHistory,
    ]);

    render(<RunAgent />);

    fireEvent.click(screen.getByRole("button", { name: "Reuse" }));

    expect(screen.getByLabelText("Agent")).toHaveValue("summarizer");
    expect(screen.getByLabelText("Model override (optional)")).toHaveValue(
      "anthropic/claude-sonnet",
    );
    expect(screen.getByLabelText("Input text")).toHaveValue(
      "Summarize yesterday",
    );
  });
});
