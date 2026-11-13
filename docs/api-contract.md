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
{"message":"When does Project Juniper launch?","useDocuments":true,"documentIds":["<owned-document-uuid>"]}
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


## Saved conversation document selection

`GET /api/v1/chat/conversations/{id}/documents` returns the owned conversation's saved search mode and ordered selection:

```json
{"useDocuments":true,"documents":[{"id":"<document-uuid>","filename":"launch.pdf","available":true}]}
```

`POST /api/v1/chat/conversations/{id}/documents` replaces the selection immediately (session + CSRF required):

```json
{"useDocuments":true,"documentIds":["<document-uuid>","<another-document-uuid>"]}
```

Both fields are required on this selection endpoint. At most 50 non-null UUIDs; duplicates are collapsed. Empty IDs explicitly clear the selection. Unknown/foreign conversations or new unknown/foreign document IDs return **404**. Malformed IDs, missing fields, null list elements or more than 50 IDs return **400**. Previously saved deleted IDs may be retained and return `available:false` with their saved filename. Removing such an ID discards that tombstone; it cannot subsequently be re-added.

Chat POST also accepts optional `documentIds` with the same validation. Omitted/null retains existing selection (empty for a new conversation); `[]` clears it. `useDocuments` omitted/null/false remains general chat. The selection and mode are committed atomically with a successful turn, including no-evidence responses. A provider failure does not save a new conversation or selection from that send. Existing conversation selection can be saved independently using the endpoint above.

Retrieval is restricted to the selected, still-available document IDs **and** account ownership before ranking and limiting; it never falls back to the entire library. An empty/unavailable-only selection returns a no-evidence response without calling the embedding model or LLM; the UI requires at least one available selection before sending in document mode. Old conversations start with general chat and no selection. Switching to general chat retains the selected IDs for later reuse. Concurrent selection changes while generating an answer cause **409**, preventing a stale reply from overwriting the newer selection.

Existing message `sources` are immutable evidence snapshots. Expand a source beneath an answer to inspect its excerpt and retrieval score, including after deleting the original document. Deletion removes the indexed document and chunks, not historic evidence. `GET /api/v1/ai/status` includes `documentSelection:true`; the frontend blocks sending against an older backend to avoid silently ignoring the requested scope.


## Document folders (V7)

Folders are private to the signed-in account. Folder writes and document moves require the existing session and CSRF header. The structure is flat: a document has one `folderId`, or `null` for Unfiled. Moving changes metadata only, preserving document/chunk IDs and embeddings.

- `GET /api/v1/folders` → **200** array ordered by name; each entry contains `id`, `name`, `icon`, `color`, `documentCount`, `createdAt`.
- `POST /api/v1/folders` → **201** creates a folder.
- `POST /api/v1/folders/{id}` → **200** edits the owned folder's name, icon and color.

Create/update body (all fields required):

```json
{"name":"Agreements","icon":"AGREEMENT","color":"VIOLET"}
```

Names are trimmed, 1–80 characters, without control characters; exact names must be unique within an account. A duplicate name returns **409**. Supported icons: `FOLDER`, `WORK`, `ACCOUNTS`, `AUDITS`, `AGREEMENT`, `PROJECT`, `PEOPLE`, `ARCHIVE`. Colors: `BLUE`, `VIOLET`, `GREEN`, `AMBER`, `ROSE`, `SLATE`. Invalid values return **400**. Unknown/foreign folders return **404**. No arbitrary SVG or HTML is accepted.

Example list response:

```json
[{"id":"<folder-uuid>","name":"Agreements","icon":"AGREEMENT","color":"VIOLET","documentCount":2,"createdAt":"2026-11-11T12:00:00Z"}]
```

`POST /api/v1/folders/move` → **204** after moving all requested documents. The operation is atomic; an unknown/foreign document or destination returns **404** and moves none. The list must contain 1–100 non-null document UUIDs (duplicates collapse); invalid lists return **400**. `folderId:null` or omitted moves files to Unfiled.

```json
{"documentIds":["<document-uuid>","<another-document-uuid>"],"folderId":"<folder-uuid>"}
```

`GET /api/v1/documents` now includes nullable `folderId`. The library applies filename, folder and type filters client-side to the owned library. `POST /api/v1/documents` optionally accepts multipart field `folderId`; ownership is checked before embedding and again when saving. Upload and folder assignment commit together; failure leaves no partially indexed file. Existing clients may omit the field.

### Folder scope in chat

Both chat POST and conversation-selection POST accept optional `folderIds` (up to 50 non-null UUIDs). Omitted/null retains the saved folder selection; `[]` clears it. Existing `documentIds` behavior is unchanged. The context response now includes `folders`:

```json
{"useDocuments":true,"documents":[],"folders":[{"id":"<folder-uuid>","name":"Agreements","available":true,"documentCount":2}]}
```

Example mixed scope:

```json
{"message":"What are our payment terms?","useDocuments":true,"documentIds":["<specific-document-uuid>"],"folderIds":["<folder-uuid>"]}
```

Folders and individual files are a **union**, constrained by account ownership and embedding model before ranking/limiting. Each chunk is returned once even if its document is selected both ways. A folder searches current membership at query time: additions and moves affect future answers; previously saved source excerpts are unchanged. Explicit files remain selected even if moved. Empty folders do not broaden the search. Empty/unavailable-only scope returns no evidence; the UI disables sending until a non-empty source is selected. Folder selections persist with the conversation; names/counts refresh on read.

`GET /api/v1/ai/status` includes `folderSelection:true`. The frontend offers folder selection only when advertised; individual-file selection remains usable with the previous document-selection backend. Folder deletion, nesting and multi-folder membership are not implemented in this version.

## Organizations and workspace scope (V8)

All existing document, folder and chat endpoints now accept `X-Workspace-Id`. The server requires active membership; omission selects the caller's private Personal workspace. Reads require any workspace role, knowledge mutations require Editor/Admin. Conversation reads/updates additionally require authorship. Header parsing failure is **400**, inaccessible workspaces/resources **404**, and insufficient mutation privileges **403**. Session/CSRF protections still apply.

`DocumentView`, folder views and conversation views now expose `workspaceId` and `createdBy`. Folder create/update accepts optional `parentId` (null = workspace root). A foreign-workspace parent returns 404; a self/descendant cycle returns **409**. Folder search/counts include descendants. Folder names must be unique within a workspace.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/organizations` | List caller's active organization memberships: `id,name,role` |
| `POST /api/v1/organizations` | `{ "name":"Acme" }`; creates organization and Admin membership; **201** |
| `GET /api/v1/organizations/{id}/members` | Organization Admin-only roster |
| `POST /api/v1/organizations/{id}/members` | `{ "email":"registered@example.com" }`; adds an existing account as organization MEMBER, **204**. Does not grant organization Admin or workspace access |
| `POST /api/v1/organizations/{id}/workspaces` | Organization Admin-only; `{ "name":"HR","description":"HR policies" }`; **201** |
| `GET /api/v1/workspaces` | List accessible active workspaces and real document/folder/member counts |
| `GET /api/v1/workspaces/{id}` | Workspace details, requiring membership |
| `POST /api/v1/workspaces/{id}` | Admin-only name/description update; **200** |
| `DELETE /api/v1/workspaces/{id}` | Admin-only soft deletion; **204**; personal workspace deletion **409** |
| `GET /api/v1/workspaces/{id}/members` | Workspace Admin-only roster |
| `POST /api/v1/workspaces/{id}/members` | Admin-only `{ "email":"registered@example.com","role":"VIEWER" }`; add/update membership, **204** |
| `DELETE /api/v1/workspaces/{id}/members/{userId}` | Admin-only access revocation, **204** |

Workspace roles are `ADMIN`, `EDITOR`, `VIEWER`. Adding a workspace member who is not an active organization member returns **409**. Unknown registered accounts return **404**. Last-Admin removal/demotion, sharing Personal workspaces, and duplicate workspace/folder names return **409**. Invalid DTOs return **400**. No invitations or emails are sent by these endpoints.

Workspace response example:

```json
{"id":"<workspace-uuid>","organizationId":"<organization-uuid>","organizationName":"Acme","name":"HR","description":"HR policies","role":"EDITOR","personal":false,"documentCount":12,"folderCount":3,"memberCount":4,"createdBy":"<user-uuid>","createdAt":"2026-11-12T02:00:00Z"}
```

Member response fields: `userId,firstName,lastName,email,role`. The workspace role version is checked again after generation; revoked access returns **404**, changed role/version **409**, and no generated answer or new turn is persisted. Existing source excerpts stay tenant-scoped through immutable resource references.

See [workspace design and rollout](workspaces.md) for migration, privacy, deletion semantics and test limitations.
