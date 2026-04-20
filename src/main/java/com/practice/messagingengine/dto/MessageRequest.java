package com.practice.messagingengine.dto;

import com.practice.messagingengine.domain.MessageType;

public class MessageRequest {

    private String senderId;
    private String receiver;
    private MessageType messageType;
    private String content;

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
