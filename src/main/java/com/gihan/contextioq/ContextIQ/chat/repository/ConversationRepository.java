package com.gihan.contextioq.ContextIQ.chat.repository;

import com.gihan.contextioq.ContextIQ.chat.entity.Conversation;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ConversationRepository
  extends JpaRepository<Conversation, UUID>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
    "select c from Conversation c where c.id = :id and c.ownerId = :ownerId"
  )
  Optional<Conversation> findForUpdateById(UUID id, UUID ownerId);

  List<Conversation> findByOwnerId(
    UUID ownerId,
    org.springframework.data.domain.Sort sort
  );
  boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
