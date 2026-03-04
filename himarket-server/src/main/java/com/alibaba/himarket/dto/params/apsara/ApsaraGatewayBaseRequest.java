package com.alibaba.himarket.dto.params.apsara;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public class ApsaraGatewayBaseRequest {

    public JSONObject toJsonObject() {
        JSONObject json = JSONUtil.parseObj(this);

        // 移除值为 null 的字段
        json.keySet().removeIf(key -> json.get(key) == null);

        // 或者移除空字符串
        json.keySet()
                .removeIf(
                        key -> {
                            Object value = json.get(key);
                            return value instanceof String && ((String) value).isEmpty();
                        });

        return json;
    }
}
