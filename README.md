# ContextIQ — Phase 3: Private document RAG

An enterprise knowledge platform with authenticated local Ollama chat and private document retrieval. **Existing installations: follow [Phase 3 rollout](docs/phase-3-rag.md) to add pgvector before restarting the updated backend.** See [Phase 2 setup](docs/phase-2-ollama.md) for Spring AI and gemma4:e2b; MOCK mode remains available for offline demos. Account authentication is implemented with email/password and optional Google login. See [authentication setup](docs/authentication.md) for configuration and manual Docker rebuild steps.

## Layout and prerequisites

The original backend remains in `ContextIQ/`: Spring Boot **4.1.2**, Maven wrapper, Java **21**, WAR packaging, group `com.gihan.contextioq`, artifact `ContextIQ`, package `com.gihan.contextioq.ContextIQ`. The application class, servlet initializer, original test, and Maven plugins are retained. The sibling `../ContextIQ-frontend/` contains React, TypeScript, Vite, Tailwind 4, React Router, Axios, TanStack Query and Lucide.

Install JDK 21, Node.js 22.18+ with npm, and Docker Desktop (running). Dependency installation needs internet access. Maven is supplied by `./mvnw`.

## Start the database and backend

From this backend folder:

```sh
cp .env.example .env # only if .env does not already exist
# Edit .env: set DB_USERNAME and a strong DB_PASSWORD. Never commit .env.
docker compose up -d --build --wait postgres
./mvnw spring-boot:run
```

The verified local `.env` uses PostgreSQL host port **5433** because 5432 was already occupied (the example defaults remain 5432). A generated local `.env` may already exist from verification; keep it private. Spring imports the optional `.env` in the working directory as a Java properties file. For IntelliJ launches, set the working directory to this backend folder. Use plain `KEY=value` entries without surrounding quotes or `export`; quotes become part of the value in Java properties. Environment variables override values from the file, and deployments can omit it entirely. `DB_URL` must start with `jdbc:postgresql://` (for example, `jdbc:postgresql://localhost:5433/contextiq`). Backend: http://localhost:8080. It binds to loopback by default. Compose stores data in the named `contextiq-data` volume. `docker compose down` preserves data; `down -v` destroys it and should only be used deliberately.

Flyway runs the versioned migrations in `src/main/resources/db/migration/` and `src/main/resources/db/postgresql/` before JPA initialization. Hibernate uses `ddl-auto=validate`, including in tests. Add new versioned migrations for schema changes; never edit migrations already applied to a database. UTC `Instant` timestamps use PostgreSQL `TIMESTAMP WITH TIME ZONE` (equivalent to TIMESTAMPTZ).

## Run backend and PostgreSQL together in Docker

Start Docker Desktop, then run from this backend folder:

```sh
# Only create .env if it does not exist, then set DB_USERNAME and DB_PASSWORD.
cp -n .env.example .env
docker compose up --build -d
docker compose logs -f backend
```

The Dockerfile builds and tests the WAR with Java 21, then runs it in a smaller Java 21 runtime image as a non-root user. `.dockerignore` excludes local credentials and build output. Compose starts PostgreSQL first and waits for its health check before launching Spring Boot. Flyway creates or updates the application tables on startup.

The backend connects to `postgres:5432` over Docker's internal network. This is independent of the host `DB_URL` in `.env`. On your Mac, the API is at `http://localhost:8080` and pgAdmin connects to `localhost` at `DB_PORT` (5433 in the existing local setup). The container binds to `0.0.0.0` internally, while Compose publishes it only on the Mac's loopback interface. The frontend continues to run separately as described below.

Stop an IntelliJ backend before starting the Docker backend, or run `BACKEND_PORT=8081 docker compose up --build -d` to publish the container on a different port. Use `curl http://localhost:8080/actuator/health` to check readiness; `docker compose up -d` alone does not mean Spring Boot has finished starting.

```sh
docker compose ps                 # Show containers and ports
docker compose down               # Stop/remove containers; preserve database data
docker compose up --build -d       # Rebuild after code changes and start again
```

The `contextiq-data` volume retains database data. Do not add `-v` to `docker compose down` unless you intend to delete that data. Changing credentials in `.env` does not update users in an already initialized PostgreSQL volume.

## Start the frontend

In a second terminal:

```sh
cd ../ContextIQ-frontend
npm ci
cp .env.example .env # optional: the same local API URL is the default
npm run dev
```

Open http://localhost:5173. Vite uses strict port 5173, matching the default allowed CORS origin. Public routes: `/login`, `/register`. Protected routes: `/dashboard`, `/chat`, `/documents`, `/workspaces`, `/settings`; `/` redirects to `/dashboard`. A production static host must rewrite these routes to `index.html`.

## Environment

| Variable | Purpose / default |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set in the process environment or IntelliJ run configuration: `dev` for local startup, `prod` for deployment preparation. An uppercase entry imported from `.env` does not activate a profile. |
| `DB_URL` | Required JDBC URL; dev fallback `jdbc:postgresql://localhost:5432/contextiq` |
| `DB_USERNAME`, `DB_PASSWORD` | Required, no credentials committed; used by Java and Compose |
| `DB_NAME` | Compose database name, defaults to `contextiq`; update DB_URL if changed |
| `DB_PORT` | Compose host port, defaults to `5432`; update DB_URL if changed |
| `FRONTEND_URL` | Exact CORS origin; default `http://localhost:5173`, required in prod |
| `SERVER_ADDRESS` | Backend bind address, default `127.0.0.1` |
| `PORT` | Backend port, default `8080` |
| `GOOGLE_LOGIN_ENABLED` | Defaults to false; enable after configuring credentials |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Server-only Google OAuth web client credentials |
| `SESSION_COOKIE_SECURE` | false for localhost HTTP; set true on HTTPS deployments |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text:latest`; 768-dimensional Nomic embeddings |
| `RAG_MIN_SCORE` | Minimum cosine similarity for retrieval, default `0.45` |
| `VITE_API_BASE_URL` | Frontend API root, default `http://localhost:8080/api/v1` |

Never place provider API keys in Vite variables: those values are public browser code. A prod profile alone does not make this development foundation production-ready.

## Checks

```sh
# Backend: H2 PostgreSQL compatibility mode, actual Flyway migration, schema validation
./mvnw test
./mvnw package
# Frontend
cd ../ContextIQ-frontend
npm run typecheck
npm run build
```

Backend tests cover context startup, health, mock dashboard, blank/missing inputs, malformed IDs, missing conversations, mock chat, persistence and history reuse. H2 tests do not replace PostgreSQL verification. To verify the full stack, start both services, send a chat message, reload its URL, and confirm history remains. Restart the backend and repeat to verify durable storage. Inspect `/actuator/health` for database readiness; `/api/v1/health` is an application liveness/identity endpoint.

## API routes

- `GET /api/v1/health`
- `GET /api/v1/dashboard/summary` — explicitly MOCK metrics
- `POST /api/v1/chat/messages` — persist a user/assistant turn, create conversation if ID omitted
- `GET /api/v1/chat/conversations`
- `GET /api/v1/chat/conversations/{id}/messages`
- `GET /api/v1/ai/status`
- `GET /api/v1/documents`
- `POST /api/v1/documents` — multipart PDF/Word/TXT/Markdown upload and indexing
- `DELETE /api/v1/documents/{id}` — remove owned document and chunks
- `GET /actuator/health` and `/actuator/info`

See [authentication setup and auth API](docs/authentication.md), [API contract](docs/api-contract.md), [architecture](docs/architecture.md), and [roadmap](docs/roadmap.md).

## Limitations and next steps

OLLAMA mode calls your local model through Spring AI; MOCK mode remains deterministic and never calls a provider. Documents are real, private PDF/DOC/DOCX/TXT/Markdown uploads with local Nomic embeddings, pgvector retrieval and saved source excerpts. Dashboard metrics and recent activity remain synthetic. OCR, background ingestion, hybrid ranking, workspace membership, account linking, and agents remain deferred. Conversations are private to their owning account. History is unpaginated for Phase 0; add bounded pagination before larger datasets. Sending is transactional, but retries are not idempotent: after an ambiguous timeout, inspect history before retrying. Token usage is recorded on generated assistant messages; streaming is deferred.

Phase 3 adds opt-in grounded chat using documents owned by the signed-in account. See [Phase 3 setup, evaluation and limitations](docs/phase-3-rag.md). Streaming and agents remain deferred. Spring AI 2.0.2 provides the native Ollama integration. See [Phase 2 setup and limitations](docs/phase-2-ollama.md).

## Ollama environment

`AI_MODE` defaults to `OLLAMA` (`MOCK` for offline demos). `OLLAMA_MODEL` defaults to `gemma4:e2b`. Java on the Mac uses `OLLAMA_BASE_URL=http://localhost:11434`; Compose uses `OLLAMA_DOCKER_BASE_URL=http://host.docker.internal:11434`. Output/context defaults are 1200/16384 tokens. No API key is required. See [setup instructions](docs/phase-2-ollama.md).

PDF and Word extraction uses Spring AI `TikaDocumentReader` with Apache Tika. See [file support, limits and rollout](docs/tika-ingestion.md). No database migration is needed for this reader extension.


### Saved document scope in chat

Enable **Search selected documents**, expand **Select documents**, and choose one or several indexed files. Chips appear above the composer. New-chat selections save with the first successful message; existing-chat changes save immediately. Reloading or reopening a conversation restores both the selection and search mode. Deleted sources stay labelled **Unavailable (deleted)** and are excluded from retrieval. Expand a numbered source below an answer to inspect the saved evidence excerpt.

This update adds Flyway **V6**. Apply it by rebuilding/recreating only your backend through your normal Docker workflow (`docker compose up -d --build --force-recreate --no-deps backend` from the backend directory). No database recreation is needed when Phase 3/pgvector is already running. The frontend blocks sending until the backend advertises the new selection capability. No services are restarted automatically by this change.

Selection endpoints: `GET` and `POST /api/v1/chat/conversations/{id}/documents`; see [API contract](docs/api-contract.md). Verify by selecting two files, asking a question, reloading, and expanding the answer's source. Deleting one selected file should leave its unavailable chip; an empty or unavailable-only scope never searches other files.


### Folders and the redesigned library

Create folders from **Documents → New folder**, choose one of eight preset vector icons and six muted colors, and save. Select a folder pill to filter; use **Edit folder** to change its name/icon/color. Additional controls filter by file type and filename, or sort by date/name.

Select document checkboxes, choose a destination, and click **Move** (up to 100 files). Choose **Unfiled** to remove a folder assignment. Files live in one folder at a time; moving does not re-embed them. Uploads can be assigned directly to a folder.

In chat, enable **Search knowledge → Choose sources**. **Folders** selects current folder contents; **Individual files** retains explicit-file selection with a folder filter. Both can be combined. Folder selections save with the conversation, and files added to those folders are included in future queries. Earlier evidence snapshots stay unchanged.

Apply Flyway **V7** by rebuilding only the backend:

```sh
cd /Users/gihanpunarji/Developer/ContextIQ
docker compose up -d --build --force-recreate --no-deps backend
```

Refresh the frontend afterwards. No database recreation or model changes are required. Until rebuilt, the current backend cannot serve folder routes; the UI keeps existing documents accessible and disables folder-scoped chat. The implementation does not automatically start/restart services.

Regression tests cover folder validation and ownership, atomic moves, upload assignment, saved chat folders, changing membership and retrieval union/deduplication. The retrieval-scope SQL test substitutes distance arithmetic under H2; it verifies filters and limits, not native pgvector distance calculations. Live PostgreSQL/Ollama verification remains a manual rollout check.
