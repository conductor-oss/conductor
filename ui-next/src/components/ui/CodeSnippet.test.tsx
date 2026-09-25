import { render, screen } from "@testing-library/react";
import { CodeSnippet } from "./CodeSnippet";

describe("CodeSnippet", () => {
  it("renders the high-contrast guide toolbar", () => {
    render(
      <CodeSnippet
        code="export CONDUCTOR_SERVER_URL=http://localhost:8080/api"
        className="bash"
        variant="guide"
      />,
    );

    expect(screen.getByText("bash")).toBeVisible();
    expect(screen.getByRole("button", { name: "Copy" })).toBeVisible();
    const code = document.querySelector("code");
    expect(code).toHaveTextContent(
      "export CONDUCTOR_SERVER_URL=http://localhost:8080/api",
    );
    expect(code?.parentElement?.tagName).toBe("PRE");
  });
});
