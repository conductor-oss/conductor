export interface ConnectedAppDefinition {
  type: string;
  category?: string;
  tags?: string[];
  name?: string;
}

export const isConnectedAppDefinition = (definition: ConnectedAppDefinition) =>
  definition.category === "CLOUD" &&
  definition.type.startsWith("plugin:") &&
  definition.tags?.includes("packaged-plugin") === true;

export const connectedAppOperationPath = (
  integrationName: string,
  operation?: string,
) =>
  `/integrations/${encodeURIComponent(integrationName)}/def/${
    operation ? `api/${encodeURIComponent(operation)}` : "apis"
  }`;

export const connectedAppInput = (integrationName: string, operation = "") => ({
  integrationName,
  operation,
  arguments: {},
});
