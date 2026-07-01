import { InstanceCapabilities } from "utils/accessControl";

export type PromptDef = {
  name: string;
  createdBy?: string;
  createTime?: number;
  template: string;
  updatedBy?: string;
  updateTime?: string;
  description?: string;
  variables: string[];
  integrations: string[];
  version?: number;
  tokens?: number;
  temperature?: number;
  topP?: number;
  stopWords?: string[];
  responseFormat?: "json";
  /** Per-instance write capability hints — populated on GET and list responses. */
  capabilities?: InstanceCapabilities | null;
};
