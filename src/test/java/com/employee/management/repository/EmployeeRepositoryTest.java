package com.employee.management.repository;

import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("EmployeeRepository — JPA integration tests")
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department engineering;
    private Department hr;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();

        engineering = departmentRepository.save(
                Department.builder().name("Engineering").description("Eng team").build());
        hr = departmentRepository.save(
                Department.builder().name("Human Resources").description("HR team").build());

        employeeRepository.save(Employee.builder()
                .firstName("Alice").lastName("Johnson")
                .email("alice@example.com").jobTitle("Senior Engineer")
                .salary(new BigDecimal("95000")).hireDate(LocalDate.of(2021, 3, 15))
                .status(EmployeeStatus.ACTIVE).department(engineering).build());

        employeeRepository.save(Employee.builder()
                .firstName("Bob").lastName("Smith")
                .email("bob@example.com").jobTitle("HR Manager")
                .salary(new BigDecimal("75000")).hireDate(LocalDate.of(2020, 7, 1))
                .status(EmployeeStatus.ACTIVE).department(hr).build());

        employeeRepository.save(Employee.builder()
                .firstName("Carol").lastName("Williams")
                .email("carol@example.com").jobTitle("Backend Engineer")
                .salary(new BigDecimal("88000")).hireDate(LocalDate.of(2022, 1, 10))
                .status(EmployeeStatus.ON_LEAVE).department(engineering).build());
    }

    @Test
    @DisplayName("findByEmailIgnoreCase returns employee for matching email")
    void findByEmailIgnoreCase_found() {
        Optional<Employee> result = employeeRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM");
        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findByEmailIgnoreCase returns empty for unknown email")
    void findByEmailIgnoreCase_notFound() {
        assertThat(employeeRepository.findByEmailIgnoreCase("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("existsByEmailIgnoreCase returns true for existing email")
    void existsByEmailIgnoreCase_true() {
        assertThat(employeeRepository.existsByEmailIgnoreCase("bob@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmailIgnoreCaseAndIdNot excludes the given id")
    void existsByEmailIgnoreCaseAndIdNot() {
        Employee alice = employeeRepository.findByEmailIgnoreCase("alice@example.com").orElseThrow();
        // Same email but same id → should return false (not a conflict with self)
        assertThat(employeeRepository.existsByEmailIgnoreCaseAndIdNot("alice@example.com", alice.getId()))
                .isFalse();
        // Same email but a different id → conflict
        assertThat(employeeRepository.existsByEmailIgnoreCaseAndIdNot("alice@example.com", -999L))
                .isTrue();
    }

    @Test
    @DisplayName("findByDepartmentId returns only employees in that department")
    void findByDepartmentId() {
        Page<Employee> page = employeeRepository.findByDepartmentId(
                engineering.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Employee::getEmail)
                .containsExactlyInAnyOrder("alice@example.com", "carol@example.com");
    }

    @Test
    @DisplayName("findByStatus returns only employees with that status")
    void findByStatus() {
        List<Employee> onLeave = employeeRepository.findByStatus(EmployeeStatus.ON_LEAVE);
        assertThat(onLeave).hasSize(1);
        assertThat(onLeave.get(0).getFirstName()).isEqualTo("Carol");
    }

    @Test
    @DisplayName("searchByKeyword matches first name, last name, email, and job title")
    void searchByKeyword() {
        // Match by job title substring
        Page<Employee> byTitle = employeeRepository.searchByKeyword(
                "engineer", PageRequest.of(0, 10));
        assertThat(byTitle.getTotalElements()).isEqualTo(2); // Alice=Senior Engineer, Carol=Backend Engineer

        // Match by last name
        Page<Employee> byLastName = employeeRepository.searchByKeyword(
                "smith", PageRequest.of(0, 10));
        assertThat(byLastName.getTotalElements()).isEqualTo(1);
        assertThat(byLastName.getContent().get(0).getLastName()).isEqualTo("Smith");

        // Match by email domain
        Page<Employee> byEmail = employeeRepository.searchByKeyword(
                "example.com", PageRequest.of(0, 10));
        assertThat(byEmail.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("countByDepartmentId returns correct count")
    void countByDepartmentId() {
        assertThat(employeeRepository.countByDepartmentId(engineering.getId())).isEqualTo(2);
        assertThat(employeeRepository.countByDepartmentId(hr.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Saving an employee persists all fields")
    void saveAndRetrieve_allFields() {
        Employee e = Employee.builder()
                .firstName("Dave").lastName("Test")
                .email("dave.test@example.com").phone("555-9999")
                .jobTitle("QA Engineer").salary(new BigDecimal("65000"))
                .hireDate(LocalDate.of(2023, 6, 1))
                .status(EmployeeStatus.ACTIVE).department(engineering).build();

        Employee saved = employeeRepository.save(e);
        assertThat(saved.getId()).isNotNull();

        Employee loaded = employeeRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getFirstName()).isEqualTo("Dave");
        assertThat(loaded.getEmail()).isEqualTo("dave.test@example.com");
        assertThat(loaded.getSalary()).isEqualByComparingTo("65000");
        assertThat(loaded.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(loaded.getDepartment().getName()).isEqualTo("Engineering");
    }
}
