# CodeAlpha DevOps Internship - Tasks

[![Task 1 - App and infra checks](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task1-azure-app-checks.yml/badge.svg)](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task1-azure-app-checks.yml)
[![Task 3 - Java Gradle CI/CD](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task3-java-gradle.yml/badge.svg)](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task3-java-gradle.yml)
[![Task 4 - Docker Web Server](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task4-docker-web-server.yml/badge.svg)](https://github.com/ahmedelgmal310/codealpha_tasks/actions/workflows/task4-docker-web-server.yml)

My solutions for the **CodeAlpha DevOps Internship** (October 2026 batch). Each task lives in its own folder with its own README explaining the design, how to run it and what to look for.

| # | Task | Folder | Main tools |
|---|---|---|---|
| 1 | CI/CD Pipeline using Azure | [CodeAlpha_Azure_CICD_Pipeline](CodeAlpha_Azure_CICD_Pipeline) | Azure Pipelines, Azure Container Registry, Azure App Service, Bicep, Docker, Node.js |
| 2 | Jenkins Remoting Project | [CodeAlpha_Jenkins_Remoting](CodeAlpha_Jenkins_Remoting) | Jenkins, Remoting (JNLP4 inbound agents), JCasC, Job DSL, Docker Compose, multi-arch (amd64/arm64) |
| 3 | Java Application using Gradle | [CodeAlpha_Java_Gradle_App](CodeAlpha_Java_Gradle_App) | Java 21, Gradle 9 (wrapper, version catalog), JUnit 5, JaCoCo, GitHub Actions, GHCR |
| 4 | Web Server using Docker | [CodeAlpha_Docker_Web_Server](CodeAlpha_Docker_Web_Server) | Docker, Nginx, Docker Compose, health checks, hadolint |

## Overview

**Task 1 - CI/CD Pipeline using Azure.** A four-stage Azure Pipeline (Build → Package → Deploy → Verify) tests a small Node.js app, pushes the image to Azure Container Registry, deploys it to Azure App Service and checks that the new build is live. Infrastructure is written in Bicep; authentication uses workload identity and managed identity, so no passwords are stored.

**Task 2 - Jenkins Remoting.** A Jenkins controller with zero executors and two inbound agents (x86_64 and aarch64) connected over Remoting. Jobs run on both architectures in parallel, load is spread across executors, and a dedicated job verifies node isolation (non-root, no capabilities, separate networks, nothing on the controller). Everything is configured as code.

**Task 3 - Java Application using Gradle.** A REST API built with the Gradle wrapper, a version catalog for dependencies, JUnit 5 tests and a JaCoCo coverage gate. GitHub Actions builds and tests every push and publishes a Docker image to GitHub Container Registry from `main`. The same build also runs on the Task 2 Jenkins agents through a Jenkinsfile.

**Task 4 - Web Server using Docker.** Nginx serving a static site from a hardened container (non-root, read-only filesystem, no capabilities, resource limits), with a health check, lifecycle and monitoring scripts, smoke tests and a troubleshooting guide.

## Repository layout

```
codealpha_tasks/
├── .github/workflows/                # GitHub Actions for tasks 1, 3 and 4
├── CodeAlpha_Azure_CICD_Pipeline/    # Task 1
├── CodeAlpha_Jenkins_Remoting/       # Task 2
├── CodeAlpha_Java_Gradle_App/        # Task 3
└── CodeAlpha_Docker_Web_Server/      # Task 4
```

## Quick start

All tasks except Task 1's Azure deployment run locally with Docker:

```bash
git clone https://github.com/ahmedelgmal310/codealpha_tasks.git
cd codealpha_tasks

# Task 4
(cd CodeAlpha_Docker_Web_Server && docker compose up -d --build)      # http://localhost:8080

# Task 3
(cd CodeAlpha_Java_Gradle_App && docker build -t task-tracker . && docker run -d -p 8081:8080 task-tracker)

# Task 2 (set your own passwords in .env first)
(cd CodeAlpha_Jenkins_Remoting && cp .env.example .env && docker compose up -d --build)

# Task 1 (app only; the pipeline needs an Azure subscription, see docs/SETUP.md)
(cd CodeAlpha_Azure_CICD_Pipeline && npm ci && npm test && npm start)
```

## Author

**Ahmed Elgammal**, DevOps Intern at CodeAlpha

[LinkedIn](https://www.linkedin.com/in/ahmed-elgmal-ab730a1b1/) · [Portfolio](https://elgamal.codlek.com) · [GitHub](https://github.com/ahmedelgmal310)
