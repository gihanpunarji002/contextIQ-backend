package com.gihan.contextioq.ContextIQ.document;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class FolderDtos {
    private FolderDtos() {}
    public enum Icon { FOLDER, WORK, ACCOUNTS, AUDITS, AGREEMENT, PROJECT, PEOPLE, ARCHIVE }
    public enum Color { BLUE, VIOLET, GREEN, AMBER, ROSE, SLATE }
    public record FolderRequest(@NotBlank @Size(max = 80) String name, @NotNull Icon icon, @NotNull Color color, UUID parentId) {
        public FolderRequest(String name, Icon icon, Color color) { this(name, icon, color, null); }
    }
    public record FolderView(UUID id, String name, Icon icon, Color color, long documentCount, Instant createdAt, UUID parentId, UUID workspaceId, UUID createdBy) {}
    public record MoveRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> documentIds, UUID folderId) {}
}
