package com.employee.management.controller;

import com.employee.management.dto.ApiResponse;
import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyDocumentDto;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.dto.PolicyUploadResponse;
import com.employee.management.service.HrPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for HR Policy RAG Assistant.
 * Exposes document upload, grounded question-answering, metadata listing, and document deletion.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr-policies")
@Tag(name = "HR Policy Assistant", description = "Endpoints for uploading HR policy documents and asking grounded policy questions using Spring AI RAG")
public class HrPolicyController {

    private final HrPolicyService hrPolicyService;

    public HrPolicyController(HrPolicyService hrPolicyService) {
        this.hrPolicyService = hrPolicyService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an HR policy document (PDF or TXT) into the vector store")
    public ResponseEntity<ApiResponse<PolicyUploadResponse>> uploadPolicy(
            @Parameter(description = "HR Policy document file (.pdf or .txt, max 10MB)")
            @RequestParam("file") MultipartFile file) {
        log.info("Received request to upload policy file: {}", file != null ? file.getOriginalFilename() : "null");
        PolicyUploadResponse response = hrPolicyService.uploadPolicy(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Document uploaded and indexed successfully"));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question about HR policies with RAG retrieval and source citations")
    public ResponseEntity<ApiResponse<PolicyAnswerResponse>> askPolicyQuestion(
            @Valid @RequestBody PolicyQuestionRequest request) {
        log.info("Received HR policy question: '{}'", request.getQuestion());
        PolicyAnswerResponse response = hrPolicyService.askQuestion(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Policy question answered successfully"));
    }

    @GetMapping
    @Operation(summary = "List all indexed HR policy documents")
    public ResponseEntity<ApiResponse<List<PolicyDocumentDto>>> listPolicies() {
        log.info("Received request to list indexed HR policy documents");
        List<PolicyDocumentDto> policies = hrPolicyService.listPolicies();
        return ResponseEntity.ok(ApiResponse.success(policies, "Indexed policy documents retrieved successfully"));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete an HR policy document and purge its vector embeddings")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @Parameter(description = "ID of the policy document to delete")
            @PathVariable("documentId") String documentId) {
        log.info("Received request to delete policy document ID: {}", documentId);
        hrPolicyService.deletePolicy(documentId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Policy document and embeddings deleted successfully")
                .build());
    }
}
