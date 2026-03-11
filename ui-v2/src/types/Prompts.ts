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
};
