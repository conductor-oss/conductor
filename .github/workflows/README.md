# Conductor CI/CD Workflow

This repository contains workflow files for implementing Continuous Integration (CI) and Continuous Deployment (CD) processes separately for Conductor UI and server components. The workflow is designed to deploy to both development (dev) and production (prd) environments on AWS (ECS).

## Workflow Overview

The CI/CD workflow is triggered manually & comprises two main components:

1. **Conductor UI CI/CD:**
   - Workflow file: `.github/workflows/ci-ui.yaml`
                    `.github/workflows/cd-ui.yaml`
   - These workflows handle the CI & CD process for Conductor UI.

2. **Conductor Server CI/CD:**
   - Workflow file: `.github/workflows/ci-server.yaml`
                    `.github/workflows/cd-server.yaml`
   - These workflows handle the CI & CD process for Conductor server.

## Deployment Strategy

- **Branches:**
  - The `production` branch is considered the master branch for all deployments.
  - All deployments to both development and production environments are triggered from the `production` branch.

- **Input Variables:**
  - The workflow takes the following input variables:
    1. **Branch:** Specifies the branch to be deployed (e.g., `production`).
    2. **Environment:** Specifies the deployment environment (e.g., `dev` or `prd`).
    3. **Tag:** Specifies the version to be deployed. This version is used for tagging the Docker image.

## Versioning and Docker Image Tagging

The version provided as an input variable is crucial for versioning and tagging Docker images. The workflow utilizes this version to tag the Docker image before deploying to the AWS Elastic Container Registry (ECR). During ECS deployment, this tagged image is fetched from ECR.

