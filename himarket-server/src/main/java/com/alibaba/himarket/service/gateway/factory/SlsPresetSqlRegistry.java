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
 * Registry for preset scenario SQL.
 *
 * <p>Scenario names are frontend/backend contracts, such as pv, uv, and qps_total. Each preset
 * defines the display type, SQL, and optional field aliases for time-series rendering.
 */
@Component
@Slf4j
public class SlsPresetSqlRegistry {

    /**
     * Display type.
     */
    @Getter
    public enum DisplayType {
        CARD,
        LINE,
        TABLE
    }

    /**
     * Scenario preset.
     */
    @Getter
    public static class Preset {
        /**
         * Scenario name.
         */
        private final String name;

        /**
         * Display type.
         */
        private final DisplayType type;

        /**
         * Preset SQL in search-segment | select-segment form.
         */
        private final String sql;

        /**
         * Time field alias required by LINE presets.
         */
        private final String timeField;

        /**
         * Value field alias required by LINE presets.
         */
        private final String valueField;

        public Preset(
                String name, DisplayType type, String sql, String timeField, String valueField) {
            this.name = name;
            this.type = type;
            this.sql = sql;
            this.timeField = timeField;
            this.valueField = valueField;
        }
    }

    private final Map<String, Preset> presets = new HashMap<>();

    public SlsPresetSqlRegistry() {
        // Card presets.
        // Total request count for model and MCP dashboards.
        presets.put(
                "pv",
                new Preset("pv", DisplayType.CARD, "(*) | select count(1) as pv", null, null));
        // Unique caller count for model and MCP dashboards.
        presets.put(
                "uv",
                new Preset(
                        "uv",
                        DisplayType.CARD,
                        "(*) | select approx_distinct(\"x_forwarded_for\") as uv",
                        null,
                        null));
        // Fallback request count for the model dashboard.
        presets.put(
                "fallback_count",
                new Preset(
                        "fallback_count",
                        DisplayType.CARD,
                        "(* and response_code_details: internal_redirect) | select count(1) as cnt",
                        null,
                        null));
        // Gateway inbound traffic in MB for the MCP dashboard.
        presets.put(
                "bytes_received",
                new Preset(
                        "bytes_received",
                        DisplayType.CARD,
                        "(*) | select round(sum(\"bytes_received\") / 1024.0 / 1024.0, 3) as"
                                + " received",
                        null,
                        null));
        // Gateway outbound traffic in MB for the MCP dashboard.
        presets.put(
                "bytes_sent",
                new Preset(
                        "bytes_sent",
                        DisplayType.CARD,
                        "(*) | select round(sum(\"bytes_sent\") / 1024.0 / 1024.0, 3) as sent",
                        null,
                        null));
        // Total input tokens for the model dashboard.
        presets.put(
                "input_token_total",
                new Preset(
                        "input_token_total",
                        DisplayType.CARD,
                        "(ai_log.model : *) | select sum(cast(json_extract(ai_log, '$.input_token')"
                                + " as integer)) as input_token",
                        null,
                        null));
        // Total output tokens for the model dashboard.
        presets.put(
                "output_token_total",
                new Preset(
                        "output_token_total",
                        DisplayType.CARD,
                        "(ai_log.model : *) | select sum(cast(json_extract(ai_log,"
                                + " '$.output_token') as integer)) as output_token",
                        null,
                        null));
        // Total tokens for the model dashboard.
        presets.put(
                "token_total",
                new Preset(
                        "token_total",
                        DisplayType.CARD,
                        "(ai_log.model : *) | select sum(cast(json_extract(ai_log, '$.input_token')"
                            + " as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer)) as token",
                        null,
                        null));

        // Line chart presets.
        // Streaming QPS for the model dashboard.
        presets.put(
                "qps_stream",
                new Preset(
                        "qps_stream",
                        DisplayType.LINE,
                        "(ai_log.response_type : stream) | select cast (count(1) as"
                            + " double)/{interval} as stream_qps, date_format(__time__ - __time__ %"
                            + " {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time"
                            + " order by time limit all",
                        "time",
                        "stream_qps"));
        // Non-streaming QPS for the model dashboard.
        presets.put(
                "qps_normal",
                new Preset(
                        "qps_normal",
                        DisplayType.LINE,
                        "(ai_log.response_type : normal) | select cast (count(1) as"
                            + " double)/{interval} as normal_qps, date_format(__time__ - __time__ %"
                            + " {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time"
                            + " order by time limit all",
                        "time",
                        "normal_qps"));
        // Total QPS for the model dashboard.
        presets.put(
                "qps_total",
                new Preset(
                        "qps_total",
                        DisplayType.LINE,
                        "((ai_log.response_type : normal or ai_log.response_type : stream)) |"
                            + " select cast (count(1) as double)/{interval} as total_qps,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "total_qps"));
        // Request success rate for model and MCP dashboards.
        presets.put(
                "success_rate",
                new Preset(
                        "success_rate",
                        DisplayType.LINE,
                        "(*) | select cast(cnt_right as double)/cnt_total as success_rate, t2.time"
                            + " from (select count(1) as cnt_right, date_format(__time__ - __time__"
                            + " % {interval}, '%Y-%m-%d %H:%i:%s') as time from log where"
                            + " response_code < 300 and response_code > 0 group by time) as t1 join"
                            + " (select count(1) as cnt_total, date_format(__time__ - __time__ %"
                            + " {interval}, '%Y-%m-%d %H:%i:%s') as time from log group by time) as"
                            + " t2 on t1.time = t2.time order by t2.time limit all",
                        "time",
                        "success_rate"));
        // Input tokens per second for the model dashboard.
        presets.put(
                "token_per_sec_input",
                new Preset(
                        "token_per_sec_input",
                        DisplayType.LINE,
                        "(*) | select sum(cast(json_extract(ai_log, '$.input_token') as"
                            + " integer))/{interval} as input_token, date_format(__time__ -"
                            + " __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP"
                            + " BY time order by time limit all",
                        "time",
                        "input_token"));
        // Output tokens per second for the model dashboard.
        presets.put(
                "token_per_sec_output",
                new Preset(
                        "token_per_sec_output",
                        DisplayType.LINE,
                        "(*) | select sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer))/{interval} as output_token, date_format(__time__ -"
                            + " __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP"
                            + " BY time order by time limit all",
                        "time",
                        "output_token"));
        // Total tokens per second for the model dashboard.
        presets.put(
                "token_per_sec_total",
                new Preset(
                        "token_per_sec_total",
                        DisplayType.LINE,
                        "(*) | select (sum(cast(json_extract(ai_log, '$.input_token') as integer))"
                            + " + sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer)))/{interval} as total_token, date_format(__time__ -"
                            + " __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP"
                            + " BY time order by time limit all",
                        "time",
                        "total_token"));
        // Overall average response time for the model dashboard.
        presets.put(
                "rt_avg_total",
                new Preset(
                        "rt_avg_total",
                        DisplayType.LINE,
                        "(ai_log.llm_service_duration : *) | select sum(cast(json_extract(ai_log,"
                            + " '$.llm_service_duration') as double))/count(*) as total_rt,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "total_rt"));
        // Streaming average response time for the model dashboard.
        presets.put(
                "rt_avg_stream",
                new Preset(
                        "rt_avg_stream",
                        DisplayType.LINE,
                        "(ai_log.llm_service_duration : * and ai_log.response_type : stream) |"
                            + " select sum(cast(json_extract(ai_log, '$.llm_service_duration') as"
                            + " double))/count(*) as stream_rt, date_format(__time__ - __time__ %"
                            + " {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time"
                            + " order by time limit all",
                        "time",
                        "stream_rt"));
        // Non-streaming average response time for the model dashboard.
        presets.put(
                "rt_avg_normal",
                new Preset(
                        "rt_avg_normal",
                        DisplayType.LINE,
                        "(ai_log.llm_service_duration : * and ai_log.response_type : normal) |"
                            + " select sum(cast(json_extract(ai_log, '$.llm_service_duration') as"
                            + " double))/count(*) as normal_rt, date_format(__time__ - __time__ %"
                            + " {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time"
                            + " order by time limit all",
                        "time",
                        "normal_rt"));
        // First-token response time for the model dashboard.
        presets.put(
                "rt_first_token",
                new Preset(
                        "rt_first_token",
                        DisplayType.LINE,
                        "(ai_log.llm_first_token_duration : * and ai_log.llm_service_duration : *)"
                            + " | select sum(cast(json_extract(ai_log,"
                            + " '$.llm_first_token_duration') as double))/count(*) as"
                            + " first_token_rt, date_format(__time__ - __time__ % {interval},"
                            + " '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time"
                            + " limit all",
                        "time",
                        "first_token_rt"));
        // Cache hit, miss, and skip rates for the model dashboard.
        presets.put(
                "cache_hit",
                new Preset(
                        "cache_hit",
                        DisplayType.LINE,
                        "(ai_log.cache_status : hit) | select cast (count(1) as double)/{interval}"
                            + " as hit, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d"
                            + " %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "hit"));
        presets.put(
                "cache_miss",
                new Preset(
                        "cache_miss",
                        DisplayType.LINE,
                        "(ai_log.cache_status : miss) | select cast (count(1) as double)/{interval}"
                            + " as miss, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d"
                            + " %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "miss"));
        presets.put(
                "cache_skip",
                new Preset(
                        "cache_skip",
                        DisplayType.LINE,
                        "(ai_log.cache_status : skip) | select cast (count(1) as double)/{interval}"
                            + " as skip, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d"
                            + " %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "skip"));
        // Rate-limited requests per second for the model dashboard.
        presets.put(
                "ratelimited_per_sec",
                new Preset(
                        "ratelimited_per_sec",
                        DisplayType.LINE,
                        "(ai_log.token_ratelimit_status : limited) | select cast (count(1) as"
                            + " double)/{interval} as ratelimited, date_format(__time__ - __time__"
                            + " % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time"
                            + " order by time limit all",
                        "time",
                        "ratelimited"));
        // QPS grouped by status code for the MCP dashboard.
        presets.put(
                "qps_by_status",
                new Preset(
                        "qps_by_status",
                        DisplayType.LINE,
                        "(*) | select cast (count(1) as double)/{interval} as qps, response_code,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time, response_code order by time limit"
                            + " all",
                        "time",
                        "qps"));
        // Total QPS for the MCP dashboard.
        presets.put(
                "qps_total_simple",
                new Preset(
                        "qps_total_simple",
                        DisplayType.LINE,
                        "(*) | select cast (count(1) as double)/{interval} as total, 'total' as"
                            + " response_code, date_format(__time__ - __time__ % {interval},"
                            + " '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time, response_code"
                            + " order by time limit all",
                        "time",
                        "total"));
        // Average response time for the MCP dashboard.
        presets.put(
                "rt_avg",
                new Preset(
                        "rt_avg",
                        DisplayType.LINE,
                        "(*) | select sum(cast(duration as double))/count(*) as rt_avg,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "rt_avg"));
        // P99 response time for the MCP dashboard.
        presets.put(
                "rt_p99",
                new Preset(
                        "rt_p99",
                        DisplayType.LINE,
                        "(*) | select approx_percentile(duration, 0.99) as rt_p99,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "rt_p99"));
        // P95 response time for the MCP dashboard.
        presets.put(
                "rt_p95",
                new Preset(
                        "rt_p95",
                        DisplayType.LINE,
                        "(*) | select approx_percentile(duration, 0.95) as rt_p95,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "rt_p95"));
        // P90 response time for the MCP dashboard.
        presets.put(
                "rt_p90",
                new Preset(
                        "rt_p90",
                        DisplayType.LINE,
                        "(*) | select approx_percentile(duration, 0.9) as rt_p90,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "rt_p90"));
        // P50 response time for the MCP dashboard.
        presets.put(
                "rt_p50",
                new Preset(
                        "rt_p50",
                        DisplayType.LINE,
                        "(*) | select approx_percentile(duration, 0.5) as rt_p50,"
                            + " date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s')"
                            + " AS time FROM log GROUP BY time order by time limit all",
                        "time",
                        "rt_p50"));

        // Table presets.
        // Token usage by model for the model dashboard.
        presets.put(
                "model_token_table",
                new Preset(
                        "model_token_table",
                        DisplayType.TABLE,
                        "(ai_log.model : *) | select json_extract(ai_log, '$.model') as model,"
                            + " sum(cast(json_extract(ai_log, '$.input_token') as integer)) as"
                            + " input_token, sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer)) as output_token, sum(cast(json_extract(ai_log,"
                            + " '$.input_token') as integer)) + sum(cast(json_extract(ai_log,"
                            + " '$.output_token') as integer)) as total_token, count(1) as request"
                            + " group by model order by total_token desc",
                        null,
                        null));
        // Token usage by consumer for the model dashboard.
        presets.put(
                "consumer_token_table",
                new Preset(
                        "consumer_token_table",
                        DisplayType.TABLE,
                        "(consumer : *) | select consumer as consumer,"
                            + " sum(cast(json_extract(ai_log, '$.input_token') as integer)) as"
                            + " input_token, sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer)) as output_token, sum(cast(json_extract(ai_log,"
                            + " '$.input_token') as integer)) + sum(cast(json_extract(ai_log,"
                            + " '$.output_token') as integer)) as total_token, count(1) as request"
                            + " group by consumer order by total_token desc",
                        null,
                        null));
        // Token usage by service for the model dashboard.
        presets.put(
                "service_token_table",
                new Preset(
                        "service_token_table",
                        DisplayType.TABLE,
                        "(ai_log.model : *) | select upstream_cluster,"
                            + " sum(cast(json_extract(ai_log, '$.input_token') as integer)) as"
                            + " input_token, sum(cast(json_extract(ai_log, '$.output_token') as"
                            + " integer)) as output_token, sum(cast(json_extract(ai_log,"
                            + " '$.input_token') as integer)) + sum(cast(json_extract(ai_log,"
                            + " '$.output_token') as integer)) as total_token, count(1) as request"
                            + " group by upstream_cluster order by total_token desc",
                        null,
                        null));
        // Error request statistics for the model dashboard.
        presets.put(
                "error_requests_table",
                new Preset(
                        "error_requests_table",
                        DisplayType.TABLE,
                        "(*) | select response_code, response_code_details, response_flags,"
                            + " count(*) as cnt from log where response_code = 0 or response_code"
                            + " >= 400 group by response_code, response_code_details,"
                            + " response_flags order by cnt desc limit all",
                        null,
                        null));
        // Rate-limited consumer statistics for the model dashboard.
        presets.put(
                "ratelimited_consumer_table",
                new Preset(
                        "ratelimited_consumer_table",
                        DisplayType.TABLE,
                        "(ai_log.token_ratelimit_status : limited) | select json_extract(ai_log,"
                            + " '$.consumer') as consumer, count(1) as ratelimited_count group by"
                            + " consumer order by ratelimited_count desc",
                        null,
                        null));
        // Risk type statistics for the model dashboard.
        presets.put(
                "risk_label_table",
                new Preset(
                        "risk_label_table",
                        DisplayType.TABLE,
                        "(ai_log.safecheck_status : \"deny\") | select json_extract(ai_log,"
                                + " '$.safecheck_riskLabel') as risklabel, count(*) as cnt group by"
                                + " risklabel order by cnt desc",
                        null,
                        null));
        // Risk consumer statistics for the model dashboard.
        presets.put(
                "risk_consumer_table",
                new Preset(
                        "risk_consumer_table",
                        DisplayType.TABLE,
                        "(ai_log.safecheck_status : \"deny\") | select json_extract(ai_log,"
                            + " '$.consumer') as consumer, count(*) as cnt group by consumer order"
                            + " by cnt desc",
                        null,
                        null));
        // Method distribution for the MCP dashboard.
        presets.put(
                "method_distribution",
                new Preset(
                        "method_distribution",
                        DisplayType.TABLE,
                        "(method: *) | select \"method\" as method, count(1) as count group by"
                                + " method",
                        null,
                        null));
        // Gateway status code distribution for the MCP dashboard.
        presets.put(
                "gateway_status_distribution",
                new Preset(
                        "gateway_status_distribution",
                        DisplayType.TABLE,
                        "(response_code: *) | select response_code as status, count(1) as count"
                                + " group by status",
                        null,
                        null));
        // Backend status code distribution for the MCP dashboard.
        presets.put(
                "backend_status_distribution",
                new Preset(
                        "backend_status_distribution",
                        DisplayType.TABLE,
                        "(response_code_details: via_upstream) | select \"response_code\" as"
                                + " status, count(1) as count group by status",
                        null,
                        null));
        // Request distribution for the MCP dashboard.
        presets.put(
                "request_distribution",
                new Preset(
                        "request_distribution",
                        DisplayType.TABLE,
                        "(*) | select json_extract(ai_log, '$.mcp_tool_name') as tool_name,"
                            + " response_code, response_flags, response_code_details, count(*) as"
                            + " cnt from log group by tool_name, response_code, response_flags,"
                            + " response_code_details order by cnt desc limit all",
                        null,
                        null));

        // Frontend filter option presets.
        // Instance list.
        presets.put(
                "filter_service_options",
                new Preset(
                        "filter_service_options",
                        DisplayType.TABLE,
                        "(*) | select distinct cluster_id as service from log where cluster_id is"
                                + " not null limit 100",
                        null,
                        null));
        // API list.
        presets.put(
                "filter_api_options",
                new Preset(
                        "filter_api_options",
                        DisplayType.TABLE,
                        "(*) | select distinct json_extract(ai_log, '$.api') as api from log where"
                                + " json_extract(ai_log, '$.api') is not null limit 100",
                        null,
                        null));
        // Model list.
        presets.put(
                "filter_model_options",
                new Preset(
                        "filter_model_options",
                        DisplayType.TABLE,
                        "(*) | select distinct json_extract(ai_log, '$.model') as model from log"
                                + " where json_extract(ai_log, '$.model') is not null limit 100",
                        null,
                        null));
        // Route list.
        presets.put(
                "filter_route_options",
                new Preset(
                        "filter_route_options",
                        DisplayType.TABLE,
                        "(*) | select distinct route_name from log where route_name is not null"
                                + " limit 100",
                        null,
                        null));
        // Consumer list.
        presets.put(
                "filter_consumer_options",
                new Preset(
                        "filter_consumer_options",
                        DisplayType.TABLE,
                        "(*) | select distinct consumer as consumer from log where consumer is not"
                                + " null limit 100",
                        null,
                        null));
        // Upstream service list.
        presets.put(
                "filter_upstream_options",
                new Preset(
                        "filter_upstream_options",
                        DisplayType.TABLE,
                        "(*) | select distinct upstream_cluster from log where upstream_cluster is"
                                + " not null limit 100",
                        null,
                        null));
        // MCP tool name list.
        presets.put(
                "filter_mcp_tool_options",
                new Preset(
                        "filter_mcp_tool_options",
                        DisplayType.TABLE,
                        "(*) | select distinct json_extract(ai_log, '$.mcp_tool_name') as"
                                + " mcp_tool_name from log where json_extract(ai_log,"
                                + " '$.mcp_tool_name') is not null limit 100",
                        null,
                        null));
        // MCP server list.
        presets.put(
                "filter_mcp_server_options",
                new Preset(
                        "filter_mcp_server_options",
                        DisplayType.TABLE,
                        "(*) | select distinct route_name as mcp_server from log where"
                                + " route_name is not null limit 100",
                        null,
                        null));
    }

    /**
     * Returns the preset for a scenario name.
     */
    public Preset getPreset(String scenario) {
        if (scenario == null) return null;
        Preset p = presets.get(scenario);
        if (p == null) {
            log.warn("Unknown SLS scenario, scenario={}", scenario);
        }
        return p;
    }
}
