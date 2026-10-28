package com.employee.management.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    /** The AI-generated answer. */
    private String answer;

    /** The original question echoed back for convenience. */
    private String question;

    /** The conversation ID (echoed or newly assigned). */
    private String conversationId;
}
