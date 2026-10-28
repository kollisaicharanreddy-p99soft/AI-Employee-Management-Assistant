package com.employee.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Question must not be blank")
    @Size(max = 2000, message = "Question must not exceed 2000 characters")
    private String question;

    /**
     * Optional conversation ID for multi-turn context.
     * Leave null to start a new conversation.
     */
    private String conversationId;
}
