import { FunctionComponent } from "react";
import { useMCPIntegrations } from "utils/hooks/useMCPIntegrations";
import { integrationIconMap } from "./integrationIconMap";

interface IntegrationIconProps {
  integrationName?: string;
  size?: number;
}

export const IntegrationIcon: FunctionComponent<IntegrationIconProps> = ({
  integrationName,
  size = 24,
}) => {
  // Resolve via map first (handles integration type → iconName)
  const resolved =
    integrationName != null && integrationName in integrationIconMap
      ? integrationIconMap[integrationName]
      : integrationName;

  const isUrl = resolved?.match(/^https?:\/\//i);

  return (
    <img
      alt={integrationName}
      src={isUrl ? resolved : `/integrations-icons/${resolved}.svg`}
      style={{ width: size, height: size, objectFit: "contain" }}
      onError={({ currentTarget }) => {
        if (!isUrl) {
          currentTarget.onerror = null;
          currentTarget.src = `/integrations-icons/default.svg`;
        }
      }}
    />
  );
};

export const ConnectedAppIcon = ({
  integrationName,
  size,
}: IntegrationIconProps) => {
  const { integrations } = useMCPIntegrations(true);
  const integration = integrations.find(
    (item) => item.name === integrationName,
  );
  return (
    <IntegrationIcon
      integrationName={integration?.iconName ?? "default"}
      size={size}
    />
  );
};
