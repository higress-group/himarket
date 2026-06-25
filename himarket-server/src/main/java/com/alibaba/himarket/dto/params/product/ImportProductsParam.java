package com.alibaba.himarket.dto.params.product;

import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.ProductImportSource;
import com.alibaba.himarket.support.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Product import request")
public class ImportProductsParam {

    @Schema(
            description = "Import source",
            example = "GATEWAY",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Import source is required")
    private ProductImportSource source;

    @Schema(
            description = "Product type to create after import",
            example = "MCP_SERVER",
            requiredMode = Schema.RequiredMode.REQUIRED)
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
        @JsonSubTypes.Type(value = AiRegistryImportConfigParam.class, name = "AIREGISTRY"),
        @JsonSubTypes.Type(value = ExternalImportConfigParam.class, name = "EXTERNAL")
    })
    @Schema(
            description =
                    "Import source configuration. GATEWAY, NACOS, and AIREGISTRY use instance"
                            + " configuration, while EXTERNAL uses marketplace configuration.",
            oneOf = {
                GatewayImportConfigParam.class,
                NacosImportConfigParam.class,
                AiRegistryImportConfigParam.class,
                ExternalImportConfigParam.class
            },
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ProductImportSourceConfigParam sourceConfig;

    @Schema(description = "Resources to import", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Import items cannot be empty")
    private List<@Valid ProductImportItemParam> items;

    @AssertTrue(message = "Gateway, Nacos, and AIRegistry import items must specify resource name")
    public boolean hasResourceNamesForInstanceSources() {
        if (source != ProductImportSource.GATEWAY
                && source != ProductImportSource.NACOS
                && source != ProductImportSource.AIREGISTRY) {
            return true;
        }
        return items != null
                && items.stream().allMatch(item -> Strings.isNotBlank(item.getResourceName()));
    }

    @AssertTrue(message = "External import items must specify resource ID")
    public boolean hasResourceIdsForExternalSource() {
        if (source != ProductImportSource.EXTERNAL) {
            return true;
        }
        return items != null
                && items.stream().allMatch(item -> Strings.isNotBlank(item.getResourceId()));
    }
}
