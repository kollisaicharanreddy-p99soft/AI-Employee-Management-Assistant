package com.employee.management.repository;

import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DepartmentRepository — JPA integration tests")
class DepartmentRepositoryTest {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
    }

    @Test
    @DisplayName("findByNameIgnoreCase returns department for matching name")
    void findByNameIgnoreCase_found() {
        departmentRepository.save(
                Department.builder().name("Engineering").description("Eng").build());

        Optional<Department> result = departmentRepository.findByNameIgnoreCase("engineering");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Engineering");
    }

    @Test
    @DisplayName("findByNameIgnoreCase returns empty for unknown name")
    void findByNameIgnoreCase_notFound() {
        assertThat(departmentRepository.findByNameIgnoreCase("Unknown")).isEmpty();
    }

    @Test
    @DisplayName("existsByNameIgnoreCase returns true for existing name")
    void existsByNameIgnoreCase_true() {
        departmentRepository.save(
                Department.builder().name("Finance").description("Finance").build());
        assertThat(departmentRepository.existsByNameIgnoreCase("FINANCE")).isTrue();
    }

    @Test
    @DisplayName("existsByNameIgnoreCase returns false for unknown name")
    void existsByNameIgnoreCase_false() {
        assertThat(departmentRepository.existsByNameIgnoreCase("Marketing")).isFalse();
    }

    @Test
    @DisplayName("findAllOrderedByName returns departments sorted alphabetically")
    void findAllOrderedByName() {
        departmentRepository.save(Department.builder().name("Zebra").build());
        departmentRepository.save(Department.builder().name("Apple").build());
        departmentRepository.save(Department.builder().name("Mango").build());

        List<Department> ordered = departmentRepository.findAllOrderedByName();
        assertThat(ordered).extracting(Department::getName)
                .containsExactly("Apple", "Mango", "Zebra");
    }

    @Test
    @DisplayName("findByIdWithEmployees eagerly loads the employees collection")
    void findByIdWithEmployees_loadsEmployees() {
        Department dept = departmentRepository.save(
                Department.builder().name("HR").description("HR team").build());

        Employee emp = Employee.builder()
                .firstName("Tom").lastName("Jones").email("tom@example.com")
                .status(EmployeeStatus.ACTIVE).department(dept).build();
        employeeRepository.save(emp);

        // Flush and clear the persistence context so the JOIN FETCH actually loads
        // from the database rather than the first-level cache.
        entityManager.flush();
        entityManager.clear();

        Optional<Department> loaded = departmentRepository.findByIdWithEmployees(dept.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getEmployees()).hasSize(1);
        assertThat(loaded.get().getEmployees().get(0).getFirstName()).isEqualTo("Tom");
    }

    @Test
    @DisplayName("findByIdWithEmployees returns empty for unknown id")
    void findByIdWithEmployees_notFound() {
        assertThat(departmentRepository.findByIdWithEmployees(-1L)).isEmpty();
    }
}
