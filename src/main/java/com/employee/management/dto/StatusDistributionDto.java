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
public class StatusDistributionDto {

    private EmployeeStatus status;
    private long count;
    private double percentage;
}

