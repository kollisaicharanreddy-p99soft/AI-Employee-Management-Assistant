package com.employee.management.mapper;

import com.employee.management.dto.EmployeeRequest;
import com.employee.management.dto.EmployeeResponse;
import com.employee.management.model.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeeMapper {

    @Mapping(target = "fullName",
             expression = "java(employee.getFirstName() + ' ' + employee.getLastName())")
    @Mapping(target = "departmentId",
             source = "department.id")
    @Mapping(target = "departmentName",
             source = "department.name")
    EmployeeResponse toResponse(Employee employee);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // FQN required: MapStruct copies this expression verbatim into the generated impl,
    // which does not import EmployeeStatus automatically.
    @Mapping(target = "status",
             defaultExpression = "java(com.employee.management.model.EmployeeStatus.ACTIVE)")
    Employee toEntity(EmployeeRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(EmployeeRequest request, @MappingTarget Employee employee);
}
