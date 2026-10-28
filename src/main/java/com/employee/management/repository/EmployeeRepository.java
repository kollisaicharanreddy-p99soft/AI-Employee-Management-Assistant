package com.employee.management.repository;

import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>,
        JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    List<Employee> findByStatus(EmployeeStatus status);

    @Query("""
            SELECT e FROM Employee e
            LEFT JOIN FETCH e.department
            WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(e.lastName)  LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(e.email)     LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(e.jobTitle)  LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Employee> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :deptId")
    long countByDepartmentId(@Param("deptId") Long deptId);

    @Query("SELECT e.status, COUNT(e) FROM Employee e GROUP BY e.status")
    List<Object[]> countGroupByStatus();

    @Query("""
            SELECT e.department.name, COUNT(e)
            FROM Employee e
            WHERE e.department IS NOT NULL
            GROUP BY e.department.id, e.department.name
            ORDER BY e.department.name ASC
            """)
    List<Object[]> countGroupByDepartment();
}
