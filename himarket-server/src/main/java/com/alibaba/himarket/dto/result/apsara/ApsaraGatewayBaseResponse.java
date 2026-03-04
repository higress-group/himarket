package com.alibaba.himarket.dto.result.apsara;

import java.util.Map;
import lombok.Data;

@Data
public class ApsaraGatewayBaseResponse<T> {
    public Map<String, String> headers;
    public Integer statusCode;
    public T body;
}
