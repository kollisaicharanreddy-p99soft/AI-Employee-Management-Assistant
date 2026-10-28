package com.employee.management.controller;

import com.employee.management.dto.ApiResponse;
import com.employee.management.dto.ChatRequest;
import com.employee.management.dto.ChatResponse;
import com.employee.management.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Natural-language HR assistant powered by Spring AI")
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    @Operation(
        summary = "Ask the HR AI assistant a question",
        description = """
            Submit a natural-language question about employees or departments.
            The AI will query the live database via tool calling to provide
            accurate, database-backed answers.

            Example questions:
            - "How many employees are in the Engineering department?"
            - "List all active employees."
            - "Who is the HR Manager?"
            - "Show me all employees in Finance."
            - "How many employees have the status ON_LEAVE?"
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "AI response with database-backed answer"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request (blank question, too long)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503",
            description = "AI service unavailable (missing API key, network error)")
    })
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = aiChatService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
