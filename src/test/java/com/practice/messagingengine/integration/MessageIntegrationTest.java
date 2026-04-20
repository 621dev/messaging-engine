package com.practice.messagingengine.integration;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.domain.MessageType;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.repository.MessageLogRepository;
import com.practice.messagingengine.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

/**
 * Redis는 Mock, MySQL은 H2로 대체하는 통합 테스트.
 * Spring 컨텍스트 전체를 올려 Service-Repository 간 실제 흐름을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("메시징 엔진 통합 테스트")
class MessageIntegrationTest {

    @Autowired
    MessageService messageService;

    @Autowired
    MessageLogRepository messageLogRepository;

    @MockBean
    StringRedisTemplate redisTemplate;

    // redisTemplate.opsForList()가 반환할 Mock — @MockBean이 아닌 일반 Mock
    @Mock
    ListOperations<String, String> listOps;

    @BeforeEach
    void setUpRedis() {
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    @AfterEach
    void cleanup() {
        messageLogRepository.deleteAll();
    }

    // ---------------------------------------------------------------
    // send → DB 저장 검증
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("send() 통합 흐름")
    class SendFlow {

        @Test
        @DisplayName("send() 호출 시 MySQL에 PENDING 상태로 저장된다")
        void send_persistsPendingToDB() {
            String messageId = messageService.send(request("u1", "recv", "hello", MessageType.SMS));

            Optional<MessageLog> saved = messageLogRepository.findByMessageId(messageId);
            assertThat(saved).isPresent();
            assertThat(saved.get().getStatus()).isEqualTo(MessageStatus.PENDING);
            assertThat(saved.get().getContent()).isEqualTo("hello");
            assertThat(saved.get().getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("send() 호출 시 Redis rightPush가 1회 호출된다")
        void send_pushesToRedis() {
            messageService.send(request("u1", "recv", "hello", MessageType.SMS));

            then(listOps).should(times(1)).rightPush(eq("message_queue"), anyString());
        }

        @Test
        @DisplayName("10건 연속 전송 시 MySQL에 10건 모두 저장된다")
        void send_multiple_allPersistedToDB() {
            int count = 10;
            for (int i = 0; i < count; i++) {
                messageService.send(request("u" + i, "recv", "msg-" + i, MessageType.SMS));
            }

            assertThat(messageLogRepository.count()).isEqualTo(count);
        }
    }

    // ---------------------------------------------------------------
    // send → consume 전체 흐름
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("send() → consume() 전체 흐름")
    class SendConsumeFlow {

        @Test
        @DisplayName("send 후 consume 시 상태가 PENDING → SUCCESS로 변경된다")
        void sendThenConsume_statusChangesToSuccess() {
            String messageId = messageService.send(request("u1", "recv", "flow test", MessageType.SMS));
            given(listOps.leftPop("message_queue")).willReturn(messageId);

            MessageLog consumed = messageService.consume();

            assertThat(consumed).isNotNull();
            assertThat(consumed.getStatus()).isEqualTo(MessageStatus.SUCCESS);
            assertThat(consumed.getSentAt()).isNotNull();

            // DB에서 직접 재조회하여 최종 상태 확인
            MessageLog dbRecord = messageLogRepository.findByMessageId(messageId).get();
            assertThat(dbRecord.getStatus()).isEqualTo(MessageStatus.SUCCESS);
        }

        @Test
        @DisplayName("FIFO 순서: 5건 전송 후 전송 순서대로 소비된다")
        void fifoOrder_consumedInSendOrder() {
            List<String> sentIds = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                sentIds.add(messageService.send(request("u" + i, "recv", "msg-" + i, MessageType.SMS)));
            }

            // willReturn 체이닝으로 순서대로 반환
            BDDMockito.BDDMyOngoingStubbing<String> stub =
                    given(listOps.leftPop("message_queue")).willReturn(sentIds.get(0));
            for (int i = 1; i < sentIds.size(); i++) {
                stub = stub.willReturn(sentIds.get(i));
            }

            for (int i = 0; i < 5; i++) {
                MessageLog consumed = messageService.consume();
                assertThat(consumed.getMessageId()).isEqualTo(sentIds.get(i));
            }
        }

        @Test
        @DisplayName("큐가 비어 있을 때 consume()은 null을 반환한다")
        void consume_emptyQueue_returnsNull() {
            given(listOps.leftPop("message_queue")).willReturn(null);

            assertThat(messageService.consume()).isNull();
        }
    }

    // ---------------------------------------------------------------
    // 이상 케이스
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("이상 케이스")
    class EdgeCases {

        @Test
        @DisplayName("content가 null이면 DB에 저장되지 않고 예외가 발생한다")
        void send_nullContent_notSavedToDB() {
            assertThatThrownBy(() ->
                    messageService.send(request("u1", "recv", null, MessageType.SMS)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(messageLogRepository.count()).isZero();
        }

        @Test
        @DisplayName("Redis에만 있고 DB에 없는 messageId consume 시 예외가 발생한다")
        void consume_ghostId_throwsIllegalState() {
            given(listOps.leftPop("message_queue")).willReturn("ghost-id-not-in-db");

            assertThatThrownBy(() -> messageService.consume())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ghost-id-not-in-db");
        }

        @Test
        @DisplayName("모든 MessageType(SMS/LMS/KAKAO/EMAIL) 저장 후 DB에서 타입 확인된다")
        void send_allTypes_persistedCorrectly() {
            for (MessageType type : MessageType.values()) {
                String id = messageService.send(request("u1", "recv", "content", type));
                MessageLog log = messageLogRepository.findByMessageId(id).get();
                assertThat(log.getMessageType()).isEqualTo(type);
            }
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
}
