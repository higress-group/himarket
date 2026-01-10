package com.alibaba.himarket.service.hichat.support;

import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.support.chat.ChatUsage;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ChatContext {

    /**
     * Chat ID for tracking
     */
    private final String chatId;

    /**
     * Answer content
     */
    private final StringBuilder answerContent = new StringBuilder();

    /**
     * Chat usage (tokens)
     */
    private ChatUsage usage;

    /**
     * Success flag
     */
    private boolean success = true;

    /**
     * Request start time in milliseconds
     */
    private Long startTime;

    /**
     * First byte timeout (time to first byte in milliseconds)
     */
    private Long firstByteTimeout;

    /**
     * Tool name to tool metadata mapping
     */
    private Map<String, ToolMeta> toolMetas;

    public ChatContext(String chatId) {
        this.chatId = chatId;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Record first byte arrival time
     */
    public void recordFirstByteTimeout() {
        if (firstByteTimeout == null && startTime != null) {
            firstByteTimeout = System.currentTimeMillis() - startTime;
            log.debug("First byte received after {} ms", firstByteTimeout);
        }
    }

    /**
     * Stop timing and update usage with elapsed time
     */
    public void stop() {
        if (startTime == null) {
            return;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        if (usage != null) {
            usage.setElapsedTime(elapsedTime);
            log.debug("Total elapsed time: {} ms", elapsedTime);
        }
    }

    /**
     * Collect chat event and update context
     *
     * @param event ChatEvent to collect
     */
    public void collect(ChatEvent event) {
        if (event == null) {
            return;
        }

        switch (event.getType()) {
            case ASSISTANT:
            case THINKING:
                // Accumulate assistant response and thinking content
                if (event.getContent() != null) {
                    // Record first byte arrival time
                    recordFirstByteTimeout();
                    answerContent.append(event.getContent());
                }
                break;

            case DONE:
                break;

            case ERROR:
                // Mark as failed
                this.success = false;
                if (event.getMessage() != null) {
                    answerContent.append("\n[Error: ").append(event.getMessage()).append("]");
                }
                break;

            default:
                // Ignore other event types (TOOL_CALL, TOOL_RESULT, START)
                break;
        }
    }

    /**
     * Append additional content to answer
     *
     * @param content Content to append
     */
    public void appendAnswer(String content) {
        if (content != null) {
            answerContent.append(content);
        }
    }

    /**
     * Get complete answer content
     *
     * @return Complete answer as string
     */
    public String getAnswer() {
        return answerContent.toString();
    }

    /**
     * Get tool metadata for a given tool name
     *
     * @param toolName tool name
     * @return ToolMeta, or null if not found
     */
    public ToolMeta getToolMeta(String toolName) {
        return toolMetas != null ? toolMetas.get(toolName) : null;
    }

    /**
     * Convert to LlmInvokeResult for database persistence
     *
     * @return LlmInvokeResult instance
     */
    public LlmInvokeResult toResult() {
        return LlmInvokeResult.builder().success(success).answer(getAnswer()).usage(usage).build();
    }

    public void fail() {
        this.success = false;
    }
}
