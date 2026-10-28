package com.employee.management.repository;

import com.employee.management.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Eagerly fetches employees alongside the department so that
     * {@code department.getEmployees().size()} is safe to call without
     * triggering a LazyInitializationException or N+1 queries.
     */
    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.employees WHERE d.id = :id")
    Optional<Department> findByIdWithEmployees(@Param("id") Long id);

    @Query("SELECT d FROM Department d ORDER BY d.name ASC")
    List<Department> findAllOrderedByName();

    /**
     * Returns every department with its exact employee count as a two-element
     * Object array: [0] = department name (String), [1] = count (Long).
     * Uses a LEFT JOIN so departments with zero employees are included.
     * Ordered alphabetically by name.
     */
    @Query("""
            SELECT d.name, COUNT(e)
            FROM Department d
            LEFT JOIN d.employees e
            GROUP BY d.id, d.name
            ORDER BY d.name ASC
            """)
    List<Object[]> countEmployeesPerDepartment();

    /**
     * Same aggregation as {@link #countEmployeesPerDepartment()} but ordered
     * by headcount descending, then alphabetically. Used for "most employees"
     * queries. Returns [0] = department name, [1] = count.
     */
    @Query("""
            SELECT d.name, COUNT(e)
            FROM Department d
            LEFT JOIN d.employees e
            GROUP BY d.id, d.name
            ORDER BY COUNT(e) DESC, d.name ASC
            """)
    List<Object[]> countEmployeesPerDepartmentByHeadcountDesc();
}
