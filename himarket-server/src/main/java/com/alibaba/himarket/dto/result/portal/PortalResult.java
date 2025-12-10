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

package com.alibaba.himarket.dto.result.portal;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.Portal;
import com.alibaba.himarket.entity.PortalDomain;
import com.alibaba.himarket.support.enums.DomainType;
import com.alibaba.himarket.support.enums.ProtocolType;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.PortalUiConfig;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class PortalResult implements OutputConverter<PortalResult, Portal> {

    private String portalId;

    private String name;

    private String description;

    private String adminId;

    private PortalSettingConfig portalSettingConfig;

    private PortalUiConfig portalUiConfig;

    private List<PortalDomainConfig> portalDomainConfig;

    @Override
    public PortalResult convertFrom(Portal source) {
        OutputConverter.super.convertFrom(source);
        if (CollUtil.isNotEmpty(source.getPortalDomains())) {
            portalDomainConfig = source.getPortalDomains().stream().map(domain -> new PortalDomainConfig().convertFrom(domain)).collect(Collectors.toList());
        }
        return this;
    }

    @Data
    static
    class PortalDomainConfig implements OutputConverter<PortalDomainConfig, PortalDomain> {

        private String domain;

        private DomainType type;

        private ProtocolType protocol = ProtocolType.HTTP;
    }
}
