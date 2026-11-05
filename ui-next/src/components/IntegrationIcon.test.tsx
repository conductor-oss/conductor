import { render, screen } from "@testing-library/react";
import { ConnectedAppIcon, IntegrationIcon } from "./IntegrationIcon";
import CardIcon from "./features/flow/components/shapes/TaskCard/CardIcon";
import { TaskType } from "types";
const metadata = vi.hoisted(() => ({ integrations: vi.fn() }));
vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: metadata.integrations,
}));
beforeEach(() =>
  metadata.integrations.mockReturnValue({
    integrations: [
      { name: "prod", type: "plugin:aws-lambda", iconName: "aws-lambda" },
    ],
  }),
);
it("resolves the provider icon from connection metadata without persisting integrationType", () => {
  render(<ConnectedAppIcon integrationName="prod" />);
  expect(metadata.integrations).toHaveBeenCalledWith(true);
  expect(screen.getByAltText("aws-lambda")).toHaveAttribute(
    "src",
    "/integrations-icons/aws-lambda.svg",
  );
});
it("uses the existing fallback for dynamic or unavailable connections", () => {
  render(<ConnectedAppIcon integrationName="${workflow.input.connection}" />);
  expect(screen.getByAltText("default")).toHaveAttribute(
    "src",
    "/integrations-icons/default.svg",
  );
});
it("leaves direct integration icon mapping unchanged", () => {
  render(<IntegrationIcon integrationName="aws_sqs" />);
  expect(screen.getByAltText("aws_sqs")).toHaveAttribute(
    "src",
    "/integrations-icons/aws.svg",
  );
});

it("uses the provider icon for Connected App graph cards", () => {
  render(
    <CardIcon
      type={TaskType.CONNECTED_APP}
      integrationName="prod"
      integrationType={undefined}
    />,
  );
  expect(screen.getByAltText("aws-lambda")).toHaveAttribute(
    "src",
    "/integrations-icons/aws-lambda.svg",
  );
});
it("does not request integration metadata for unrelated graph cards", () => {
  metadata.integrations.mockClear();
  render(<CardIcon type={TaskType.HTTP} integrationType={undefined} />);
  expect(metadata.integrations).not.toHaveBeenCalled();
});
