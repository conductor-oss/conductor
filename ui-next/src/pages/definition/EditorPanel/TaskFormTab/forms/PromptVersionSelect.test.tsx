/**
 * CCOR-13425 — pinning a saved AI Prompt to a version on LLM_CHAT_COMPLETE /
 * LLM_TEXT_COMPLETE.
 *
 * Two rules carry the design. "Latest" is the absence of inputParameters.promptVersion,
 * so definitions written before this field keep their exact shape. And AI_PROMPTS_VERSIONING
 * is a UI-only flag — the server honours promptVersion regardless — so with the flag off the
 * field hides without touching a pin that is already there.
 */
import { fireEvent, render, screen } from "@testing-library/react";
import { TaskDef } from "types";
import { PromptVersionSelect } from "./PromptVersionSelect";

const isEnabled = vi.hoisted(() => vi.fn());
const useFetch = vi.hoisted(() => vi.fn());

vi.mock("utils/flags", () => ({
  FEATURES: new Proxy({}, { get: (_target, key) => String(key) }),
  featureFlags: { isEnabled: (...args: unknown[]) => isEnabled(...args) },
}));

vi.mock("utils/query", () => ({
  useFetch: (...args: unknown[]) => useFetch(...args),
}));

vi.mock("components/ui/inputs/ConductorSelect", () => ({
  default: ({ label, value, items, onChange, disabled }: any) => (
    <select
      aria-label={label}
      value={value}
      data-requested-value={String(value)}
      disabled={disabled}
      onChange={(event) => onChange({ target: { value: event.target.value } })}
    >
      {items.map((item: any) => (
        <option key={String(item.value)} value={item.value}>
          {item.label}
        </option>
      ))}
    </select>
  ),
}));

const task = (inputParameters: Record<string, unknown>): Partial<TaskDef> =>
  ({
    name: "llm_chat_complete",
    taskReferenceName: "llm_chat_complete_ref",
    inputParameters,
  }) as unknown as Partial<TaskDef>;

const renderSelect = (
  inputParameters: Record<string, unknown>,
  promptName = "greeting",
) => {
  const onChange = vi.fn();
  render(
    <PromptVersionSelect
      task={task(inputParameters)}
      onChange={onChange}
      promptName={promptName}
    />,
  );
  return onChange;
};

const versionField = () => screen.queryByLabelText("Version");

describe("PromptVersionSelect", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    isEnabled.mockReturnValue(true);
    useFetch.mockReturnValue({
      data: [
        { version: 1, variables: ["one"] },
        { version: 3, variables: ["three", "shared"] },
        { version: 2, variables: ["two", "shared"] },
      ],
    });
  });

  it("offers Latest plus every version, newest first", () => {
    renderSelect({ instructions: "greeting" });

    const options = Array.from(versionField()!.querySelectorAll("option")).map(
      (option) => option.textContent,
    );
    expect(options).toEqual(["Latest", "3", "2", "1"]);
  });

  it("shows Latest, not a blank field, when nothing is pinned", () => {
    renderSelect({ instructions: "greeting" });

    const field = versionField() as HTMLSelectElement;
    expect(field).toHaveValue("latest");
    expect(field.selectedOptions[0].textContent).toBe("Latest");
  });

  it("writes the pinned version into inputParameters", () => {
    const onChange = renderSelect({ instructions: "greeting" });

    fireEvent.change(versionField()!, { target: { value: "2" } });

    expect(onChange.mock.calls[0][0].inputParameters).toMatchObject({
      instructions: "greeting",
      promptVersion: 2,
    });
  });

  it("stores the version as a number, not the select's string", () => {
    const onChange = renderSelect({ instructions: "greeting" });

    fireEvent.change(versionField()!, { target: { value: "2" } });

    expect(onChange.mock.calls[0][0].inputParameters.promptVersion).toBe(2);
  });

  it("drops the key entirely when Latest is chosen", () => {
    const onChange = renderSelect({
      instructions: "greeting",
      promptVersion: 2,
    });

    fireEvent.change(versionField()!, { target: { value: "latest" } });

    const updated = onChange.mock.calls[0][0];
    expect("promptVersion" in updated.inputParameters).toBe(false);
    expect(updated.inputParameters.instructions).toBe("greeting");
  });

  it("shows the pinned version when the task already has one", () => {
    renderSelect({ instructions: "greeting", promptVersion: 3 });

    expect(versionField()).toHaveValue("3");
  });

  describe("prompt variables follow the pinned version", () => {
    it("replaces them with the chosen version's variables", () => {
      const onChange = renderSelect({
        instructions: "greeting",
        promptVariables: { three: "a", shared: "b" },
      });

      fireEvent.change(versionField()!, { target: { value: "1" } });

      expect(onChange.mock.calls[0][0].inputParameters.promptVariables).toEqual(
        { one: "" },
      );
    });

    it("keeps values already typed for variables the version still declares", () => {
      const onChange = renderSelect({
        instructions: "greeting",
        promptVariables: { three: "keep-me", shared: "also-keep" },
      });

      fireEvent.change(versionField()!, { target: { value: "2" } });

      expect(onChange.mock.calls[0][0].inputParameters.promptVariables).toEqual(
        { two: "", shared: "also-keep" },
      );
    });

    it("restores the latest version's variables when Latest is chosen", () => {
      const onChange = renderSelect({
        instructions: "greeting",
        promptVersion: 1,
        promptVariables: { one: "x" },
      });

      fireEvent.change(versionField()!, { target: { value: "latest" } });

      const updated = onChange.mock.calls[0][0];
      expect("promptVersion" in updated.inputParameters).toBe(false);
      expect(updated.inputParameters.promptVariables).toEqual({
        three: "",
        shared: "",
      });
    });

    it("leaves variables alone when the version declares none", () => {
      useFetch.mockReturnValue({ data: [{ version: 1 }, { version: 2 }] });
      const onChange = renderSelect({
        instructions: "greeting",
        promptVariables: { kept: "value" },
      });

      fireEvent.change(versionField()!, { target: { value: "1" } });

      expect(onChange.mock.calls[0][0].inputParameters.promptVariables).toEqual(
        { kept: "value" },
      );
    });
  });

  describe("when AI_PROMPTS_VERSIONING is off", () => {
    beforeEach(() => isEnabled.mockReturnValue(false));

    it("renders nothing", () => {
      renderSelect({ instructions: "greeting" });

      expect(versionField()).not.toBeInTheDocument();
    });

    it("does not fetch the prompt's versions", () => {
      renderSelect({ instructions: "greeting" });

      expect(useFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ when: false }),
      );
    });

    it("renders nothing at all, so the prompt field can take the full row", () => {
      renderSelect({ instructions: "greeting" });

      expect(versionField()).not.toBeInTheDocument();
    });

    it("leaves an existing pin alone", () => {
      const onChange = renderSelect({
        instructions: "greeting",
        promptVersion: 2,
      });

      expect(versionField()).not.toBeInTheDocument();
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe("without a saved prompt", () => {
    it("stays on screen but blank, so the row does not reflow", () => {
      useFetch.mockReturnValue({ data: undefined });
      renderSelect({ instructions: "you are a helpful assistant" });

      const field = versionField() as HTMLSelectElement;
      expect(field).toBeInTheDocument();
      expect(field).toBeEnabled();
      // Nothing to offer yet, so nothing is shown — not "Latest", not disabled.
      expect(field.querySelectorAll("option")).toHaveLength(0);
      expect(field).toHaveAttribute("data-requested-value", "");
    });

    it("ignores the cleared prompt's cached versions", () => {
      // useFetch keeps previous data; serving it here would leave the old prompt's
      // versions on offer after the field is cleared.
      useFetch.mockReturnValue({
        data: [{ version: 1 }, { version: 2 }],
        isPreviousData: true,
      });
      renderSelect({}, "");

      const field = versionField() as HTMLSelectElement;
      expect(field.querySelectorAll("option")).toHaveLength(0);
      expect(field).toHaveAttribute("data-requested-value", "");
    });

    it("is blank, not Latest, before any prompt has been chosen", () => {
      useFetch.mockReturnValue({ data: undefined });
      renderSelect({}, "");

      const field = versionField() as HTMLSelectElement;
      expect(field).toBeInTheDocument();
      expect(field).toHaveAttribute("data-requested-value", "");
    });

    it("offers Latest as soon as a prompt with versions is referenced", () => {
      const field = () => versionField() as HTMLSelectElement;
      renderSelect({ instructions: "greeting" });

      expect(field()).toHaveValue("latest");
      expect(field().selectedOptions[0].textContent).toBe("Latest");
    });

    it("does not look up versions when nothing is referenced", () => {
      renderSelect({}, "");

      expect(useFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ when: false }),
      );
    });

    it("keeps a pin selectable while the lookup is still in flight", () => {
      useFetch.mockReturnValue({ data: undefined });
      const onChange = renderSelect({
        instructions: "greeting",
        promptVersion: 2,
      });

      // Falling back to Latest here would misreport which version will run.
      expect(versionField()).toHaveValue("2");
      expect(onChange).not.toHaveBeenCalled();
    });
  });
});
