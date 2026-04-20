package com.practice.messagingengine.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class MessageLogId implements Serializable {

    private Long id;
    private LocalDateTime createdAt;

    public MessageLogId() {}

    public MessageLogId(Long id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageLogId)) return false;
        MessageLogId that = (MessageLogId) o;
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
