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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
@Schema(description = "Resource to import")
public class ProductImportItemParam {

    @Schema(
            description = "Resource name. Required for gateway and Nacos imports.",
            example = "fetch")
    private String resourceName;

    @Schema(
            description = "Resource ID. Required for external marketplace imports.",
            example = "@modelcontextprotocol/fetch")
    private String resourceId;

    @Schema(description = "Resource description", example = "Web content fetcher")
    private String description;

    @AssertTrue(message = "Resource name or resource ID is required")
    public boolean hasResourceIdentifier() {
        return StrUtil.isNotBlank(resourceName) || StrUtil.isNotBlank(resourceId);
    }
}
