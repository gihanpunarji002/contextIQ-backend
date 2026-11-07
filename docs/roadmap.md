# Roadmap

| Phase | Scope | Exit signal |
| --- | --- | --- |
| 0 | Foundation, PostgreSQL/Flyway, persistent mock chat, responsive frontend | Reproducible local setup and verified API/UI flow |
| 1 | Real Gemini integration through server-side provider, token usage, streaming | Provider failure/timeout handling and measured usage |
| 2 | Document parsing, background ingestion, chunking, pgvector indexing | Versioned, retryable ingestion and cited retrieval |
| 3 | Retrieval evaluation, hybrid search, ranking | Repeatable quality benchmark and regression checks |
| 4 | Tools and agents | Scoped tool access, approvals and execution audit |
| 5 | Tenant isolation, organization authorization and authentication hardening | Enforced organization/workspace/document permissions and isolation tests |
| 6 | Production hardening | Limits, observability, backups/restore, deployment and security review |

Phases 1–4 remain local development until authentication and isolation are enforced. No production enterprise data should be introduced before the required access controls. Each phase should add only the schema and abstractions its functioning features require.

Account registration, password login, Google OIDC and per-account conversation ownership were brought forward at user request. Email verification, recovery, MFA, account linking and tenant membership remain future work.
