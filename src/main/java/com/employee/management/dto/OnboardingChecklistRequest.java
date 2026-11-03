package com.employee.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingChecklistRequest {

    private Long employeeId;

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Job role is required")
    private String jobRole;

    private LocalDate joiningDate;

    private String experienceLevel;
}

