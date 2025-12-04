package com.alibaba.apiopenplatform.service.gateway.factory;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预置场景SQL注册表
 * 说明：
 * - 场景名作为前后端契约（如：pv、uv、qps_total）
 * - 每个场景包含展示类型、预设SQL、必要的字段别名（用于时序图）
 * - 严格忽略 cluster_id 与 ai_log.api 两个过滤条件，其余保留
 * - 注释说明每个场景查询的含义，便于维护与扩展
 */
@Component
@Slf4j
public class SlsPresetSqlRegistry {

    /** 展示类型 */
    @Getter
    public enum DisplayType { CARD, LINE, TABLE }

    /** 场景预设 */
    @Getter
    public static class Preset {
        /** 场景名 */
        private final String name;
        /** 展示类型 */
        private final DisplayType type;
        /** 预设SQL（检索段 | select 统计段） */
        private final String sql;
        /** 时序图时间字段别名（LINE类型需要） */
        private final String timeField;
        /** 时序图数值字段别名（LINE类型需要） */
        private final String valueField;

        public Preset(String name, DisplayType type, String sql, String timeField, String valueField) {
            this.name = name;
            this.type = type;
            this.sql = sql;
            this.timeField = timeField;
            this.valueField = valueField;
        }
    }

    private final Map<String, Preset> presets = new HashMap<>();

    public SlsPresetSqlRegistry() {
        // 卡片类
        // 总请求次数（适用场景：模型大盘、MCP大盘）
        presets.put("pv", new Preset("pv", DisplayType.CARD,
                "(*) | select count(1) as pv",
                null, null));
        // 独立调用者数量（适用场景：模型大盘、MCP大盘）
        presets.put("uv", new Preset("uv", DisplayType.CARD,
                "(*) | select approx_distinct(\"x_forwarded_for\") as uv",
                null, null));
        // Fallback 请求数（仅模型大盘）
        presets.put("fallback_count", new Preset("fallback_count", DisplayType.CARD,
                "(* and response_code_details: internal_redirect) | select count(1) as cnt",
                null, null));
        // 网关入流量MB（仅MCP大盘）
        presets.put("bytes_received", new Preset("bytes_received", DisplayType.CARD,
                "(*) | select round(sum(\"bytes_received\") / 1024.0 / 1024.0, 3) as received",
                null, null));
        // 网关出流量MB（仅MCP大盘）
        presets.put("bytes_sent", new Preset("bytes_sent", DisplayType.CARD,
                "(*) | select round(sum(\"bytes_sent\") / 1024.0 / 1024.0, 3) as sent",
                null, null));
        // 输入 Token 总数（仅模型大盘）
        presets.put("input_token_total", new Preset("input_token_total", DisplayType.CARD,
                "(ai_log.model : *) | select sum(cast(json_extract(ai_log, '$.input_token') as integer)) as input_token",
                null, null));
        // 输出 Token 总数（仅模型大盘）
        presets.put("output_token_total", new Preset("output_token_total", DisplayType.CARD,
                "(ai_log.model : *) | select sum(cast(json_extract(ai_log, '$.output_token') as integer)) as output_token",
                null, null));
        // Token 总数（仅模型大盘）
        presets.put("token_total", new Preset("token_total", DisplayType.CARD,
                "(ai_log.model : *) | select sum(cast(json_extract(ai_log, '$.input_token') as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as integer)) as token",
                null, null));

        // 线图类
        // 流式QPS（仅模型大盘）
        presets.put("qps_stream", new Preset("qps_stream", DisplayType.LINE,
                "(ai_log.response_type : stream) | select cast (count(1) as double)/{interval} as stream_qps, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "stream_qps"));
        // 非流式QPS（仅模型大盘）
        presets.put("qps_normal", new Preset("qps_normal", DisplayType.LINE,
                "(ai_log.response_type : normal) | select cast (count(1) as double)/{interval} as normal_qps, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "normal_qps"));
        // 总体QPS（仅模型大盘）
        presets.put("qps_total", new Preset("qps_total", DisplayType.LINE,
                "((ai_log.response_type : normal or ai_log.response_type : stream)) | select cast (count(1) as double)/{interval} as total_qps, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "total_qps"));
        // 请求成功率（适用场景：模型大盘、MCP大盘）
        presets.put("success_rate", new Preset("success_rate", DisplayType.LINE,
                "(*) | select cast(cnt_right as double)/cnt_total as success_rate, t2.time from (select count(1) as cnt_right, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') as time from log where response_code < 300 and response_code > 0 group by time) as t1 join (select count(1) as cnt_total, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') as time from log group by time) as t2 on t1.time = t2.time order by t2.time limit all",
                "time", "success_rate"));
        // Token/s（输入）（仅模型大盘）
        presets.put("token_per_sec_input", new Preset("token_per_sec_input", DisplayType.LINE,
                "(*) | select sum(cast(json_extract(ai_log, '$.input_token') as integer))/{interval} as input_token, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "input_token"));
        // Token/s（输出）（仅模型大盘）
        presets.put("token_per_sec_output", new Preset("token_per_sec_output", DisplayType.LINE,
                "(*) | select sum(cast(json_extract(ai_log, '$.output_token') as integer))/{interval} as output_token, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "output_token"));
        // Token/s（总）（仅模型大盘）
        presets.put("token_per_sec_total", new Preset("token_per_sec_total", DisplayType.LINE,
                "(*) | select (sum(cast(json_extract(ai_log, '$.input_token') as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as integer)))/{interval} as total_token, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "total_token"));
        // 平均RT（整体）（仅模型大盘）
        presets.put("rt_avg_total", new Preset("rt_avg_total", DisplayType.LINE,
                "(ai_log.llm_service_duration : *) | select sum(cast(json_extract(ai_log, '$.llm_service_duration') as double))/count(*) as total_rt, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "total_rt"));
        // 平均RT（流式）（仅模型大盘）
        presets.put("rt_avg_stream", new Preset("rt_avg_stream", DisplayType.LINE,
                "(ai_log.llm_service_duration : * and ai_log.response_type : stream) | select sum(cast(json_extract(ai_log, '$.llm_service_duration') as double))/count(*) as stream_rt, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "stream_rt"));
        // 平均RT（非流式）（仅模型大盘）
        presets.put("rt_avg_normal", new Preset("rt_avg_normal", DisplayType.LINE,
                "(ai_log.llm_service_duration : * and ai_log.response_type : normal) | select sum(cast(json_extract(ai_log, '$.llm_service_duration') as double))/count(*) as normal_rt, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "normal_rt"));
        // 首包RT（仅模型大盘）
        presets.put("rt_first_token", new Preset("rt_first_token", DisplayType.LINE,
                "(ai_log.llm_first_token_duration : * and ai_log.llm_service_duration : *) | select sum(cast(json_extract(ai_log, '$.llm_first_token_duration') as double))/count(*) as first_token_rt, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "first_token_rt"));
        // 缓存命中/未命中/跳过（仅模型大盘）
        presets.put("cache_hit", new Preset("cache_hit", DisplayType.LINE,
                "(ai_log.cache_status : hit) | select cast (count(1) as double)/{interval} as hit, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "hit"));
        presets.put("cache_miss", new Preset("cache_miss", DisplayType.LINE,
                "(ai_log.cache_status : miss) | select cast (count(1) as double)/{interval} as miss, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "miss"));
        presets.put("cache_skip", new Preset("cache_skip", DisplayType.LINE,
                "(ai_log.cache_status : skip) | select cast (count(1) as double)/{interval} as skip, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "skip"));
        // 限流请求数/s（仅模型大盘）
        presets.put("ratelimited_per_sec", new Preset("ratelimited_per_sec", DisplayType.LINE,
                "(ai_log.token_ratelimit_status : limited) | select cast (count(1) as double)/{interval} as ratelimited, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "ratelimited"));
        // QPS（按状态码分组）（仅MCP大盘）
        presets.put("qps_by_status", new Preset("qps_by_status", DisplayType.LINE,
                "(*) | select cast (count(1) as double)/{interval} as qps, response_code, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time, response_code order by time limit all",
                "time", "qps"));
        // 总QPS（仅MCP大盘）
        presets.put("qps_total_simple", new Preset("qps_total_simple", DisplayType.LINE,
                "(*) | select cast (count(1) as double)/{interval} as total, 'total' as response_code, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time, response_code order by time limit all",
                "time", "total"));
        // 平均RT（仅MCP大盘）
        presets.put("rt_avg", new Preset("rt_avg", DisplayType.LINE,
                "(*) | select sum(cast(duration as double))/count(*) as rt_avg, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "rt_avg"));
        // P99 RT（仅MCP大盘）
        presets.put("rt_p99", new Preset("rt_p99", DisplayType.LINE,
                "(*) | select approx_percentile(duration, 0.99) as rt_p99, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "rt_p99"));
        // P95 RT（仅MCP大盘）
        presets.put("rt_p95", new Preset("rt_p95", DisplayType.LINE,
                "(*) | select approx_percentile(duration, 0.95) as rt_p95, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "rt_p95"));
        // P90 RT（仅MCP大盘）
        presets.put("rt_p90", new Preset("rt_p90", DisplayType.LINE,
                "(*) | select approx_percentile(duration, 0.9) as rt_p90, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "rt_p90"));
        // P50 RT（仅MCP大盘）
        presets.put("rt_p50", new Preset("rt_p50", DisplayType.LINE,
                "(*) | select approx_percentile(duration, 0.5) as rt_p50, date_format(__time__ - __time__ % {interval}, '%Y-%m-%d %H:%i:%s') AS time FROM log GROUP BY time order by time limit all",
                "time", "rt_p50"));

        // 表格类
        // 模型token使用统计（仅模型大盘）
        presets.put("model_token_table", new Preset("model_token_table", DisplayType.TABLE,
                "(ai_log.model : *) | select json_extract(ai_log, '$.model') as model, sum(cast(json_extract(ai_log, '$.input_token') as integer)) as input_token, sum(cast(json_extract(ai_log, '$.output_token') as integer)) as output_token, sum(cast(json_extract(ai_log, '$.input_token') as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as integer)) as total_token, count(1) as request group by model order by total_token desc",
                null, null));
        // 消费者token使用统计（仅模型大盘）
        presets.put("consumer_token_table", new Preset("consumer_token_table", DisplayType.TABLE,
                "(consumer : *) | select consumer as consumer, sum(cast(json_extract(ai_log, '$.input_token') as integer)) as input_token, sum(cast(json_extract(ai_log, '$.output_token') as integer)) as output_token, sum(cast(json_extract(ai_log, '$.input_token') as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as integer)) as total_token, count(1) as request group by consumer order by total_token desc",
                null, null));
        // 服务token使用统计（仅模型大盘）
        presets.put("service_token_table", new Preset("service_token_table", DisplayType.TABLE,
                "(ai_log.model : *) | select upstream_cluster, sum(cast(json_extract(ai_log, '$.input_token') as integer)) as input_token, sum(cast(json_extract(ai_log, '$.output_token') as integer)) as output_token, sum(cast(json_extract(ai_log, '$.input_token') as integer)) + sum(cast(json_extract(ai_log, '$.output_token') as integer)) as total_token, count(1) as request group by upstream_cluster order by total_token desc",
                null, null));
        // 错误请求统计（仅模型大盘）
        presets.put("error_requests_table", new Preset("error_requests_table", DisplayType.TABLE,
                "(*) | select response_code, response_code_details, response_flags, count(*) as cnt from log where response_code = 0 or response_code >= 400 group by response_code, response_code_details, response_flags order by cnt desc limit all",
                null, null));
        // 限流消费者统计（仅模型大盘）
        presets.put("ratelimited_consumer_table", new Preset("ratelimited_consumer_table", DisplayType.TABLE,
                "(ai_log.token_ratelimit_status : limited) | select json_extract(ai_log, '$.consumer') as consumer, count(1) as ratelimited_count group by consumer order by ratelimited_count desc",
                null, null));
        // 风险类型统计（仅模型大盘）
        presets.put("risk_label_table", new Preset("risk_label_table", DisplayType.TABLE,
                "(ai_log.safecheck_status : \"deny\") | select json_extract(ai_log, '$.safecheck_riskLabel') as risklabel, count(*) as cnt group by risklabel order by cnt desc",
                null, null));
        // 风险消费者统计（仅模型大盘）
        presets.put("risk_consumer_table", new Preset("risk_consumer_table", DisplayType.TABLE,
                "(ai_log.safecheck_status : \"deny\") | select json_extract(ai_log, '$.consumer') as consumer, count(*) as cnt group by consumer order by cnt desc",
                null, null));
        // Method分布（仅MCP大盘）
        presets.put("method_distribution", new Preset("method_distribution", DisplayType.TABLE,
                "(method: *) | select \"method\" as method, count(1) as count group by method",
                null, null));
        // 网关状态码分布（仅MCP大盘）
        presets.put("gateway_status_distribution", new Preset("gateway_status_distribution", DisplayType.TABLE,
                "(response_code: *) | select response_code as status, count(1) as count group by status",
                null, null));
        // 后端状态码分布（仅MCP大盘）
        presets.put("backend_status_distribution", new Preset("backend_status_distribution", DisplayType.TABLE,
                "(response_code_details: via_upstream) | select \"response_code\" as status, count(1) as count group by status",
                null, null));
        // 请求分布（仅MCP大盘）
        presets.put("request_distribution", new Preset("request_distribution", DisplayType.TABLE,
                "(*) | select json_extract(ai_log, '$.mcp_tool_name') as tool_name, response_code, response_flags, response_code_details, count(*) as cnt from log group by tool_name, response_code, response_flags, response_code_details order by cnt desc limit all",
                null, null));


        // 前端下拉框选项类 - 用于辅助用户构建查询条件
        // 实例列表
        presets.put("filter_service_options", new Preset("filter_service_options", DisplayType.TABLE,
                "(*) | select distinct cluster_id as service from log where cluster_id is not null limit 100",
                null, null));
        // API列表
        presets.put("filter_api_options", new Preset("filter_api_options", DisplayType.TABLE,
                "(*) | select distinct json_extract(ai_log, '$.api') as api from log where json_extract(ai_log, '$.api') is not null limit 100",
                null, null));
        // 模型列表
        presets.put("filter_model_options", new Preset("filter_model_options", DisplayType.TABLE,
                "(*) | select distinct json_extract(ai_log, '$.model') as model from log where json_extract(ai_log, '$.model') is not null limit 100",
                null, null));
        // 路由列表
        presets.put("filter_route_options", new Preset("filter_route_options", DisplayType.TABLE,
                "(*) | select distinct route_name from log where route_name is not null limit 100",
                null, null));
        // 消费者列表
        presets.put("filter_consumer_options", new Preset("filter_consumer_options", DisplayType.TABLE,
                "(*) | select distinct consumer as consumer from log where consumer is not null limit 100",
                null, null));
        // 上游服务列表
        presets.put("filter_upstream_options", new Preset("filter_upstream_options", DisplayType.TABLE,
                "(*) | select distinct upstream_cluster from log where upstream_cluster is not null limit 100",
                null, null));
        // MCP工具名称列表
        presets.put("filter_mcp_tool_options", new Preset("filter_mcp_tool_options", DisplayType.TABLE,
                "(*) | select distinct json_extract(ai_log, '$.mcp_tool_name') as mcp_tool_name from log where json_extract(ai_log, '$.mcp_tool_name') is not null limit 100",
                null, null));
        
    }

    /** 根据场景名获取预设 */
    public Preset getPreset(String scenario) {
        if (scenario == null) return null;
        Preset p = presets.get(scenario);
        if (p == null) {
            log.warn("Unknown scenario: {}", scenario);
        }
        return p;
    }
}
