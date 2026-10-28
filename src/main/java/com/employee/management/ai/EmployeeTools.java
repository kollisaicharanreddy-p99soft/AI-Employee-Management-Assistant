package com.employee.management.ai;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
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
 * Spring AI tool implementations backed by real JPA queries.
 *
 * <p>IMPORTANT DESIGN RULES enforced here:
 * <ul>
 *   <li>Every tool performs a real DB query — no in-memory defaults, no null returns
 *       silently converted to empty lists.</li>
 *   <li>{@link DepartmentSummary} never calls {@code d.getEmployees().size()} on a
 *       lazy collection. Employee counts are sourced from aggregation queries only.</li>
 *   <li>Tool descriptions are mutually exclusive — only one tool matches each
 *       question type so the model cannot pick the wrong one.</li>
 *   <li>Mutation operations are intentionally absent.</li>
 * </ul>
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
            Returns the exact total number of employees across ALL statuses
            (ACTIVE, INACTIVE, ON_LEAVE, and TERMINATED combined).
            Use ONLY when the user asks for the overall headcount with no status filter.
            Do NOT use for status-specific counts — use getEmployeeCountByStatus instead.
            """)
    public long countAllEmployees() {
        long count = employeeRepository.count();
        log.debug("[AI Tool] countAllEmployees → {}", count);
        return count;
    }

    @Tool(description = """
            Returns the exact employee count for EVERY status in one call:
            ACTIVE, INACTIVE, ON_LEAVE, and TERMINATED.
            Use this when the user asks how many employees are active, on leave,
            inactive, or terminated — or asks for a breakdown by status.
            The result always contains all four statuses even if count is 0.
            """)
    public List<StatusCount> getEmployeeCountByStatus() {
        List<StatusCount> result = analyticsService.getCountByStatus()
                .stream()
                .map(dto -> new StatusCount(dto.getStatus().name(), dto.getCount()))
                .toList();
        log.debug("[AI Tool] getEmployeeCountByStatus → {}", result);
        return result;
    }

    @Tool(description = """
            Returns a full list of all employees in the database.
            Each entry contains: id, full name, email, job title, status, department name.
            Use when the user asks to list or show all employees regardless of status.
            Do NOT use for status-filtered lists — use listEmployeesByStatus instead.
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
            Returns all employees that match a search keyword.
            The keyword is matched (case-insensitively) against first name, last name,
            email address, and job title.
            Use when the user wants to find an employee by name, email, or job title.
            """)
    public List<EmployeeSummary> searchEmployees(
            @ToolParam(description = "Search keyword matched against name, email, and job title")
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
            Returns the full details of one employee identified by their numeric ID.
            Use only when the user provides a specific employee ID number.
            """)
    public EmployeeDetail getEmployeeById(
            @ToolParam(description = "The numeric employee ID (integer)")
            Long employeeId) {

        Optional<EmployeeDetail> detail = employeeRepository.findById(employeeId)
                .map(EmployeeDetail::from);
        log.debug("[AI Tool] getEmployeeById({}) → {}", employeeId,
                detail.isPresent() ? "found" : "not found");
        return detail.orElse(null);
    }

    @Tool(description = """
            Returns all employees that have a specific employment status.
            Valid values for the status parameter: ACTIVE, INACTIVE, ON_LEAVE, TERMINATED.
            Use when the user asks to list employees filtered by status,
            e.g. "show me active employees" or "who is on leave?".
            """)
    public List<EmployeeSummary> listEmployeesByStatus(
            @ToolParam(description = "Employment status: ACTIVE, INACTIVE, ON_LEAVE, or TERMINATED")
            String status) {

        EmployeeStatus employeeStatus;
        try {
            employeeStatus = EmployeeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[AI Tool] listEmployeesByStatus: unrecognised status '{}'", status);
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
            Returns all employees who belong to the named department.
            The department name is matched case-insensitively.
            Use when the user asks to list employees in a specific department,
            e.g. "show me Engineering employees" or "who works in Finance?".
            """)
    public List<EmployeeSummary> listEmployeesByDepartment(
            @ToolParam(description = "Department name, e.g. 'Engineering' or 'Finance'")
            String departmentName) {

        Optional<Department> dept = departmentRepository.findByNameIgnoreCase(departmentName);
        if (dept.isEmpty()) {
            log.debug("[AI Tool] listEmployeesByDepartment: '{}' not found", departmentName);
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
            Returns every department with its exact employee headcount.
            Uses a database aggregation (LEFT JOIN + COUNT) so departments
            with zero employees are included. Ordered alphabetically.
            Use when the user asks to list departments or wants to know
            how many employees each department has.
            Do NOT use for sorting by size — use getDepartmentsByHeadcount instead.
            """)
    public List<DeptCount> listAllDepartmentsWithCounts() {
        List<DeptCount> result = analyticsService.getCountByDepartment()
                .stream()
                .map(dto -> new DeptCount(dto.getDepartmentName(), dto.getCount()))
                .toList();
        log.debug("[AI Tool] listAllDepartmentsWithCounts → {}", result);
        return result;
    }

    @Tool(description = """
            Returns every department sorted by employee headcount from highest to lowest.
            Departments with equal headcount are sorted alphabetically.
            Uses a single database aggregation query — counts are exact.
            Use when the user asks which department has the most employees,
            wants a ranking of departments by size, or asks about workforce distribution.
            """)
    public List<DeptCount> getDepartmentsByHeadcount() {
        List<DeptCount> result = analyticsService.getCountByDepartmentDesc()
                .stream()
                .map(dto -> new DeptCount(dto.getDepartmentName(), dto.getCount()))
                .toList();
        log.debug("[AI Tool] getDepartmentsByHeadcount → {}", result);
        return result;
    }

    @Tool(description = """
            Returns the full details of a single named department including its description.
            Use only when the user asks about one specific department by name.
            Do NOT use to list all departments.
            """)
    public DepartmentDetail getDepartmentByName(
            @ToolParam(description = "The exact department name, e.g. 'Finance'")
            String departmentName) {

        Optional<DepartmentDetail> detail = departmentRepository
                .findByNameIgnoreCase(departmentName)
                .map(d -> {
                    // Count employees via aggregation — never touch the lazy collection
                    long count = employeeRepository.countByDepartmentId(d.getId());
                    return new DepartmentDetail(d.getId(), d.getName(), d.getDescription(), count);
                });
        log.debug("[AI Tool] getDepartmentByName('{}') → {}",
                departmentName, detail.isPresent() ? "found" : "not found");
        return detail.orElse(null);
    }

    // ── Analytics ─────────────────────────────────────────────────────────

    @Tool(description = """
            Returns a complete workforce overview in a single call:
            - totalEmployees: exact headcount across all statuses
            - totalDepartments: number of departments
            - countByStatus: employee count for each of ACTIVE, INACTIVE, ON_LEAVE, TERMINATED
            - countByDepartment: employee count per department (alphabetical)
            Use when the user asks for a workforce overview, summary, or statistics.
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
        log.debug("[AI Tool] getWorkforceSummary → {}", summary);
        return summary;
    }

    // ── Inner record types ────────────────────────────────────────────────
    // Flat, serialisable records. No JPA entities or lazy proxies are exposed.

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

    /** Department detail including exact employee count from a DB aggregation. */
    public record DepartmentDetail(
            Long id,
            String name,
            String description,
            long employeeCount) {}

    /** Per-status employee count used by {@link #getEmployeeCountByStatus()}. */
    public record StatusCount(String status, long count) {}

    /** Per-department employee count used by analytics tools. */
    public record DeptCount(String departmentName, long count) {}

    /** Full workforce summary returned by {@link #getWorkforceSummary()}. */
    public record WorkforceSummary(
            long totalEmployees,
            long totalDepartments,
            List<StatusCount> countByStatus,
            List<DeptCount> countByDepartment) {}
}
