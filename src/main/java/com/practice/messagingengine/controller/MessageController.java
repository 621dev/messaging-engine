package com.practice.messagingengine.controller;

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
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }
        messageService.send(content);
        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "content", content,
                "queueSize", messageService.queueSize()));
    }

    @DeleteMapping("/consume")
    public ResponseEntity<Map<String, Object>> consume() {
        String message = messageService.consume();
        if (message == null) {
            return ResponseEntity.ok(Map.of("status", "empty", "message", "queue is empty"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "consumed",
                "message", message,
                "remainingSize", messageService.queueSize()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        List<String> recent = messageService.peek(5);
        return ResponseEntity.ok(Map.of(
                "queueSize", messageService.queueSize(),
                "recentMessages", recent,
                "serverInfo",
                System.getenv().getOrDefault("HOSTNAME", System.getenv().getOrDefault("COMPUTERNAME", "unknown"))));
    }
}
