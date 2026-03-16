package com.alibaba.himarket.dto.result.coding;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.CodingSession;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CodingSessionResult implements OutputConverter<CodingSessionResult, CodingSession> {

    private String sessionId;

    private String cliSessionId;

    private String title;

    private String providerKey;

    private String cwd;

    private String modelProductId;

    private String modelName;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;
}
