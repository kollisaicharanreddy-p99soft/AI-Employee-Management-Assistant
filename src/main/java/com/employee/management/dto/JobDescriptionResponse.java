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
public class JobDescriptionResponse {

    private String jobTitle;
    private String department;
    private String summary;
    private List<String> responsibilities;
    private List<String> requiredSkills;
    private List<String> preferredQualifications;
    private String experienceRequirements;
    private LocalDateTime generatedAt;
}

