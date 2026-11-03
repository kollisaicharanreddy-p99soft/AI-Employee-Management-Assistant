package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingTaskDto {

    private String title;
    private String description;
    private OnboardingStage stage;
    private OnboardingTaskCategory category;
    private boolean policyGrounded;
    private String policyReference;
}

