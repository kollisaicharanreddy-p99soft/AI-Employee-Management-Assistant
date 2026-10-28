package com.employee.management.controller;

import com.employee.management.dto.ApiResponse;
import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Workforce Analytics", description = "Workforce analytics backed by exact JPA aggregations")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/employees/count")
    @Operation(summary = "Total employee count")
    public ResponseEntity<ApiResponse<Long>> getTotalCount() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getTotalEmployeeCount()));
    }

    @GetMapping("/employees/count-by-status")
    @Operation(summary = "Employee count per status (ACTIVE, INACTIVE, ON_LEAVE, TERMINATED)")
    public ResponseEntity<ApiResponse<List<StatusCountDto>>> getCountByStatus() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCountByStatus()));
    }

    @GetMapping("/employees/count-by-department")
    @Operation(summary = "Employee count per department, sorted alphabetically")
    public ResponseEntity<ApiResponse<List<DepartmentCountDto>>> getCountByDepartment() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCountByDepartment()));
    }

    @GetMapping("/workforce-summary")
    @Operation(summary = "Complete workforce summary: totals, per-status, and per-department breakdowns")
    public ResponseEntity<ApiResponse<WorkforceSummaryDto>> getWorkforceSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getWorkforceSummary()));
    }
}
