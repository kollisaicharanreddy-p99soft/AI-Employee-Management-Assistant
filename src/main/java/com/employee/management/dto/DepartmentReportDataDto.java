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
public class DepartmentReportDataDto {

    private long totalDepartments;
    private long totalEmployees;
    private List<DepartmentDetailDto> departments;
}

