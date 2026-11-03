package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrEmailResponse {

    private HrEmailType emailType;
    private String subject;
    private String body;
    private EmailTone tone;
    private LocalDateTime generatedAt;
}

