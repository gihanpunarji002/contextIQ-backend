# Phase 3 — private document RAG

ContextIQ now supports authenticated TXT/Markdown upload, local Nomic embeddings through Spring AI, exact cosine retrieval in PostgreSQL/pgvector, and Gemma answers grounded in retrieved passages. No external AI account or API key is required. General chat remains available independently.

## Apply to the existing Docker setup

No application services were started/restarted during implementation. The database image was built for verification, but the running database has not been replaced or migrated. The old `postgres:17-alpine` image lacks pgvector. `Dockerfile.postgres` builds pgvector **0.8.7** on the same PostgreSQL 17 Alpine base and Compose retains the existing named `contextiq-data` volume. Do not run `down -v`.

From `/Users/gihanpunarji/Developer/ContextIQ`, back up your existing database first:

```sh
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > /tmp/contextiq-before-rag.dump
# Check that this completed successfully before proceeding.
```

Apply the database image **before** deploying the new backend:

```sh
docker compose up -d --build --wait postgres
docker compose up -d --build --force-recreate --no-deps backend
docker compose logs -f backend
```

The database recreation briefly disconnects the current backend. Flyway then applies V4 (documents, chunks, source snapshots and retrieval flag) and V5 (vector extension and 768-dimensional vector column). Existing accounts and conversations stay intact. Normal startup requires a database role allowed to install `vector`; the local Compose role has that permission. Managed deployments should provision the extension with an administrator first.

Settings already have these defaults; existing `.env` secrets need no changes:

```dotenv
AI_MODE=OLLAMA
OLLAMA_MODEL=gemma4:e2b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text:latest
OLLAMA_DOCKER_BASE_URL=http://host.docker.internal:11434
RAG_MIN_SCORE=0.45
```

The already-running host Ollama must have both models. Neither Spring AI nor the application pulls models automatically. `GET /ai/status` reports the chat configuration, not embedding readiness. If uploads fail, check `ollama list`, Ollama availability, and backend logs.

## Use it

1. Open http://localhost:5173 and sign in again after the backend restart.
2. In **Documents**, select a UTF-8 `.txt` or `.md` file (maximum 64 KiB) and choose **Upload and index**. Wait for success. Only then does the document appear in the indexed list.
3. In **Chat**, enable **Search my documents**, then ask a self-contained question about the file.
4. Expand the numbered source excerpts beneath the answer. These are retrieved evidence, not an assertion that every generated statement is correct.
5. Reload the conversation: its answer and source snapshots remain available.
6. Disable document search for general chat. Deleting a document removes its chunks from subsequent retrieval, but existing chat excerpts remain as historical snapshots.

## Data flow and boundaries

Upload → validate extension, size, strict UTF-8 and control characters → chunks of at most 1200 Unicode code points, with 200-code-point overlap → `search_document:` task prefix → Spring AI `OllamaEmbeddingModel` → validate 768 finite, nonzero vector dimensions → one transaction saves document/chunks/vectors. The original file is not stored separately; normalized chunk text is the persisted content. Indexing is synchronous. A failed embedding or insert leaves no partially indexed document. Uploading the same file again creates another document; after an ambiguous timeout, inspect the list before retrying.

Document chat → verify conversation ownership → `search_query:` query embedding → SQL filters on owner and embedding model → exact cosine scoring → up to 4 passages scoring at least `RAG_MIN_SCORE` → current question and source text passed to Gemma → answer and source snapshots committed atomically with the conversation update. No matches produces a deterministic no-evidence response (zero token counts, `model=null`) without calling Gemma. MOCK mode rejects document-chat requests with 503 rather than pretending to use RAG.

Grounded mode deliberately does not use earlier generated answers as evidence or rewrite ambiguous follow-up questions. Ask “What is Juniper's launch date?” rather than “When is it?”. The model is instructed to treat source text as untrusted data, answer only from evidence and cite `[1]`, `[2]`, etc. Prompt instructions cannot guarantee factuality or immunity to prompt injection; source excerpts support manual verification. No execution tools are available.

## Storage and access

- `documents`: owner, filename, bytes, chunk count, embedding model and UTC timestamp.
- `document_chunks`: document FK with cascade deletion, chunk order, text, `vector(768)` embedding.
- `message_sources`: ordered immutable passage snapshots attached to a message; retained when the original document is deleted.
- `messages.retrieval_used`: distinguishes document answers from general chat, including no-match answers.

Every list, delete and retrieval operation is scoped to the current account. Chat source history is returned only after conversation ownership is verified. Filename metadata is not a filesystem path. UI displays document text as escaped text, not rendered HTML. Multipart and DELETE requests retain session authentication, exact-origin CORS and CSRF protection.

Exact search is intentional for the initial small library; it avoids approximate-index recall problems with per-owner filtering. Add HNSW only with an evaluation of filtered recall. Documents/history are currently unpaginated and there is no account storage quota, background job queue or request-rate limit; this remains a local development feature.

The embedding alias must remain fixed while the library is indexed. Changing the embedding model requires deleting/reuploading documents; a schema migration is needed for dimensions other than 768. Updating the weights behind the `latest` tag also requires reindexing. Pin a local model tag for stable evaluations. Model digests/versioned reindexing are future work.

## Verification and small evaluation set

```sh
./mvnw clean test
# Optional: calls already-running local Ollama, no service startup:
CONTEXTIQ_RAG_SMOKE=true ./mvnw -Dtest=RagLiveTests test
cd ../ContextIQ-frontend
npm run typecheck
npm run build
```

Tests cover upload/auth/CSRF/CORS validation, account-isolated list/delete, cascade deletion, failed embedding cleanup, chunk overlap/Unicode, query task prefix/dimensions, retrieval owner parameters, source persistence, and no-evidence behavior. Standard tests use H2 with a test-only TEXT embedding column; **they do not validate pgvector SQL or PostgreSQL ranking**. The live smoke test checks real Nomic dimensions, relative relevance and a cited Gemma answer using synthetic text, but does not exercise the database.

After manual rollout, verify the real pgvector path with this fixture (save it as `launch.txt`):

```text
Project Juniper launches on May 14. The launch owner is Morgan.
The support escalation mailbox is juniper-support@example.test.
```

| Check | Expected |
| --- | --- |
| Upload and ask “When does Project Juniper launch?” | May 14, source `[1]` or another matching passage, stored excerpts after reload |
| Ask “Who owns the launch?” | Morgan, grounded in the fixture |
| Ask “What is the launch budget?” | Explicitly unknown from these documents; no invented budget |
| Ask from a second account with no documents | No matching sources; no other account's passage returned |
| Delete the fixture, then ask again in document mode | No matches; previous conversation snapshots remain visible |
| Disable document search | Normal Ollama general chat |

A similarity threshold is a heuristic, not an answer-confidence score. Tune it with representative documents and questions; low thresholds may retrieve irrelevant passages, high thresholds may miss useful ones. Hybrid search, reranking, PDF/DOCX/OCR, streaming, formal evaluation runs and document versioning remain future work.

References: [Spring AI Ollama embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html), [Nomic task prefixes and dimensions](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5), [pgvector](https://github.com/pgvector/pgvector).
