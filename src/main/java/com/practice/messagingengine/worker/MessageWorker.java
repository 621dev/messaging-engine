package com.practice.messagingengine.worker;

import com.practice.messagingengine.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MessageWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageWorker.class);

    private final MessageService messageService;

    public MessageWorker(MessageService messageService) {
        this.messageService = messageService;
    }

    // 3초마다 Redis 큐 확인 → 메시지가 있으면 꺼내서 발송
    @Scheduled(fixedDelay = 3000)
    public void process() {
        while (messageService.queueSize() > 0) {
            var result = messageService.consume();
            if (result == null) {
                break;
            }
            log.debug("[Worker] 처리 완료 messageId={} status={}", result.getMessageId(), result.getStatus());
        }
    }
}
