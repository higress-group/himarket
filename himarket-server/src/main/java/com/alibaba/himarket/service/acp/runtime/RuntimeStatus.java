package com.alibaba.himarket.service.acp.runtime;

/**
 * 运行时实例状态枚举。
 */
public enum RuntimeStatus {

    /** 创建中 */
    CREATING,

    /** 运行中 */
    RUNNING,

    /** 已停止 */
    STOPPED,

    /** 异常 */
    ERROR
}
