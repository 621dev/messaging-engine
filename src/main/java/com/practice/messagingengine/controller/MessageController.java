package com.practice.messagingengine.controller;

import com.practice.messagingengine.domain.MessageLog;
import com.practice.messagingengine.dto.MessageRequest;
import com.practice.messagingengine.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> send(@RequestBody MessageRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }
        String messageId = messageService.send(request);
        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "messageId", messageId,
                "queueSize", messageService.queueSize()));
    }

    @DeleteMapping("/consume")
    public ResponseEntity<Map<String, Object>> consumeBatch(
            @RequestParam(defaultValue = "100") int limit) {
        List<MessageLog> results = messageService.consumeBatch(limit);
        if (results.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "empty", "message", "queue is empty"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "consumed",
                "count", results.size(),
                "results", results.stream().map(l -> Map.of(
                        "messageId", l.getMessageId(),
                        "messageType", l.getMessageType().name(),
                        "status", l.getStatus().name()
                )).toList()));
    }

    @DeleteMapping("/consume/{messageId}")
    public ResponseEntity<Map<String, Object>> consumeOne(@PathVariable String messageId) {
        MessageLog log = messageService.consumeByMessageId(messageId);
        if (log == null) {
            return ResponseEntity.ok(Map.of("status", "not_in_queue", "messageId", messageId));
        }
        return ResponseEntity.ok(Map.of(
                "status", log.getStatus().name(),
                "messageId", log.getMessageId(),
                "messageType", log.getMessageType().name(),
                "remainingSize", messageService.queueSize()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        List<String> recent = messageService.peek(5);
        return ResponseEntity.ok(Map.of(
                "queueSize", messageService.queueSize(),
                "recentMessageIds", recent,
                "serverInfo",
                System.getenv().getOrDefault("HOSTNAME", System.getenv().getOrDefault("COMPUTERNAME", "unknown"))));
    }
}
