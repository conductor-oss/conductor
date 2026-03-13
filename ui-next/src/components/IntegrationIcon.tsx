import { FunctionComponent } from "react";

interface IntegrationIconProps {
  integrationName?: string;
  size?: number;
}

export const IntegrationIcon: FunctionComponent<IntegrationIconProps> = ({
  integrationName,
  size = 24,
}) => {
  // Check if the integrationName is a URL (starts with http:// or https://)
  const isUrl = integrationName?.match(/^https?:\/\//i);

  return (
    <img
      alt={integrationName}
      src={
        isUrl ? integrationName : `/integrations-icons/${integrationName}.svg`
      }
      style={{ width: size, height: size, objectFit: "contain" }}
      onError={({ currentTarget }) => {
        // Only fall back to default if it's not a URL
        if (!isUrl) {
          currentTarget.onerror = null;
          currentTarget.src = `/integrations-icons/default.svg`;
        }
      }}
    />
  );
};
