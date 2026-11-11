package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import com.gihan.contextioq.ContextIQ.rag.EmbeddingService;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private final CurrentUser current;
    private final DocumentRepository documents;
    private final TextChunker chunker;
    private final EmbeddingService embeddings;
    private final TransactionTemplate transactions;

    public DocumentService(CurrentUser current, DocumentRepository documents, TextChunker chunker,
                           EmbeddingService embeddings, PlatformTransactionManager manager) {
        this.current = current;
        this.documents = documents;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.transactions = new TransactionTemplate(manager);
    }

    public List<DocumentView> list() { return documents.list(current.get().getId()); }

    public DocumentView upload(MultipartFile file) {
        UUID owner = current.get().getId();
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("")
                .replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 180 || name.chars().anyMatch(Character::isISOControl))
            throw bad("Use a filename of 1–180 characters without control characters.");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt") && !lower.endsWith(".md"))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only UTF-8 .txt and .md files are supported.");
        if (file.isEmpty() || file.getSize() > 65536)
            throw bad("Upload a non-empty text file up to 64 KiB.");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
        } catch (java.io.IOException e) {
            throw bad("The file must contain valid UTF-8 text.");
        }
        if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw bad("Binary files are not supported.");
        var chunks = chunker.split(text.replace("\uFEFF", ""));
        if (chunks.isEmpty()) throw bad("The document contains no searchable text.");
        // Embedding happens before opening a DB transaction. A failure leaves no partial document.
        var vectors = embeddings.documents(chunks);
        String filename = name;
        return transactions.execute(status -> documents.insert(owner, filename, file.getSize(), embeddings.model(), chunks, vectors));
    }

    public void delete(UUID id) {
        if (!documents.delete(id, current.get().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }

    private ResponseStatusException bad(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
