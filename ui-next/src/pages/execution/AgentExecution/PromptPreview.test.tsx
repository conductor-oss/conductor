import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PromptPreview } from "./PromptPreview";

const INSTRUCTIONS = "You are a helpful agent.";

function renderPreview(input: Record<string, unknown>) {
  return render(<PromptPreview input={input} />);
}

function longMessage(tail: string) {
  return `${"x".repeat(1300)} ${tail}`;
}

describe("PromptPreview", () => {
  it("renders one card per message with its role and character count", () => {
    renderPreview({
      messages: [
        { role: "system", message: "system context" },
        { role: "user", message: "the first question" },
        { role: "assistant", content: "an answer" },
      ],
    });

    const roles = screen
      .getAllByText(/^(system|user|assistant)$/)
      .map((node) => node.textContent);
    expect(roles).toEqual(["system", "user", "assistant"]);
    expect(screen.getByText("system context")).toBeTruthy();
    expect(screen.getByText("14 characters")).toBeTruthy();
    expect(screen.getByText("18 characters")).toBeTruthy();
    expect(screen.getByText("9 characters")).toBeTruthy();
  });

  it("shows escaped newlines as real newlines", () => {
    const { container } = renderPreview({
      messages: [{ role: "user", message: "line one\\nline two" }],
    });

    // getByText normalizes whitespace, so assert on the rendered text directly.
    expect(container.textContent).toContain("line one\nline two");
    expect(container.textContent).not.toContain("line one\\nline two");
  });

  it("labels the leading instructions message and renders it as Markdown", () => {
    renderPreview({
      instructions: `${INSTRUCTIONS}\n\nRULES\n1. Ask before acting.`,
      messages: [
        {
          role: "system",
          message: `${INSTRUCTIONS}\n\nRULES\n1. Ask before acting.`,
        },
        { role: "user", message: "hi" },
      ],
    });

    expect(screen.getByText("Instructions")).toBeTruthy();
    expect(screen.queryByText("system")).toBeNull();
    expect(screen.getByText("Rules").tagName).toBe("H2");
    expect(screen.getAllByRole("listitem").map((li) => li.textContent)).toEqual(
      ["Ask before acting."],
    );
  });

  describe("collapsing", () => {
    it.each([["system"], ["user"], ["assistant"]])(
      "collapses and expands a long %s message",
      (role) => {
        renderPreview({
          messages: [{ role, message: longMessage("tail-marker") }],
        });

        expect(screen.queryByText(/tail-marker/)).toBeNull();
        const toggle = screen.getByRole("button", {
          name: "Show full message",
        });
        expect(toggle.getAttribute("aria-expanded")).toBe("false");

        fireEvent.click(toggle);
        expect(screen.getByText(/tail-marker/)).toBeTruthy();

        fireEvent.click(screen.getByRole("button", { name: "Show less" }));
        expect(screen.queryByText(/tail-marker/)).toBeNull();
      },
    );

    it("names the instructions toggle for what it reveals", () => {
      const instructions = longMessage("tail-marker");
      renderPreview({
        instructions,
        messages: [{ role: "system", message: instructions }],
      });

      fireEvent.click(
        screen.getByRole("button", { name: "Show full instruction" }),
      );
      expect(screen.getByText(/tail-marker/)).toBeTruthy();
    });

    it("collapses each long message independently", () => {
      renderPreview({
        messages: [
          { role: "user", message: longMessage("first-tail") },
          { role: "user", message: longMessage("second-tail") },
        ],
      });

      const toggles = screen.getAllByRole("button", {
        name: "Show full message",
      });
      expect(toggles).toHaveLength(2);

      fireEvent.click(toggles[1]);
      expect(screen.getByText(/second-tail/)).toBeTruthy();
      expect(screen.queryByText(/first-tail/)).toBeNull();
    });

    it("resets expansion when a card's content changes at the same position", () => {
      const { rerender } = renderPreview({
        messages: [{ role: "user", message: longMessage("first-tail") }],
      });

      fireEvent.click(
        screen.getByRole("button", { name: "Show full message" }),
      );
      expect(screen.getByText(/first-tail/)).toBeTruthy();

      rerender(
        <PromptPreview
          input={{
            messages: [
              { role: "user", message: longMessage("a-different-longer-tail") },
            ],
          }}
        />,
      );

      expect(screen.queryByText(/first-tail/)).toBeNull();
      expect(screen.queryByText(/a-different-longer-tail/)).toBeNull();
      const toggle = screen.getByRole("button", { name: "Show full message" });
      expect(toggle.getAttribute("aria-expanded")).toBe("false");
    });

    it("leaves short messages uncollapsed", () => {
      renderPreview({ messages: [{ role: "user", message: "short" }] });

      expect(screen.queryByRole("button", { name: /^Show full/ })).toBeNull();
    });

    it("never collapses a structured message, which must stay valid JSON", () => {
      const payload = JSON.stringify({
        items: Array.from({ length: 200 }, (_, i) => ({ id: i })),
      });
      expect(payload.length).toBeGreaterThan(1200);

      renderPreview({
        messages: [{ role: "user", message: `Context:\n${payload}` }],
      });

      expect(screen.getByText("Structured data")).toBeTruthy();
      expect(screen.queryByRole("button", { name: /^Show full/ })).toBeNull();
    });
  });

  it("renders prose on both sides of an embedded payload", () => {
    renderPreview({
      messages: [{ role: "user", message: 'before\n{"a": 1}\nafter' }],
    });

    expect(screen.getByText("before")).toBeTruthy();
    expect(screen.getByText("Structured data")).toBeTruthy();
    expect(screen.getByText("after")).toBeTruthy();
  });

  it("renders a small structured payload as preformatted text, not Monaco", () => {
    renderPreview({
      messages: [{ role: "user", message: '{"ok": true}' }],
    });

    expect(screen.getByText("Structured data")).toBeTruthy();
    // @monaco-editor/react is mocked to render null, so finding the pretty-
    // printed JSON text in the DOM proves this took the plain-text path.
    expect(screen.getByText(/"ok": true/)).toBeTruthy();
  });

  it("keeps a large structured payload on the Monaco path", () => {
    const payload = JSON.stringify({
      items: Array.from({ length: 200 }, (_, i) => ({ id: i })),
    });

    renderPreview({
      messages: [{ role: "user", message: `Context:\n${payload}` }],
    });

    expect(screen.getByText("Structured data")).toBeTruthy();
    // @monaco-editor/react is mocked to render null, so no pretty-printed
    // JSON text reaches the DOM when the Monaco path is taken.
    expect(screen.queryByText(/"id": 0/)).toBeNull();
  });

  it("falls back to plain text when no payload parses", () => {
    renderPreview({
      messages: [
        { role: "user", message: 'Context follows.\n[{"unterminated": true}' },
      ],
    });

    expect(screen.queryByText("Structured data")).toBeNull();
    expect(screen.getByText(/Context follows\./)).toBeTruthy();
  });

  describe("role accents", () => {
    // The label Typography sits two levels below the card Box: card > header
    // row > label. Walking up from the (uniquely-texted) role label is more
    // robust than guessing at emotion's generated class names.
    function cardFor(role: string): HTMLElement {
      const label = screen.getByText(role);
      return label.parentElement!.parentElement as HTMLElement;
    }

    it("gives assistant, user, and system cards distinct left-border colours", () => {
      renderPreview({
        messages: [
          { role: "system", message: "s" },
          { role: "user", message: "u" },
          { role: "assistant", content: "a" },
        ],
      });

      const colours = ["system", "user", "assistant"].map(
        (role) => getComputedStyle(cardFor(role)).borderLeftColor,
      );

      colours.forEach((colour) => expect(colour).not.toBe(""));
      expect(new Set(colours).size).toBe(3);
    });

    it('renders a "constructor" role without blowing up, using the fallback accent', () => {
      renderPreview({
        messages: [
          { role: "constructor", message: "prototype-pollution attempt" },
          { role: "some-other-unrecognised-role", message: "fallback too" },
        ],
      });

      const constructorColour = getComputedStyle(
        cardFor("constructor"),
      ).borderLeftColor;
      const fallbackColour = getComputedStyle(
        cardFor("some-other-unrecognised-role"),
      ).borderLeftColor;

      // Rendered without throwing, and MUI never received a function as a
      // colour: the "constructor" card matches another unrecognised role's
      // card exactly, i.e. both took the real fallback accent.
      expect(constructorColour).not.toBe("");
      expect(constructorColour).toBe(fallbackColour);
    });
  });

  it("summarizes what the preview contains", () => {
    renderPreview({
      instructions: INSTRUCTIONS,
      messages: [
        { role: "system", message: INSTRUCTIONS },
        { role: "system", message: "injected recall context" },
        { role: "user", message: "hi" },
      ],
    });

    expect(
      screen.getByText(
        "Preview: agent instructions · 1 system context message · 1 conversation message.",
      ),
    ).toBeTruthy();
  });
});
