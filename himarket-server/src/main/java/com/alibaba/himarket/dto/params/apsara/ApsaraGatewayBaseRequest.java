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
