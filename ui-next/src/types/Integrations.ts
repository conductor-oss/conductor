import { TagDto } from "./Tag";

export enum IntegrationType {
  AMAZON_MSK = "kafka_msk",
  AMQP = "amqp",
  ANTHROPIC = "anthropic",
  APACHE_KAFKA = "kafka",
  AWS = "aws",
  AWS_BEDROCK_ANTHROPIC = "aws_bedrock_anthropic",
  AWS_BEDROCK_COHERE = "aws_bedrock_cohere",
  AWS_BEDROCK_LLAMA2 = "aws_bedrock_llama2",
  AWS_BEDROCK_TITAN = "aws_bedrock_titan",
  AWS_SQS = "aws_sqs",
  AZURE_OPENAI = "azure_openai",
  AZURE_SERVICE_BUS = "azure_service_bus",
  COHERE = "cohere",
  CONFLUENT_KAFKA = "kafka_confluent",
  GCP = "gcp",
  GCP_PUBSUB = "gcp_pubsub",
  GIT = "git",
  HUGGING_FACE = "huggingface",
  IBM_MQ = "ibm_mq",
  MISTRAL = "mistral",
  MONGO_VECTOR_DB = "mongovectordb",
  NATS_MESSAGING = "nats",
  OPENAI = "openai",
  PINECONE_DB = "pineconedb",
  POSTGRES_VECTOR_DB = "pgvectordb",
  RELATIONAL_DB = "relational_db",
  VERTEX_AI = "vertex_ai",
  VERTEX_AI_GEMINI = "vertex_ai_gemini",
  WEAVIATE_DB = "weaviatedb",
  PERPLEXITY = "perplexity",
  GROK = "Grok",
  SENDGRID = "sendgrid",
  GOOGLE_CALENDER = "google-calendar-mcp",
  GOOGLE_DRIVE = "google-drive-mcp",
  NOTION = "notion",
  GOOGLE_DOCS = "google-docs-mcp",
  GOOGLE_SLIDES = "google-slides-mcp",
  GOOGLE_SHEETS = "google-sheets-mcp",
  WORDPRESS_COM = "wordpress-com",
  DISCOURSE = "discourse",
  COMMONROOM = "commonroom",
}
export enum IntegrationCategory {
  API = "API",
  AI_MODEL = "AI_MODEL",
  VECTOR_DB = "VECTOR_DB",
  RELATIONAL_DB = "RELATIONAL_DB",
  MESSAGE_BROKER = "MESSAGE_BROKER",
  EMAIL = "EMAIL",
  EVENT_SOURCE = "EVENT_SOURCE",
  MCP = "MCP",
}

export type BaseIntegration = {
  category: string;
  description: string;
  enabled: boolean;
  name: string;
  type: IntegrationType;
};

export type IntegrationDef = BaseIntegration & {
  // Response from the def endpoint
  tags: string[];
  configuration: IntegrationConfigFieldModel[];
  categoryLabel: string;
  nameLabel: string;
  iconName: string;
};

export type IntegrationI = BaseIntegration & {
  tags?: TagDto[];
  configuration: Record<string, unknown>;
};

export type ModelDto = {
  createTime?: number;
  updateTime?: number;
  createdBy?: string;
  updatedBy?: string;
  integrationName: string;
  api: string;
  description: string;
  configuration: {
    [key: string]: string;
  };
  enabled: boolean;
  tags: TagDto[];
  endpoint?: string;
};

export type IntegrationConfigFieldModel = {
  description: string;
  fieldName: string;
  fieldType: string;
  label: string;
  valueOptions?: {
    label: string;
    value: string;
  }[];
  optional: boolean;
  dependsOn?: {
    fieldName: string;
    value: string;
  }[];
  value?: string;
};

export type IntegrationConfigFormField = {
  name: string;
  label: string;
  helperText: string;
  type: string;
  value: string | boolean;
  optional: boolean;
  options?: { label: string; value: string }[];
};
