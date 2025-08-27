package com.afs.restapi.controller;

import com.afs.restapi.dto.PlanRequestDto;
import com.afs.restapi.tool.PlannerService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
public class SemanticController {
    private final ChatClient chatClient;

    private final PlannerService plannerService;

    public SemanticController(ChatClient.Builder chatClientBuilder, PlannerService plannerService) {
        this.chatClient = chatClientBuilder.build();
        this.plannerService = plannerService;
    }

    @GetMapping("/chat")
    String generation(String userInput) {
        return this.chatClient.prompt()
                .user(userInput)
                .call()
                .content();
    }

    @PostMapping("/plan")
    String plan(@RequestBody PlanRequestDto request) {
        String result = plannerService.plan(request.getUserInput());
        return result;
    }
}
