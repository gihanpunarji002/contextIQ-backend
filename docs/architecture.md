# Architecture

## Current application

The sibling React/TypeScript frontend uses React Router, TanStack Query and Axios. Spring Boot 4.1.2, Java 21, Maven/WAR packaging, the original package and application class remain intact. Session-based email/password and optional Google OIDC authentication protect all knowledge and chat routes. Credentialed exact-origin CORS and CSRF headers cover uploads, chat mutations and deletion. Only theme preferences are stored in localStorage; the active conversation ID is in the URL.

`config` owns provider, web and security configuration. `common.exception` provides safe ProblemDetail responses. `chat` owns conversation/message DTOs, JPA persistence and transactional orchestration. `ai.service` supplies general and grounded text generation through Spring AI's native Ollama adapter, with explicit MOCK mode for general-chat demos. `document` owns validation, chunking, upload/list/delete and SQL storage. `rag` owns embedding validation, retrieval and source DTOs. `dashboard` remains explicitly synthetic; workspace membership remains deferred.

## Ingestion

Authenticated multipart upload → validate actual file type and size → extract PDF/DOC/DOCX with TikaDocumentReader (10 MiB uploads, 100,000 extracted UTF-16 characters maximum; OCR disabled), or decode TXT/Markdown (64 KiB, strict UTF-8) → normalize line endings → 1200-code-point chunks with 200-code-point overlap → Nomic `search_document:` embeddings through Spring AI → validate 768 dimensions and finite/nonzero values → one transaction inserts metadata/chunks/vectors. There are no external calls inside the write transaction. Failure does not leave partial documents. Original binary files are not retained separately from chunk text. Filename metadata is never used as a filesystem path.

PostgreSQL uses pgvector installed in a custom PostgreSQL 17 Alpine image, preserving the original volume/base family. Flyway V4 adds relational storage and source snapshots; PostgreSQL-specific V5 installs the vector extension and column. H2 tests use a separate V5 with TEXT storage and do not claim vector-search parity. JPA continues to validate its schema; JDBC handles vector SQL and document/source tables. No application schema auto-creation is used.

## Query and generation

General chat loads up to 20 recent messages within a character budget. Document mode instead retrieves using the current self-contained question; prior generated answers are not source evidence. The repository applies account ownership and embedding-model filters in SQL before sorting exact cosine scores and limiting to four passages. Similarity below the configured threshold is excluded. No matching evidence returns an explicit no-match answer without generation.

For matching evidence, Ollama receives the question and bounded passages, with instructions to treat passages as untrusted data, cite numbered sources, and acknowledge missing information. No tools or browsing are available. Retrieved excerpts are shown separately from generated prose; they do not constitute an automated guarantee of factuality. Follow-up query rewriting, hybrid search, reranking and prompt-injection evaluation remain future work.

ChatService uses short transactions around network calls: read owned conversation state, retrieve/generate outside a transaction, then lock/revalidate the conversation and atomically persist the user message, assistant answer, provenance, source snapshots and updated timestamp. A concurrent update returns 409 rather than appending an answer based on stale history. There are no automatic send retries or idempotency keys; inspect history after ambiguous network failures.

## Data and access

- `app_users`: account identity and password/Google identity.
- `conversations`, `messages`: account-owned chat, UTC timestamps, provider/token metadata and retrieval flag.
- `documents`: owner, filename, size, chunk count and embedding model.
- `document_chunks`: ordered normalized text, FK cascade deletion and `vector(768)` data.
- `message_sources`: ordered passage snapshots persisted with an assistant message. Source snapshots deliberately survive deletion of the original document; conversation authorization protects them.

All document list/delete/retrieval operations use the current account. Unknown or foreign document/conversation IDs return 404. The database has a named persistent volume; credentials stay in the environment. Source and prompt text should not be logged. No shared workspace or tenant permissions are implied by per-account isolation.

## Next extensions

Queued ingestion with status/retry/idempotency, OCR; content hashes and document versions; embedding-model digests and reindexing; lexical/vector hybrid retrieval and reranking; `evaluation_runs` for repeatable recall, groundedness and abstention measurements; organizations/workspaces and document permission grants; `tool_execution_logs` for future agents. Paginate document/chat history and add quotas, rate limits, retention, backup/restore validation and observability before production.

Read [Phase 3 setup and limitations](phase-3-rag.md), [authentication setup](authentication.md) and [API contract](api-contract.md). References: [Spring AI Ollama embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html), [pgvector](https://github.com/pgvector/pgvector).

The pinned Spring AI reader resolves Apache Tika 3.3.1. `contextiq-tika.xml` allows only PDF and Microsoft Word parsers and sets PDF OCR to `no_ocr`. The extractor sets Tika’s documented JVM configuration hook before creating readers because the reader does not expose parser/context injection. A per-request bounded SAX handler rejects excess output rather than indexing a truncated prefix. ZIP expansion is checked before container detection; content detection ignores client MIME headers. Parsing runs in-process; output/ZIP limits are not a hard CPU or memory sandbox.


### Conversation document scope

Flyway V6 adds `conversations.use_documents` and `conversation_documents` (conversation FK, document UUID, filename snapshot, selection order). The document UUID intentionally has no document FK: deletion preserves an unavailable selection rather than silently changing its meaning. Reads join the current account's documents to compute availability. Only owned documents or an existing conversation's tombstones may be saved. Conversation ownership is validated before accessing selections.

Chat snapshots the selected scope before retrieval, searches only those IDs under the existing ownership predicate, and saves selection plus both messages in the final transaction. Selection updates lock and touch the conversation so the existing generation conflict check prevents overwriting a concurrent selection change. External model calls remain outside transactions. Historic answer evidence remains in `message_sources`, independently of current selections and document deletion.
