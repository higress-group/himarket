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

package com.alibaba.himarket.dto.params.product;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import lombok.Data;

@Data
public class ModelFilter {

    /**
     * Model category: Image, Chat, Embedding, etc.
     */
    private String category;

    /**
     * Check if config matches filter
     *
     * @param configResult Model config to check
     * @return true if matches all criteria
     */
    public boolean matches(ModelConfigResult configResult) {
        if (configResult == null || configResult.getModelAPIConfig() == null) {
            return false;
        }

        return StrUtil.equalsIgnoreCase(
                category, configResult.getModelAPIConfig().getModelCategory());
    }
}
