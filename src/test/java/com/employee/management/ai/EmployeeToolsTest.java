package com.employee.management.ai;

import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import com.employee.management.service.AnalyticsService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the EmployeeTools methods in isolation using a real H2 database.
 * This validates that tool implementations correctly query the DB and
 * return the expected results without involving the LLM.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({EmployeeTools.class, AnalyticsService.class})
@DisplayName("EmployeeTools — tool method unit tests with real DB")
class EmployeeToolsTest {

    @Autowired
    private EmployeeTools employeeTools;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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

        employeeRepository.save(Employee.builder()
                .firstName("Alice").lastName("Johnson").email("alice@test.com")
                .jobTitle("Senior Engineer").salary(new BigDecimal("95000"))
                .hireDate(LocalDate.of(2021, 3, 15))
                .status(EmployeeStatus.ACTIVE).department(engineering).build());

        employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Smith").email("bob@test.com")
                .jobTitle("Financial Analyst").salary(new BigDecimal("70000"))
                .hireDate(LocalDate.of(2022, 1, 10))
                .status(EmployeeStatus.ACTIVE).department(finance).build());

        employeeRepository.save(Employee.builder()
                .firstName("Carol").lastName("Williams").email("carol@test.com")
                .jobTitle("Backend Engineer").salary(new BigDecimal("88000"))
                .hireDate(LocalDate.of(2020, 5, 1))
                .status(EmployeeStatus.ON_LEAVE).department(engineering).build());

        // Flush writes and clear the first-level cache so subsequent reads hit
        // the DB fresh, including lazy collection resolution (employees list on
        // Department) which DepartmentSummary.from() relies on for employeeCount.
        entityManager.flush();
        entityManager.clear();
    }

    // ── countAllEmployees ─────────────────────────────────────────────────

    @Test
    @DisplayName("countAllEmployees returns the correct total")
    void countAllEmployees() {
        assertThat(employeeTools.countAllEmployees()).isEqualTo(3);
    }

    // ── countEmployeesByDepartment ────────────────────────────────────────

    @Test
    @DisplayName("countEmployeesByDepartment returns correct count for known department")
    void countEmployeesByDepartment_known() {
        assertThat(employeeTools.countEmployeesByDepartment("Engineering")).isEqualTo(2);
        assertThat(employeeTools.countEmployeesByDepartment("Finance")).isEqualTo(1);
    }

    @Test
    @DisplayName("countEmployeesByDepartment is case-insensitive")
    void countEmployeesByDepartment_caseInsensitive() {
        assertThat(employeeTools.countEmployeesByDepartment("ENGINEERING")).isEqualTo(2);
        assertThat(employeeTools.countEmployeesByDepartment("engineering")).isEqualTo(2);
    }

    @Test
    @DisplayName("countEmployeesByDepartment returns 0 for unknown department")
    void countEmployeesByDepartment_unknown() {
        assertThat(employeeTools.countEmployeesByDepartment("Marketing")).isZero();
    }

    // ── listAllEmployees ──────────────────────────────────────────────────

    @Test
    @DisplayName("listAllEmployees returns all employees as summaries")
    void listAllEmployees() {
        List<EmployeeTools.EmployeeSummary> summaries = employeeTools.listAllEmployees();
        assertThat(summaries).hasSize(3);
        assertThat(summaries).extracting(EmployeeTools.EmployeeSummary::fullName)
                .containsExactlyInAnyOrder("Alice Johnson", "Bob Smith", "Carol Williams");
    }

    @Test
    @DisplayName("listAllEmployees includes department name in each summary")
    void listAllEmployees_includesDepartment() {
        List<EmployeeTools.EmployeeSummary> summaries = employeeTools.listAllEmployees();
        assertThat(summaries).extracting(EmployeeTools.EmployeeSummary::departmentName)
                .doesNotContainNull();
    }

    // ── searchEmployees ───────────────────────────────────────────────────

    @Test
    @DisplayName("searchEmployees matches by job title substring")
    void searchEmployees_byJobTitle() {
        List<EmployeeTools.EmployeeSummary> results = employeeTools.searchEmployees("engineer");
        assertThat(results).hasSize(2); // Alice (Senior Engineer) + Carol (Backend Engineer)
    }

    @Test
    @DisplayName("searchEmployees matches by last name")
    void searchEmployees_byLastName() {
        List<EmployeeTools.EmployeeSummary> results = employeeTools.searchEmployees("Smith");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).fullName()).isEqualTo("Bob Smith");
    }

    @Test
    @DisplayName("searchEmployees returns empty list for no match")
    void searchEmployees_noMatch() {
        assertThat(employeeTools.searchEmployees("xyz-no-match")).isEmpty();
    }

    // ── getEmployeeById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getEmployeeById returns full detail for known id")
    void getEmployeeById_known() {
        Employee alice = employeeRepository.findByEmailIgnoreCase("alice@test.com").orElseThrow();
        EmployeeTools.EmployeeDetail detail = employeeTools.getEmployeeById(alice.getId());

        assertThat(detail).isNotNull();
        assertThat(detail.firstName()).isEqualTo("Alice");
        assertThat(detail.lastName()).isEqualTo("Johnson");
        assertThat(detail.jobTitle()).isEqualTo("Senior Engineer");
        assertThat(detail.departmentName()).isEqualTo("Engineering");
        assertThat(detail.salary()).isEqualTo("95000.00");
        assertThat(detail.hireDate()).isEqualTo("2021-03-15");
        assertThat(detail.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getEmployeeById returns null for unknown id")
    void getEmployeeById_unknown() {
        assertThat(employeeTools.getEmployeeById(-1L)).isNull();
    }

    // ── listEmployeesByStatus ─────────────────────────────────────────────

    @Test
    @DisplayName("listEmployeesByStatus returns only matching employees")
    void listEmployeesByStatus_onLeave() {
        List<EmployeeTools.EmployeeSummary> onLeave = employeeTools.listEmployeesByStatus("ON_LEAVE");
        assertThat(onLeave).hasSize(1);
        assertThat(onLeave.get(0).fullName()).isEqualTo("Carol Williams");
    }

    @Test
    @DisplayName("listEmployeesByStatus is case-insensitive")
    void listEmployeesByStatus_caseInsensitive() {
        assertThat(employeeTools.listEmployeesByStatus("active")).hasSize(2);
        assertThat(employeeTools.listEmployeesByStatus("ACTIVE")).hasSize(2);
    }

    @Test
    @DisplayName("listEmployeesByStatus returns empty list for invalid status")
    void listEmployeesByStatus_invalid() {
        assertThat(employeeTools.listEmployeesByStatus("FLYING")).isEmpty();
    }

    // ── listEmployeesByDepartment ─────────────────────────────────────────

    @Test
    @DisplayName("listEmployeesByDepartment returns only employees in that department")
    void listEmployeesByDepartment_known() {
        List<EmployeeTools.EmployeeSummary> engEmployees =
                employeeTools.listEmployeesByDepartment("Engineering");
        assertThat(engEmployees).hasSize(2);
        assertThat(engEmployees).extracting(EmployeeTools.EmployeeSummary::fullName)
                .containsExactlyInAnyOrder("Alice Johnson", "Carol Williams");
    }

    @Test
    @DisplayName("listEmployeesByDepartment returns empty list for unknown department")
    void listEmployeesByDepartment_unknown() {
        assertThat(employeeTools.listEmployeesByDepartment("Marketing")).isEmpty();
    }

    // ── listAllDepartments ────────────────────────────────────────────────

    @Test
    @DisplayName("listAllDepartments returns all departments with employee count")
    void listAllDepartments() {
        List<EmployeeTools.DepartmentSummary> depts = employeeTools.listAllDepartments();
        assertThat(depts).hasSize(2);
        // ordered by name: Engineering first, Finance second
        assertThat(depts.get(0).name()).isEqualTo("Engineering");
        assertThat(depts.get(0).employeeCount()).isEqualTo(2);
        assertThat(depts.get(1).name()).isEqualTo("Finance");
        assertThat(depts.get(1).employeeCount()).isEqualTo(1);
    }

    // ── getDepartmentByName ───────────────────────────────────────────────

    @Test
    @DisplayName("getDepartmentByName returns department for known name")
    void getDepartmentByName_known() {
        EmployeeTools.DepartmentSummary dept = employeeTools.getDepartmentByName("Finance");
        assertThat(dept).isNotNull();
        assertThat(dept.name()).isEqualTo("Finance");
        assertThat(dept.description()).isEqualTo("Finance");
        assertThat(dept.employeeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getDepartmentByName returns null for unknown name")
    void getDepartmentByName_unknown() {
        assertThat(employeeTools.getDepartmentByName("NonExistent")).isNull();
    }
}
