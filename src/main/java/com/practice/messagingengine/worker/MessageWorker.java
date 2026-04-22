package com.practice.messagingengine.worker;

import com.practice.messagingengine.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MessageWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageWorker.class);
    private static final int BATCH_SIZE = 500;
    private static final String WORKER_KEY = "worker:enabled";

    private final MessageService messageService;
    private final StringRedisTemplate redisTemplate;

    public MessageWorker(MessageService messageService, StringRedisTemplate redisTemplate) {
        this.messageService = messageService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void run() {
        if (!isEnabled()) return;
        if (messageService.queueSize() == 0) return;

        int processed = messageService.consumeBatch(BATCH_SIZE).size();
        log.debug("[Worker] {}건 처리 완료, 잔여 {}건", processed, messageService.queueSize());
    }

    public void enable() {
        redisTemplate.opsForValue().set(WORKER_KEY, "true");
        log.info("[Worker] 활성화");
    }

    public void disable() {
        redisTemplate.opsForValue().set(WORKER_KEY, "false");
        log.info("[Worker] 비활성화");
    }

    public boolean isEnabled() {
        return "true".equals(redisTemplate.opsForValue().get(WORKER_KEY));
    }
}
