package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAnswerResponse {

    private String question;
    private String answer;
    private List<PolicySourceReference> sources;
    private LocalDateTime answeredAt;
    private boolean informationFound;
}

