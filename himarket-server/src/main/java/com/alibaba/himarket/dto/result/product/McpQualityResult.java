package com.alibaba.himarket.dto.result.product;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Quality evaluation result for an MCP product. */
@Data
@Builder
public class McpQualityResult {

    /** Overall score (0-100), weighted by check severity. */
    private int score;

    /** Grade derived from score: S / A / B / C / D */
    private String grade;

    /** Number of checks that passed. */
    private int passedChecks;

    /** Total number of checks performed. */
    private int totalChecks;

    /** Number of tools found in the MCP configuration. */
    private int toolCount;

    /** All issues across every level (product + tools + parameters). */
    private List<QualityIssue> issues;

    /** Per-tool evaluation details. */
    private List<ToolQuality> tools;

    @Data
    @Builder
    public static class QualityIssue {

        /** Severity: CRITICAL or WARNING */
        private String level;

        /**
         * Dot-notation field path, e.g.:
         * product.description / tool[get_weather].name /
         * tool[get_weather].param[city].type
         */
        private String field;

        /** Human-readable description of the issue. */
        private String message;

        /** The standard or requirement that was violated. */
        private String standard;
    }

    @Data
    @Builder
    public static class ToolQuality {

        /** Tool name. */
        private String name;

        /** Number of parameters defined in the tool's inputSchema. */
        private int paramCount;

        /** Issues found for this tool (name, description, parameters). */
        private List<QualityIssue> issues;
    }
}
