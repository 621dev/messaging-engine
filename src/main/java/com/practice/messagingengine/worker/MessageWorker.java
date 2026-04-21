package com.practice.messagingengine.worker;

import com.practice.messagingengine.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MessageWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageWorker.class);
    private static final int BATCH_SIZE = 100;

    private final MessageService messageService;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public MessageWorker(MessageService messageService) {
        this.messageService = messageService;
    }

    @Scheduled(fixedDelay = 1000)
    public void run() {
        if (!enabled.get()) return;
        if (messageService.queueSize() == 0) return;

        int processed = messageService.consumeBatch(BATCH_SIZE).size();
        log.info("[Worker] {}건 처리 완료, 잔여 {}건", processed, messageService.queueSize());
    }

    public void enable() {
        enabled.set(true);
        log.info("[Worker] 활성화");
    }

    public void disable() {
        enabled.set(false);
        log.info("[Worker] 비활성화");
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}
