package com.employee.management.ai;

import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring AI tool implementations.
 *
 * <p>All methods are read-only and query the real database — the LLM never
 * invents employee records or statistics. Each method is annotated with
 * {@code @Tool} so that Spring AI can expose it to the chat model and call
 * it automatically when relevant.
 *
 * <p>Mutation operations are intentionally <em>not</em> exposed here; they
 * require explicit confirmation through the normal REST endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeTools {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AnalyticsService analyticsService;

    // ── Employee queries ──────────────────────────────────────────────────

    @Tool(description = """
            Returns the total number of employees currently stored in the database.
            Use this when the user asks how many employees there are in total.
            """)
    public long countAllEmployees() {
        long count = employeeRepository.count();
        log.debug("[AI Tool] countAllEmployees → {}", count);
        return count;
    }

    @Tool(description = """
            Returns the total number of employees in a specific department.
            Use this when the user asks how many employees are in a named department.
            The departmentName is matched case-insensitively.
            """)
    public long countEmployeesByDepartment(
            @ToolParam(description = "The name of the department, e.g. 'Engineering'")
            String departmentName) {

        Optional<Department> dept = departmentRepository.findByNameIgnoreCase(departmentName);
        if (dept.isEmpty()) {
            log.debug("[AI Tool] countEmployeesByDepartment: department '{}' not found", departmentName);
            return 0L;
        }
        long count = employeeRepository.countByDepartmentId(dept.get().getId());
        log.debug("[AI Tool] countEmployeesByDepartment('{}') → {}", departmentName, count);
        return count;
    }

    @Tool(description = """
            Returns a summary list of all employees.
            Each entry contains: id, full name, email, job title, status, and department name.
            Use when the user asks to list, show, or find all employees.
            """)
    public List<EmployeeSummary> listAllEmployees() {
        List<EmployeeSummary> result = employeeRepository.findAll()
                .stream()
                .map(EmployeeSummary::from)
                .toList();
        log.debug("[AI Tool] listAllEmployees → {} records", result.size());
        return result;
    }

    @Tool(description = """
            Searches employees by a keyword matched against first name, last name,
            email address, or job title. Returns a list of matching employee summaries.
            Use when the user asks to find or search for employees by name, email, or role.
            """)
    public List<EmployeeSummary> searchEmployees(
            @ToolParam(description = "Keyword to search across name, email, and job title")
            String keyword) {

        List<EmployeeSummary> result = employeeRepository
                .searchByKeyword(keyword, Pageable.unpaged())
                .getContent()
                .stream()
                .map(EmployeeSummary::from)
                .toList();
        log.debug("[AI Tool] searchEmployees('{}') → {} records", keyword, result.size());
        return result;
    }

    @Tool(description = """
            Returns the details of a single employee by their numeric ID.
            Use when the user asks about a specific employee and provides their ID.
            """)
    public EmployeeDetail getEmployeeById(
            @ToolParam(description = "The numeric employee ID")
            Long employeeId) {

        return employeeRepository.findById(employeeId)
                .map(EmployeeDetail::from)
                .orElse(null);
    }

    @Tool(description = """
            Returns all employees with the given status.
            Valid status values: ACTIVE, INACTIVE, ON_LEAVE, TERMINATED.
            Use when the user asks about employees filtered by their current status.
            """)
    public List<EmployeeSummary> listEmployeesByStatus(
            @ToolParam(description = "Employee status: ACTIVE, INACTIVE, ON_LEAVE, or TERMINATED")
            String status) {

        EmployeeStatus employeeStatus;
        try {
            employeeStatus = EmployeeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("[AI Tool] listEmployeesByStatus: invalid status '{}'", status);
            return List.of();
        }

        List<EmployeeSummary> result = employeeRepository.findByStatus(employeeStatus)
                .stream()
                .map(EmployeeSummary::from)
                .toList();
        log.debug("[AI Tool] listEmployeesByStatus('{}') → {} records", status, result.size());
        return result;
    }

    @Tool(description = """
            Returns all employees belonging to a specific department.
            The departmentName is matched case-insensitively.
            Use when the user asks to list employees in a specific department.
            """)
    public List<EmployeeSummary> listEmployeesByDepartment(
            @ToolParam(description = "The name of the department, e.g. 'Engineering'")
            String departmentName) {

        Optional<Department> dept = departmentRepository.findByNameIgnoreCase(departmentName);
        if (dept.isEmpty()) {
            log.debug("[AI Tool] listEmployeesByDepartment: department '{}' not found", departmentName);
            return List.of();
        }
        List<EmployeeSummary> result = employeeRepository
                .findByDepartmentId(dept.get().getId(), Pageable.unpaged())
                .getContent()
                .stream()
                .map(EmployeeSummary::from)
                .toList();
        log.debug("[AI Tool] listEmployeesByDepartment('{}') → {} records", departmentName, result.size());
        return result;
    }

    // ── Department queries ────────────────────────────────────────────────

    @Tool(description = """
            Returns a list of all departments with their names, descriptions,
            and the number of employees in each.
            Use when the user asks to list or show departments.
            """)
    public List<DepartmentSummary> listAllDepartments() {
        List<DepartmentSummary> result = departmentRepository.findAllOrderedByName()
                .stream()
                .map(DepartmentSummary::from)
                .toList();
        log.debug("[AI Tool] listAllDepartments → {} departments", result.size());
        return result;
    }

    @Tool(description = """
            Returns the details of a single department by name, including
            its description and the number of employees.
            Use when the user asks about a specific department by name.
            """)
    public DepartmentSummary getDepartmentByName(
            @ToolParam(description = "The department name, e.g. 'Finance'")
            String departmentName) {

        return departmentRepository.findByNameIgnoreCase(departmentName)
                .map(DepartmentSummary::from)
                .orElse(null);
    }

    // ── Analytics queries ─────────────────────────────────────────────────

    @Tool(description = """
            Returns the exact number of employees for each status value
            (ACTIVE, INACTIVE, ON_LEAVE, TERMINATED) as calculated by the database.
            Use when the user asks how many employees are active, on leave, etc.,
            or asks for a breakdown by employment status.
            """)
    public List<StatusCount> getEmployeeCountByStatus() {
        List<StatusCount> result = analyticsService.getCountByStatus()
                .stream()
                .map(dto -> new StatusCount(dto.getStatus().name(), dto.getCount()))
                .toList();
        log.debug("[AI Tool] getEmployeeCountByStatus → {} buckets", result.size());
        return result;
    }

    @Tool(description = """
            Returns a complete workforce summary: total employee count,
            total department count, employee counts per status, and employee
            counts per department. Use when the user asks for an overall
            workforce overview or a summary of the organisation.
            """)
    public WorkforceSummary getWorkforceSummary() {
        WorkforceSummaryDto dto = analyticsService.getWorkforceSummary();

        List<StatusCount> byStatus = dto.getCountByStatus()
                .stream()
                .map(s -> new StatusCount(s.getStatus().name(), s.getCount()))
                .toList();

        List<DeptCount> byDept = dto.getCountByDepartment()
                .stream()
                .map(d -> new DeptCount(d.getDepartmentName(), d.getCount()))
                .toList();

        WorkforceSummary summary = new WorkforceSummary(
                dto.getTotalEmployees(), dto.getTotalDepartments(), byStatus, byDept);
        log.debug("[AI Tool] getWorkforceSummary → {} employees, {} departments",
                dto.getTotalEmployees(), dto.getTotalDepartments());
        return summary;
    }

    // ── Inner record types returned to the model ──────────────────────────
    // Flat, serialisable records — no JPA entities or lazy proxies exposed.

    public record EmployeeSummary(
            Long id,
            String fullName,
            String email,
            String jobTitle,
            String status,
            String departmentName) {

        static EmployeeSummary from(Employee e) {
            return new EmployeeSummary(
                    e.getId(),
                    e.getFirstName() + " " + e.getLastName(),
                    e.getEmail(),
                    e.getJobTitle(),
                    e.getStatus() != null ? e.getStatus().name() : null,
                    e.getDepartment() != null ? e.getDepartment().getName() : null);
        }
    }

    public record EmployeeDetail(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String jobTitle,
            String salary,
            String hireDate,
            String status,
            String departmentName) {

        static EmployeeDetail from(Employee e) {
            return new EmployeeDetail(
                    e.getId(),
                    e.getFirstName(),
                    e.getLastName(),
                    e.getEmail(),
                    e.getPhone(),
                    e.getJobTitle(),
                    e.getSalary() != null ? e.getSalary().toPlainString() : null,
                    e.getHireDate() != null ? e.getHireDate().toString() : null,
                    e.getStatus() != null ? e.getStatus().name() : null,
                    e.getDepartment() != null ? e.getDepartment().getName() : null);
        }
    }

    public record DepartmentSummary(
            Long id,
            String name,
            String description,
            int employeeCount) {

        static DepartmentSummary from(Department d) {
            return new DepartmentSummary(
                    d.getId(),
                    d.getName(),
                    d.getDescription(),
                    d.getEmployees().size());
        }
    }

    /** Used by {@link #getEmployeeCountByStatus()}. */
    public record StatusCount(String status, long count) {}

    /** Used by {@link #getWorkforceSummary()}. */
    public record DeptCount(String departmentName, long count) {}

    /** Used by {@link #getWorkforceSummary()}. */
    public record WorkforceSummary(
            long totalEmployees,
            long totalDepartments,
            List<StatusCount> countByStatus,
            List<DeptCount> countByDepartment) {}
}
