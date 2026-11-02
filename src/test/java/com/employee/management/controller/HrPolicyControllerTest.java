package com.employee.management.controller;

import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyDocumentDto;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.dto.PolicySourceReference;
import com.employee.management.dto.PolicyUploadResponse;
import com.employee.management.exception.DuplicateResourceException;
import com.employee.management.exception.ResourceNotFoundException;
import com.employee.management.service.HrPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HrPolicyController.class)
@DisplayName("HrPolicyController — web layer tests")
class HrPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HrPolicyService hrPolicyService;

    @Test
    @DisplayName("POST /api/v1/hr-policies/upload returns 201 with PolicyUploadResponse")
    void uploadPolicy_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "leave_policy.txt", "text/plain", "Sample leave policy".getBytes());

        PolicyUploadResponse response = PolicyUploadResponse.builder()
                .documentId("doc-123")
                .fileName("leave_policy.txt")
                .fileType("txt")
                .chunkCount(2)
                .status("SUCCESS")
                .message("Document successfully processed and indexed into vector store")
                .uploadedAt(LocalDateTime.now())
                .build();

        when(hrPolicyService.uploadPolicy(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/hr-policies/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").value("doc-123"))
                .andExpect(jsonPath("$.data.fileName").value("leave_policy.txt"))
                .andExpect(jsonPath("$.data.chunkCount").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/hr-policies/upload returns 409 Conflict on duplicate upload")
    void uploadPolicy_duplicateConflict() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dup.txt", "text/plain", "Duplicate content".getBytes());

        when(hrPolicyService.uploadPolicy(any())).thenThrow(
                new DuplicateResourceException("Policy document", "content hash", "abc123hash"));

        mockMvc.perform(multipart("/api/v1/hr-policies/upload").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/hr-policies/ask returns 200 with answer and source citations")
    void askQuestion_success() throws Exception {
        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("What is the remote work policy?")
                .build();

        PolicySourceReference source = PolicySourceReference.builder()
                .documentName("wfh_policy.pdf")
                .pageNumber(1)
                .snippet("Remote work is permitted 2 days per week.")
                .score(0.92)
                .build();

        PolicyAnswerResponse response = PolicyAnswerResponse.builder()
                .question("What is the remote work policy?")
                .answer("Employees may work remotely 2 days per week.")
                .sources(List.of(source))
                .answeredAt(LocalDateTime.now())
                .informationFound(true)
                .build();

        when(hrPolicyService.askQuestion(any(PolicyQuestionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/hr-policies/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.question").value("What is the remote work policy?"))
                .andExpect(jsonPath("$.data.answer").value("Employees may work remotely 2 days per week."))
                .andExpect(jsonPath("$.data.informationFound").value(true))
                .andExpect(jsonPath("$.data.sources[0].documentName").value("wfh_policy.pdf"))
                .andExpect(jsonPath("$.data.sources[0].pageNumber").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/hr-policies/ask returns 400 on blank question")
    void askQuestion_blankQuestion_badRequest() throws Exception {
        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("   ")
                .build();

        mockMvc.perform(post("/api/v1/hr-policies/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/hr-policies returns 200 with list of documents")
    void listPolicies_success() throws Exception {
        PolicyDocumentDto doc1 = PolicyDocumentDto.builder()
                .documentId("doc-1")
                .fileName("pto.txt")
                .fileType("txt")
                .fileSize(1024)
                .chunkCount(1)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(hrPolicyService.listPolicies()).thenReturn(List.of(doc1));

        mockMvc.perform(get("/api/v1/hr-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.data[0].fileName").value("pto.txt"));
    }

    @Test
    @DisplayName("DELETE /api/v1/hr-policies/{documentId} returns 200 on success")
    void deletePolicy_success() throws Exception {
        doNothing().when(hrPolicyService).deletePolicy("doc-123");

        mockMvc.perform(delete("/api/v1/hr-policies/doc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Policy document and embeddings deleted successfully"));
    }

    @Test
    @DisplayName("DELETE /api/v1/hr-policies/{documentId} returns 404 when not found")
    void deletePolicy_notFound() throws Exception {
        doThrow(new ResourceNotFoundException("Policy document not found with ID: doc-999"))
                .when(hrPolicyService).deletePolicy("doc-999");

        mockMvc.perform(delete("/api/v1/hr-policies/doc-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

