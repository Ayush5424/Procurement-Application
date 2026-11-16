package com.cantill.procurement.controller;

import com.cantill.procurement.domain.ChatMessage;
import com.cantill.procurement.repository.ChatMessageRepository;
import com.cantill.procurement.service.EmployeeChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class EmployeeChatController {

    private final EmployeeChatService employeeChatService;
    private final ChatMessageRepository chatMessageRepository;

    public EmployeeChatController(
            EmployeeChatService employeeChatService,
            ChatMessageRepository chatMessageRepository) {
        this.employeeChatService = employeeChatService;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("/message")
    public ResponseEntity<EmployeeChatService.ChatResponseDto> sendMessage(
            @RequestBody Map<String, String> payload) {
        String sessionId = payload.get("sessionId");
        String message = payload.getOrDefault("message", "");
        EmployeeChatService.ChatResponseDto response = employeeChatService.processMessage(sessionId, message);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getSessionMessages(@PathVariable("sessionId") String sessionId) {
        return ResponseEntity.ok(chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId));
    }
}
