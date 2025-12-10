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

package com.alibaba.himarket.dto.params.portal;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.Portal;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.PortalUiConfig;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePortalParam implements InputConverter<Portal> {

    @Size(max = 50, message = "门户名称长度不能超过50个字符")
    private String name;

    @Size(max = 1024, message = "门户描述长度不能超过1024个字符")
    private String description;

    private PortalSettingConfig portalSettingConfig;

    private PortalUiConfig portalUiConfig;
}
