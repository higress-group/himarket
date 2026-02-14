# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Preference

**请使用中文回复所有开发相关的问题和交流。** This project's development team prefers Chinese for all communications. Please respond in Chinese (Simplified) when working on this codebase.

## Project Overview

HiMarket AI OPEN Platform - A comprehensive API marketplace and developer portal for managing REST APIs, MCP (Model Context Protocol) servers, AI agents, and AI models. The platform provides multi-tenant support, OAuth/OIDC authentication, subscription management, and integrates with Aliyun cloud services and various AI SDKs.

## Tech Stack

**Backend:**
- Java 17
- Spring Boot 3.2.11
- Spring Data JPA with MariaDB
- Spring Security with OAuth2 Client
- Flyway for database migrations
- WebSocket support (pty4j for terminal emulation)
- Swagger/OpenAPI (SpringDoc)

**Frontend:**
- React 18/19 + TypeScript
- Vite build tool
- Ant Design 5.15 (himarket-frontend)
- Radix UI + Tailwind CSS (himarket-admin)

**Key Integrations:**
- Aliyun: API Gateway SDK, MSE SDK, STS, Log Service
- AI SDKs: OpenAI Java, DashScope (Alibaba), Google Gemini, AgentScope
- Gateway: Higress Admin SDK, Nacos Client
- ACP (Agent Client Protocol) CLI integration

## Module Architecture

The project follows a layered multi-module Maven structure:

1. **himarket-dal** (Data Access Layer)
   - JPA entities in `entity/` package
   - Spring Data repositories in `repository/` package
   - JSON converters for complex types in `converter/` package
   - Domain support classes in `support/` package (enums, configs, DTOs)

2. **himarket-server** (Service Layer)
   - REST controllers in `controller/` package
   - Business services in `service/` package
   - DTOs and converters in `dto/` package
   - Core utilities and annotations in `core/` package
   - Gateway client integrations in `service/gateway/client/`

3. **himarket-bootstrap** (Application Entry Point)
   - Main application class: `HiMarketApplication.java`
   - Spring configuration in `config/` package
   - Flyway migrations in `src/main/resources/db/migration/`
   - Application properties in `application.yml`

4. **himarket-web** (Frontend Applications)
   - `himarket-frontend/`: Developer portal (React + Ant Design)
   - `himarket-admin/`: Admin portal (React + Radix UI + Tailwind)

## Development Commands

### Backend (Maven)

```bash
# Build entire project
mvn clean install

# Run application (from himarket-bootstrap)
cd himarket-bootstrap
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Format code (Google Java Format - AOSP style)
mvn spotless:apply

# Check code formatting
mvn spotless:check

# Run tests
mvn test

# Package application
mvn package
```

### Frontend

**himarket-frontend (Developer Portal):**
```bash
cd himarket-web/himarket-frontend
npm install
npm run dev          # Start dev server on http://0.0.0.0:5173
npm run build        # Build for production
npm run lint         # Lint code
npm run type-check   # TypeScript type checking
```

**himarket-admin (Admin Portal):**
```bash
cd himarket-web/himarket-admin
npm install
npm run dev          # Start dev server
npm run build        # Build for production
```

## Configuration

### Database Setup

The application uses MariaDB. Configure via environment variables or `application.yml`:

```yaml
DB_HOST=localhost
DB_PORT=3306
DB_NAME=himarket
DB_USERNAME=root
DB_PASSWORD=12345678
```

Database schema is managed by Flyway migrations in `himarket-bootstrap/src/main/resources/db/migration/`.

### Environment Variables

Key environment variables:
- `DB_*`: Database connection settings
- `SLS_*`: Aliyun Log Service configuration
- `ACP_CLI_COMMAND`: ACP CLI command (default: `qodercli`)
- `ACP_CLI_ARGS`: ACP CLI arguments (default: `--acp`)
- `ACP_WORKSPACE_ROOT`: Workspace directory (default: `~/.himarket/workspaces`)

### JWT Configuration

JWT settings in `application.yml`:
```yaml
jwt:
  secret: YourJWTSecret
  expiration: 7d
```

## Code Style and Formatting

The project enforces code formatting using Spotless Maven plugin with Google Java Format (AOSP style):
- 4 spaces per tab
- AOSP style formatting
- Automatic import organization
- Format annotations enabled

**Before committing, always run:**
```bash
mvn spotless:apply
```

## Key Architectural Patterns

### Authentication Flow

1. OAuth2/OIDC providers configured via backend
2. JWT tokens issued after successful authentication
3. Spring Security filters validate tokens on each request
4. Custom annotations for authorization: `@AdminAuth`, `@DeveloperAuth`, `@AdminOrDeveloperAuth`

### Product Types

The platform manages four product types (defined in `support/enums/ProductType.java`):
- `REST_API`: Traditional REST APIs with OpenAPI specs
- `MCP_SERVER`: Model Context Protocol servers
- `AGENT_API`: AI agent APIs
- `MODEL_API`: AI model APIs

Each product type has specific configuration structures stored as JSON in the database.

### Gateway Integration

The platform supmultiple gateway types:
- **Aliyun API Gateway**: Via `APIGClient` using Aliyun SDK
- **Higress**: Via `higress-admin-sdk`
- **Nacos**: For service discovery and configuration
- **ApsaraStack CSB**: Custom SDK in `himarket-server/lib/`

Gateway configurations are polymorphic and stored using JPA converters.

### Data Encryption

Sensitive data (credentials, secrets) is encrypted using the `@Encrypted` annotation and `Encryptor` utility. The root encryption key is configured in `application.yml`:
```yaml
encryption:
  root-key: portalmanagement
```

### WebSocket and Terminal Support

The platform includes terminal emulation support using pty4j for interactive CLI sessions. WebSocket configuration is in `WebSocketConfig.java`.

## Database Migrations

Flyway migrations are located in `himarket-bootstrap/src/main/resources/db/migration/`:
- `V1__Create_baseline_schema.sql`: Initial schema
- `V2__Migrate_to_v1_0_5.sql`: Version 1.0.5 updates
- `V3__Add_chat_tables.sql`: Chat feature tables
- `V4__Optimize_resource_definition.sql`: Resource optimization
- `V5__Fix_chat_attachment.sql`: Chat attachment fixes

When adding new migrations, follow the naming convention: `V{version}__{description}.sql`

## API Documentation

Swagger UI is available at: `http://localhost:8080/portal/swagger-ui.html`

OpenAPI docs: `http://localhost:8080/portal/v3/api-docs`

## Testing

Run tests from the root directory:
```bash
mvn test
```

For specific modules:
```bash
cd himarket-server
mvn test
```

## Docker Deployment

Frontend applications include Dockerfiles for containerized deployment:

```bash
# Build frontend image
cd himarket-web/himarket-frontend
docker buildx build --platform linux/amd64 -t api-portal-frontend:latest .

# Build admin image
cd himarket-web/himarket-admin
docker buildx build --platfoux/amd64 -t api-portamin:latest .
```

## Important Notes

- The project uses system-scoped dependency for ApsaraStack CSB SDK located in `himarket-server/lib/csb220230206-1.5.3-cleaned.jar`
- JVM arguments required for pty4j: `--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED`
- Frontend development servers proxy API requests to `http://localhost:8080`
- The platform supports multi-tenancy through portal isolation
- Subscription workflow includes approval mechanisms (auto-approve or manual)
