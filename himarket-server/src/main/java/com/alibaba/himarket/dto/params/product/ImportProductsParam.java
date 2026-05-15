package com.alibaba.himarket.dto.params.product;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.enums.ProductImportSource;
import com.alibaba.himarket.support.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ImportProductsParam {

    @NotNull(message = "Import source is required")
    private ProductImportSource source;

    @NotNull(message = "Product type is required")
    private ProductType productType;

    @Valid
    @NotNull(message = "Import source config is required")
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "source")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = GatewayImportConfigParam.class, name = "GATEWAY"),
        @JsonSubTypes.Type(value = NacosImportConfigParam.class, name = "NACOS"),
        @JsonSubTypes.Type(value = ExternalImportConfigParam.class, name = "EXTERNAL")
    })
    private ProductImportSourceConfigParam sourceConfig;

    @NotEmpty(message = "Import items cannot be empty")
    private List<@Valid ProductImportItemParam> items;

    @AssertTrue(message = "Gateway and Nacos import items must specify resource name")
    public boolean hasResourceNamesForInstanceSources() {
        if (source != ProductImportSource.GATEWAY && source != ProductImportSource.NACOS) {
            return true;
        }
        return items != null
                && items.stream().allMatch(item -> StrUtil.isNotBlank(item.getResourceName()));
    }

    @AssertTrue(message = "External import items must specify resource ID")
    public boolean hasResourceIdsForExternalSource() {
        if (source != ProductImportSource.EXTERNAL) {
            return true;
        }
        return items != null
                && items.stream().allMatch(item -> StrUtil.isNotBlank(item.getResourceId()));
    }
}
