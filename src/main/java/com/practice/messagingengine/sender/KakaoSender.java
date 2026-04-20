package com.practice.messagingengine.sender;

import com.practice.messagingengine.domain.MessageLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KakaoSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(KakaoSender.class);

    @Override
    public void send(MessageLog message) {
        log.info("[KAKAO 발송] messageId={} | 채널={} | 수신자={} | 내용={}",
                message.getMessageId(),
                message.getSenderId(),
                message.getReceiver(),
                message.getContent());
    }
}
