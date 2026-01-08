package com.alibaba.himarket.service.hichat.support;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Data
@Builder
public class ChatBot {

    private static final int MAX_MEMORY_SIZE = 30;
    private static final long DEGRADED_TTL_MS = 2 * 60 * 1000;

    private final ReActAgent agent;
    private final Map<String, ToolMeta> toolMetas;

    /**
     * Whether this ChatBot is in degraded mode (some MCP tools failed to initialize)
     */
    @Builder.Default private boolean degraded = false;

    /**
     * Timestamp when this ChatBot was created
     */
    @Builder.Default private long createTime = System.currentTimeMillis();

    public Flux<Event> chat(Msg userMsg) {
        // Truncate memory before adding new messages
        truncateMemory();

        StreamOptions streamOptions =
                StreamOptions.builder()
                        .eventTypes(EventType.ALL)
                        .incremental(true)
                        .includeReasoningChunk(true)
                        // Exclude complete result to avoid duplication
                        .includeReasoningResult(true)
                        .build();

        return agent.stream(userMsg, streamOptions);
    }

    /**
     * Truncate memory if it exceeds maximum size. Remove the oldest message.
     */
    private void truncateMemory() {
        Memory memory = agent.getMemory();
        List<Msg> messages = memory.getMessages();

        if (messages.size() > MAX_MEMORY_SIZE) {
            // Remove the oldest message
            messages.remove(0);
            log.debug("Memory overflow, removed oldest message, current size: {}", messages.size());
        }
    }

    /**
     * Check if this ChatBot is still valid for use
     *
     * <p>Validation rules:
     * - Normal ChatBot: always valid (no expiration)
     * - Degraded ChatBot: valid only within 2 minutes after creation
     *
     * @return true if valid and can be reused, false if should be recreated
     */
    public boolean isValid() {
        // Degraded ChatBot: check TTL
        if (degraded) {
            long age = System.currentTimeMillis() - createTime;
            return age <= DEGRADED_TTL_MS;
        }

        // Normal ChatBot: always valid
        return true;
    }
}
