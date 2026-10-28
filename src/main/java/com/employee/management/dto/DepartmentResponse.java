package com.employee.management.dto;

import lombok.Data;

@Data
public class DepartmentResponse {
    private Long id;
    private String name;
    private String description;
    private int employeeCount;
}
