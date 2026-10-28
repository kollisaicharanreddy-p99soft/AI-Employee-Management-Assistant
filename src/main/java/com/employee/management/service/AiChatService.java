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
 * Orchestrates AI chat requests.
 *
 * <p>A single {@link ChatClient} is built at construction time.
 * All {@link EmployeeTools} {@code @Tool}-annotated methods are registered as
 * default tools so the model can call any of them on every request.
 *
 * <h3>Hallucination prevention</h3>
 * The system prompt forbids the model from producing any numbers, names, or
 * statistics that were not returned by a tool call. This is enforced at the
 * prompt level; the model-independent safeguard is that every tool returns
 * structured Java records — the model's only job is to restate them in prose.
 */
@Slf4j
@Service
public class AiChatService {

    /**
     * System prompt designed to be effective on small local models (llama3.2 3B,
     * mistral 7B) as well as larger ones. Key properties:
     * <ul>
     *   <li>Explicit FORBIDDEN list stops placeholder generation.</li>
     *   <li>Mandatory tool-before-answer rule prevents hallucination.</li>
     *   <li>Simple prose instruction avoids meta-commentary on the tools.</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
            You are an HR data assistant. Your only job is to answer questions
            about employees and departments using the tools provided to you.

            MANDATORY RULES — follow every rule on every response:
            1. You MUST call a tool before answering any question about employees
               or departments. Never answer from memory or training data.
            2. Use the exact numbers and names returned by the tool. Do not change,
               round, estimate, or paraphrase any count or name.
            3. If a tool returns an empty list, say "No records found." Do not
               invent records to fill the gap.
            4. If a tool returns a count of 0 for a status or department, report
               exactly 0. Do not omit it or claim it is unknown.

            FORBIDDEN — never include any of the following in a response:
            - Placeholder text such as [Insert count here], [Employee Count Here],
              [Department Name], or any text inside square brackets.
            - Example JSON, sample data, or illustrative records.
            - Instructions, tool descriptions, or explanations of how tools work.
            - Counts or names you have not received from a tool result this turn.
            - Phrases like "based on typical data" or "usually there are".

            SCOPE:
            - Only answer questions about employees and departments.
            - For any other topic, reply: "I can only answer questions about
              employees and departments."
            - For create/update/delete requests, reply: "Data changes must be made
              through the REST API. I only read data."
            """;

    private final ChatClient chatClient;

    public AiChatService(ChatModel chatModel, EmployeeTools employeeTools) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(employeeTools)
                .build();
    }

    /**
     * Sends the user's question to the LLM. The model will invoke one or more
     * tools from {@link EmployeeTools} and then compose a prose answer from the
     * returned data. The raw tool results are logged at DEBUG level.
     *
     * @param request validated chat request
     * @return AI-generated answer backed exclusively by tool results
     * @throws AiServiceException if the underlying model call fails
     */
    public ChatResponse chat(ChatRequest request) {
        log.debug("AI chat request: '{}'", request.getQuestion());
        try {
            String answer = chatClient
                    .prompt()
                    .user(request.getQuestion())
                    .call()
                    .content();

            log.debug("AI chat response length: {} chars",
                    answer != null ? answer.length() : 0);

            return ChatResponse.builder()
                    .question(request.getQuestion())
                    .answer(answer)
                    .conversationId(request.getConversationId())
                    .build();

        } catch (Exception ex) {
            log.error("AI service error for question='{}': {}",
                    request.getQuestion(), ex.getMessage(), ex);
            throw new AiServiceException(
                    "AI service is currently unavailable. Please try again later.", ex);
        }
    }
}
