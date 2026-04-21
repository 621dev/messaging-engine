package com.practice.messagingengine.repository;

import com.practice.messagingengine.domain.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    Optional<MessageLog> findByMessageId(String messageId);
}
