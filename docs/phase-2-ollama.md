# Phase 2 — Spring AI + local Ollama

ContextIQ now uses **Spring AI 2.0.2** with Ollama's native chat API and the exact model **`gemma4:e2b`**. Spring Boot 4.1.2, Java 21, account sessions, conversation ownership and database history are preserved. OpenAI is no longer an active provider and no API key or paid API account is needed.

## Apply the change

Ollama is already running on your Mac and its model list includes `gemma4:e2b`. The existing backend container was verified able to reach it through `host.docker.internal:11434`. No services were started/restarted and no models were downloaded during implementation.

The ignored backend `.env` was updated only to select `AI_MODE=OLLAMA`. Existing secrets were not printed or modified. These are the configurable settings (defaults work for your current Mac/Docker setup):

```dotenv
AI_MODE=OLLAMA
OLLAMA_MODEL=gemma4:e2b
# Used when Java runs directly on the Mac:
OLLAMA_BASE_URL=http://localhost:11434
# Compose forwards this as OLLAMA_BASE_URL inside the backend container:
OLLAMA_DOCKER_BASE_URL=http://host.docker.internal:11434
OLLAMA_MAX_OUTPUT_TOKENS=1200
OLLAMA_CONTEXT_SIZE=16384
```

When ready, rebuild the backend **yourself**:

```sh
cd /Users/gihanpunarji/Developer/ContextIQ
docker compose up -d --build --no-deps backend
docker compose logs -f backend
```

This leaves PostgreSQL and its volume intact. The running Vite frontend should hot-reload. Open **http://localhost:5173** (not 127.0.0.1, because the configured CORS origin is localhost), sign in again after the backend restarts, and open Chat. The badge should show `Ollama · gemma4:e2b`.

Existing `OPENAI_*` entries in your private `.env` are no longer used or forwarded by Compose; you can remove them yourself. Do not copy any API key into the frontend. `AI_MODE=MOCK` remains available for explicit offline demos; OpenAI errors do not fall back to mock responses because OpenAI is no longer called at all.

## Implementation

- Spring AI BOM pins version 2.0.2; `spring-ai-ollama` supplies `OllamaChatModel`. `OllamaConfig` constructs the model explicitly so HTTP timeouts, retry policy and model behavior are visible in code. No OpenAI-compatible proxy/client is used.
- `OllamaAiChatService` maps persisted user/assistant turns to Spring AI messages and supplies the existing ContextIQ system instructions. It maps model output and usage metadata into the existing DTO boundary.
- The existing short database transactions surround generation; no database transaction/row lock is held while Ollama runs. Provider errors save no successful turn, and concurrent history changes return 409.
- Recent history is capped at 20 previous messages and 32,000 Java characters including the current prompt. Mock turns are excluded from real context. Earlier real OpenAI replies can remain in the conversational context but are sent only to local Ollama now. Stored provider labels are never rewritten.
- Default Ollama context size is 16,384 tokens and output limit is 1,200 tokens. The character cap is not an exact tokenizer budget. These settings affect memory and generation time. Output limits are validated from 16–8192; context size from 2048–131072.
- Thinking output is disabled for this text-chat interface. Only final answer text is displayed; no tools, RAG, file ingestion or embeddings are enabled. Your installed embedding model is not used.
- HTTP connect timeout is 10 seconds; read timeout is 120 seconds. Frontend send timeout is 135 seconds. No HTTP redirects, automatic retries or model downloads are enabled. Cold model loading can make the first response slower.
- The local endpoint is backend-only; browser CORS access to Ollama is unnecessary. Do not change Ollama to a public bind address to solve a browser error. If container connectivity later fails, first verify Docker Desktop's host gateway and that Ollama is running.

## API behavior

`GET /api/v1/ai/status` still requires authentication:

```json
{"mode":"OLLAMA","model":"gemma4:e2b","configured":true}
```

`configured=true` means the adapter is configured, not that the server/model has just passed a live health check. Actual generation reports connectivity or missing-model failures.

Chat send and history DTOs keep the same shape. New assistant messages use `mode=OLLAMA`, model `gemma4:e2b`, and Ollama-reported input/output token counts. Historical OPENAI and MOCK records retain their original values. No schema change is needed because V3 already supports provider metadata.

Missing model returns 503, timeout returns 504, and transport/empty/truncated output failures return 502 with a safe actionable message. Error bodies do not expose raw server responses or prompts. Login/CSRF/ownership rules are unchanged.

## Verify

```sh
# Read-only checks against existing Ollama:
curl http://localhost:11434/api/tags

# Isolated database and mock-model tests (no live generation):
./mvnw clean test

# Explicit one-prompt integration test against existing local Ollama:
CONTEXTIQ_OLLAMA_SMOKE=true ./mvnw -Dtest=OllamaLiveTests test

cd ../ContextIQ-frontend
npm run typecheck
npm run build
```

The live test does not start an HTTP application server, launch Ollama or pull a model. It invokes the same configured Spring AI model against the already-running Ollama service with a short, non-sensitive prompt.

References: [Spring AI Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html), [Spring AI 2.0 / Boot 4 compatibility](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/).
