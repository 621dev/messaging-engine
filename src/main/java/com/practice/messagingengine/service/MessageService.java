package com.practice.messagingengine.service;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.domain.MessageStatus;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.repository.MessageLogRepository;
import com.practice.messagingengine.sender.MessageSenderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String QUEUE_KEY = "message_queue";

    private final StringRedisTemplate redisTemplate;
    private final MessageLogRepository messageLogRepository;
    private final MessageSenderRouter senderRouter;

    public MessageService(StringRedisTemplate redisTemplate,
                          MessageLogRepository messageLogRepository,
                          MessageSenderRouter senderRouter) {
        this.redisTemplate = redisTemplate;
        this.messageLogRepository = messageLogRepository;
        this.senderRouter = senderRouter;
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

        MessageLog message = messageLogRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalStateException("MessageLog not found: " + messageId));

        try {
            senderRouter.route(message.getMessageType()).send(message);
            message.setStatus(MessageStatus.SUCCESS);
            message.setSentAt(LocalDateTime.now());
            log.info("[발송 완료] messageId={} type={}", messageId, message.getMessageType());
        } catch (Exception e) {
            message.setStatus(MessageStatus.FAILED);
            log.error("[발송 실패] messageId={} type={} reason={}", messageId, message.getMessageType(), e.getMessage());
        }

        return messageLogRepository.save(message);
    }

    @Transactional
    public MessageLog consumeByMessageId(String messageId) {
        Long removed = redisTemplate.opsForList().remove(QUEUE_KEY, 1, messageId);
        if (removed == null || removed == 0) {
            return null;
        }

        MessageLog message = messageLogRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalStateException("MessageLog not found: " + messageId));

        try {
            senderRouter.route(message.getMessageType()).send(message);
            message.setStatus(MessageStatus.SUCCESS);
            message.setSentAt(LocalDateTime.now());
            log.info("[발송 완료] messageId={} type={}", messageId, message.getMessageType());
        } catch (Exception e) {
            message.setStatus(MessageStatus.FAILED);
            log.error("[발송 실패] messageId={} type={} reason={}", messageId, message.getMessageType(), e.getMessage());
        }

        return messageLogRepository.save(message);
    }

    public List<MessageLog> consumeAll() {
        List<MessageLog> results = new java.util.ArrayList<>();
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
}
