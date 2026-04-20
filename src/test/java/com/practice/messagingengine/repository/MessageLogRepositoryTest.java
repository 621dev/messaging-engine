package com.practice.messagingengine.repository;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.domain.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MessageLogRepository 테스트")
class MessageLogRepositoryTest {

    @Autowired
    MessageLogRepository repository;

    @Test
    @DisplayName("save 후 findByMessageId로 조회할 수 있다")
    void saveAndFindByMessageId() {
        MessageLog log = newLog(UUID.randomUUID().toString(), MessageStatus.PENDING, MessageType.SMS);

        repository.save(log);
        Optional<MessageLog> found = repository.findByMessageId(log.getMessageId());

        assertThat(found).isPresent();
        assertThat(found.get().getSenderId()).isEqualTo("sender1");
        assertThat(found.get().getStatus()).isEqualTo(MessageStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지 않는 messageId 조회 시 empty를 반환한다")
    void findByMessageId_notFound() {
        Optional<MessageLog> result = repository.findByMessageId("non-existent-id");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("상태를 PENDING → SUCCESS로 변경하면 DB에 반영된다")
    void updateStatus_pendingToSuccess() {
        MessageLog log = newLog(UUID.randomUUID().toString(), MessageStatus.PENDING, MessageType.LMS);
        repository.save(log);

        MessageLog saved = repository.findByMessageId(log.getMessageId()).get();
        saved.setStatus(MessageStatus.SUCCESS);
        repository.save(saved);

        MessageLog updated = repository.findByMessageId(log.getMessageId()).get();
        assertThat(updated.getStatus()).isEqualTo(MessageStatus.SUCCESS);
    }

    @Test
    @DisplayName("모든 MessageType 저장 및 조회가 가능하다")
    void saveAllMessageTypes() {
        for (MessageType type : MessageType.values()) {
            String messageId = UUID.randomUUID().toString();
            MessageLog log = newLog(messageId, MessageStatus.PENDING, type);
            repository.save(log);

            MessageLog found = repository.findByMessageId(messageId).get();
            assertThat(found.getMessageType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("여러 건 저장 후 전체 count가 일치한다")
    void saveMultiple_countMatches() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            repository.save(newLog(UUID.randomUUID().toString(), MessageStatus.PENDING, MessageType.SMS));
        }

        assertThat(repository.count()).isGreaterThanOrEqualTo(count);
    }

    @Test
    @DisplayName("createdAt이 @PrePersist에 의해 자동 설정된다")
    void prePersist_setsCreatedAt() {
        MessageLog log = newLog(UUID.randomUUID().toString(), MessageStatus.PENDING, MessageType.EMAIL);
        repository.save(log);

        MessageLog saved = repository.findByMessageId(log.getMessageId()).get();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------
    private MessageLog newLog(String messageId, MessageStatus status, MessageType type) {
        MessageLog log = new MessageLog();
        log.setMessageId(messageId);
        log.setSenderId("sender1");
        log.setReceiver("receiver1");
        log.setMessageType(type);
        log.setContent("test content");
        log.setStatus(status);
        return log;
    }
}
