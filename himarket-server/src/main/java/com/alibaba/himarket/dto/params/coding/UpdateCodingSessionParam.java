package com.alibaba.himarket.dto.params.coding;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.CodingSession;
import lombok.Data;

@Data
public class UpdateCodingSessionParam implements InputConverter<CodingSession> {

    private String title;

    private String cliSessionId;
}
