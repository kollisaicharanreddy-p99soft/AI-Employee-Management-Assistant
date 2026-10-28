package com.employee.management.service;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(AnalyticsService.class)
@DisplayName("AnalyticsService — integration tests with real H2 DB")
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Department engineering;
    private Department finance;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        engineering = departmentRepository.save(
                Department.builder().name("Engineering").description("Eng").build());
        finance = departmentRepository.save(
                Department.builder().name("Finance").description("Finance").build());

        // Engineering: 2 ACTIVE, 1 ON_LEAVE
        employeeRepository.save(employee("Alice", "Johnson", "alice@t.com",
                EmployeeStatus.ACTIVE, engineering, "95000"));
        employeeRepository.save(employee("Bob", "Smith", "bob@t.com",
                EmployeeStatus.ACTIVE, engineering, "88000"));
        employeeRepository.save(employee("Carol", "Williams", "carol@t.com",
                EmployeeStatus.ON_LEAVE, engineering, "70000"));

        // Finance: 1 ACTIVE, 1 TERMINATED
        employeeRepository.save(employee("Dave", "Brown", "dave@t.com",
                EmployeeStatus.ACTIVE, finance, "75000"));
        employeeRepository.save(employee("Eve", "Davis", "eve@t.com",
                EmployeeStatus.TERMINATED, finance, "65000"));

        entityManager.flush();
        entityManager.clear();
    }

    // ── getTotalEmployeeCount ─────────────────────────────────────────────

    @Test
    @DisplayName("getTotalEmployeeCount returns exact DB count")
    void totalCount() {
        assertThat(analyticsService.getTotalEmployeeCount()).isEqualTo(5);
    }

    // ── getCountByStatus ──────────────────────────────────────────────────

    @Test
    @DisplayName("getCountByStatus returns a row for every EmployeeStatus")
    void countByStatus_allStatusesPresent() {
        List<StatusCountDto> result = analyticsService.getCountByStatus();
        assertThat(result).hasSize(EmployeeStatus.values().length);
        assertThat(result).extracting(StatusCountDto::getStatus)
                .containsExactlyInAnyOrder(EmployeeStatus.values());
    }

    @Test
    @DisplayName("getCountByStatus returns correct counts")
    void countByStatus_correctCounts() {
        Map<EmployeeStatus, Long> map = analyticsService.getCountByStatus()
                .stream()
                .collect(Collectors.toMap(StatusCountDto::getStatus, StatusCountDto::getCount));

        assertThat(map.get(EmployeeStatus.ACTIVE)).isEqualTo(3);
        assertThat(map.get(EmployeeStatus.ON_LEAVE)).isEqualTo(1);
        assertThat(map.get(EmployeeStatus.TERMINATED)).isEqualTo(1);
        assertThat(map.get(EmployeeStatus.INACTIVE)).isEqualTo(0);
    }

    @Test
    @DisplayName("getCountByStatus returns zero for unused statuses")
    void countByStatus_unusedStatusIsZero() {
        assertThat(analyticsService.getCountByStatus())
                .filteredOn(s -> s.getStatus() == EmployeeStatus.INACTIVE)
                .singleElement()
                .extracting(StatusCountDto::getCount)
                .isEqualTo(0L);
    }

    // ── getCountByDepartment ──────────────────────────────────────────────

    @Test
    @DisplayName("getCountByDepartment returns correct per-department counts")
    void countByDepartment_correctCounts() {
        List<DepartmentCountDto> result = analyticsService.getCountByDepartment();
        assertThat(result).hasSize(2);

        Map<String, Long> map = result.stream()
                .collect(Collectors.toMap(DepartmentCountDto::getDepartmentName,
                        DepartmentCountDto::getCount));
        assertThat(map.get("Engineering")).isEqualTo(3);
        assertThat(map.get("Finance")).isEqualTo(2);
    }

    @Test
    @DisplayName("getCountByDepartment result is ordered alphabetically")
    void countByDepartment_alphabeticalOrder() {
        List<DepartmentCountDto> result = analyticsService.getCountByDepartment();
        assertThat(result).extracting(DepartmentCountDto::getDepartmentName)
                .containsExactly("Engineering", "Finance");
    }

    @Test
    @DisplayName("getCountByDepartment excludes employees without a department")
    void countByDepartment_noNullDepartment() {
        // Save an employee with no department
        employeeRepository.save(employee("Orphan", "Emp", "orphan@t.com",
                EmployeeStatus.ACTIVE, null, "50000"));
        entityManager.flush();
        entityManager.clear();

        List<DepartmentCountDto> result = analyticsService.getCountByDepartment();
        // Should still be only 2 departments, counts unchanged
        assertThat(result).hasSize(2);
    }

    // ── getWorkforceSummary ───────────────────────────────────────────────

    @Test
    @DisplayName("getWorkforceSummary aggregates all sub-queries correctly")
    void workforceSummary() {
        WorkforceSummaryDto summary = analyticsService.getWorkforceSummary();

        assertThat(summary.getTotalEmployees()).isEqualTo(5);
        assertThat(summary.getTotalDepartments()).isEqualTo(2);
        assertThat(summary.getCountByStatus()).hasSize(EmployeeStatus.values().length);
        assertThat(summary.getCountByDepartment()).hasSize(2);
    }

    // ── helper ───────────────────────────────────────────────────────────

    private Employee employee(String first, String last, String email,
                              EmployeeStatus status, Department dept, String salary) {
        return Employee.builder()
                .firstName(first).lastName(last).email(email)
                .salary(new BigDecimal(salary))
                .hireDate(LocalDate.of(2022, 1, 1))
                .status(status).department(dept).build();
    }
}
