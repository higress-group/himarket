package com.alibaba.apiopenplatform.service.impl;

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

import com.alibaba.apiopenplatform.config.SlsConfig;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCheckLogstoreRequest;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCheckProjectRequest;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCommonQueryRequest;
import com.alibaba.apiopenplatform.service.SlsLogService;
import com.alibaba.apiopenplatform.service.gateway.factory.SlsClientFactory;
import com.alibaba.apiopenplatform.support.enums.SlsAuthType;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 通用SLS日志查询服务实现
 *
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlsLogServiceImpl implements SlsLogService {

    private static final String ALIYUN_LOG_CONFIG_CRD_NAME = "aliyunlogconfigs.log.alibabacloud.com";

    private final SlsClientFactory slsClientFactory;

    private final SlsConfig slsConfig;

    /**
     * Project存在性缓存，10分钟过期
     */
    private final Cache<String, Boolean> projectExistsCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(100)
        .build();

    /**
     * Logstore存在性缓存，10分钟过期
     */
    private final Cache<String, Boolean> logstoreExistsCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(200)
        .build();

    /**
     * 分词符常量 - 用于不同类型的字段索引
     */
    // 22个分词符（保守策略）：用于标识类字段（model、api、consumer、route_name等），保留完整标识，不拆分 -、_、.
    private static final List<String> TOKENS_22 = Arrays.asList(
        ",", "'", "\"", ";", "=", "(", ")", "[", "]", "{", "}",
        "?", "@", "&", "<", ">", "/", ":", "\n", "\t", "\r", " "
    );

    // 26个分词符（完整策略）：用于路径、网络地址等字段，更全面的分词
    private static final List<String> TOKENS_26 = Arrays.asList(
        ",", "'", "\"", ";", "\\", "$", "#", "|", "=", "\n", "\t", "\r",
        "!", "%", "&", "*", "+", "-", ".", "/", ":", "<", ">", "?",
        "@", "[", "]", "^", "_", "{", "}", " "
    );

    /**
     * 索引字段定义 - 集中管理所有需要建立索引的字段
     */
    // 文本类型字段
    private static final String[] TEXT_FIELDS = {
        "_time_", "answer", "authority", "cluster_id", "consumer",
        "downstream_local_address", "downstream_remote_address", "downstream_transport_failure_reason",
        "http_referer", "method", "original_path", "path", "protocol", "question",
        "request_id", "requested_server_name", "response_code_details", "response_flags",
        "route_name", "start_time", "trace_id", "upstream_cluster", "upstream_host",
        "upstream_local_address", "upstream_protocol", "upstream_transport_failure_reason",
        "user_agent", "x_forwarded_for", "request_body", "response_body", "__tag__:_cluster_id_"
    };

    // 数值类型字段
    private static final String[] LONG_FIELDS = {
        "bytes_received", "bytes_sent", "duration", "ext_authz_duration",
        "ext_authz_status_code", "request_duration", "response_code",
        "response_tx_duration", "upstream_service_time"
    };

    @Override
    public GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request) {
        long startTime = System.currentTimeMillis();

        // 验证请求参数
        validateQueryRequest(request);

        // 创建SLS客户端（根据配置文件自动选择认证方式）
        Client client = slsClientFactory.createClient(request.getUserId());

        String project = slsConfig.getDefaultProject();
        String logstore = slsConfig.getDefaultLogstore();

        // 构建SQL并应用过滤条件和interval替换
        String finalSql = buildSqlWithFilters(request);
        finalSql = replaceSqlInterval(finalSql, request.getInterval());
        String scenario = StringUtils.hasText(request.getScenario()) ? request.getScenario() : "custom";

        try {
            // 检查project和logstore是否存在
            if (!isProjectExists(client, project)) {
                log.warn("[SLS Query] Project not found: {}", project);
                return buildEmptyResponse(request.getSql(), "Project not found: " + project);
            }

            if (!isLogstoreExists(client, project, logstore)) {
                log.warn("[SLS Query] Logstore not found: {}/{}", project, logstore);
                return buildEmptyResponse(request.getSql(), "Logstore not found: " + logstore);
            }

            // 执行查询
            GetLogsResponse response = client.executeLogstoreSql(
                project,
                logstore,
                request.getFromTime(),
                request.getToTime(),
                finalSql,
                false
            );

            // 解析结果
            GenericSlsQueryResponse result = parseQueryResponse(response, request.getSql());
            result.setElapsedMillis(System.currentTimeMillis() - startTime);

            // 统一打印一次查询结果日志
            log.info("\n========== SLS Query Result ==========\n" +
                    "Scenario: {}\n" +
                    "Project: {}\n" +
                    "Logstore: {}\n" +
                    "Time Range: {} ~ {}\n" +
                    "Result Count: {}\n" +
                    "Elapsed: {}ms\n" +
                    "Original SQL: {}\n" +
                    "Final SQL: {}\n" +
                    "======================================",
                scenario, project, logstore,
                request.getFromTime(), request.getToTime(),
                result.getCount(), result.getElapsedMillis(),
                request.getSql(), finalSql);

            return result;

        } catch (LogException e) {
            log.error("\n========== SLS Query Failed ==========\n" +
                    "Scenario: {}\n" +
                    "Project: {}\n" +
                    "Logstore: {}\n" +
                    "Original SQL: {}\n" +
                    "Final SQL: {}\n" +
                    "Error: {}\n" +
                    "======================================",
                scenario, project, logstore, request.getSql(), finalSql, e.getMessage(), e);
            return buildErrorResponse(request.getSql(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public GenericSlsQueryResponse executeQuery(SlsCommonQueryRequest request) {
        // 将 SlsCommonQueryRequest 转换为 GenericSlsQueryRequest
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
        // 如果传入了project参数，则检查指定的project；否则检查默认project
        String project = StringUtils.hasText(request.getProject())
            ? request.getProject()
            : slsConfig.getDefaultProject();
        return isProjectExists(client, project);
    }

    @Override
    public Boolean checkLogstoreExists(SlsCheckLogstoreRequest request) {
        Client client = slsClientFactory.createClient(request.getUserId());
        // 如果传入了project参数，则使用指定的project；否则使用默认project
        String project = StringUtils.hasText(request.getProject())
            ? request.getProject()
            : slsConfig.getDefaultProject();
        // 如果传入了logstore参数，则检查指定的logstore；否则检查默认logstore
        String logstore = StringUtils.hasText(request.getLogstore())
            ? request.getLogstore()
            : slsConfig.getDefaultLogstore();
        return isLogstoreExists(client, project, logstore);
    }

    /**
     * 检查Project是否存在
     */
    private boolean isProjectExists(Client client, String project) {
        return Boolean.TRUE.equals(projectExistsCache.get(project, key -> {
            try {
                client.GetProject(project);
                return true;
            } catch (LogException e) {
                return false;
            }
        }));
    }

    /**
     * 检查Logstore是否存在
     */
    private boolean isLogstoreExists(Client client, String project, String logstore) {
        String cacheKey = project + ":" + logstore;
        return Boolean.TRUE.equals(logstoreExistsCache.get(cacheKey, key -> {
            try {
                client.GetLogStore(project, logstore);
                return true;
            } catch (LogException e) {
                return false;
            }
        }));
    }

    /**
     * 解析查询响应
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
                // 判断是否为聚合结果（通常聚合结果没有__time__字段）
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
            .count((long)response.GetCount())
            .logs(logs.isEmpty() ? null : logs)
            .aggregations(aggregations.isEmpty() ? null : aggregations)
            .sql(sql)
            .build();
    }

    /**
     * 构建空响应
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
     * 构建错误响应
     */
    private GenericSlsQueryResponse buildErrorResponse(String sql, String errorMessage, long elapsed) {
        return GenericSlsQueryResponse.builder()
            .success(false)
            .sql(sql)
            .errorMessage(errorMessage)
            .elapsedMillis(elapsed)
            .build();
    }

    /**
     * 替换SQL中的interval占位符
     *
     * @param sql      原始SQL
     * @param interval 时间间隔（秒），为null时默认15秒
     * @return 替换后的SQL
     */
    private String replaceSqlInterval(String sql, Integer interval) {
        if (sql == null || !sql.contains("{interval}")) {
            return sql;
        }
        // 默认15秒
        int actualInterval = (interval != null && interval > 0) ? interval : 15;
        return sql.replace("{interval}", String.valueOf(actualInterval));
    }

    /**
     * 验证查询请求参数
     */
    private void validateQueryRequest(GenericSlsQueryRequest request) {

        // 验证认证参数（仅当配置为STS时需要userId）
        if (slsConfig.getAuthType() == SlsAuthType.STS) {
            if (!StringUtils.hasText(request.getUserId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "UserId is required when authType is STS");
            }
        }

        if (!StringUtils.hasText(request.getSql())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SQL cannot be empty");
        }

        // 处理时间区间：优先使用 fromTime/toTime；若为空则解析 startTime/endTime（支持 ISO 8601）
        if (request.getFromTime() == null || request.getToTime() == null) {
            if (StringUtils.hasText(request.getStartTime()) && StringUtils.hasText(request.getEndTime())) {
                try {
                    int from = parseToEpochSeconds(request.getStartTime().trim());
                    int to = parseToEpochSeconds(request.getEndTime().trim());
                    request.setFromTime(from);
                    request.setToTime(to);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Invalid StartTime/EndTime format, expected ISO 8601 or yyyy-MM-dd HH:mm:ss");
                }
            } else {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "FromTime/ToTime or StartTime/EndTime is required");
            }
        }
        if (request.getFromTime() >= request.getToTime()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "FromTime must be less than ToTime");
        }
    }

    /**
     * 解析字符串时间为 Unix 秒，支持：
     * - ISO 8601（如：2025-11-08T17:18:08.762Z、2025-11-08T17:18:08+08:00）
     * - 本地时间格式（yyyy-MM-dd HH:mm:ss，按系统时区解析）
     */
    private int parseToEpochSeconds(String timeStr) {
        // 优先尝试 ISO 8601（UTC Z）
        try {
            if (timeStr.endsWith("Z")) {
                return (int)Instant.parse(timeStr).getEpochSecond();
            }
        } catch (Exception ignored) {
        }
        // 尝试 ISO 8601（带偏移）
        try {
            if (timeStr.contains("T")) {
                return (int)OffsetDateTime.parse(timeStr).toEpochSecond();
            }
        } catch (Exception ignored) {
        }
        // 回退到本地时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime ldt = LocalDateTime.parse(timeStr, formatter);
        return (int)ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * 将通用过滤参数合并到SQL的检索段,保持select部分不变
     * 示例SQL结构:(<检索段>) | select <统计语句>
     * 支持数组过滤条件,使用OR语句拼接,并添加字段存在性检查
     */
    private String buildSqlWithFilters(GenericSlsQueryRequest request) {
        String sql = request.getSql();
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        String[] parts = sql.split("\\|", 2);
        if (parts.length < 2) {
            // 没有检索段,直接返回原SQL
            return sql;
        }
        String searchPart = parts[0].trim();
        String selectPart = parts[1].trim();

        List<String> filters = new ArrayList<>();

        // cluster_id => cluster_id(精确匹配,支持OR)
        if (request.getClusterId() != null && request.getClusterId().length > 0) {
            filters.add(buildOrFilter("cluster_id", request.getClusterId()));
        }

        // api => ai_log.api(精确匹配,支持OR)
        if (request.getApi() != null && request.getApi().length > 0) {
            filters.add(buildOrFilter("ai_log.api", request.getApi()));
        }

        // model => ai_log.model(精确匹配,支持OR)
        if (request.getModel() != null && request.getModel().length > 0) {
            filters.add(buildOrFilter("ai_log.model", request.getModel()));
        }

        // consumer => consumer(顶层字段,精确匹配,支持OR)
        if (request.getConsumer() != null && request.getConsumer().length > 0) {
            filters.add(buildOrFilter("consumer", request.getConsumer()));
        }

        // route => route_name(精确匹配,支持OR)
        if (request.getRoute() != null && request.getRoute().length > 0) {
            filters.add(buildOrFilter("route_name", request.getRoute()));
        }

        // service => upstream_cluster(精确匹配,支持OR)
        if (request.getService() != null && request.getService().length > 0) {
            filters.add(buildOrFilter("upstream_cluster", request.getService()));
        }

        String merged = StringUtils.hasText(searchPart) ? searchPart : "(*)";

        // 将所有过滤条件用 and 连接,并用括号包裹以保持优先级
        if (!filters.isEmpty()) {
            String allFilters = "(" + String.join(" and ", filters) + ")";
            merged = "(" + merged + ")" + "and " + allFilters;
        }

        // 追加limit限制,避免返回过多数据
        String lowerSelect = selectPart.toLowerCase(Locale.ROOT);
        int defaultLimit = 1000;
        int maxLimit = 5000;
        Integer reqLimit = request.getPageSize();
        int limit = reqLimit == null ? defaultLimit : Math.min(Math.max(reqLimit, 1), maxLimit);
        String finalSelect = lowerSelect.contains(" limit ") ? selectPart : (selectPart + " limit " + limit);
        return merged + " | " + finalSelect;
    }

    /**
     * 构建OR过滤条件，并添加字段存在性检查
     *
     * @param field  字段名
     * @param values 值数组
     * @return OR过滤条件字符串，例如：((field: "value1" OR field: "value2") and field: *)
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

        // 单个值：(field: "value" and field: *)
        // 多个值：((field: "value1" OR field: "value2") and field: *)
        String orCondition = conditions.size() == 1
            ? conditions.get(0)
            : "(" + String.join(" OR ", conditions) + ")";

        // 添加字段存在性检查
        return "(" + orCondition + " and " + field + ": *)";
    }

    /**
     * 为全局日志的 logstore 更新索引
     * 使用配置中心的 project 和 logstore
     *
     * @param userId 用户ID（用于STS认证）
     */
    @Override
    public void updateLogIndex(String userId) {
        String project = slsConfig.getDefaultProject();
        String logstore = slsConfig.getDefaultLogstore();

        if (!StringUtils.hasText(project) || !StringUtils.hasText(logstore)) {
            log.warn("[Global Log Index] Project or Logstore not configured, skip index update");
            return;
        }

        try {
            Client client = slsClientFactory.createClient(userId);

            // 检查 project 和 logstore 是否存在
            if (!isProjectExists(client, project)) {
                log.warn("[Global Log Index] Project not found: {}, skip index update", project);
                return;
            }

            if (!isLogstoreExists(client, project, logstore)) {
                log.warn("[Global Log Index] Logstore not found: {}/{}, skip index update", project, logstore);
                return;
            }

            log.info("[Global Log Index] Updating index for project: {}, logstore: {}", project, logstore);
            addGlobalLogIndex(client, project, logstore);
            log.info("[Global Log Index] Successfully updated index for project: {}, logstore: {}", project, logstore);

        } catch (Exception e) {
            log.error("[Global Log Index] Failed to update index for project: {}, logstore: {}",
                project, logstore, e);
        }
    }

    /**
     * 为全局日志 logstore 添加或更新索引
     * 包含 ai_log（JSON显式子字段索引）以及其他必要字段的索引配置
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

        // 如果索引已存在，检查所有必要字段是否都已配置
        if (indexExists && isAllRequiredIndexesConfigured(index)) {
            log.info("[Global Log Index] All required indexes already configured for {}/{}, skip update", project,
                logstore);
            return;
        }

        // 设置全文索引（如果索引不存在），使用22个分词符
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

        // ai_log - JSON类型字段，显式配置子字段索引和统计
        addAiLogJsonIndex(indexKeys);

        // 批量添加文本字段索引
        for (String field : TEXT_FIELDS) {
            addTextIndexIfNotExists(indexKeys, field);
        }

        // 批量添加数值字段索引
        for (String field : LONG_FIELDS) {
            addLongIndexIfNotExists(indexKeys, field);
        }

        index.SetKeys(indexKeys);
        index.setMaxTextLen(16384);

        try {
            if (indexExists) {
                client.UpdateIndex(project, logstore, index);
                log.info("[Global Log Index] Updated existing index for {}/{}", project, logstore);
            } else {
                client.CreateIndex(project, logstore, index);
                log.info("[Global Log Index] Created new index for {}/{}", project, logstore);
            }
        } catch (LogException e) {
            log.error("[Global Log Index] Failed to create or update index for logstore: {}", logstore, e);
            throw new RuntimeException("Failed to create or update index", e);
        }
    }

    /**
     * 检查所有必要的索引字段是否都已配置
     *
     * @param index 当前索引配置
     * @return true 如果所有必要字段都已配置，false 否则
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

        // 收集代码中定义的所有必要字段
        // 这个方法会在实际添加索引的逻辑中调用，确保字段列表与实际添加的字段保持同步
        Set<String> definedFields = getDefinedIndexFields();

        // 检查所有定义的字段是否都存在于现有索引中
        for (String field : definedFields) {
            if (!keys.containsKey(field)) {
                return false;
            }
        }

        // 特别检查 ai_log 是否为 JSON 类型索引
        IndexKey aiLogKey = keys.get("ai_log");
        if (!(aiLogKey instanceof IndexJsonKey)) {
            return false;
        }

        return true;
    }

    /**
     * 添加文本类型字段索引（如果不存在）
     * 根据字段类型使用不同的分词符策略
     *
     * @param indexKeys 索引键集合
     * @param fieldName 字段名称
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

        // 根据字段类型选择合适的分词符
        List<String> tokens = getTokensForField(fieldName);
        key.SetToken(tokens);

        indexKeys.AddKey(fieldName, key);
    }

    /**
     * 根据字段名称返回对应的分词符策略
     */
    private List<String> getTokensForField(String fieldName) {
        // IP/网络地址类字段、路径类字段 - 使用26个分词符（完整策略）
        if ("x_forwarded_for".equals(fieldName) ||
            "downstream_local_address".equals(fieldName) ||
            "downstream_remote_address".equals(fieldName) ||
            "upstream_local_address".equals(fieldName) ||
            "upstream_host".equals(fieldName) ||
            "path".equals(fieldName) ||
            "original_path".equals(fieldName)) {
            return TOKENS_26;
        }

        // 标识类字段 - 使用22个分词符（保守策略），保留 -、_、. 等标识符内部字符
        // 包括：route_name, upstream_cluster, consumer, cluster_id, authority,
        //      requested_server_name, method, protocol, trace_id, request_id 等
        return TOKENS_22;
    }

    /**
     * 添加数值类型（long）字段索引（如果不存在）
     *
     * @param indexKeys 索引键集合
     * @param fieldName 字段名称
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
     * 为 ai_log JSON 字段添加显式子字段索引配置
     *
     * @param indexKeys 索引键集合
     */
    private void addAiLogJsonIndex(IndexKeys indexKeys) {
        Map<String, IndexKey> keys = indexKeys.GetKeys();
        if (keys != null && keys.containsKey("ai_log")) {
            return;
        }

        IndexJsonKey jsonKey = new IndexJsonKey();
        // ai_log 可能存在自定义扩展字段
        jsonKey.setIndexAll(true);
        jsonKey.setMaxDepth(2);
        jsonKey.SetDocValue(true);
        jsonKey.SetCaseSensitive(false);
        jsonKey.SetChn(false);
        jsonKey.SetToken(TOKENS_26);

        // 配置 ai_log 的子字段索引
        IndexKeys subIndexKeys = new IndexKeys();

        // 文本类型子字段
        addJsonSubField(subIndexKeys, "api", "text");
        addJsonSubField(subIndexKeys, "cache_status", "text");
        addJsonSubField(subIndexKeys, "consumer", "text");
        addJsonSubField(subIndexKeys, "fallback_from", "text");
        addJsonSubField(subIndexKeys, "mcp_tool_name", "text");
        addJsonSubField(subIndexKeys, "model", "text");
        addJsonSubField(subIndexKeys, "response_type", "text");
        addJsonSubField(subIndexKeys, "safecheck_status", "text");
        addJsonSubField(subIndexKeys, "token_ratelimit_status", "text");

        // 数值类型子字段
        addJsonSubField(subIndexKeys, "input_token", "long");
        addJsonSubField(subIndexKeys, "llm_first_token_duration", "long");
        addJsonSubField(subIndexKeys, "llm_service_duration", "long");
        addJsonSubField(subIndexKeys, "output_token", "long");

        jsonKey.setJsonKeys(subIndexKeys);
        indexKeys.AddKey("ai_log", jsonKey);
        log.info("[Global Log Index] Added JSON index with {} explicit sub-fields for field: ai_log",
            subIndexKeys.GetKeys().size());
    }

    /**
     * 为JSON字段添加子字段索引（通用方法）
     *
     * @param indexKeys 索引键集合
     * @param fieldName 字段名称
     * @param type      字段类型（text/long）
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
     * 获取代码中定义的所有索引字段名称
     * 这个列表应该与 addGlobalLogIndex() 方法中实际添加的字段保持一致
     *
     * @return 所有索引字段名称的集合
     */
    private Set<String> getDefinedIndexFields() {
        Set<String> fields = new HashSet<>();

        // JSON字段
        fields.add("ai_log");

        // 文本字段
        fields.addAll(Arrays.asList(TEXT_FIELDS));

        // 数值字段
        fields.addAll(Arrays.asList(LONG_FIELDS));

        return fields;
    }

}
