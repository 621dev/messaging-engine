package com.practice.messagingengine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.domain.MessageType;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.repository.MessageLogRepository;
import com.practice.messagingengine.sender.MessageSenderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String QUEUE_KEY = "message_queue";
    private static final String MESSAGE_KEY_PREFIX = "message:";

    private final StringRedisTemplate redisTemplate;
    private final MessageLogRepository messageLogRepository;
    private final MessageSenderRouter senderRouter;
    private final ObjectMapper objectMapper;

    public MessageService(StringRedisTemplate redisTemplate,
                          MessageLogRepository messageLogRepository,
                          MessageSenderRouter senderRouter,
                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messageLogRepository = messageLogRepository;
        this.senderRouter = senderRouter;
        this.objectMapper = objectMapper;
    }

    public String send(MessageRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        String messageId = UUID.randomUUID().toString();

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "messageId",   messageId,
                    "senderId",    request.getSenderId()   != null ? request.getSenderId()   : "",
                    "receiver",    request.getReceiver()   != null ? request.getReceiver()   : "",
                    "messageType", request.getMessageType().name(),
                    "content",     request.getContent()
            ));
            redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + messageId, json);
        } catch (Exception e) {
            throw new RuntimeException("메시지 직렬화 실패: " + messageId, e);
        }

        redisTemplate.opsForList().rightPush(QUEUE_KEY, messageId);
        return messageId;
    }

    @Transactional
    public MessageLog consume() {
        String messageId = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (messageId == null) {
            return null;
        }
        return process(messageId);
    }

    @Transactional
    public MessageLog consumeByMessageId(String messageId) {
        Long removed = redisTemplate.opsForList().remove(QUEUE_KEY, 1, messageId);
        if (removed == null || removed == 0) {
            return null;
        }
        return process(messageId);
    }

    public List<MessageLog> consumeAll() {
        List<MessageLog> results = new ArrayList<>();
        while (queueSize() > 0) {
            MessageLog result = consume();
            if (result == null) break;
            results.add(result);
        }
        return results;
    }

    public long queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    public List<String> peek(int count) {
        return redisTemplate.opsForList().range(QUEUE_KEY, 0, count - 1);
    }

    private MessageLog process(String messageId) {
        String json = redisTemplate.opsForValue().get(MESSAGE_KEY_PREFIX + messageId);
        if (json == null) {
            throw new IllegalStateException("Redis에 메시지 데이터 없음: " + messageId);
        }

        Map<String, String> data;
        try {
            data = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("메시지 역직렬화 실패: " + messageId, e);
        }

        MessageLog message = new MessageLog();
        message.setMessageId(messageId);
        message.setSenderId(data.get("senderId"));
        message.setReceiver(data.get("receiver"));
        message.setMessageType(MessageType.valueOf(data.get("messageType")));
        message.setContent(data.get("content"));

        try {
            senderRouter.route(message.getMessageType()).send(message);
            message.setStatus(MessageStatus.SUCCESS);
            message.setSentAt(LocalDateTime.now());
            log.info("[발송 완료] messageId={} type={}", messageId, message.getMessageType());
        } catch (Exception e) {
            message.setStatus(MessageStatus.FAILED);
            log.error("[발송 실패] messageId={} type={} reason={}", messageId, message.getMessageType(), e.getMessage());
        }

        redisTemplate.delete(MESSAGE_KEY_PREFIX + messageId);
        return messageLogRepository.save(message);
    }
}
