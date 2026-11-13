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
  maxRetryDelaySeconds?: number;
  backoffJitterMs?: number;
  totalTimeoutSeconds?: number;
  taskStatusListenerEnabled?: boolean;
  // Already read by the task definition form; the type had drifted.
  enforceSchema?: boolean;
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
