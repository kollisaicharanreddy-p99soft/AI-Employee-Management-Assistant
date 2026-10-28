package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkforceSummaryDto {

    /** Total headcount across all statuses. */
    private long totalEmployees;

    /** Total number of departments. */
    private long totalDepartments;

    /** Per-status breakdown (ACTIVE, INACTIVE, ON_LEAVE, TERMINATED). */
    private List<StatusCountDto> countByStatus;

    /** Per-department headcount, ordered alphabetically. */
    private List<DepartmentCountDto> countByDepartment;
}
