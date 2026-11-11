> Historical implementation: OpenAI was replaced by Spring AI + Ollama. Follow [the current setup](phase-2-ollama.md), not the configuration below.

# Phase 2 — OpenAI chat

At the user's request this phase adds real OpenAI text chat to the authenticated foundation. It supersedes the original Gemini-first phase ordering. Ingestion, RAG, embeddings and agents remain deferred. Chat is request/response text in this phase; streaming is not implemented.

## Plus and API access

ChatGPT Plus does not include this app's API usage. OpenAI's current subscriber-credit documentation lists Plus as ineligible. Create an API project/key and configure API billing separately; review usage/spend controls in that project. See [API credits for subscribers](https://developers.openai.com/api/docs/guides/chatgpt-api-credits) and [API quickstart](https://developers.openai.com/api/docs/quickstart).

Never paste your key into a chat, commit it, or place it in a `VITE_*` variable. The React client calls Spring Boot; only Spring Boot talks to OpenAI.

## Setup (run these steps yourself)

1. Create a project API key in the [OpenAI platform](https://platform.openai.com/api-keys), with Responses API permission and access to the chosen model. Ensure API billing/quota is available.
2. Append/update these entries in your existing `ContextIQ/.env`, keeping database and Google OAuth settings unchanged:

   ```dotenv
   AI_MODE=OPENAI
   OPENAI_API_KEY=your-own-api-key
   OPENAI_MODEL=gpt-4.1-mini
   OPENAI_MAX_OUTPUT_TOKENS=1200
   ```

   `gpt-4.1-mini` is a configurable text-model default, not a claim that it is the latest model. Choose another Responses-compatible text model if appropriate for your project. Account access varies. Invalid model/output settings produce explicit errors. Output limits must be between 16 and 8192.
3. Rebuild only the backend when ready:

   ```sh
   cd /Users/gihanpunarji/Developer/ContextIQ
   docker compose up -d --build --no-deps backend
   docker compose logs -f backend
   ```

   No process was started or restarted by Codex for this phase. The running Vite app should hot-reload source changes. If it does not, refresh the page; restart it yourself only if needed.
4. Sign in again after the backend restart and open `/chat`. Confirm the OpenAI model badge is shown. Send a short prompt, then a follow-up referencing the first response. Reload to verify history and token metadata persist.
5. For offline demonstrations, set `AI_MODE=MOCK` and rebuild the backend yourself. There is no automatic fallback from a failed OpenAI call to a fake response.

When the key is absent, the app still starts: the provider status reports `configured=false`, the chat shows a configuration notice, and sending is disabled. The status indicates key presence only; it does not validate billing, permissions, or model access.

## Behavior and data flow

- Authenticated browser → Spring chat service → `POST https://api.openai.com/v1/responses` → transactional persistence → browser. The provider endpoint is fixed in production and does not follow redirects.
- Recent saved user/assistant pairs plus the new user message provide context. At most 20 previous messages are read, and total context is bounded to 32,000 Java characters including the new message. This is a character budget, not an exact token budget. Older context is dropped; no summary is generated. Earlier mock turns are excluded from real-model context.
- `store=false` disables Responses API response storage; it does not mean no provider processing or no other applicable retention. Conversation content is sent to OpenAI. No files, organizational documents, tools or web search are provided. See [Responses API guidance](https://developers.openai.com/api/docs/guides/migrate-to-responses).
- Requests use a 10-second connection timeout and 60-second overall provider timeout. The frontend allows 75 seconds. No automatic retries are made, avoiding unintentional repeated API usage.
- No database transaction or connection is held while awaiting OpenAI. After success a short transaction locks the conversation, validates it has not changed, and saves the user and assistant messages atomically. Concurrent changes return 409 instead of saving a reply generated from stale context. Such an API call can still incur usage even though its output is not saved.
- Provider failures, incomplete output, empty output or malformed responses do not save a successful chat turn. The UI keeps the draft for retry. An ambiguous timeout or local persistence failure may incur API usage; inspect history before retrying. End-to-end idempotency is not implemented.
- Refusal text is displayed as the assistant response. Message text renders as React text, without interpreting provider HTML. Non-text/tool responses are not implemented.
- V3 adds provider mode, model and nullable token counts to messages. Existing assistant messages are labeled MOCK. New assistant messages retain the actual mode/model/usage across reloads; user messages have no provider metadata. Counts are actual provider-reported tokens, not currency estimates.

## API additions

`GET /api/v1/ai/status` requires authentication:

```json
{"mode":"OPENAI","model":"gpt-4.1-mini","configured":true}
```

`POST /api/v1/chat/messages` retains its request shape. Its response adds `model`, `inputTokens`, and `outputTokens`; `mode` can now be OPENAI or MOCK. The history endpoint adds the same fields to each message (null where not applicable).

```json
{
  "conversationId":"11111111-1111-4111-8111-111111111111",
  "userMessageId":"22222222-2222-4222-8222-222222222222",
  "assistantMessageId":"33333333-3333-4333-8333-333333333333",
  "response":"Here is a suggested plan…",
  "mode":"OPENAI",
  "model":"gpt-4.1-mini",
  "inputTokens":90,
  "outputTokens":120
}
```

Existing authentication, ownership checks, request validation and CSRF requirements remain unchanged. Safe ProblemDetail errors: 503 for absent/rejected credentials or interruption, 429 for quota/rate limits, 504 for provider timeout, 502 for provider/model/response failures, and 409 for a concurrent conversation update. Provider bodies, API keys and prompts are never included in application error messages or chat logs.

## Verification and limits

`./mvnw test` runs isolated H2/Flyway and mocked HTTP tests. Test configuration does not import your `.env` and uses MOCK mode; no tests call OpenAI or incur usage. `npm run typecheck` and `npm run build` check the frontend. A real billable OpenAI request requires the user's configured key and manual runtime verification.

The backend's project key pays for requests from all registered users. Keep this development service local; add per-user limits, rate limiting and deployment controls before shared/public access. Streaming, cost accounting, retries with durable idempotency, richer Markdown rendering and advanced context summarization are future work. None of this phase enables document retrieval.
