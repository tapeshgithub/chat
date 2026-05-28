package com.ai.chat.controllers;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ai.chat.dto.ChatRequest;
import com.ai.chat.dto.ChatResponse;
import com.ai.chat.models.AppUser;
import com.ai.chat.models.ChatMessage;
import com.ai.chat.repository.ChatRepository;
import com.ai.chat.repository.UserRepository;
import com.ai.chat.services.GeminiAiService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatRepository chatrepo;

    @Autowired
    private UserRepository user_repo;

    @Autowired
    private GeminiAiService geminiAiService;

    @PostMapping("/response")
    public ChatResponse chat(@RequestBody ChatRequest request, Principal principal) {
        AppUser user = user_repo.findByUsername(principal.getName()).orElseThrow();

        // Fetch last 10 DB rows (5 user + 5 AI = 5 conversation pairs)
        List<ChatMessage> history = chatrepo
                .findRecentByUser(user, PageRequest.of(0, 10))
                .stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());

        String ai_reply = geminiAiService.askGemini(history, request.getMessage());

        // Save current user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        userMsg.setUser(user);
        chatrepo.save(userMsg);

        // Save AI reply
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setRole("assistant");
        aiMsg.setContent(ai_reply);
        aiMsg.setUser(user);
        chatrepo.save(aiMsg);

        return new ChatResponse(ai_reply);
    }

    @GetMapping("/history")
    public List<ChatMessage> history(Principal principal) {
        AppUser user = user_repo.findByUsername(principal.getName()).orElseThrow();
        return chatrepo.findByUserOrderByCreatedAtAsc(user);
    }
}