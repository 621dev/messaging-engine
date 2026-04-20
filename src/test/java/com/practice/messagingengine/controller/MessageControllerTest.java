package com.practice.messagingengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.domain.MessageType;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageController.class)
@DisplayName("MessageController 단위 테스트")
class MessageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    MessageService messageService;

    // ---------------------------------------------------------------
    // POST /api/messages
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/messages")
    class PostMessages {

        @Test
        @DisplayName("정상 요청 시 200과 messageId를 반환한다")
        void send_success() throws Exception {
            given(messageService.send(any())).willReturn("test-uuid-001");
            given(messageService.queueSize()).willReturn(1L);

            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson("user1", "01012345678", "hello", "SMS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("queued"))
                    .andExpect(jsonPath("$.messageId").value("test-uuid-001"))
                    .andExpect(jsonPath("$.queueSize").value(1));
        }

        @Test
        @DisplayName("content가 없으면 400을 반환한다")
        void send_missingContent_returns400() throws Exception {
            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderId\":\"user1\",\"receiver\":\"01012345678\",\"messageType\":\"SMS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("content가 빈 문자열이면 400을 반환한다")
        void send_blankContent_returns400() throws Exception {
            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderId\":\"user1\",\"content\":\"\",\"messageType\":\"SMS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("content is required"));
        }

        @Test
        @DisplayName("Body가 비어 있으면 400을 반환한다")
        void send_emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/api/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("MessageType이 LMS/KAKAO/EMAIL인 경우도 정상 처리된다")
        void send_variousTypes_success() throws Exception {
            given(messageService.send(any())).willReturn("uuid-lms");
            given(messageService.queueSize()).willReturn(1L);

            for (String type : List.of("LMS", "KAKAO", "EMAIL")) {
                mockMvc.perform(post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson("sender", "receiver", "content for " + type, type)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("queued"));
            }
        }
    }

    // ---------------------------------------------------------------
    // GET /api/messages/status
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/messages/status")
    class GetStatus {

        @Test
        @DisplayName("queueSize와 recentMessageIds를 반환한다")
        void status_success() throws Exception {
            given(messageService.queueSize()).willReturn(3L);
            given(messageService.peek(5)).willReturn(List.of("id1", "id2", "id3"));

            mockMvc.perform(get("/api/messages/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.queueSize").value(3))
                    .andExpect(jsonPath("$.recentMessageIds").isArray())
                    .andExpect(jsonPath("$.recentMessageIds[0]").value("id1"))
                    .andExpect(jsonPath("$.serverInfo").exists());
        }

        @Test
        @DisplayName("큐가 비어 있을 때 queueSize=0, recentMessageIds=[] 반환한다")
        void status_emptyQueue() throws Exception {
            given(messageService.queueSize()).willReturn(0L);
            given(messageService.peek(5)).willReturn(List.of());

            mockMvc.perform(get("/api/messages/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.queueSize").value(0))
                    .andExpect(jsonPath("$.recentMessageIds").isEmpty());
        }
    }

    // ---------------------------------------------------------------
    // DELETE /api/messages/consume
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("DELETE /api/messages/consume")
    class DeleteConsume {

        @Test
        @DisplayName("큐에 메시지가 있으면 consumed 상태와 내용을 반환한다")
        void consume_success() throws Exception {
            MessageLog log = new MessageLog();
            log.setMessageId("uuid-001");
            log.setContent("hello");
            log.setStatus(MessageStatus.SUCCESS);
            given(messageService.consume()).willReturn(log);
            given(messageService.queueSize()).willReturn(0L);

            mockMvc.perform(delete("/api/messages/consume"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("consumed"))
                    .andExpect(jsonPath("$.messageId").value("uuid-001"))
                    .andExpect(jsonPath("$.content").value("hello"))
                    .andExpect(jsonPath("$.remainingSize").value(0));
        }

        @Test
        @DisplayName("큐가 비어 있으면 empty 상태를 반환한다")
        void consume_emptyQueue() throws Exception {
            given(messageService.consume()).willReturn(null);

            mockMvc.perform(delete("/api/messages/consume"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("empty"))
                    .andExpect(jsonPath("$.message").value("queue is empty"));
        }

        @Test
        @DisplayName("content가 null인 메시지 소비 시 빈 문자열로 응답한다")
        void consume_nullContent_returnsEmptyString() throws Exception {
            MessageLog log = new MessageLog();
            log.setMessageId("uuid-002");
            log.setContent(null);
            log.setStatus(MessageStatus.SUCCESS);
            given(messageService.consume()).willReturn(log);
            given(messageService.queueSize()).willReturn(0L);

            mockMvc.perform(delete("/api/messages/consume"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value(""));
        }
    }

    // ---------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------
    private String toJson(String senderId, String receiver, String content, String messageType) throws Exception {
        MessageRequest req = new MessageRequest();
        req.setSenderId(senderId);
        req.setReceiver(receiver);
        req.setContent(content);
        req.setMessageType(MessageType.valueOf(messageType));
        return objectMapper.writeValueAsString(req);
    }
}
