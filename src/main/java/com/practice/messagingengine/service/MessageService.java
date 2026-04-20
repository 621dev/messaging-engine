package com.practice.messagingengine.service;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.repository.MessageLogRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final String QUEUE_KEY = "message_queue";

    private final StringRedisTemplate redisTemplate;
    private final MessageLogRepository messageLogRepository;

    public MessageService(StringRedisTemplate redisTemplate, MessageLogRepository messageLogRepository) {
        this.redisTemplate = redisTemplate;
        this.messageLogRepository = messageLogRepository;
    }

    @Transactional
    public String send(MessageRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        String messageId = UUID.randomUUID().toString();

        MessageLog log = new MessageLog();
        log.setMessageId(messageId);
        log.setSenderId(request.getSenderId());
        log.setReceiver(request.getReceiver());
        log.setMessageType(request.getMessageType());
        log.setContent(request.getContent());
        log.setStatus(MessageStatus.PENDING);
        messageLogRepository.save(log);

        redisTemplate.opsForList().rightPush(QUEUE_KEY, messageId);

        return messageId;
    }

    @Transactional
    public MessageLog consume() {
        String messageId = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (messageId == null) {
            return null;
        }

        MessageLog log = messageLogRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalStateException("MessageLog not found: " + messageId));

        log.setStatus(MessageStatus.SUCCESS);
        log.setSentAt(LocalDateTime.now());
        return messageLogRepository.save(log);
    }

    public long queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    public List<String> peek(int count) {
        return redisTemplate.opsForList().range(QUEUE_KEY, 0, count - 1);
    }
}
