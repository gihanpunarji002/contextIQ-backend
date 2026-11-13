package com.gihan.contextioq.ContextIQ.chat.service;

import com.gihan.contextioq.ContextIQ.ai.service.AiChatService;
import com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.*;
import com.gihan.contextioq.ContextIQ.chat.entity.*;
import com.gihan.contextioq.ContextIQ.chat.repository.*;

import java.time.Instant;
import java.util.*;

import org.slf4j.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ConversationRepository conversations;
    private final ConversationDocuments selections;
    private final com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access;
    private final MessageRepository messages;
    private final AiChatService ai;
    private final com.gihan.contextioq.ContextIQ.rag.RetrievalService retrieval;
    private final com.gihan.contextioq.ContextIQ.document.DocumentRepository documents;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final com.gihan.contextioq.ContextIQ.auth.service.CurrentUser current;

    public ChatService(
            ConversationRepository c,
            ConversationDocuments selections,
            com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access,
            MessageRepository m,
            AiChatService ai,
            com.gihan.contextioq.ContextIQ.auth.service.CurrentUser current,
            org.springframework.transaction.PlatformTransactionManager manager,
            com.gihan.contextioq.ContextIQ.rag.RetrievalService retrieval,
            com.gihan.contextioq.ContextIQ.document.DocumentRepository documents
    ) {
        conversations = c;
        this.selections = selections;
        this.access = access;
        messages = m;
        this.ai = ai;
        this.retrieval = retrieval;
        this.documents = documents;
        this.current = current;
        transactions =
                new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    private record Snapshot(
            UUID id,
            Instant updatedAt,
            List<AiChatService.Turn> history,
            DocumentContext context
    ) {
    }

    public SendResponse send(SendRequest request) {
        var scope = access.current();
        UUID owner = scope.userId();
        String text = request.message().strip();
        Snapshot snapshot = transactions.execute(status -> {
            if (request.conversationId() == null) return new Snapshot(
                    null,
                    null,
                    List.of(),
                    selections.resolve(null, scope.workspaceId(), request.useDocuments(), request.documentIds(), request.folderIds())
            );
            var conversation = conversations
                    .findById(request.conversationId())
                    .filter(c -> owner.equals(c.getOwnerId()) && scope.workspaceId().equals(c.getWorkspaceId()))
                    .orElseThrow(this::notFound);
            var recent = new ArrayList<>(
                    messages.findTop20ByConversationIdOrderByCreatedAtDescIdDesc(
                            conversation.getId()
                    )
            );
            Collections.reverse(recent);
            // Exclude old mock turns from real model context. Keep complete recent turns within a character budget.
            var context = new ArrayList<AiChatService.Turn>();
            int chars = text.length();
            for (int i = recent.size() - 2; i >= 0; i -= 2) {
                Message user = recent.get(i),
                        assistant = recent.get(i + 1);
                if (
                        user.getRole() != MessageRole.USER ||
                                assistant.getRole() != MessageRole.ASSISTANT
                ) continue;
                if (
                        !"MOCK".equals(ai.status().mode()) &&
                                "MOCK".equals(assistant.getMode())
                ) continue;
                int length =
                        user.getContent().length() + assistant.getContent().length();
                if (chars + length > 32000) break;
                chars += length;
                context.addFirst(
                        new AiChatService.Turn("assistant", assistant.getContent())
                );
                context.addFirst(new AiChatService.Turn("user", user.getContent()));
            }
            return new Snapshot(
                    conversation.getId(),
                    conversation.getUpdatedAt(),
                    List.copyOf(context),
                    selections.resolve(conversation.getId(), scope.workspaceId(), request.useDocuments(), request.documentIds(), request.folderIds())
            );
        });
        var input = new ArrayList<>(snapshot.history());
        input.add(new AiChatService.Turn("user", text));
        // Never hold a database connection or row lock across the external API call.
        if (request.useDocuments() && !"OLLAMA".equals(ai.status().mode())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Document chat requires Ollama. Switch the backend to OLLAMA.");
        }
        boolean retrievalUsed = request.useDocuments() && !ai.isIdentityQuestion(text);
        var sources = retrievalUsed ? retrieval.retrieveScoped(scope, text, snapshot.context().documents().stream().filter(SelectedDocument::available).map(SelectedDocument::id).toList(),
                snapshot.context().folders().stream().filter(f -> f.available() && f.documentCount() > 0).map(SelectedFolder::id).toList())
                : java.util.List.<com.gihan.contextioq.ContextIQ.rag.Source>of();
        AiChatService.Reply reply;
        if (!retrievalUsed) reply = ai.reply(List.copyOf(input));
        else if (sources.isEmpty()) reply = new AiChatService.Reply(
                "I couldn't find relevant passages in your documents. Select available documents or ask a more specific question.",
                "OLLAMA", null, 0, 0);
        else reply = ai.groundedReply(text, sources);
        return transactions.execute(status -> {
            access.lock(scope);
            Conversation c;
            if (snapshot.id() == null) c = conversations.save(
                    new Conversation(text.substring(0, Math.min(120, text.length())), owner, scope.workspaceId(), scope.organizationId())
            );
            else {
                c = conversations
                        .findForUpdateById(snapshot.id(), owner)
                        .filter(item -> scope.workspaceId().equals(item.getWorkspaceId()))
                        .orElseThrow(this::notFound);
                if (
                        !c.getUpdatedAt().equals(snapshot.updatedAt())
                ) throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This conversation changed while generating a reply. Reload history before retrying."
                );
            }
            Instant time = Instant.now().truncatedTo(
                    java.time.temporal.ChronoUnit.MICROS
            );
            if (!time.isAfter(c.getUpdatedAt())) time = c
                    .getUpdatedAt()
                    .plusNanos(1000);
            Message user = messages.save(
                    new Message(c, MessageRole.USER, text, time)
            );
            Message assistant = new Message(
                    c,
                    MessageRole.ASSISTANT,
                    reply.content(),
                    time.plusNanos(1000)
            );
            assistant.setProvider(reply);
            assistant.setRetrievalUsed(retrievalUsed);
            messages.saveAndFlush(assistant);
            documents.saveSources(assistant.getId(), sources);
            selections.save(c.getId(), snapshot.context());
            c.touch(assistant.getCreatedAt());
            log.info(
                    "Saved {} chat turn in conversation {}",
                    reply.mode(),
                    c.getId()
            );
            return new SendResponse(
                    c.getId(),
                    user.getId(),
                    assistant.getId(),
                    reply.content(),
                    reply.mode(),
                    reply.model(),
                    reply.inputTokens(),
                    reply.outputTokens(),
                    retrievalUsed,
                    sources
            );
        });
    }

    @Transactional(readOnly = true)
    public DocumentContext documentContext(UUID id) {
        var scope = access.current();
        UUID owner = scope.userId();
        if (!conversations.existsByIdAndOwnerIdAndWorkspaceId(id, owner, scope.workspaceId())) throw notFound();
        return selections.get(id, scope.workspaceId());
    }

    @Transactional
    public DocumentContext selectDocuments(UUID id, DocumentSelection request) {
        var scope = access.lock(access.current());
        UUID owner = scope.userId();
        var c = conversations.findForUpdateById(id, owner).filter(item -> scope.workspaceId().equals(item.getWorkspaceId())).orElseThrow(this::notFound);
        var context = selections.resolve(id, scope.workspaceId(), request.useDocuments(), request.documentIds(), request.folderIds());
        selections.save(id, context);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        c.touch(now.isAfter(c.getUpdatedAt()) ? now : c.getUpdatedAt().plusNanos(1000));
        return context;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Conversation not found"
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationView> conversations() {
        var scope = access.current();
        return conversations
                .findByOwnerIdAndWorkspaceId(
                        scope.userId(), scope.workspaceId(),
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"))
                )
                .stream()
                .map(ConversationView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> history(UUID id) {
        var scope = access.current();
        if (
                !conversations.existsByIdAndOwnerIdAndWorkspaceId(id, scope.userId(), scope.workspaceId())
        ) throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Conversation not found"
        );
        return messages
                .findByConversationIdOrderByCreatedAtAscIdAsc(id)
                .stream()
                .map(message -> MessageView.from(message, documents.sources(message.getId())))
                .toList();
    }
}
