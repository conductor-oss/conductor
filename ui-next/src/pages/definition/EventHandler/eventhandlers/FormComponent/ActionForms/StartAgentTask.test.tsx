import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { StartAgentActionForm } from "./StartAgentTask";

const useFetch = vi.hoisted(() => vi.fn());

vi.mock("utils/query", () => ({ useFetch }));

vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({ label, value, onChange, onBlur, options }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      data-options={JSON.stringify(options)}
      onChange={(event) => onChange(event, event.target.value)}
      onBlur={(event) => onBlur?.(event)}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, onTextInputChange, multiline }: any) => {
    const Tag = multiline ? "textarea" : "input";
    return (
      <Tag
        aria-label={label}
        value={value ?? ""}
        onChange={(event: any) => onTextInputChange?.(event.target.value)}
      />
    );
  },
}));

vi.mock("components/FlatMapForm/ConductorFlatMapForm", () => ({
  ConductorFlatMapFormBase: ({ onChange, value }: any) => (
    <button
      aria-label="Edit context"
      onClick={() => onChange({ ...value, addedKey: "addedValue" })}
    >
      Edit context
    </button>
  ),
}));

function Harness({ initialPayload }: { initialPayload: any }) {
  const [payload, setPayload] = useState(initialPayload);
  return (
    <>
      <StartAgentActionForm
        index={0}
        payload={payload}
        handleChangeAction={(_index: number, newPayload: any) =>
          setPayload(newPayload)
        }
        onRemove={() => {}}
      />
      <pre data-testid="payload-json">{JSON.stringify(payload)}</pre>
    </>
  );
}

const savedPayload = () =>
  JSON.parse(screen.getByTestId("payload-json").textContent ?? "{}");

const basePayload = {
  action: "start_agent",
  expandInlineJSON: false,
  start_agent: {
    name: "",
    version: "",
    prompt: "",
    sessionId: "",
    idempotencyKey: "",
  },
};

describe("StartAgentActionForm", () => {
  beforeEach(() => {
    useFetch.mockReset();
    useFetch.mockReturnValue({
      data: [
        { name: "researcher", version: 1 },
        { name: "researcher", version: 2 },
        { name: "summarizer", version: 5 },
      ],
    });
  });

  it("writes the typed agent name to start_agent.name", () => {
    render(<Harness initialPayload={basePayload} />);

    fireEvent.change(screen.getByLabelText("Agent name"), {
      target: { value: "researcher" },
    });

    expect(savedPayload().start_agent.name).toBe("researcher");
  });

  it("commits the agent name on blur, mirroring Start Workflow's name field", () => {
    render(<Harness initialPayload={basePayload} />);

    const field = screen.getByLabelText("Agent name");
    fireEvent.change(field, { target: { value: "researcher" } });
    fireEvent.blur(field);

    expect(savedPayload().start_agent.name).toBe("researcher");
  });

  it("filters the version field's options to the versions of the selected agent", () => {
    render(<Harness initialPayload={basePayload} />);

    fireEvent.change(screen.getByLabelText("Agent name"), {
      target: { value: "researcher" },
    });

    const versionField = screen.getByLabelText("Agent version");
    expect(
      JSON.parse(versionField.getAttribute("data-options") ?? "[]"),
    ).toEqual(["1", "2"]);
  });

  it("writes prompt, session id, and idempotency key to their respective fields", () => {
    render(<Harness initialPayload={basePayload} />);

    fireEvent.change(screen.getByLabelText("Prompt"), {
      target: { value: "Summarize: ${event.payload.doc}" },
    });
    fireEvent.change(screen.getByLabelText("Session ID"), {
      target: { value: "${event.payload.session_id}" },
    });
    fireEvent.change(screen.getByLabelText("Idempotency key"), {
      target: { value: "${event.payload.event_id}" },
    });

    expect(savedPayload().start_agent).toMatchObject({
      prompt: "Summarize: ${event.payload.doc}",
      sessionId: "${event.payload.session_id}",
      idempotencyKey: "${event.payload.event_id}",
    });
  });

  it("splits the media textarea into a trimmed, non-empty string array", () => {
    render(<Harness initialPayload={basePayload} />);

    fireEvent.change(screen.getByLabelText("Media (one URL per line)"), {
      target: {
        value: "https://example.com/a.png\n  \nhttps://example.com/b.png\n",
      },
    });

    expect(savedPayload().start_agent.media).toEqual([
      "https://example.com/a.png",
      "https://example.com/b.png",
    ]);
  });

  it("updates start_agent.context via the flat map editor", () => {
    render(<Harness initialPayload={basePayload} />);

    fireEvent.click(screen.getByLabelText("Edit context"));

    expect(savedPayload().start_agent.context).toMatchObject({
      addedKey: "addedValue",
    });
  });
});
