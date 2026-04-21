package com.practice.messagingengine.worker;

import com.practice.messagingengine.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MessageWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageWorker.class);

    private final MessageService messageService;

    public MessageWorker(MessageService messageService) {
        this.messageService = messageService;
    }
}
