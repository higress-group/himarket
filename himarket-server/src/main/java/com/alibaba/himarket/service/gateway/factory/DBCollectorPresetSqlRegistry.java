/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.gateway.factory;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DBCollector scenario SQL registry for the MySQL/MariaDB dialect.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Scenario names match frontend contracts and should stay close to {@link
 *       SlsPresetSqlRegistry} where possible.
 *   <li>SQL uses the access_logs table from init.sql.
 *   <li>SQL templates include the {@code /*WHERE*\/} placeholder for service-layer time and
 *       filter injection.
 *   <li>SQL templates may include the {@code {interval}} placeholder, replaced by the requested
 *       interval in seconds.
 * </ul>
 */
@Component
@Slf4j
public class DBCollectorPresetSqlRegistry {

    public static final String WHERE_PLACEHOLDER = "/*WHERE*/";
    public static final String BIZ_PLACEHOLDER = "/*BIZ*/";

    /**
     * Scenario preset.
     */
    @Getter
    public static class Preset {
        private final String name;
        private final SlsPresetSqlRegistry.DisplayType type;
        private final String sqlTemplate;
        private final String timeField;
        private final String valueField;

        public Preset(
                String name,
                SlsPresetSqlRegistry.DisplayType type,
                String sqlTemplate,
                String timeField,
                String valueField) {
            this.name = name;
            this.type = type;
            this.sqlTemplate = sqlTemplate;
            this.timeField = timeField;
            this.valueField = valueField;
        }
    }

    private final Map<String, Preset> presets = new HashMap<>();

    public DBCollectorPresetSqlRegistry() {
        presets.put(
                "pv",
                new Preset(
                        "pv",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COUNT(1) AS pv FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " "
                                + BIZ_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "uv",
                new Preset(
                        "uv",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COUNT(DISTINCT x_forwarded_for) AS uv FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " "
                                + BIZ_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "fallback_count",
                new Preset(
                        "fallback_count",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COUNT(1) AS cnt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND response_code_details = 'internal_redirect'",
                        null,
                        null));
        presets.put(
                "bytes_received",
                new Preset(
                        "bytes_received",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT ROUND(SUM(bytes_received) / 1024.0 / 1024.0, 3) AS received"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "bytes_sent",
                new Preset(
                        "bytes_sent",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT ROUND(SUM(bytes_sent) / 1024.0 / 1024.0, 3) AS sent FROM"
                                + " access_logs "
                                + WHERE_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "input_token_total",
                new Preset(
                        "input_token_total",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COALESCE(SUM(input_tokens), 0) AS input_token FROM access_logs "
                                + WHERE_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "output_token_total",
                new Preset(
                        "output_token_total",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COALESCE(SUM(output_tokens), 0) AS output_token FROM access_logs "
                                + WHERE_PLACEHOLDER,
                        null,
                        null));
        presets.put(
                "token_total",
                new Preset(
                        "token_total",
                        SlsPresetSqlRegistry.DisplayType.CARD,
                        "SELECT COALESCE(SUM(total_tokens), 0) AS token FROM access_logs "
                                + WHERE_PLACEHOLDER,
                        null,
                        null));

        // LINE presets, always returning a time field.
        // Streaming, non-streaming, and total QPS.
        presets.put(
                "qps_stream",
                new Preset(
                        "qps_stream",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS stream_qps FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.response_type')) = 'stream' GROUP BY time ORDER BY time",
                        "time",
                        "stream_qps"));
        presets.put(
                "qps_normal",
                new Preset(
                        "qps_normal",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS normal_qps FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.response_type')) = 'normal' GROUP BY time ORDER BY time",
                        "time",
                        "normal_qps"));
        presets.put(
                "qps_total",
                new Preset(
                        "qps_total",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS total_qps FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.response_type')) IN ('normal','stream') GROUP BY time ORDER"
                                + " BY time",
                        "time",
                        "total_qps"));

        // Success rate for gateway and model dashboards.
        presets.put(
                "success_rate",
                new Preset(
                        "success_rate",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(SUM(CASE WHEN response_code <"
                            + " 300 AND response_code > 0 THEN 1 ELSE 0 END) AS DOUBLE) /"
                            + " NULLIF(COUNT(1), 0) AS success_rate FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "success_rate"));

        // Input, output, and total tokens per second.
        presets.put(
                "token_per_sec_input",
                new Preset(
                        "token_per_sec_input",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, COALESCE(SUM(input_tokens),"
                            + " 0)/{interval} AS input_token FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "input_token"));
        presets.put(
                "token_per_sec_output",
                new Preset(
                        "token_per_sec_output",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, COALESCE(SUM(output_tokens),"
                            + " 0)/{interval} AS output_token FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "output_token"));
        presets.put(
                "token_per_sec_total",
                new Preset(
                        "token_per_sec_total",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, COALESCE(SUM(total_tokens),"
                            + " 0)/{interval} AS total_token FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "total_token"));

        // Average response time from ai_log for model metrics.
        presets.put(
                "rt_avg_total",
                new Preset(
                        "rt_avg_total",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time,"
                            + " SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                            + " '$.llm_service_duration')) AS DOUBLE)) / NULLIF(COUNT(1), 0) AS"
                            + " total_rt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log)"
                                + " AND JSON_EXTRACT(ai_log, '$.llm_service_duration') IS NOT NULL"
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "total_rt"));
        presets.put(
                "rt_avg_stream",
                new Preset(
                        "rt_avg_stream",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time,"
                            + " SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                            + " '$.llm_service_duration')) AS DOUBLE)) / NULLIF(COUNT(1), 0) AS"
                            + " stream_rt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_EXTRACT(ai_log,"
                                + " '$.llm_service_duration') IS NOT NULL AND"
                                + " JSON_UNQUOTE(JSON_EXTRACT(ai_log, '$.response_type')) ="
                                + " 'stream' GROUP BY time ORDER BY time",
                        "time",
                        "stream_rt"));
        presets.put(
                "rt_avg_normal",
                new Preset(
                        "rt_avg_normal",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time,"
                            + " SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                            + " '$.llm_service_duration')) AS DOUBLE)) / NULLIF(COUNT(1), 0) AS"
                            + " normal_rt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_EXTRACT(ai_log,"
                                + " '$.llm_service_duration') IS NOT NULL AND"
                                + " JSON_UNQUOTE(JSON_EXTRACT(ai_log, '$.response_type')) ="
                                + " 'normal' GROUP BY time ORDER BY time",
                        "time",
                        "normal_rt"));
        presets.put(
                "rt_first_token",
                new Preset(
                        "rt_first_token",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time,"
                            + " SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                            + " '$.llm_first_token_duration')) AS DOUBLE)) / NULLIF(COUNT(1), 0) AS"
                            + " first_token_rt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_EXTRACT(ai_log,"
                                + " '$.llm_first_token_duration') IS NOT NULL GROUP BY time ORDER"
                                + " BY time",
                        "time",
                        "first_token_rt"));

        // Cache hit, miss, and skip rates for model metrics.
        presets.put(
                "cache_hit",
                new Preset(
                        "cache_hit",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS hit FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.cache_status')) = 'hit' GROUP BY time ORDER BY time",
                        "time",
                        "hit"));
        presets.put(
                "cache_miss",
                new Preset(
                        "cache_miss",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS miss FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.cache_status')) = 'miss' GROUP BY time ORDER BY time",
                        "time",
                        "miss"));
        presets.put(
                "cache_skip",
                new Preset(
                        "cache_skip",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS skip FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.cache_status')) = 'skip' GROUP BY time ORDER BY time",
                        "time",
                        "skip"));

        // Rate-limited requests per second for model metrics.
        presets.put(
                "ratelimited_per_sec",
                new Preset(
                        "ratelimited_per_sec",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS ratelimited FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.token_ratelimit_status')) = 'limited' GROUP BY time ORDER BY"
                                + " time",
                        "time",
                        "ratelimited"));

        // Total QPS for the MCP dashboard.
        presets.put(
                "qps_total_simple",
                new Preset(
                        "qps_total_simple",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, CAST(COUNT(1) AS DOUBLE)/{interval}"
                            + " AS total, 'total' AS response_code FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "total"));

        // MCP average and percentile response times based on duration.
        presets.put(
                "rt_avg",
                new Preset(
                        "rt_avg",
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "SELECT DATE_FORMAT("
                            + "FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval}),"
                            + " '%Y-%m-%d %H:%i:%s') AS time, SUM(CAST(duration AS DOUBLE)) /"
                            + " NULLIF(COUNT(1), 0) AS rt_avg FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND duration IS NOT NULL"
                                + " GROUP BY time ORDER BY time",
                        "time",
                        "rt_avg"));

        addDurationPercentilePreset("rt_p99", 0.99, "rt_p99");
        addDurationPercentilePreset("rt_p95", 0.95, "rt_p95");
        addDurationPercentilePreset("rt_p90", 0.90, "rt_p90");
        addDurationPercentilePreset("rt_p50", 0.50, "rt_p50");

        presets.put(
                "model_token_table",
                new Preset(
                        "model_token_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT model AS model,"
                                + " COALESCE(SUM(input_tokens), 0) AS input_token,"
                                + " COALESCE(SUM(output_tokens), 0) AS output_token,"
                                + " COALESCE(SUM(total_tokens), 0) AS total_token,"
                                + " COUNT(1) AS request"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND model IS NOT NULL"
                                + " GROUP BY model ORDER BY total_token DESC",
                        null,
                        null));
        presets.put(
                "consumer_token_table",
                new Preset(
                        "consumer_token_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT consumer AS consumer,"
                                + " COALESCE(SUM(input_tokens), 0) AS input_token,"
                                + " COALESCE(SUM(output_tokens), 0) AS output_token,"
                                + " COALESCE(SUM(total_tokens), 0) AS total_token,"
                                + " COUNT(1) AS request"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND consumer IS NOT NULL"
                                + " GROUP BY consumer ORDER BY total_token DESC",
                        null,
                        null));
        presets.put(
                "service_token_table",
                new Preset(
                        "service_token_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT upstream_cluster AS upstream_cluster,"
                                + " COALESCE(SUM(input_tokens), 0) AS input_token,"
                                + " COALESCE(SUM(output_tokens), 0) AS output_token,"
                                + " COALESCE(SUM(total_tokens), 0) AS total_token,"
                                + " COUNT(1) AS request"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND upstream_cluster IS NOT NULL"
                                + " GROUP BY upstream_cluster ORDER BY total_token DESC",
                        null,
                        null));
        presets.put(
                "error_requests_table",
                new Preset(
                        "error_requests_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT response_code, response_code_details, response_flags, COUNT(1) AS"
                                + " cnt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND (response_code = 0 OR response_code >= 400)"
                                + " GROUP BY response_code, response_code_details, response_flags"
                                + " ORDER BY cnt DESC",
                        null,
                        null));
        presets.put(
                "ratelimited_consumer_table",
                new Preset(
                        "ratelimited_consumer_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT consumer AS consumer, COUNT(1) AS ratelimited_count"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.token_ratelimit_status')) = 'limited' AND consumer IS NOT"
                                + " NULL GROUP BY consumer ORDER BY ratelimited_count DESC",
                        null,
                        null));
        presets.put(
                "risk_label_table",
                new Preset(
                        "risk_label_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT JSON_UNQUOTE(JSON_EXTRACT(ai_log, '$.safecheck_riskLabel')) AS"
                                + " risklabel, COUNT(1) AS cnt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.safecheck_status')) = 'reqeust deny' GROUP BY risklabel"
                                + " ORDER BY cnt DESC",
                        null,
                        null));
        presets.put(
                "risk_consumer_table",
                new Preset(
                        "risk_consumer_table",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT consumer AS consumer, COUNT(1) AS cnt"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND JSON_VALID(ai_log) AND JSON_UNQUOTE(JSON_EXTRACT(ai_log,"
                                + " '$.safecheck_status')) = 'reqeust deny' AND consumer IS NOT"
                                + " NULL GROUP BY consumer ORDER BY cnt DESC",
                        null,
                        null));

        // MCP table presets.
        presets.put(
                "method_distribution",
                new Preset(
                        "method_distribution",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT method AS method, COUNT(1) AS count"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND method IS NOT NULL"
                                + " GROUP BY method",
                        null,
                        null));
        presets.put(
                "gateway_status_distribution",
                new Preset(
                        "gateway_status_distribution",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT response_code AS status, COUNT(1) AS count"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY status",
                        null,
                        null));
        presets.put(
                "backend_status_distribution",
                new Preset(
                        "backend_status_distribution",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT response_code_details AS status, COUNT(1) AS count"
                                + " FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " "
                                + BIZ_PLACEHOLDER
                                + " AND response_code_details IS NOT NULL"
                                + " GROUP BY status",
                        null,
                        null));
        presets.put(
                "request_distribution",
                new Preset(
                        "request_distribution",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT mcp_tool AS tool_name, response_code, response_flags,"
                                + " response_code_details, COUNT(1) AS cnt FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " GROUP BY tool_name, response_code, response_flags,"
                                + " response_code_details ORDER BY cnt DESC",
                        null,
                        null));

        // Filter option presets. Returned field names must match the frontend extractField logic.
        presets.put(
                "filter_service_options",
                new Preset(
                        "filter_service_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT instance_id AS service FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND instance_id IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_api_options",
                new Preset(
                        "filter_api_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT api AS api FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND api IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_model_options",
                new Preset(
                        "filter_model_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT model AS model FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND model IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_route_options",
                new Preset(
                        "filter_route_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT route_name FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " "
                                + BIZ_PLACEHOLDER
                                + " AND route_name IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_consumer_options",
                new Preset(
                        "filter_consumer_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT consumer AS consumer FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND consumer IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_upstream_options",
                new Preset(
                        "filter_upstream_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT upstream_cluster FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND upstream_cluster IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_mcp_tool_options",
                new Preset(
                        "filter_mcp_tool_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT mcp_tool AS mcp_tool_name FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND mcp_tool IS NOT NULL LIMIT 100",
                        null,
                        null));
        presets.put(
                "filter_mcp_server_options",
                new Preset(
                        "filter_mcp_server_options",
                        SlsPresetSqlRegistry.DisplayType.TABLE,
                        "SELECT DISTINCT mcp_server FROM access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND mcp_tool IS NOT NULL LIMIT 100",
                        null,
                        null));
    }

    private void addDurationPercentilePreset(String name, double p, String valueAlias) {
        // Use window functions by time bucket instead of relying on database-specific percentile
        // functions.
        String percentile = Double.toString(p);
        presets.put(
                name,
                new Preset(
                        name,
                        SlsPresetSqlRegistry.DisplayType.LINE,
                        "WITH bucketed AS ( SELECT "
                            + " FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(start_time)/{interval})*{interval})"
                            + " AS bucket_time, duration AS duration, ROW_NUMBER() OVER (PARTITION"
                            + " BY FLOOR(UNIX_TIMESTAMP(start_time)/{interval}) ORDER BY duration)"
                            + " AS rn, COUNT(1) OVER (PARTITION BY"
                            + " FLOOR(UNIX_TIMESTAMP(start_time)/{interval})) AS cnt FROM"
                            + " access_logs "
                                + WHERE_PLACEHOLDER
                                + " AND duration IS NOT NULL"
                                + " )"
                                + " SELECT DATE_FORMAT(bucket_time, '%Y-%m-%d %H:%i:%s') AS time,"
                                + " MAX(CASE WHEN rn = CAST(CEIL("
                                + percentile
                                + " * cnt) AS UNSIGNED) THEN duration END) AS "
                                + valueAlias
                                + " FROM bucketed"
                                + " GROUP BY bucket_time ORDER BY bucket_time",
                        "time",
                        valueAlias));
    }

    public Preset getPreset(String scenario) {
        if (scenario == null) return null;
        Preset p = presets.get(scenario);
        if (p == null) {
            log.warn("Unknown DBCollector scenario, scenario={}", scenario);
        }
        return p;
    }
}
