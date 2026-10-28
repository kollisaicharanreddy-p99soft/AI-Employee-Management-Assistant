package com.employee.management.service;

import com.employee.management.dto.DepartmentRequest;
import com.employee.management.dto.DepartmentResponse;
import com.employee.management.exception.DuplicateResourceException;
import com.employee.management.exception.ResourceNotFoundException;
import com.employee.management.mapper.DepartmentMapper;
import com.employee.management.model.Department;
import com.employee.management.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAllOrderedByName()
                .stream()
                .map(departmentMapper::toResponse)
                .toList();
    }

    public DepartmentResponse getDepartmentById(Long id) {
        Department department = findDepartmentOrThrow(id);
        return departmentMapper.toResponse(department);
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Department", "name", request.getName());
        }
        Department department = departmentMapper.toEntity(request);
        Department saved = departmentRepository.save(department);
        log.info("Created department with id={}", saved.getId());
        return departmentMapper.toResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department department = findDepartmentOrThrow(id);

        if (!department.getName().equalsIgnoreCase(request.getName())
                && departmentRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Department", "name", request.getName());
        }

        departmentMapper.updateEntity(request, department);
        Department saved = departmentRepository.save(department);
        log.info("Updated department with id={}", id);
        return departmentMapper.toResponse(saved);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = findDepartmentOrThrow(id);
        if (!department.getEmployees().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete department with employees. Reassign or remove employees first.");
        }
        departmentRepository.delete(department);
        log.info("Deleted department with id={}", id);
    }

    private Department findDepartmentOrThrow(Long id) {
        return departmentRepository.findByIdWithEmployees(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }
}
