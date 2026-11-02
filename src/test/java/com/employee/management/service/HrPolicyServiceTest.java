package com.employee.management.service;

import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyDocumentDto;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.dto.PolicyUploadResponse;
import com.employee.management.exception.DuplicateResourceException;
import com.employee.management.exception.ResourceNotFoundException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HrPolicyService Unit Tests")
class HrPolicyServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    private HrPolicyService hrPolicyService;

    @BeforeEach
    void setUp() {
        hrPolicyService = new HrPolicyService(vectorStore, chatModel);
    }

    private byte[] createSamplePdf(String content) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(content);
                contentStream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Upload valid TXT document successfully stores chunks in VectorStore")
    void uploadTxtDocument_success() {
        String content = "Employees are entitled to 20 days of paid vacation per calendar year. Sick leave is 10 days.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "vacation_policy.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        PolicyUploadResponse response = hrPolicyService.uploadPolicy(file);

        assertThat(response).isNotNull();
        assertThat(response.getDocumentId()).isNotBlank();
        assertThat(response.getFileName()).isEqualTo("vacation_policy.txt");
        assertThat(response.getFileType()).isEqualTo("txt");
        assertThat(response.getChunkCount()).isGreaterThan(0);
        assertThat(response.getStatus()).isEqualTo("SUCCESS");

        verify(vectorStore, times(1)).add(anyList());

        List<PolicyDocumentDto> policies = hrPolicyService.listPolicies();
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).getDocumentId()).isEqualTo(response.getDocumentId());
    }

    @Test
    @DisplayName("Upload valid PDF document successfully extracts text and indexes chunks")
    void uploadPdfDocument_success() throws IOException {
        byte[] pdfBytes = createSamplePdf("Employees may work remotely up to two days per week with manager approval.");
        MockMultipartFile file = new MockMultipartFile(
                "file", "wfh_policy.pdf", "application/pdf", pdfBytes);

        PolicyUploadResponse response = hrPolicyService.uploadPolicy(file);

        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("wfh_policy.pdf");
        assertThat(response.getFileType()).isEqualTo("pdf");
        assertThat(response.getChunkCount()).isGreaterThan(0);

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @DisplayName("Upload rejects empty file")
    void uploadEmptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> hrPolicyService.uploadPolicy(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Upload rejects unsupported file types")
    void uploadUnsupportedFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "sample content".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> hrPolicyService.uploadPolicy(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    @DisplayName("Upload duplicate document with identical content throws DuplicateResourceException")
    void uploadDuplicateDocument_throwsException() {
        String content = "Annual performance reviews occur every December.";
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "reviews_v1.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "reviews_v2.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        hrPolicyService.uploadPolicy(file1);

        assertThatThrownBy(() -> hrPolicyService.uploadPolicy(file2))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("matches 'reviews_v1.txt'");
    }

    @Test
    @DisplayName("Ask question with no documents indexed returns fallback immediately")
    void askQuestion_noDocuments_returnsFallback() {
        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("What is the sick leave policy?")
                .build();

        PolicyAnswerResponse response = hrPolicyService.askQuestion(req);

        assertThat(response.getAnswer()).isEqualTo(HrPolicyService.FALLBACK_NO_INFO);
        assertThat(response.isInformationFound()).isFalse();
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    @DisplayName("Ask question with no matching vector results returns fallback without LLM call")
    void askQuestion_noMatchingChunks_returnsFallback() {
        // Ingest a document
        String content = "Health insurance covers dental and vision.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "insurance.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
        hrPolicyService.uploadPolicy(file);

        // Vector store returns no matches above similarity threshold
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("What is the parental leave duration?")
                .build();

        PolicyAnswerResponse response = hrPolicyService.askQuestion(req);

        assertThat(response.getAnswer()).isEqualTo(HrPolicyService.FALLBACK_NO_INFO);
        assertThat(response.isInformationFound()).isFalse();
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    @DisplayName("Ask question with relevant chunks returns grounded answer with source citations")
    void askQuestion_withMatchingChunks_callsLlmAndReturnsCitations() {
        // Ingest a document
        String content = "Employees receive 10 days of paid sick leave per year.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "sick_leave.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
        hrPolicyService.uploadPolicy(file);

        Document matchedDoc = Document.builder()
                .text("Employees receive 10 days of paid sick leave per year.")
                .metadata(Map.of("fileName", "sick_leave.txt", "pageNumber", 1))
                .score(0.88)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(matchedDoc));

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                new org.springframework.ai.chat.messages.AssistantMessage("Employees are entitled to 10 days of paid sick leave per year.");
        Generation gen = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(gen));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("How many sick days do employees get?")
                .build();

        PolicyAnswerResponse response = hrPolicyService.askQuestion(req);

        assertThat(response.isInformationFound()).isTrue();
        assertThat(response.getAnswer()).contains("10 days");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getDocumentName()).isEqualTo("sick_leave.txt");
        assertThat(response.getSources().get(0).getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Delete document removes vector entries and metadata from registry")
    void deleteDocument_success() {
        String content = "Overtime must be pre-approved by the department manager.";
        MockMultipartFile file = new MockMultipartFile(
                "file", "overtime.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        PolicyUploadResponse uploadRes = hrPolicyService.uploadPolicy(file);
        assertThat(hrPolicyService.listPolicies()).hasSize(1);

        hrPolicyService.deletePolicy(uploadRes.getDocumentId());

        assertThat(hrPolicyService.listPolicies()).isEmpty();
        verify(vectorStore, times(1)).delete(anyList());
    }

    @Test
    @DisplayName("Delete non-existent document throws ResourceNotFoundException")
    void deleteDocument_notFound_throwsException() {
        assertThatThrownBy(() -> hrPolicyService.deletePolicy("non-existent-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
