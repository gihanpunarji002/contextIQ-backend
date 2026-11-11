# PDF and Word ingestion with Tika

Spring AI **2.0.2** `spring-ai-tika-document-reader` supplies `TikaDocumentReader`; the BOM-resolved Apache Tika version is **3.3.1**. Extracted text feeds the existing overlapping chunks, local Nomic embeddings, owner-filtered pgvector search and Gemma answers. No extra AI provider or database schema is required.

## Apply this update

If Phase 3/pgvector is already deployed, rebuild only the backend:

```sh
cd /Users/gihanpunarji/Developer/ContextIQ
docker compose up -d --build --force-recreate --no-deps backend
```

If Phase 3 is not deployed yet, first follow [the database rollout](phase-3-rag.md). The existing Vite frontend hot-reloads these UI changes; refresh the page and sign in again after the backend restart. No services were started/restarted during development of this extension.

## Supported inputs

| Format | Upload limit | Extraction |
| --- | --- | --- |
| TXT, Markdown | 64 KiB | Strict UTF-8, no binary control characters |
| PDF | 10 MiB | Existing text layer through Tika; OCR explicitly disabled |
| DOC | 10 MiB | Legacy Microsoft Word text through Tika |
| DOCX | 10 MiB | Word paragraphs and table text through Tika |

All documents must produce nonblank text and stay within **100,000 extracted UTF-16 characters**. Files exceeding the text cap are rejected entirely, never silently truncated. DOCX/ZIP inputs are limited to **1,000 entries and 32 MiB total expanded data** before type detection/parsing. The multipart request limit is 11 MiB to allow envelope overhead around a 10 MiB file.

Select the file in **Documents**, choose **Upload and index**, then enable **Search my documents** in Chat. Extracted text, chunk embeddings and source excerpts persist just as with existing text uploads. Original uploaded files are not stored for download. References remain **filename + passage number**, not PDF page numbers. Table text is extracted, but exact layout, complex reading order and visual meaning are not guaranteed.

A PDF that mixes images and selectable text contributes its available text only; text inside images is not read. Scanned/image-only and blank documents return “No searchable text … OCR is not enabled.” Password-protected files must be exported as unencrypted copies first. No passwords are accepted or stored.

## Validation and failures

- 400: empty file, invalid filename or invalid UTF-8/text input.
- 413: upload, expanded ZIP or extracted-text limit exceeded; split/simplify the file.
- 415: unsupported extension or content that does not match its extension.
- 422: unreadable/corrupt/encrypted file or no searchable text.
- 502: local embedding failure.

Extraction completes before embeddings, and embeddings complete before the database write transaction. Failed extraction leaves no indexed document and does not call Ollama. If the browser times out during indexing, inspect the document list before retrying; upload retries are not idempotent.

`contextiq-tika.xml` restricts parser selection to PDF/Word and disables PDF OCR. `DocumentTextExtractor` selects this bundled configuration via the `tika.config` JVM property because Spring AI's reader does not provide parser/ParseContext injection. The setting is application-wide; do not override it to enable arbitrary parsers or OCR without reviewing ingestion behavior. Each extraction gets a separate bounded SAX handler. No OCR executable, external Tika server or remote parsing service is installed.

The parser runs inside the backend JVM. Byte/text/ZIP limits reduce workload but do not enforce a hard CPU/memory deadline. Process-isolated parsing and queued ingestion remain future production work. Tika's standard parser dependency brings its supported parser libraries; application MIME/extension checks and the explicit configuration limit which formats this endpoint accepts.

## Tests

```sh
./mvnw clean package
cd ../ContextIQ-frontend
npm run typecheck
npm run build
```

Tests use real Tika extraction of generated PDF/DOCX and an Apache-licensed legacy DOC fixture. They cover Word table text, mismatched extensions, malformed PDFs, encryption, no-text PDFs, ZIP expansion, output limits, independent handlers, saved chunk content and no writes/embeddings on extraction failure. Embeddings are mocked in API tests; no running service is started by the tests.

Reference: [Spring AI TikaDocumentReader](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/reader/tika/TikaDocumentReader.html).
