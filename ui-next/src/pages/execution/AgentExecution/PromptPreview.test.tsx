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

  it("falls back to plain text when no payload parses", () => {
    renderPreview({
      messages: [
        { role: "user", message: 'Context follows.\n[{"unterminated": true}' },
      ],
    });

    expect(screen.queryByText("Structured data")).toBeNull();
    expect(screen.getByText(/Context follows\./)).toBeTruthy();
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
