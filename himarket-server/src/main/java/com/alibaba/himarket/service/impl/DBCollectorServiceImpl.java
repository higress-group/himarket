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

package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.service.DBCollectorService;
import com.alibaba.himarket.service.gateway.factory.DBCollectorPresetSqlRegistry;
import com.alibaba.himarket.support.common.Strings;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * access_logs query implementation backed by MariaDB or MySQL.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DBCollectorServiceImpl implements DBCollectorService {

    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 5000;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request) {
        long start = System.currentTimeMillis();
        String originalSql = request != null ? request.getSql() : null;

        try {
            if (request == null || Strings.isBlank(request.getSql())) {
                return buildErrorResponse(originalSql, "sql is empty", start);
            }

            int interval = clampInterval(request.getInterval());
            String sql = applyInterval(request.getSql(), interval);

            MapSqlParameterSource params = new MapSqlParameterSource();
            String where = buildWhereClause(request, params);

            if (!sql.contains(DBCollectorPresetSqlRegistry.WHERE_PLACEHOLDER)) {
                // Fallback for legacy templates without a WHERE placeholder.
                sql = sql + " " + where;
            } else {
                sql = sql.replace(DBCollectorPresetSqlRegistry.WHERE_PLACEHOLDER, where);
            }

            if (sql.contains(DBCollectorPresetSqlRegistry.BIZ_PLACEHOLDER)) {
                sql =
                        sql.replace(
                                DBCollectorPresetSqlRegistry.BIZ_PLACEHOLDER,
                                buildBizClause(request));
            }

            if (request.getPageSize() != null && !containsLimit(sql)) {
                sql = sql + " LIMIT " + clampLimit(request.getPageSize());
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            List<Map<String, String>> resultRows = new ArrayList<>(rows.size());
            for (Map<String, Object> r : rows) {
                Map<String, String> m = new HashMap<>();
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    Object v = e.getValue();
                    m.put(e.getKey(), v == null ? null : String.valueOf(v));
                }
                resultRows.add(m);
            }

            return GenericSlsQueryResponse.builder()
                    .success(true)
                    .processStatus("Complete")
                    .count((long) resultRows.size())
                    .aggregations(resultRows)
                    .sql(originalSql)
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn(
                    "DBCollector query failed, sql={}, errorMessage={}",
                    originalSql,
                    e.getMessage(),
                    e);
            return GenericSlsQueryResponse.builder()
                    .success(false)
                    .processStatus("Complete")
                    .count(0L)
                    .aggregations(List.of())
                    .sql(originalSql)
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private GenericSlsQueryResponse buildErrorResponse(String sql, String msg, long startMillis) {
        return GenericSlsQueryResponse.builder()
                .success(false)
                .processStatus("Complete")
                .count(0L)
                .aggregations(List.of())
                .sql(sql)
                .elapsedMillis(System.currentTimeMillis() - startMillis)
                .errorMessage(msg)
                .build();
    }

    private int clampInterval(Integer interval) {
        int v = interval == null ? 60 : interval;
        if (v <= 0) return 60;
        // Keep overly large intervals bounded while preserving common frontend values.
        if (v < 1) v = 1;
        if (v > 24 * 3600) v = 24 * 3600;
        return v;
    }

    private int clampLimit(Integer pageSize) {
        int v = pageSize == null ? DEFAULT_LIMIT : pageSize;
        v = Math.max(1, v);
        v = Math.min(MAX_LIMIT, v);
        return v;
    }

    private boolean containsLimit(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.contains(" limit ");
    }

    private String applyInterval(String sql, int interval) {
        if (Strings.isBlank(sql)) return sql;
        return sql.replace("{interval}", String.valueOf(interval));
    }

    private String buildWhereClause(GenericSlsQueryRequest request, MapSqlParameterSource params) {
        Timestamp startTs = resolveStartTime(request);
        Timestamp endTs = resolveEndTime(request);

        StringBuilder sb = new StringBuilder();
        sb.append("WHERE 1=1");

        if (startTs != null) {
            sb.append(" AND start_time >= :startTime");
            params.addValue("startTime", startTs);
        }
        if (endTs != null) {
            sb.append(" AND start_time <= :endTime");
            params.addValue("endTime", endTs);
        }

        appendOrEquals(sb, params, "instance_id", "clusterId", request.getClusterId());
        appendOrEquals(sb, params, "api", "api", request.getApi());
        appendOrEquals(sb, params, "model", "model", request.getModel());
        appendOrEquals(sb, params, "consumer", "consumer", request.getConsumer());
        appendOrEquals(sb, params, "route_name", "route", request.getRoute());
        appendOrEquals(sb, params, "upstream_cluster", "service", request.getService());

        appendOrEquals(sb, params, "route_name", "routeName", request.getRouteName());
        appendOrEquals(
                sb, params, "upstream_cluster", "upstreamCluster", request.getUpstreamCluster());
        appendOrEquals(sb, params, "mcp_tool", "mcpToolName", request.getMcpToolName());

        return sb.toString();
    }

    /**
     * Distinguishes pv/uv sources by product type.
     *
     * <ul>
     *   <li>Model requests require a non-empty ai_log.model field.
     *   <li>MCP requests are identified by the /mcp-servers path prefix.
     * </ul>
     */
    private String buildBizClause(GenericSlsQueryRequest request) {
        boolean isMcp = request.getBizType() != null && request.getBizType().equals("MCP_SERVER");

        if (isMcp) {
            return "AND REGEXP_LIKE(path, '^/mcp-servers')";
        }
        return "AND JSON_VALID(ai_log)"
                + " AND NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ai_log, '$.model')), '') IS NOT NULL";
    }

    private void appendOrEquals(
            StringBuilder sb,
            MapSqlParameterSource params,
            String column,
            String paramPrefix,
            String[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (String v : values) {
            if (Strings.isNotBlank(v)) {
                normalized.add(v.trim());
            }
        }
        if (normalized.isEmpty()) {
            return;
        }

        sb.append(" AND (");
        for (int i = 0; i < normalized.size(); i++) {
            String p = paramPrefix + "_" + i;
            if (i > 0) sb.append(" OR ");
            sb.append(column).append(" = :").append(p);
            params.addValue(p, normalized.get(i));
        }
        sb.append(")");
    }

    private Timestamp resolveStartTime(GenericSlsQueryRequest request) {
        if (request == null) return null;
        if (Strings.isNotBlank(request.getStartTime())) {
            return toTimestamp(parseTime(request.getStartTime().trim()));
        }
        if (request.getFromTime() != null) {
            return Timestamp.from(Instant.ofEpochSecond(request.getFromTime()));
        }
        return null;
    }

    private Timestamp resolveEndTime(GenericSlsQueryRequest request) {
        if (request == null) return null;
        if (Strings.isNotBlank(request.getEndTime())) {
            return toTimestamp(parseTime(request.getEndTime().trim()));
        }
        if (request.getToTime() != null) {
            return Timestamp.from(Instant.ofEpochSecond(request.getToTime()));
        }
        return null;
    }

    private Instant parseTime(String timeStr) {
        // ISO-8601
        try {
            if (timeStr.endsWith("Z")) {
                return Instant.parse(timeStr);
            }
        } catch (Exception ignored) {
        }

        // ISO 8601 with offset
        try {
            if (timeStr.contains("T")) {
                return Instant.ofEpochSecond(OffsetDateTime.parse(timeStr).toEpochSecond());
            }
        } catch (Exception ignored) {
        }

        // yyyy-MM-dd HH:mm:ss
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime ldt = LocalDateTime.parse(timeStr, formatter);
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
