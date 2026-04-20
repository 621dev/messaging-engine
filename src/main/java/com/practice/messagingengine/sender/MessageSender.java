package com.practice.messagingengine.sender;

import com.practice.messagingengine.domain.MessageLog;

public interface MessageSender {

    void send(MessageLog log);
}
