package com.alibaba.himarket.service.hicoding.runtime;

/**
 * 运行时实例状态枚举。
 */
public enum RuntimeStatus {

    /** 创建中 */
    CREATING,

    /** 运行中 */
    RUNNING,

    /** WS 已断开，但 sidecar session 可能仍存活 */
    DETACHED,

    /** 已停止 */
    STOPPED,

    /** 异常 */
    ERROR
}
