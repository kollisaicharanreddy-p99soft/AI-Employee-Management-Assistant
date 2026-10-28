package com.employee.management.dto;

import com.employee.management.model.EmployeeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCountDto {

    /** The employee status bucket. */
    private EmployeeStatus status;

    /** Exact count of employees in that status, sourced directly from the DB. */
    private long count;
}
