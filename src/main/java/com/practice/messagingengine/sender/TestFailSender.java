package com.practice.messagingengine.sender;

import com.practice.messagingengine.domain.MessageLog;
import org.springframework.stereotype.Component;

@Component
public class TestFailSender implements MessageSender {

    @Override
    public void send(MessageLog message) {
        throw new RuntimeException("TESTFAIL: 의도된 발송 실패 (messageId=" + message.getMessageId() + ")");
    }
}
