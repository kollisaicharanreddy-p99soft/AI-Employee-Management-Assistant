package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCountDto {

    /** Department name. */
    private String departmentName;

    /** Exact count of employees in that department, sourced directly from the DB. */
    private long count;
}
