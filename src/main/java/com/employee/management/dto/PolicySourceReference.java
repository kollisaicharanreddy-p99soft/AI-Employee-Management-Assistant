package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicySourceReference {

    private String documentName;
    private Integer pageNumber;
    private String snippet;
    private Double score;
}

