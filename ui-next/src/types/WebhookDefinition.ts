export interface WebhookExecution {
  eventId: string;
  matched: boolean;
  workflowIds: string[];
  payload: string;
  timeStamp: number;
}

export interface WebhookDefinition {
  name: string;
  receiverWorkflowNamesToVersions: Record<string, number>;
  sourcePlatform: string;
  id?: string;
  workflowsToStart: Record<string, number | string | undefined>;
  headers: Record<string, string | undefined>;
  webhookExecutionHistory: WebhookExecution[];
  urlVerified: boolean;
  verifier: string;
  headerKey: string;
  secretKey: string;
  secretValue: string;
  createdBy: string;
}
