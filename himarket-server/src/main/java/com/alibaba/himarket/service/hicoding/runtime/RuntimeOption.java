package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;

/**
 * 运行时选项，封装单个运行时方案的展示信息和可用性状态。
 * <p>
 * 用于前端 RuntimeSelector 组件展示可选运行时列表。
 *
 * @param type              沙箱类型
 * @param label             显示名称
 * @param description       描述信息
 * @param available         当前环境是否可用
 * @param unavailableReason 不可用原因（available 为 true 时为 null）
 */
public record RuntimeOption(
        SandboxType type,
        String label,
        String description,
        boolean available,
        String unavailableReason) {}
