# Architecture

## Current foundation

The sibling frontend communicates over JSON/HTTP with the retained Spring Boot backend. React Router owns navigation; TanStack Query owns server state; Axios and feature API modules isolate HTTP. Only the theme is stored in browser localStorage. The active conversation ID is in the URL so reloads restore API history. New conversation is a draft until the first successful send. AuthProvider resolves the real session via `/auth/me`; protected routes wait for authentication.

Backend flow: controller validates DTO → transactional ChatService → JPA repositories → PostgreSQL. ChatService calls AiChatService; MockAiChatService currently produces an explicitly labeled deterministic response. Chat entities never cross the HTTP boundary. A transaction commits both messages and the conversation update together, or rolls all of them back. Existing conversation rows are locked to serialize concurrent turns. Message timestamps have microsecond spacing and history is ordered by time and ID. IDs are UUIDs; timestamps are UTC Instants. Logs record IDs and mode, not message content.

Modules: `config` configures exact-origin CORS; `common.exception` maps errors to ProblemDetail; `health` exposes application identity; `dashboard` serves mock DTOs; `chat` contains controller/service/DTO/entity/repository layers; `ai.service` provides the replaceable provider boundary. There are no empty document or workspace backend modules. Flyway alone changes schema. Actuator health checks database connectivity separately from application liveness.

Accounts, conversations and messages are persisted. V2 adds app_users and conversation owner_id; legacy anonymous records remain unowned and hidden. The foreign key and indexes support ordered history. No speculative tenant or vector schema is present. The development database is bound to loopback and uses a named Docker volume. PostgreSQL credentials are supplied from the environment. No credentials are committed.

## Future ingestion pipeline (Phase 2)

Authorized upload → validate size/type → durable object storage → queued parsing → normalized text → chunking → embedding provider → PostgreSQL pgvector index. Track document versions, ingestion status, errors, retries, and source metadata. Do not treat untrusted document text as instructions. Apply document access filtering before retrieval and generation.

## Future query pipeline

Authenticated tenant/workspace scope → query normalization → lexical and vector retrieval → permission filtering → ranking → context assembly → Gemini generation → streamed answer with citations → usage and evaluation events. A real provider implementation will replace the mock; conversation orchestration will need explicit streaming/failure semantics rather than holding database transactions across remote calls.

## Proposed tables, deliberately deferred

- `organizations`, `workspaces`: membership and tenant scope; account identity now lives in `app_users`.
- `documents`, `document_chunks`: versions, ownership, access rules, parsing/chunk metadata and source offsets.
- Embeddings/vector data: model/version/dimension metadata and pgvector values, linked to chunks.
- `citations`: links from generated answers to verified source chunks.
- `tool_execution_logs`: agent invocation audit, results and approval state.
- `evaluation_runs`: retrieval and answer-quality datasets, measurements and model versions.

Account authentication and conversation ownership are implemented; tenant isolation still must land before a shared enterprise deployment. CORS is not authorization. Plan pagination, request limits, retention/deletion, idempotency, telemetry, backups, TLS and secret management before production.

## Integration references

The dependency setup follows [Spring Boot Flyway guidance](https://docs.spring.io/spring-boot/how-to/data-initialization.html) and [Tailwind's Vite plugin integration](https://tailwindcss.com/docs/installation/using-vite). Java 21, WAR packaging and the existing Boot version are preserved.

## Authentication extension

Spring Security protects APIs using server sessions, credentialed exact-origin CORS and CSRF tokens. Auth controllers use DTOs and explicit security-context persistence; AccountService owns hashing, registration and Google account provisioning. Google OIDC delegates token/state/nonce validation to Spring Security. The provider remains optional and secrets never enter the browser. See [authentication design and setup](authentication.md) for session lifecycle, ownership and limitations.
