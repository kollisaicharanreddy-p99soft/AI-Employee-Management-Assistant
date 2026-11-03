package com.employee.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionRequest {

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    @NotBlank(message = "Department is required")
    private String department;

    private List<String> requiredSkills;

    private String experienceRange;

    private List<String> responsibilities;

    private String additionalInstructions;
}

