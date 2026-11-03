package com.employee.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrEmailRequest {

    @NotNull(message = "Email type is required")
    private HrEmailType emailType;

    private String recipientName;

    private String context;

    private EmailTone tone;

    private String additionalInstructions;
}

