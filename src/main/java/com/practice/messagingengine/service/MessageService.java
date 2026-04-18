package com.practice.messagingengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    private static final String QUEUE_KEY = "message_queue";
    private final StringRedisTemplate redisTemplate;

    public MessageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void send(String message) {
        if (message == null) throw new IllegalArgumentException("message must not be null");
        redisTemplate.opsForList().rightPush(QUEUE_KEY, message);
    }

    public String consume() {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY);
    }

    public long queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    public List<String> peek(int count) {
        return redisTemplate.opsForList().range(QUEUE_KEY, 0, count - 1);
    }
}