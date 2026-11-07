# API contract — Phase 0

Base: `http://localhost:8080/api/v1`. JSON requests/responses; UTC ISO-8601 timestamps and UUID identifiers. Session authentication is required except for health and public auth endpoints. Chat history is scoped to the signed-in account. Tenant membership is not yet implemented. See [authentication API and setup](authentication.md). Responses are DTOs without a redundant envelope. Successful existing feature calls return 200; auth registration returns 201 and logout 204. CORS allows the configured exact frontend origin for GET/POST and Content-Type/X-CSRF-TOKEN, with credentials allowed. All POST requests require a session-backed CSRF token.

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

Returns saved messages in chronological order, then ID. `role` is `USER` or `ASSISTANT`. All assistant messages in Phase 0 are MOCK.

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

Document upload, metadata CRUD, search/RAG, workspace creation/membership, account linking and AI provider key management have no backend endpoints in Phase 0. Frontend document records and recent activity are clearly labeled demo samples. Global search is a visual planned capability, not a functioning search endpoint.
