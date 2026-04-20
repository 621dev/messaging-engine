package com.practice.messagingengine.service;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.domain.MessageType;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 단위 테스트")
class MessageServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ListOperations<String, String> listOps;

    @Mock
    MessageLogRepository messageLogRepository;

    @InjectMocks
    MessageService messageService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    // ---------------------------------------------------------------
    // send()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("send()")
    class Send {

        @Test
        @DisplayName("정상 전송 시 UUID messageId를 반환하고 Redis에 push한다")
        void send_success() {
            MessageRequest req = request("user1", "01012345678", "hello", MessageType.SMS);
            MessageLog saved = savedLog("uuid-001", MessageStatus.PENDING);
            given(messageLogRepository.save(any())).willReturn(saved);

            String messageId = messageService.send(req);

            assertThat(messageId).isNotBlank();
            then(listOps).should().rightPush(eq("message_queue"), anyString());
            ArgumentCaptor<MessageLog> captor = ArgumentCaptor.forClass(MessageLog.class);
            then(messageLogRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MessageStatus.PENDING);
            assertThat(captor.getValue().getSenderId()).isEqualTo("user1");
        }

        @Test
        @DisplayName("content가 null이면 예외를 던진다")
        void send_nullContent_throws() {
            MessageRequest req = request("user1", "01012345678", null, MessageType.SMS);

            assertThatThrownBy(() -> messageService.send(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("content must not be blank");
        }

        @Test
        @DisplayName("content가 공백이면 예외를 던진다")
        void send_blankContent_throws() {
            MessageRequest req = request("user1", "01012345678", "   ", MessageType.SMS);

            assertThatThrownBy(() -> messageService.send(req))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("각 MessageType(SMS/LMS/KAKAO/EMAIL) 모두 정상 처리된다")
        void send_allMessageTypes() {
            for (MessageType type : MessageType.values()) {
                MessageRequest req = request("sender", "receiver", "content", type);
                MessageLog saved = savedLog("id-" + type, MessageStatus.PENDING);
                given(messageLogRepository.save(any())).willReturn(saved);

                String id = messageService.send(req);

                assertThat(id).isNotBlank();
            }
        }
    }

    // ---------------------------------------------------------------
    // consume()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("consume()")
    class Consume {

        @Test
        @DisplayName("큐에 메시지가 있으면 꺼내고 상태를 SUCCESS로 변경한다")
        void consume_success() {
            String messageId = "uuid-001";
            MessageLog log = savedLog(messageId, MessageStatus.PENDING);
            given(listOps.leftPop("message_queue")).willReturn(messageId);
            given(messageLogRepository.findByMessageId(messageId)).willReturn(Optional.of(log));
            given(messageLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            MessageLog result = messageService.consume();

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(MessageStatus.SUCCESS);
            assertThat(result.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("큐가 비어 있으면 null을 반환한다")
        void consume_emptyQueue_returnsNull() {
            given(listOps.leftPop("message_queue")).willReturn(null);

            MessageLog result = messageService.consume();

            assertThat(result).isNull();
            then(messageLogRepository).should(never()).findByMessageId(any());
        }

        @Test
        @DisplayName("Redis에는 있지만 DB에 없으면 예외를 던진다")
        void consume_notFoundInDb_throws() {
            given(listOps.leftPop("message_queue")).willReturn("ghost-id");
            given(messageLogRepository.findByMessageId("ghost-id")).willReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.consume())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ghost-id");
        }
    }

    // ---------------------------------------------------------------
    // queueSize() / peek()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("queueSize() / peek()")
    class QueueInfo {

        @Test
        @DisplayName("queueSize는 Redis LLEN 결과를 그대로 반환한다")
        void queueSize_returnsRedisSize() {
            given(listOps.size("message_queue")).willReturn(7L);

            assertThat(messageService.queueSize()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Redis가 null 반환 시 0을 반환한다")
        void queueSize_nullFromRedis_returnsZero() {
            given(listOps.size("message_queue")).willReturn(null);

            assertThat(messageService.queueSize()).isEqualTo(0L);
        }

        @Test
        @DisplayName("peek은 Redis LRANGE 결과를 반환한다")
        void peek_returnsRange() {
            List<String> ids = List.of("id1", "id2", "id3");
            given(listOps.range("message_queue", 0, 4)).willReturn(ids);

            List<String> result = messageService.peek(5);

            assertThat(result).containsExactly("id1", "id2", "id3");
        }
    }

    // ---------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------
    private MessageRequest request(String senderId, String receiver, String content, MessageType type) {
        MessageRequest r = new MessageRequest();
        r.setSenderId(senderId);
        r.setReceiver(receiver);
        r.setContent(content);
        r.setMessageType(type);
        return r;
    }

    private MessageLog savedLog(String messageId, MessageStatus status) {
        MessageLog log = new MessageLog();
        log.setMessageId(messageId);
        log.setStatus(status);
        return log;
    }
}
