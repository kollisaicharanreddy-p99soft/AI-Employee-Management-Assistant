package com.employee.management.service;

import com.employee.management.dto.EmployeeRequest;
import com.employee.management.dto.EmployeeResponse;
import com.employee.management.dto.PagedResponse;
import com.employee.management.exception.DuplicateResourceException;
import com.employee.management.exception.ResourceNotFoundException;
import com.employee.management.mapper.EmployeeMapper;
import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;

    public PagedResponse<EmployeeResponse> getAllEmployees(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Employee> employeePage = employeeRepository.findAll(pageable);
        return buildPagedResponse(employeePage);
    }

    public EmployeeResponse getEmployeeById(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        return employeeMapper.toResponse(employee);
    }

    public PagedResponse<EmployeeResponse> searchEmployees(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Employee> result = employeeRepository.searchByKeyword(keyword, pageable);
        return buildPagedResponse(result);
    }

    public PagedResponse<EmployeeResponse> getEmployeesByDepartment(Long departmentId, int page, int size) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department", "id", departmentId);
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Employee> result = employeeRepository.findByDepartmentId(departmentId, pageable);
        return buildPagedResponse(result);
    }

    public List<EmployeeResponse> getEmployeesByStatus(EmployeeStatus status) {
        return employeeRepository.findByStatus(status)
                .stream()
                .map(employeeMapper::toResponse)
                .toList();
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        if (employeeRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("Employee", "email", request.getEmail());
        }

        Employee employee = employeeMapper.toEntity(request);

        if (request.getDepartmentId() != null) {
            Department dept = findDepartmentOrThrow(request.getDepartmentId());
            employee.setDepartment(dept);
        }

        Employee saved = employeeRepository.save(employee);
        log.info("Created employee with id={}", saved.getId());
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);

        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Employee", "email", request.getEmail());
        }

        employeeMapper.updateEntity(request, employee);

        if (request.getDepartmentId() != null) {
            Department dept = findDepartmentOrThrow(request.getDepartmentId());
            employee.setDepartment(dept);
        } else {
            employee.setDepartment(null);
        }

        Employee saved = employeeRepository.save(employee);
        log.info("Updated employee with id={}", id);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse updateEmployeeStatus(Long id, EmployeeStatus status) {
        Employee employee = findEmployeeOrThrow(id);
        employee.setStatus(status);
        Employee saved = employeeRepository.save(employee);
        log.info("Updated status of employee id={} to {}", id, status);
        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employeeRepository.delete(employee);
        log.info("Deleted employee with id={}", id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }

    private PagedResponse<EmployeeResponse> buildPagedResponse(Page<Employee> page) {
        List<EmployeeResponse> content = page.getContent()
                .stream()
                .map(employeeMapper::toResponse)
                .toList();

        return PagedResponse.<EmployeeResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
