package com.alibaba.himarket.dto.params.product;

import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.enums.SourceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateProductSourceParam {

    private SourceType sourceType;

    private SkillRegistryType registryType;

    private String nacosId;

    @JsonProperty("airegistryId")
    private String aiRegistryId;

    private String namespace;
}
