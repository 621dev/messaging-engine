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
    private static final String WORKER_KEY = "worker:enabled";
    private static final String BATCH_SIZE_KEY = "worker:batch_size";
    private static final int DEFAULT_BATCH_SIZE = 500;

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

        int processed = messageService.consumeBatch(getBatchSize()).size();
        log.info("[Worker] {}건 처리 완료, 잔여 {}건", processed, messageService.queueSize());
    }

    public int getBatchSize() {
        String val = redisTemplate.opsForValue().get(BATCH_SIZE_KEY);
        if (val == null) return DEFAULT_BATCH_SIZE;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return DEFAULT_BATCH_SIZE;
        }
    }

    public void setBatchSize(int size) {
        redisTemplate.opsForValue().set(BATCH_SIZE_KEY, String.valueOf(size));
        log.info("[Worker] 배치 사이즈 변경: {}건", size);
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
