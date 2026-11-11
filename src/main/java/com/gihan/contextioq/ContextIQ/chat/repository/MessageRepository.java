package com.gihan.contextioq.ContextIQ.chat.repository;

import com.gihan.contextioq.ContextIQ.chat.entity.Message;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
  List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID id);
  List<Message> findTop20ByConversationIdOrderByCreatedAtDescIdDesc(UUID id);
}
