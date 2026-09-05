import { fireEvent, render, screen } from "@testing-library/react";
import { vi } from "vitest";
import AgentCredentialsSection from "./AgentCredentialsSection";

vi.mock("utils/query", () => ({
  useSecretNames: () => ["AZURE_CRED", "OPENAI_KEY"],
}));

vi.mock("components/ui/inputs/RadioButtonGroup", () => ({
  default: ({ items, value, onChange }: any) => (
    <div>
      {items.map((item: any) => (
        <input
          key={item.value}
          type="radio"
          aria-label={`method:${item.label}`}
          checked={value === item.value}
          onChange={() => onChange({}, item.value)}
        />
      ))}
    </div>
  ),
}));

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({ label, value, onChange }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value)}
    />
  ),
}));

const renderSection = (
  runtime: string,
  credentials: Record<string, unknown> | undefined,
) => {
  const onCredentialsChange = vi.fn();
  render(
    <AgentCredentialsSection
      runtime={runtime}
      credentials={credentials}
      onCredentialsChange={onCredentialsChange}
      useCallerIdentity={false}
      onUseCallerIdentityChange={vi.fn()}
    />,
  );
  return onCredentialsChange;
};

describe("AgentCredentialsSection", () => {
  it("offers only the methods the provider supports", () => {
    renderSection("openai-assistants", undefined);

    expect(screen.getByLabelText("API key")).toBeInTheDocument();
    // OpenAI has no service principal or role to assume.
    expect(
      screen.queryByLabelText("Service principal"),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Assume a role")).not.toBeInTheDocument();
  });

  it("shows the fields for the configured method", () => {
    renderSection("microsoft-foundry", {
      client_id: "cid",
      client_secret: "cs",
      tenant_id: "tid",
    });

    expect(screen.getByLabelText("Client ID")).toHaveValue("cid");
    expect(screen.getByLabelText("Tenant ID")).toHaveValue("tid");
    // The API key field belongs to a different method, so it is not on screen.
    expect(screen.queryByLabelText("API key")).not.toBeInTheDocument();
  });

  it("keeps the chosen method on screen while its fields are still empty", () => {
    // Detection alone would fall back to the server's own identity the moment the fields are
    // cleared, snapping the choice away mid-edit.
    renderSection("microsoft-foundry", {});

    fireEvent.click(screen.getByLabelText("method:Service principal"));

    expect(screen.getByLabelText("Client ID")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Fill from Conductor secret store"),
    ).toBeInTheDocument();
  });

  it("writes the secret references so the user never types the syntax", () => {
    const onChange = renderSection("microsoft-foundry", {
      client_id: "cid",
      client_secret: "cs",
      tenant_id: "tid",
    });

    fireEvent.change(
      screen.getByLabelText("Fill from Conductor secret store"),
      {
        target: { value: "AZURE_CRED" },
      },
    );

    expect(onChange).toHaveBeenCalledWith({
      client_id: "${workflow.secrets.AZURE_CRED.client_id}",
      client_secret: "${workflow.secrets.AZURE_CRED.client_secret}",
      tenant_id: "${workflow.secrets.AZURE_CRED.tenant_id}",
    });
  });

  it("drops the previous method's keys when switching", () => {
    const onChange = renderSection("bedrock", {
      accessKeyId: "AKIA",
      secretAccessKey: "secret",
    });

    fireEvent.click(screen.getByLabelText("method:Assume a role"));

    // Leaving accessKeyId behind would keep the server on the old method entirely.
    expect(onChange).toHaveBeenCalledWith({
      roleArn: "",
      roleSessionName: "",
      externalId: "",
    });
  });

  it("keeps configuration that is not a credential", () => {
    const onChange = renderSection("bedrock", {
      accessKeyId: "AKIA",
      secretAccessKey: "secret",
      scope: "keep-me",
    });

    fireEvent.click(
      screen.getByLabelText("method:The server's own AWS credentials"),
    );

    expect(onChange).toHaveBeenCalledWith({ scope: "keep-me" });
  });

  it("has nothing to configure for the server's own identity", () => {
    renderSection("microsoft-foundry", {});

    expect(
      screen.queryByLabelText("Fill from Conductor secret store"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/whatever the server is running as/i),
    ).toBeInTheDocument();
  });

  it("offers caller identity only for Foundry", () => {
    renderSection("microsoft-foundry", {});
    expect(
      screen.getByLabelText("Run as the person who triggered the workflow"),
    ).toBeInTheDocument();
  });

  it("does not offer caller identity for Bedrock", () => {
    renderSection("bedrock", {});
    expect(
      screen.queryByLabelText("Run as the person who triggered the workflow"),
    ).not.toBeInTheDocument();
  });
});
