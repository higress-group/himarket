package com.alibaba.himarket.dto.result.apsara;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetInstanceInfoResponse
        extends ApsaraGatewayBaseResponse<GetInstanceInfoResponse.ResponseBody> {

    @Data
    public static class ResponseBody {
        private Integer code;
        private ResponseData data;
        private String msg;
    }

    @Data
    public static class ResponseData {
        private List<AccessMode> accessMode;
        private String brokerEngineType;
        private String brokerEngineVersion;
        private String brokerLatestEngineVersion;
        private String createTime;
        private String department;
        private String deployClusterAttribute;
        private String deployClusterCode;
        private String deployClusterName;
        private String deployClusterNamespace;
        private String deployMode;
        private String edasAppId;
        private List<EdasAppInfo> edasAppInfos;
        private String edasNamespaceId;
        private String gwInstanceId;
        private String ingressClassName;
        private String instanceClass;
        private String instanceName;
        private String k8sClusterId;
        private String k8sNamespace;
        private String k8sServiceName;
        private String modifyTime;
        private String name;
        private Integer nodeNumber;
        private String regionId;
        private String resourceGroup;
        private Boolean sharedInstance;
        private String sharedInstanceSource;
        private Integer status;
        private String tid;
        private String vSwitchId;
        private String vpcId;
        private String zoneId;
        private Observability o11y;
    }

    @Data
    public static class Observability {
        private Boolean slsEnabled;
        private Boolean prometheusEnabled;
    }

    @Data
    public static class AccessMode {
        private String accessModeType;
        private List<String> clusterIp;
        private List<String> externalIps;
        private List<String> ips;
        private String loadBalancerAddressType;
        private String loadBalancerNetworkType;
        private List<String> ports;
        private String serviceName;
        private String edasAppId;
    }

    @Data
    public static class EdasAppInfo {
        private String appId;
        private String edasNamespaceId;
        private String k8sClusterId;
        private String k8sNamespace;
    }
}
