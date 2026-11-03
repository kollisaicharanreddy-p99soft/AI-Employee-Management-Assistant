package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingChecklistResponse {

    private Long employeeId;
    private String employeeName;
    private String department;
    private String jobRole;
    private LocalDate joiningDate;
    private String experienceLevel;
    private List<OnboardingTaskDto> tasks;
    private int totalTasks;
    private String disclaimer;
    private LocalDateTime generatedAt;
}

