package com.practice.messagingengine.sender;

import com.practice.messagingengine.domain.MessageType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MessageSenderRouter {

    private final Map<MessageType, MessageSender> senderMap;

    public MessageSenderRouter(SmsSender smsSender,
                               LmsSender lmsSender,
                               KakaoSender kakaoSender,
                               EmailSender emailSender,
                               TestFailSender testFailSender) {
        this.senderMap = Map.of(
                MessageType.SMS,      smsSender,
                MessageType.LMS,      lmsSender,
                MessageType.KAKAO,    kakaoSender,
                MessageType.EMAIL,    emailSender,
                MessageType.TESTFAIL, testFailSender
        );
    }

    public MessageSender route(MessageType type) {
        MessageSender sender = senderMap.get(type);
        if (sender == null) {
            throw new IllegalArgumentException("지원하지 않는 MessageType: " + type);
        }
        return sender;
    }
}
