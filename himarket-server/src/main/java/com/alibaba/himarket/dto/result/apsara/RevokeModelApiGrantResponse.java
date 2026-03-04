package com.alibaba.himarket.dto.result.apsara;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RevokeModelApiGrantResponse
        extends ApsaraGatewayBaseResponse<RevokeModelApiGrantResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private String data;
        private String msg;
    }
}
