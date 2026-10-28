package com.employee.management.ai;

import com.employee.management.dto.ChatRequest;
import com.employee.management.dto.ChatResponse;
import com.employee.management.exception.AiServiceException;
import com.employee.management.repository.DepartmentRepository;
import com.employee.management.repository.EmployeeRepository;
import com.employee.management.service.AiChatService;
import com.employee.management.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AiChatService using a mock ChatModel.
 *
 * Verifies:
 * - Successful responses are correctly wrapped in ChatResponse.
 * - The question is echoed back.
 * - A ChatModel failure is wrapped in AiServiceException.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiChatService — unit tests with mock ChatModel")
class AiChatServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AnalyticsService analyticsService;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        EmployeeTools tools = new EmployeeTools(employeeRepository, departmentRepository, analyticsService);
        aiChatService = new AiChatService(chatModel, tools);
    }

    @Test
    @DisplayName("chat returns answer and echoes the question")
    void chat_success() {
        String expectedAnswer = "There are 5 active employees.";
        org.springframework.ai.chat.model.ChatResponse mockResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage(expectedAnswer))));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        ChatRequest request = new ChatRequest();
        request.setQuestion("How many active employees are there?");

        ChatResponse response = aiChatService.chat(request);

        assertThat(response.getAnswer()).isEqualTo(expectedAnswer);
        assertThat(response.getQuestion()).isEqualTo("How many active employees are there?");
        assertThat(response.getConversationId()).isNull();
    }

    @Test
    @DisplayName("chat preserves conversationId from request")
    void chat_preservesConversationId() {
        String expectedAnswer = "Engineering has 2 employees.";
        org.springframework.ai.chat.model.ChatResponse mockResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage(expectedAnswer))));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        ChatRequest request = new ChatRequest();
        request.setQuestion("How many people in Engineering?");
        request.setConversationId("session-abc-123");

        ChatResponse response = aiChatService.chat(request);

        assertThat(response.getConversationId()).isEqualTo("session-abc-123");
    }

    @Test
    @DisplayName("chat wraps ChatModel exception in AiServiceException")
    void chat_modelFailure_throwsAiServiceException() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        ChatRequest request = new ChatRequest();
        request.setQuestion("List all employees.");

        assertThatThrownBy(() -> aiChatService.chat(request))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("AI service is currently unavailable");
    }
}
