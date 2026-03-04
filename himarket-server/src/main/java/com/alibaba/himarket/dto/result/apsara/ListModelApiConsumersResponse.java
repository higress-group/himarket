package com.alibaba.himarket.dto.result.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListModelApiConsumersResponse
        extends ApsaraGatewayBaseResponse<ListModelApiConsumersResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private ResponseData data;
        private String msg;
    }

    @Data
    public static class ResponseData {
        private Integer total;
        private Integer current;
        private Integer size;
        private List<Record> records;
    }

    @Data
    public static class Record {
        private String gwInstanceId;
        private String modelApiId;
        private String appId;
        private String consumerName;
        private Integer authType;
        private Boolean enable;
        private String authId;
    }
}
