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

import com.alibaba.himarket.config.SlsConfig;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.dto.params.sls.SlsCheckLogstoreRequest;
import com.alibaba.himarket.dto.params.sls.SlsCheckProjectRequest;
import com.alibaba.himarket.dto.params.sls.SlsCommonQueryRequest;
import com.alibaba.himarket.service.SlsLogService;
import com.alibaba.himarket.service.gateway.factory.SlsClientFactory;
import com.alibaba.himarket.support.enums.SlsAuthType;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.Index;
import com.aliyun.openservices.log.common.IndexJsonKey;
import com.aliyun.openservices.log.common.IndexKey;
import com.aliyun.openservices.log.common.IndexKeys;
import com.aliyun.openservices.log.common.IndexLine;
import com.aliyun.openservices.log.common.LogContent;
import com.aliyun.openservices.log.common.QueriedLog;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.response.GetIndexResponse;
import com.aliyun.openservices.log.response.GetLogsResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Generic SLS log query service implementation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlsLogServiceImpl implements SlsLogService {

    private static final String ALIYUN_LOG_CONFIG_CRD_NAME =
            "aliyunlogconfigs.log.alibabacloud.com";

    private final SlsClientFactory slsClientFactory;

    private final SlsConfig slsConfig;

    /**
     * Project existence cache, expiring after 10 minutes.
     */
    private final Cache<String, Boolean> projectExistsCache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(100).build();

    /**
     * Logstore existence cache, expiring after 10 minutes.
     */
    private final Cache<String, Boolean> logstoreExistsCache =
            Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(200).build();

    /**
     * Conservative tokenizer for identifier-like fields. It preserves separators such as hyphen,
     * underscore, and dot inside identifiers.
     */
    private static final List<String> TOKENS_22 =
            Arrays.asList(
                    ",", "'", "\"", ";", "=", "(", ")", "[", "]", "{", "}", "?", "@", "&", "<", ">",
                    "/", ":", "\n", "\t", "\r", " ");

    /**
     * Broader tokenizer for path and network-address fields.
     */
    private static final List<String> TOKENS_26 =
            Arrays.asList(
                    ",", "'", "\"", ";", "\\", "$", "#", "|", "=", "\n", "\t", "\r", "!", "%", "&",
                    "*", "+", "-", ".", "/", ":", "<", ">", "?", "@", "[", "]", "^", "_", "{", "}",
                    " ");

    /**
     * Text fields that require SLS index configuration.
     */
    private static final String[] TEXT_FIELDS = {
        "_time_",
        "answer",
        "authority",
        "cluster_id",
        "consumer",
        "downstream_local_address",
        "downstream_remote_address",
        "downstream_transport_failure_reason",
        "http_referer",
        "method",
        "original_path",
        "path",
        "protocol",
        "question",
        "request_id",
        "requested_server_name",
        "response_code_details",
        "response_flags",
        "route_name",
        "start_time",
        "trace_id",
        "upstream_cluster",
        "upstream_host",
        "upstream_local_address",
        "upstream_protocol",
        "upstream_transport_failure_reason",
        "user_agent",
        "x_forwarded_for",
        "request_body",
        "response_body",
        "__tag__:_cluster_id_"
    };

    /**
     * Numeric fields that require SLS long index configuration.
     */
    private static final String[] LONG_FIELDS = {
        "bytes_received",
        "bytes_sent",
        "duration",
        "ext_authz_duration",
        "ext_authz_status_code",
        "request_duration",
        "response_code",
        "response_tx_duration",
        "upstream_service_time"
    };

    @Override
    public GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request) {
        long startTime = System.currentTimeMillis();

        validateQueryRequest(request);

        Client client = slsClientFactory.createClient(request.getUserId());

        String project = slsConfig.getDefaultProject();
        String logstore = slsConfig.getDefaultLogstore();

        String finalSql = buildSqlWithFilters(request);
        finalSql = replaceSqlInterval(finalSql, request.getInterval());
        String scenario =
                StringUtils.hasText(request.getScenario()) ? request.getScenario() : "custom";

        try {
            if (!isProjectExists(client, project)) {
                log.warn("SLS query project not found, project={}", project);
                return buildEmptyResponse(request.getSql(), "Project not found: " + project);
            }

            if (!isLogstoreExists(client, project, logstore)) {
                log.warn(
                        "SLS query logstore not found, project={}, logstore={}", project, logstore);
                return buildEmptyResponse(request.getSql(), "Logstore not found: " + logstore);
            }

            GetLogsResponse response =
                    client.executeLogstoreSql(
                            project,
                            logstore,
                            request.getFromTime(),
                            request.getToTime(),
                            finalSql,
                            false);

            GenericSlsQueryResponse result = parseQueryResponse(response, request.getSql());
            result.setElapsedMillis(System.currentTimeMillis() - startTime);

            log.info(
                    "SLS query completed, scenario={}, project={}, logstore={}, fromTime={},"
                            + " toTime={}, resultCount={}, elapsedMillis={}, sql={}, finalSql={}",
                    scenario,
                    project,
                    logstore,
                    request.getFromTime(),
                    request.getToTime(),
                    result.getCount(),
                    result.getElapsedMillis(),
                    request.getSql(),
                    finalSql);

            return result;

        } catch (LogException e) {
            log.error(
                    "SLS query failed, scenario={}, project={}, logstore={}, sql={}, finalSql={},"
                            + " errorMessage={}",
                    scenario,
                    project,
                    logstore,
                    request.getSql(),
                    finalSql,
                    e.getMessage(),
                    e);
            return buildErrorResponse(
                    request.getSql(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public GenericSlsQueryResponse executeQuery(SlsCommonQueryRequest request) {
        GenericSlsQueryRequest genericRequest = new GenericSlsQueryRequest();
        genericRequest.setUserId(request.getUserId());
        genericRequest.setFromTime(request.getFromTime());
        genericRequest.setToTime(request.getToTime());
        genericRequest.setStartTime(request.getStartTime());
        genericRequest.setEndTime(request.getEndTime());
        genericRequest.setSql(request.getSql());
        genericRequest.setPageSize(request.getPageSize());
        return executeQuery(genericRequest);
    }

    @Override
    public Boolean checkProjectExists(SlsCheckProjectRequest request) {
        Client client = slsClientFactory.createClient(request.getUserId());
        String project =
                StringUtils.hasText(request.getProject())
                        ? request.getProject()
                        : slsConfig.getDefaultProject();
        return isProjectExists(client, project);
    }

    @Override
    public Boolean checkLogstoreExists(SlsCheckLogstoreRequest request) {
        Client client = slsClientFactory.createClient(request.getUserId());
        String project =
                StringUtils.hasText(request.getProject())
                        ? request.getProject()
                        : slsConfig.getDefaultProject();
        String logstore =
                StringUtils.hasText(request.getLogstore())
                        ? request.getLogstore()
                        : slsConfig.getDefaultLogstore();
        return isLogstoreExists(client, project, logstore);
    }

    /**
     * Checks whether a project exists.
     */
    private boolean isProjectExists(Client client, String project) {
        return Boolean.TRUE.equals(
                projectExistsCache.get(
                        project,
                        key -> {
                            try {
                                client.GetProject(project);
                                return true;
                            } catch (LogException e) {
                                return false;
                            }
                        }));
    }

    /**
     * Checks whether a logstore exists.
     */
    private boolean isLogstoreExists(Client client, String project, String logstore) {
        String cacheKey = project + ":" + logstore;
        return Boolean.TRUE.equals(
                logstoreExistsCache.get(
                        cacheKey,
                        key -> {
                            try {
                                client.GetLogStore(project, logstore);
                                return true;
                            } catch (LogException e) {
                                return false;
                            }
                        }));
    }

    /**
     * Parses an SLS query response into the generic response model.
     */
    private GenericSlsQueryResponse parseQueryResponse(GetLogsResponse response, String sql) {
        List<Map<String, String>> logs = new ArrayList<>();
        List<Map<String, String>> aggregations = new ArrayList<>();

        for (QueriedLog queriedLog : response.getLogs()) {
            Map<String, String> logMap = new LinkedHashMap<>();

            for (LogContent content : queriedLog.GetLogItem().mContents) {
                String key = content.mKey;
                String value = content.mValue;

                if (StringUtils.hasText(value) && !"null".equals(value)) {
                    logMap.put(key, value);
                }
            }

            if (!logMap.isEmpty()) {
                if (logMap.containsKey("__time__") || logMap.containsKey("time")) {
                    logs.add(logMap);
                } else {
                    aggregations.add(logMap);
                }
            }
        }

        return GenericSlsQueryResponse.builder()
                .success(true)
                .processStatus(response.IsCompleted() ? "Complete" : "InComplete")
                .count((long) response.GetCount())
                .logs(logs.isEmpty() ? null : logs)
                .aggregations(aggregations.isEmpty() ? null : aggregations)
                .sql(sql)
                .build();
    }

    /**
     * Builds an empty successful response for unavailable SLS resources.
     */
    private GenericSlsQueryResponse buildEmptyResponse(String sql, String message) {
        return GenericSlsQueryResponse.builder()
                .success(true)
                .processStatus("Complete")
                .count(0L)
                .logs(Collections.emptyList())
                .sql(sql)
                .errorMessage(message)
                .build();
    }

    /**
     * Builds an error response.
     */
    private GenericSlsQueryResponse buildErrorResponse(
            String sql, String errorMessage, long elapsed) {
        return GenericSlsQueryResponse.builder()
                .success(false)
                .sql(sql)
                .errorMessage(errorMessage)
                .elapsedMillis(elapsed)
                .build();
    }

    /**
     * Replaces the interval placeholder in SQL.
     *
     * @param sql original SQL
     * @param interval interval in seconds, defaulting to 15 when null
     * @return SQL with interval placeholders replaced
     */
    private String replaceSqlInterval(String sql, Integer interval) {
        if (sql == null || !sql.contains("{interval}")) {
            return sql;
        }
        int actualInterval = (interval != null && interval > 0) ? interval : 15;
        return sql.replace("{interval}", String.valueOf(actualInterval));
    }

    /**
     * Validates and normalizes query request parameters.
     */
    private void validateQueryRequest(GenericSlsQueryRequest request) {

        if (slsConfig.getAuthType() == SlsAuthType.STS) {
            if (!StringUtils.hasText(request.getUserId())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "UserId is required when authType is STS");
            }
        }

        if (!StringUtils.hasText(request.getSql())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SQL cannot be empty");
        }

        if (request.getFromTime() == null || request.getToTime() == null) {
            if (StringUtils.hasText(request.getStartTime())
                    && StringUtils.hasText(request.getEndTime())) {
                try {
                    int from = parseToEpochSeconds(request.getStartTime().trim());
                    int to = parseToEpochSeconds(request.getEndTime().trim());
                    request.setFromTime(from);
                    request.setToTime(to);
                } catch (Exception e) {
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Invalid StartTime/EndTime format, expected ISO 8601 or yyyy-MM-dd"
                                    + " HH:mm:ss");
                }
            } else {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "FromTime/ToTime or StartTime/EndTime is required");
            }
        }
        if (request.getFromTime() >= request.getToTime()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "FromTime must be less than ToTime");
        }
    }

    /**
     * Parses a time string to Unix epoch seconds.
     *
     * <p>Supported formats are ISO 8601 timestamps and local yyyy-MM-dd HH:mm:ss values parsed in
     * the system time zone.
     */
    private int parseToEpochSeconds(String timeStr) {
        try {
            if (timeStr.endsWith("Z")) {
                return (int) Instant.parse(timeStr).getEpochSecond();
            }
        } catch (Exception ignored) {
        }
        try {
            if (timeStr.contains("T")) {
                return (int) OffsetDateTime.parse(timeStr).toEpochSecond();
            }
        } catch (Exception ignored) {
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime ldt = LocalDateTime.parse(timeStr, formatter);
        return (int) ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * Merges generic filter parameters into the SQL search segment while preserving the select
     * segment.
     *
     * <p>Expected SQL shape: {@code (<search segment>) | select <aggregation statement>}. Array
     * filters are joined with OR and include field-existence checks.
     */
    private String buildSqlWithFilters(GenericSlsQueryRequest request) {
        String sql = request.getSql();
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        String[] parts = sql.split("\\|", 2);
        if (parts.length < 2) {
            return sql;
        }
        String searchPart = parts[0].trim();
        String selectPart = parts[1].trim();

        List<String> filters = new ArrayList<>();

        // clusterId maps to cluster_id.
        if (request.getClusterId() != null && request.getClusterId().length > 0) {
            filters.add(buildOrFilter("cluster_id", request.getClusterId()));
        }

        // api maps to ai_log.api.
        if (request.getApi() != null && request.getApi().length > 0) {
            filters.add(buildOrFilter("ai_log.api", request.getApi()));
        }

        // model maps to ai_log.model.
        if (request.getModel() != null && request.getModel().length > 0) {
            filters.add(buildOrFilter("ai_log.model", request.getModel()));
        }

        // consumer maps to the top-level consumer field.
        if (request.getConsumer() != null && request.getConsumer().length > 0) {
            filters.add(buildOrFilter("consumer", request.getConsumer()));
        }

        // route maps to route_name.
        if (request.getRoute() != null && request.getRoute().length > 0) {
            filters.add(buildOrFilter("route_name", request.getRoute()));
        }

        // routeName is kept for MCP monitoring frontend compatibility.
        if (request.getRouteName() != null && request.getRouteName().length > 0) {
            filters.add(buildOrFilter("route_name", request.getRouteName()));
        }

        // service maps to upstream_cluster.
        if (request.getService() != null && request.getService().length > 0) {
            filters.add(buildOrFilter("upstream_cluster", request.getService()));
        }

        // upstreamCluster is kept for MCP monitoring frontend compatibility.
        if (request.getUpstreamCluster() != null && request.getUpstreamCluster().length > 0) {
            filters.add(buildOrFilter("upstream_cluster", request.getUpstreamCluster()));
        }

        // mcpToolName is kept for MCP monitoring frontend compatibility.
        if (request.getMcpToolName() != null && request.getMcpToolName().length > 0) {
            filters.add(buildOrFilter("ai_log.mcp_tool_name", request.getMcpToolName()));
        }

        String merged = StringUtils.hasText(searchPart) ? searchPart : "(*)";

        if (!filters.isEmpty()) {
            String allFilters = "(" + String.join(" and ", filters) + ")";
            merged = "(" + merged + ")" + "and " + allFilters;
        }

        String lowerSelect = selectPart.toLowerCase(Locale.ROOT);
        int defaultLimit = 1000;
        int maxLimit = 5000;
        Integer reqLimit = request.getPageSize();
        int limit = reqLimit == null ? defaultLimit : Math.min(Math.max(reqLimit, 1), maxLimit);
        String finalSelect =
                lowerSelect.contains(" limit ") ? selectPart : (selectPart + " limit " + limit);
        return merged + " | " + finalSelect;
    }

    /**
     * Builds an OR filter condition with a field-existence check.
     *
     * @param field field name
     * @param values filter values
     * @return OR filter condition, such as ((field: "value1" OR field: "value2") and field: *)
     */
    private String buildOrFilter(String field, String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                String v = value.trim();
                conditions.add(field + ": \"" + v + "\"");
            }
        }
        if (conditions.isEmpty()) {
            return "";
        }

        String orCondition =
                conditions.size() == 1
                        ? conditions.get(0)
                        : "(" + String.join(" OR ", conditions) + ")";

        return "(" + orCondition + " and " + field + ": *)";
    }

    /**
     * Updates indexes for the configured global log logstore.
     *
     * @param userId user ID used when STS authentication is enabled
     */
    @Override
    public void updateLogIndex(String userId) {
        String project = slsConfig.getDefaultProject();
        String logstore = slsConfig.getDefaultLogstore();

        if (!StringUtils.hasText(project) || !StringUtils.hasText(logstore)) {
            log.warn("SLS global log index update skipped because project or logstore is missing");
            return;
        }

        try {
            Client client = slsClientFactory.createClient(userId);

            if (!isProjectExists(client, project)) {
                log.warn(
                        "SLS global log index update skipped because project was not found,"
                                + " project={}",
                        project);
                return;
            }

            if (!isLogstoreExists(client, project, logstore)) {
                log.warn(
                        "SLS global log index update skipped because logstore was not found,"
                                + " project={}, logstore={}",
                        project,
                        logstore);
                return;
            }

            log.info("Updating SLS global log index, project={}, logstore={}", project, logstore);
            addGlobalLogIndex(client, project, logstore);
            log.info("Updated SLS global log index, project={}, logstore={}", project, logstore);

        } catch (Exception e) {
            log.error(
                    "Failed to update SLS global log index, project={}, logstore={},"
                            + " errorMessage={}",
                    project,
                    logstore,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Adds or updates indexes for the global log logstore, including explicit ai_log JSON
     * sub-field indexes.
     */
    private void addGlobalLogIndex(Client client, String project, String logstore) {
        Index index;
        boolean indexExists = false;

        try {
            GetIndexResponse getIndexResponse = client.GetIndex(project, logstore);
            index = getIndexResponse.GetIndex();
            indexExists = true;
        } catch (LogException e) {
            index = new Index();
        }

        if (indexExists && isAllRequiredIndexesConfigured(index)) {
            log.info(
                    "SLS global log indexes already configured, skipping update, project={},"
                            + " logstore={}",
                    project,
                    logstore);
            return;
        }

        if (!indexExists) {
            IndexLine indexLine = new IndexLine();
            indexLine.SetCaseSensitive(false);
            indexLine.SetToken(TOKENS_22);
            index.SetLine(indexLine);
        }

        IndexKeys indexKeys = index.GetKeys();
        if (Objects.isNull(indexKeys)) {
            indexKeys = new IndexKeys();
        }

        addAiLogJsonIndex(indexKeys);

        for (String field : TEXT_FIELDS) {
            addTextIndexIfNotExists(indexKeys, field);
        }

        for (String field : LONG_FIELDS) {
            addLongIndexIfNotExists(indexKeys, field);
        }

        index.SetKeys(indexKeys);
        index.setMaxTextLen(16384);

        try {
            if (indexExists) {
                client.UpdateIndex(project, logstore, index);
                log.info("Updated existing SLS index, project={}, logstore={}", project, logstore);
            } else {
                client.CreateIndex(project, logstore, index);
                log.info("Created new SLS index, project={}, logstore={}", project, logstore);
            }
        } catch (LogException e) {
            log.error(
                    "Failed to create or update SLS index, project={}, logstore={},"
                            + " errorMessage={}",
                    project,
                    logstore,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Failed to create or update index: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether all required index fields are configured.
     *
     * @param index current index configuration
     * @return true when all required fields are configured
     */
    private boolean isAllRequiredIndexesConfigured(Index index) {
        IndexKeys indexKeys = index.GetKeys();
        if (Objects.isNull(indexKeys)) {
            return false;
        }

        Map<String, IndexKey> keys = indexKeys.GetKeys();
        if (keys == null) {
            return false;
        }

        Set<String> definedFields = getDefinedIndexFields();

        for (String field : definedFields) {
            if (!keys.containsKey(field)) {
                return false;
            }
        }

        IndexKey aiLogKey = keys.get("ai_log");
        if (!(aiLogKey instanceof IndexJsonKey)) {
            return false;
        }

        return true;
    }

    /**
     * Adds a text field index when it does not already exist.
     *
     * @param indexKeys index key collection
     * @param fieldName field name
     */
    private void addTextIndexIfNotExists(IndexKeys indexKeys, String fieldName) {
        Map<String, IndexKey> keys = indexKeys.GetKeys();
        if (keys != null && keys.containsKey(fieldName)) {
            return;
        }

        IndexKey key = new IndexKey();
        key.SetType("text");
        key.SetDocValue(true);
        key.SetCaseSensitive(false);
        key.SetChn(false);

        List<String> tokens = getTokensForField(fieldName);
        key.SetToken(tokens);

        indexKeys.AddKey(fieldName, key);
    }

    /**
     * Returns the tokenizer strategy for a field.
     */
    private List<String> getTokensForField(String fieldName) {
        // Use the broader tokenizer for IP, network address, and path fields.
        if ("x_forwarded_for".equals(fieldName)
                || "downstream_local_address".equals(fieldName)
                || "downstream_remote_address".equals(fieldName)
                || "upstream_local_address".equals(fieldName)
                || "upstream_host".equals(fieldName)
                || "path".equals(fieldName)
                || "original_path".equals(fieldName)) {
            return TOKENS_26;
        }

        // Use the conservative tokenizer for identifier-like fields such as route_name,
        // upstream_cluster, consumer, cluster_id, authority, trace_id, and request_id.
        return TOKENS_22;
    }

    /**
     * Adds a long field index when it does not already exist.
     *
     * @param indexKeys index key collection
     * @param fieldName field name
     */
    private void addLongIndexIfNotExists(IndexKeys indexKeys, String fieldName) {
        Map<String, IndexKey> keys = indexKeys.GetKeys();
        if (keys != null && keys.containsKey(fieldName)) {
            return;
        }

        IndexKey key = new IndexKey();
        key.SetType("long");
        key.SetDocValue(true);
        indexKeys.AddKey(fieldName, key);
    }

    /**
     * Adds explicit sub-field index configuration for the ai_log JSON field.
     *
     * @param indexKeys index key collection
     */
    private void addAiLogJsonIndex(IndexKeys indexKeys) {
        Map<String, IndexKey> keys = indexKeys.GetKeys();
        if (keys != null && keys.containsKey("ai_log")) {
            return;
        }

        IndexJsonKey jsonKey = new IndexJsonKey();
        jsonKey.setIndexAll(true);
        jsonKey.setMaxDepth(2);
        jsonKey.SetDocValue(true);
        jsonKey.SetCaseSensitive(false);
        jsonKey.SetChn(false);
        jsonKey.SetToken(TOKENS_26);

        IndexKeys subIndexKeys = new IndexKeys();

        addJsonSubField(subIndexKeys, "api", "text");
        addJsonSubField(subIndexKeys, "cache_status", "text");
        addJsonSubField(subIndexKeys, "consumer", "text");
        addJsonSubField(subIndexKeys, "fallback_from", "text");
        addJsonSubField(subIndexKeys, "mcp_tool_name", "text");
        addJsonSubField(subIndexKeys, "model", "text");
        addJsonSubField(subIndexKeys, "response_type", "text");
        addJsonSubField(subIndexKeys, "safecheck_status", "text");
        addJsonSubField(subIndexKeys, "token_ratelimit_status", "text");

        addJsonSubField(subIndexKeys, "input_token", "long");
        addJsonSubField(subIndexKeys, "llm_first_token_duration", "long");
        addJsonSubField(subIndexKeys, "llm_service_duration", "long");
        addJsonSubField(subIndexKeys, "output_token", "long");

        jsonKey.setJsonKeys(subIndexKeys);
        indexKeys.AddKey("ai_log", jsonKey);
        log.info(
                "Added SLS JSON index, field=ai_log, subFieldCount={}",
                subIndexKeys.GetKeys().size());
    }

    /**
     * Adds a JSON sub-field index.
     *
     * @param indexKeys index key collection
     * @param fieldName field name
     * @param type field type, such as text or long
     */
    private void addJsonSubField(IndexKeys indexKeys, String fieldName, String type) {
        IndexKey key = new IndexKey();
        key.SetType(type);
        key.SetDocValue(true);

        if ("text".equals(type)) {
            key.SetCaseSensitive(false);
            key.SetChn(false);
        }

        indexKeys.AddKey(fieldName, key);
    }

    /**
     * Returns all index field names defined in code.
     *
     * @return index field names
     */
    private Set<String> getDefinedIndexFields() {
        Set<String> fields = new HashSet<>();

        fields.add("ai_log");

        fields.addAll(Arrays.asList(TEXT_FIELDS));

        fields.addAll(Arrays.asList(LONG_FIELDS));

        return fields;
    }
}
