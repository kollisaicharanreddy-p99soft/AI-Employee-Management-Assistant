package com.employee.management.ai;

import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import com.employee.management.service.AnalyticsService;
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
@Import({EmployeeTools.class, AnalyticsService.class})
@DisplayName("EmployeeTools analytics tools — integration tests with real H2 DB")
class EmployeeToolsAnalyticsTest {

    @Autowired
    private EmployeeTools employeeTools;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        Department eng = departmentRepository.save(
                Department.builder().name("Engineering").description("Eng").build());
        Department fin = departmentRepository.save(
                Department.builder().name("Finance").description("Finance").build());

        employeeRepository.save(emp("Alice", "alice@t.com", EmployeeStatus.ACTIVE, eng));
        employeeRepository.save(emp("Bob", "bob@t.com", EmployeeStatus.ACTIVE, eng));
        employeeRepository.save(emp("Carol", "carol@t.com", EmployeeStatus.ON_LEAVE, eng));
        employeeRepository.save(emp("Dave", "dave@t.com", EmployeeStatus.ACTIVE, fin));
        employeeRepository.save(emp("Eve", "eve@t.com", EmployeeStatus.TERMINATED, fin));

        entityManager.flush();
        entityManager.clear();
    }

    // ── getEmployeeCountByStatus ──────────────────────────────────────────

    @Test
    @DisplayName("getEmployeeCountByStatus returns all status buckets")
    void countByStatus_allPresent() {
        List<EmployeeTools.StatusCount> result = employeeTools.getEmployeeCountByStatus();
        assertThat(result).hasSize(EmployeeStatus.values().length);
    }

    @Test
    @DisplayName("getEmployeeCountByStatus returns correct counts")
    void countByStatus_correctValues() {
        Map<String, Long> map = employeeTools.getEmployeeCountByStatus()
                .stream()
                .collect(Collectors.toMap(EmployeeTools.StatusCount::status,
                        EmployeeTools.StatusCount::count));

        assertThat(map.get("ACTIVE")).isEqualTo(3);
        assertThat(map.get("ON_LEAVE")).isEqualTo(1);
        assertThat(map.get("TERMINATED")).isEqualTo(1);
        assertThat(map.get("INACTIVE")).isEqualTo(0);
    }

    // ── getWorkforceSummary ───────────────────────────────────────────────

    @Test
    @DisplayName("getWorkforceSummary returns correct totals")
    void workforceSummary_totals() {
        EmployeeTools.WorkforceSummary summary = employeeTools.getWorkforceSummary();
        assertThat(summary.totalEmployees()).isEqualTo(5);
        assertThat(summary.totalDepartments()).isEqualTo(2);
    }

    @Test
    @DisplayName("getWorkforceSummary includes per-status and per-department breakdowns")
    void workforceSummary_breakdowns() {
        EmployeeTools.WorkforceSummary summary = employeeTools.getWorkforceSummary();

        assertThat(summary.countByStatus()).hasSize(EmployeeStatus.values().length);
        assertThat(summary.countByDepartment()).hasSize(2);

        Map<String, Long> deptMap = summary.countByDepartment()
                .stream()
                .collect(Collectors.toMap(EmployeeTools.DeptCount::departmentName,
                        EmployeeTools.DeptCount::count));
        assertThat(deptMap.get("Engineering")).isEqualTo(3);
        assertThat(deptMap.get("Finance")).isEqualTo(2);
    }

    // ── helper ───────────────────────────────────────────────────────────

    private Employee emp(String name, String email, EmployeeStatus status, Department dept) {
        return Employee.builder()
                .firstName(name).lastName("Test").email(email)
                .salary(new BigDecimal("60000"))
                .hireDate(LocalDate.of(2022, 1, 1))
                .status(status).department(dept).build();
    }
}
