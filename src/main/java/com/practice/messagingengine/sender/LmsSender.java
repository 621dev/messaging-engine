package com.practice.messagingengine.sender;

import com.practice.messagingengine.domain.MessageLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LmsSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(LmsSender.class);

    @Override
    public void send(MessageLog message) {
        log.info("[LMS 발송] messageId={} | 수신자={} | 내용길이={}자 | 내용={}",
                message.getMessageId(),
                message.getReceiver(),
                message.getContent() != null ? message.getContent().length() : 0,
                message.getContent());
    }
}
