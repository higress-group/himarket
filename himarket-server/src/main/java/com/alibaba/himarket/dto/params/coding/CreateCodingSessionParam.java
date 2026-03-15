package com.alibaba.himarket.dto.params.coding;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.CodingSession;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCodingSessionParam implements InputConverter<CodingSession> {

    @NotBlank(message = "cliSessionId cannot be empty")
    private String cliSessionId;

    private String title;

    private String providerKey;

    private String cwd;

    private String modelProductId;

    private String modelName;
}
