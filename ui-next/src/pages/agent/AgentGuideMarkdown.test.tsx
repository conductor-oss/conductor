import { render, screen } from "@testing-library/react";
import AgentGuideMarkdown from "./AgentGuideMarkdown";
import { resolveAgentGuideMarkdown } from "./guideMarkdown";

vi.mock("components/ui/CodeSnippet", () => ({
  CodeSnippet: ({ code, className, variant }: any) => (
    <pre data-language={className} data-variant={variant}>
      {code}
    </pre>
  ),
}));

describe("AgentGuideMarkdown", () => {
  it("resolves the active Conductor API URL", () => {
    expect(resolveAgentGuideMarkdown("{{CONDUCTOR_SERVER_URL}}/agent")).toBe(
      `${window.location.origin}/api/agent`,
    );
  });

  it("renders fenced code and safe external links", () => {
    render(
      <AgentGuideMarkdown
        markdown={[
          "```bash",
          "echo hello",
          "```",
          "",
          "[Docs](https://example.com/docs)",
        ].join("\n")}
      />,
    );

    expect(screen.getByText("echo hello")).toHaveAttribute(
      "data-language",
      "bash",
    );
    expect(screen.getByText("echo hello")).toHaveAttribute(
      "data-variant",
      "guide",
    );
    expect(screen.getByRole("link", { name: "Docs" })).toHaveAttribute(
      "rel",
      "noopener noreferrer",
    );
  });
});
