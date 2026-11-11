package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import com.gihan.contextioq.ContextIQ.rag.EmbeddingService;
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
    private final DocumentTextExtractor extractor;
    private final EmbeddingService embeddings;
    private final TransactionTemplate transactions;

    public DocumentService(CurrentUser current, DocumentRepository documents, TextChunker chunker,
                           EmbeddingService embeddings, PlatformTransactionManager manager, DocumentTextExtractor extractor) {
        this.current = current;
        this.documents = documents;
        this.chunker = chunker;
        this.extractor = extractor;
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
        String text = extractor.extract(file, name);
        var chunks = chunker.split(text);
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
