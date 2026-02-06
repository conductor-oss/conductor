#!/bin/bash
# AI Integration Test Environment Variables
# Source this file before running integration tests:
#   source ai/src/test/resources/ai-test-env.sh
#
# Store your actual keys in a separate file that's NOT committed to git:
#   cp ai-test-env.sh ai-test-env.local.sh
#   # Edit ai-test-env.local.sh with your actual keys
#   source ai-test-env.local.sh

# ============================================================================
# OpenAI
# ============================================================================
export OPENAI_API_KEY="your-openai-api-key"
# Optional: Override base URL for OpenAI-compatible APIs
# export OPENAI_BASE_URL="https://api.openai.com/v1"

# ============================================================================
# Anthropic (Claude)
# ============================================================================
export ANTHROPIC_API_KEY="your-anthropic-api-key"
# Optional: Override base URL
# export ANTHROPIC_BASE_URL="https://api.anthropic.com"

# ============================================================================
# Google Cloud / Gemini (Vertex AI)
# ============================================================================
export GOOGLE_PROJECT_ID="your-gcp-project-id"
export GOOGLE_LOCATION="us-central1"
# Set path to your service account JSON key file
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/service-account-key.json"

# ============================================================================
# Mistral AI
# ============================================================================
export MISTRAL_API_KEY="your-mistral-api-key"
# Optional: Override base URL
# export MISTRAL_BASE_URL="https://api.mistral.ai"

# ============================================================================
# Ollama (Local or GPT-OSS)
# ============================================================================
# Default: http://localhost:11434
export OLLAMA_BASE_URL="http://localhost:11434"
# Optional: If your Ollama requires authentication
# export OLLAMA_AUTH_HEADER_NAME="Authorization"
# export OLLAMA_AUTH_HEADER="Bearer your-token"

# ============================================================================
# Grok (xAI)
# ============================================================================
export GROK_API_KEY="your-grok-api-key"
export GROK_BASE_URL="https://api.x.ai/v1"

# ============================================================================
# Cohere
# ============================================================================
export COHERE_API_KEY="your-cohere-api-key"
export COHERE_BASE_URL="https://api.cohere.com"

# ============================================================================
# Perplexity
# ============================================================================
export PERPLEXITY_API_KEY="your-perplexity-api-key"
export PERPLEXITY_BASE_URL="https://api.perplexity.ai"

# ============================================================================
# Azure OpenAI
# ============================================================================
export AZURE_OPENAI_API_KEY="your-azure-openai-api-key"
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
# Optional: Deployment name for image generation
# export AZURE_OPENAI_DEPLOYMENT_NAME="dall-e-3"

# ============================================================================
# AWS Bedrock
# ============================================================================
export AWS_ACCESS_KEY_ID="your-aws-access-key"
export AWS_SECRET_ACCESS_KEY="your-aws-secret-key"
export AWS_REGION="us-east-1"

# ============================================================================
# HuggingFace
# ============================================================================
export HUGGINGFACE_API_KEY="your-huggingface-api-key"
export HUGGINGFACE_BASE_URL="https://api-inference.huggingface.co/models"

echo "AI integration test environment variables loaded."
echo "Providers configured:"
[ -n "$OPENAI_API_KEY" ] && [ "$OPENAI_API_KEY" != "your-openai-api-key" ] && echo "  ✓ OpenAI"
[ -n "$ANTHROPIC_API_KEY" ] && [ "$ANTHROPIC_API_KEY" != "your-anthropic-api-key" ] && echo "  ✓ Anthropic"
[ -n "$GOOGLE_PROJECT_ID" ] && [ "$GOOGLE_PROJECT_ID" != "your-gcp-project-id" ] && echo "  ✓ Gemini/Vertex AI"
[ -n "$MISTRAL_API_KEY" ] && [ "$MISTRAL_API_KEY" != "your-mistral-api-key" ] && echo "  ✓ Mistral"
[ -n "$OLLAMA_BASE_URL" ] && echo "  ✓ Ollama (at $OLLAMA_BASE_URL)"
[ -n "$GROK_API_KEY" ] && [ "$GROK_API_KEY" != "your-grok-api-key" ] && echo "  ✓ Grok (xAI)"
[ -n "$COHERE_API_KEY" ] && [ "$COHERE_API_KEY" != "your-cohere-api-key" ] && echo "  ✓ Cohere"
[ -n "$PERPLEXITY_API_KEY" ] && [ "$PERPLEXITY_API_KEY" != "your-perplexity-api-key" ] && echo "  ✓ Perplexity"
[ -n "$AZURE_OPENAI_API_KEY" ] && [ "$AZURE_OPENAI_API_KEY" != "your-azure-openai-api-key" ] && echo "  ✓ Azure OpenAI"
[ -n "$AWS_ACCESS_KEY_ID" ] && [ "$AWS_ACCESS_KEY_ID" != "your-aws-access-key" ] && echo "  ✓ AWS Bedrock"
[ -n "$HUGGINGFACE_API_KEY" ] && [ "$HUGGINGFACE_API_KEY" != "your-huggingface-api-key" ] && echo "  ✓ HuggingFace"
