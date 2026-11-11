# API contract — Phase 3

Base: `http://localhost:8080/api/v1`. JSON requests/responses; UTC ISO-8601 timestamps and UUID identifiers. Session authentication is required except for health and public auth endpoints. Chat history is scoped to the signed-in account. Tenant membership is not yet implemented. See [authentication API and setup](authentication.md). Responses are DTOs without a redundant envelope. Successful existing feature calls return 200; auth registration returns 201 and logout 204. CORS allows the configured exact frontend origin for GET/POST/DELETE and Content-Type/X-CSRF-TOKEN, with credentials allowed. All POST and DELETE requests require a session-backed CSRF token.

## GET /health

Liveness/identity, not database readiness:

```json
{"status":"UP","application":"ContextIQ","phase":"PHASE_0"}
```

## GET /dashboard/summary

Every count is synthetic, not persisted usage:

```json
{"mode":"MOCK","documents":128,"conversations":24,"queries":356,"workspaces":3}
```

## POST /chat/messages

`message` is required, nonblank, at most 8,000 characters; surrounding whitespace is trimmed. `conversationId` is optional/null to create a conversation. Supplied IDs must be valid UUIDs referencing an existing conversation. The title is the first 120 characters of the first message. Both messages and conversation updates persist atomically.

```json
{"message":"How will knowledge search work?"}
```

```json
{
  "conversationId":"11111111-1111-4111-8111-111111111111",
  "userMessageId":"22222222-2222-4222-8222-222222222222",
  "assistantMessageId":"33333333-3333-4333-8333-333333333333",
  "response":"[MOCK] Your message was saved. This is a deterministic demonstration response. No documents were searched and no AI provider was called.",
  "mode":"MOCK"
}
```

To continue, include `conversationId` alongside the next message. No separate create-conversation endpoint is necessary: empty drafts are frontend state. Retries are not idempotent; inspect history after ambiguous network errors. No automatic mutation retries are enabled in the frontend.

## GET /chat/conversations

Returns an array ordered by most recently updated, then ID. No owned conversations: `[]`.

```json
[{"id":"11111111-1111-4111-8111-111111111111","title":"How will knowledge search work?","createdAt":"2026-11-07T09:00:00Z","updatedAt":"2026-11-07T09:00:00.000001Z"}]
```

## GET /chat/conversations/{id}/messages

Returns saved messages in chronological order, then ID. `role` is `USER` or `ASSISTANT`. Historical Phase 0 assistant messages remain MOCK. New responses preserve their actual mode and model. See [Phase 2 API additions](phase-2-ollama.md#api-behavior) for metadata and provider errors.

```json
[{"id":"22222222-2222-4222-8222-222222222222","role":"USER","content":"How will knowledge search work?","createdAt":"2026-11-07T09:00:00Z"}]
```

## Errors

Spring ProblemDetail (`application/problem+json`) includes `type`, `title`, `status`, `detail`, and when supplied by Spring `instance`. Missing/blank/oversized messages, malformed JSON and invalid UUIDs return 400; unknown conversations return 404 on both send and history. Unexpected errors return a generic 500 and are logged server-side without leaking exception details to callers.

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"Conversation not found"}
```

## Actuator (outside /api/v1)

`GET /actuator/health` reports readiness components in aggregate (`{"status":"UP"}`), including database connectivity, without component details; unhealthy status may return 503. `GET /actuator/info` currently returns `{}`. Other actuator endpoints are not exposed.

## Deferred routes

Workspace creation/membership, account linking and AI provider key management remain deferred. Recent dashboard activity is still demo data. Document routes and RAG are implemented as described below. Global search is a visual planned capability, not a functioning search endpoint.

## Phase 2 provider contract

[Ollama chat additions](phase-2-ollama.md#api-behavior) extend send/history DTOs with model and token usage and introduce authenticated GET `/api/v1/ai/status`. Earlier JSON examples above illustrate MOCK mode; new real responses use `mode=OLLAMA`; historical OPENAI records remain valid. No secret is returned by provider status.

## Phase 3 documents

`GET /api/v1/documents` returns the current account's indexed documents, newest first. Empty library: `[]`.

`POST /api/v1/documents` accepts **multipart/form-data**, part `file`, containing a non-empty PDF/DOC/DOCX file up to 10 MiB or UTF-8 `.txt`/`.md` file up to 65,536 bytes. File content must match its extension; client MIME headers are not trusted. All formats have a 100,000-character extracted-text cap; excess text is rejected, not truncated. It indexes synchronously and returns **201** only after all embeddings and chunks are saved. Use the same session and CSRF header as chat. Let the browser set the multipart boundary; do not set a JSON content type.

Example document/list item:

```json
{
  "id": "44444444-4444-4444-8444-444444444444",
  "filename": "launch.md",
  "sizeBytes": 132,
  "chunkCount": 1,
  "embeddingModel": "nomic-embed-text:latest",
  "createdAt": "2026-11-11T03:00:00Z"
}
```

`DELETE /api/v1/documents/{id}` requires ownership and CSRF, returns **204**, and cascades to the document's chunks. Unknown or foreign IDs return **404**. Existing chat source snapshots are retained. Upload validation errors return **400**; unsupported extensions or mismatched file contents **415**; upload, expanded ZIP or extracted-text limits **413**; encrypted, corrupt or textless documents **422**; failed embedding **502**. OCR is disabled; scanned documents without text return an actionable error. Nothing is indexed on embedding failure. Upload retries are not idempotent; check the list after an ambiguous timeout.

## Phase 3 grounded chat

The existing `POST /api/v1/chat/messages` accepts optional `useDocuments` (omitted/null/false means general chat):

```json
{"message":"When does Project Juniper launch?","useDocuments":true}
```

The response retains all previous fields and adds `retrievalUsed` and `sources`. History messages expose the same two fields. Source ordering matches the context's `[1]`, `[2]`, etc. Source content is a snapshot, not a live document download.

```json
{
  "conversationId":"11111111-1111-4111-8111-111111111111",
  "userMessageId":"22222222-2222-4222-8222-222222222222",
  "assistantMessageId":"33333333-3333-4333-8333-333333333333",
  "response":"Project Juniper launches on May 14 [1].",
  "mode":"OLLAMA",
  "model":"gemma4:e2b",
  "inputTokens":154,
  "outputTokens":14,
  "retrievalUsed":true,
  "sources":[{
    "documentId":"44444444-4444-4444-8444-444444444444",
    "chunkId":"55555555-5555-4555-8555-555555555555",
    "filename":"launch.md",
    "chunkIndex":0,
    "content":"Project Juniper launches on May 14.",
    "score":0.81
  }]
}
```

This is an illustrative response; real token counts, answers and similarity vary. `chunkIndex` is zero-based. Similarity is a retrieval score, not confidence in the generated answer. Up to four owner-filtered source passages are returned. No relevant sources returns a persisted explicit no-evidence answer with `retrievalUsed=true`, `sources=[]`, `model=null`, token counts zero and no Gemma call. `mode=OLLAMA` identifies the selected provider path, not proof that generation occurred. MOCK provider + `useDocuments=true` returns **503**. Retrieval/embedding failure does not silently fall back to general chat. Grounded mode uses the current question, not prior chat turns; ask self-contained questions.

`GET /api/v1/ai/status` now also returns `documentSearch: true` for the Ollama adapter (`false` for MOCK). This advertises capability, not a live embedding/database health check. The UI keeps document search disabled if the field is absent, preventing a newer frontend from treating an older backend as RAG-capable.
