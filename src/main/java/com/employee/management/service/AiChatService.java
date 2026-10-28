package com.employee.management.service;

import com.employee.management.ai.EmployeeTools;
import com.employee.management.dto.ChatRequest;
import com.employee.management.dto.ChatResponse;
import com.employee.management.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the AI chat interaction.
 *
 * <p>A new {@link ChatClient} is built per request so each conversation starts
 * with a clean context. The {@link EmployeeTools} bean is registered as a
 * tool provider, giving the model read-only access to live database data.
 *
 * <p>No LLM key or network call is made unless a real {@code ChatModel}
 * implementation is on the classpath and {@code SPRING_AI_OPENAI_API_KEY}
 * is set.
 */
@Slf4j
@Service
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
            You are an HR assistant for the Employee Management System.
            You help users answer questions about employees and departments.

            IMPORTANT RULES:
            - ALWAYS use the provided tools to fetch data from the database.
              Never invent, estimate, or guess employee names, counts, salaries,
              or any other factual information.
            - Only answer questions about employees and departments.
              Politely decline unrelated topics.
            - For mutation requests (create, update, delete), explain that those
              operations require using the dedicated REST API endpoints and cannot
              be performed through this chat.
            - When you cannot find a matching employee or department via the tools,
              say so clearly rather than making something up.
            - Present data in a clear, concise, human-readable format.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final EmployeeTools employeeTools;

    public AiChatService(ChatModel chatModel, EmployeeTools employeeTools) {
        this.chatClientBuilder = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(employeeTools);   // registers all @Tool methods on EmployeeTools
        this.employeeTools = employeeTools;
    }

    /**
     * Sends the user's question to the LLM, allowing it to call
     * {@link EmployeeTools} methods as needed, and returns the final answer.
     *
     * @param request the user's question and optional conversation ID
     * @return the AI-generated answer backed by real database data
     * @throws AiServiceException if the underlying model call fails
     */
    public ChatResponse chat(ChatRequest request) {
        log.debug("AI chat request: '{}'", request.getQuestion());
        try {
            String answer = chatClientBuilder.build()
                    .prompt()
                    .user(request.getQuestion())
                    .call()
                    .content();

            log.debug("AI chat response length: {} chars", answer != null ? answer.length() : 0);

            return ChatResponse.builder()
                    .question(request.getQuestion())
                    .answer(answer)
                    .conversationId(request.getConversationId())
                    .build();

        } catch (Exception ex) {
            log.error("AI service error for question='{}': {}", request.getQuestion(), ex.getMessage(), ex);
            throw new AiServiceException("AI service is currently unavailable. Please try again later.", ex);
        }
    }
}
