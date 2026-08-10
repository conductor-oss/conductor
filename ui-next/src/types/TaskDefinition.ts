export type TaskDefinitionDto = {
  backoffScaleFactor: number;
  concurrentExecLimit?: number;
  createTime: number;
  createdBy: string;
  description: string;
  inputKeys: string[];
  inputTemplate: Record<string, any>;
  name: string;
  outputKeys: string[];
  ownerEmail: string;
  pollTimeoutSeconds: number;
  rateLimitFrequencyInSeconds: number;
  rateLimitPerFrequency: number;
  responseTimeoutSeconds: number;
  retryCount: number;
  retryDelaySeconds: number;
  retryLogic: string;
  timeoutPolicy: string;
  timeoutSeconds: number;
  updateTime?: number;
  updatedBy?: string;
  inputSchema?: {
    name: string;
    version?: number;
  };
  outputSchema?: {
    name: string;
    version?: number;
  };
};
