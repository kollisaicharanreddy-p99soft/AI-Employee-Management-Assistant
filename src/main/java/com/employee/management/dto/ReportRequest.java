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
public class ReportRequest {

    @NotNull(message = "Report type is required")
    private ReportType reportType;

    /**
     * Optional department name filter for department-specific reports.
     */
    private String departmentName;
}

