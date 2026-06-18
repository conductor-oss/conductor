/**
 * Maps integration type identifiers to their iconName, only where they differ.
 * Used by IntegrationIcon to resolve the correct icon when the passed value
 * is an integration type rather than a direct icon name.
 */
export const integrationIconMap: Record<string, string> = {
  azure_openai: "azureOpenAI",
  openai: "openAI",
  vertex_ai: "vertexAI",
  vertex_ai_gemini: "vertexAI",
  huggingface: "huggingFace",
  pineconedb: "pinecone",
  weaviatedb: "weaviate",
  pgvectordb: "pgvector",
  mongovectordb: "mongovector",
  aws_sqs: "aws",
  aws_bedrock_anthropic: "aws",
  aws_bedrock_cohere: "aws",
  aws_bedrock_titan: "aws",
  "slack-webhook-v2": "slack",
  "google-calendar": "googlecalendar",
  "wordpress-com": "wordpress",
  "pagerduty-inline": "pagerduty",
  "gmail-v2": "gmail",
  "firebase-firestore-v5": "firebase-firestore",
  "clickhouse-v2": "clickhouse",
  "stripe-v2": "stripe",
  "google-analytics": "googleanalytics",
  "google-ads": "googleads",
};
