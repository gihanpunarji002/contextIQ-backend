# Roadmap

The implementation order follows the user's revised phase requests.

| Phase | Scope | State / next work |
| --- | --- | --- |
| 0 | Spring Boot/PostgreSQL/Flyway, persistent mock chat, React dashboard | Implemented; dashboard metrics remain explicitly synthetic |
| 1 | Email/password registration and login, optional Google OIDC | Implemented with account-owned conversations; recovery/MFA/verification deferred |
| 2 | Spring AI and local Ollama `gemma4:e2b` chat | Implemented; OpenAI replaced; usage/context/errors supported, streaming deferred |
| 3 | Local Nomic embeddings, private documents, pgvector RAG and sources | TXT/Markdown ingestion, owner-filtered exact retrieval, grounded chat and source snapshots implemented; manual database rollout required |
| 3 follow-up | Rich parsing, retrieval quality and scale | PDF/DOCX/OCR, background ingestion, idempotency, versions, hybrid search/reranking and formal evaluation datasets |
| 4 | Tools and agents | Scoped tool access, approvals, execution audit and evaluation |
| 5 | Enterprise identity and tenant isolation | Organization/workspace membership, document grants, account hardening and isolation tests |
| 6 | Production hardening | Quotas/rate limits, pagination, telemetry, backups/restore, TLS, deployment and security review |

Phase 3 includes a small manual evaluation fixture and opt-in local-model smoke test. These are not a production retrieval benchmark. Account isolation is implemented; team tenancy, ingestion workers and enterprise readiness are not implied. See [Phase 3 behavior and rollout](phase-3-rag.md).
