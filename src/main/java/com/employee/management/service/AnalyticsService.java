package com.employee.management.service;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides workforce analytics calculated directly from the database.
 *
 * <p>All counts and aggregations are executed as single JPA queries — the
 * service never performs in-memory aggregation over a full table scan.
 * No AI or estimation is involved; every number is an exact DB result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Returns the total employee headcount.
     */
    public long getTotalEmployeeCount() {
        return employeeRepository.count();
    }

    /**
     * Returns employee counts bucketed by {@link EmployeeStatus}.
     * Every status value is always present in the result (count = 0 when none exist).
     */
    public List<StatusCountDto> getCountByStatus() {
        Map<EmployeeStatus, Long> dbCounts = employeeRepository.countGroupByStatus()
                .stream()
                .collect(Collectors.toMap(
                        row -> (EmployeeStatus) row[0],
                        row -> (Long) row[1]));

        // Guarantee every status appears — missing ones get count 0
        return Arrays.stream(EmployeeStatus.values())
                .map(status -> StatusCountDto.builder()
                        .status(status)
                        .count(dbCounts.getOrDefault(status, 0L))
                        .build())
                .toList();
    }

    /**
     * Returns employee counts per department, alphabetically ordered.
     * Uses LEFT JOIN so departments with zero employees are included.
     */
    public List<DepartmentCountDto> getCountByDepartment() {
        return departmentRepository.countEmployeesPerDepartment()
                .stream()
                .map(row -> DepartmentCountDto.builder()
                        .departmentName((String) row[0])
                        .count((Long) row[1])
                        .build())
                .toList();
    }

    /**
     * Returns employee counts per department sorted by headcount descending.
     * Departments with equal headcount are sorted alphabetically.
     * Used for "which departments have the most employees?" queries.
     */
    public List<DepartmentCountDto> getCountByDepartmentDesc() {
        return departmentRepository.countEmployeesPerDepartmentByHeadcountDesc()
                .stream()
                .map(row -> DepartmentCountDto.builder()
                        .departmentName((String) row[0])
                        .count((Long) row[1])
                        .build())
                .toList();
    }

    /**
     * Returns the full workforce summary: totals + per-status + per-department.
     */
    public WorkforceSummaryDto getWorkforceSummary() {
        return WorkforceSummaryDto.builder()
                .totalEmployees(getTotalEmployeeCount())
                .totalDepartments(departmentRepository.count())
                .countByStatus(getCountByStatus())
                .countByDepartment(getCountByDepartment())
                .build();
    }
}
