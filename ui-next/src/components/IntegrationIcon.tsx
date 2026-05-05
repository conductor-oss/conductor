import { FunctionComponent } from "react";
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
