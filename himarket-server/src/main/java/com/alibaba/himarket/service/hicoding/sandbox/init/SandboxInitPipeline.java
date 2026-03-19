package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 沙箱初始化流水线。 按顺序执行注册的 InitPhase，每个阶段有前置检查、执行逻辑和就绪验证。 对所有沙箱类型（Local/K8s/E2B）通用。
 */
public class SandboxInitPipeline {

    private static final Logger logger = LoggerFactory.getLogger(SandboxInitPipeline.class);

    private final List<InitPhase> phases;
    private final InitConfig initConfig;

    public SandboxInitPipeline(List<InitPhase> phases, InitConfig initConfig) {
        this.phases = new ArrayList<>(phases);
        this.phases.sort(Comparator.comparingInt(InitPhase::order));
        this.initConfig = initConfig;
    }

    /** 执行完整的初始化流水线。 */
    public InitResult execute(InitContext context) {
        return executeFromIndex(context, 0);
    }

    /** 从指定阶段恢复执行。 */
    public InitResult resumeFrom(InitContext context, String fromPhase) {
        int startIndex = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).name().equals(fromPhase)) {
                startIndex = i;
                break;
            }
        }
        return executeFromIndex(context, startIndex);
    }

    private InitResult executeFromIndex(InitContext context, int startIndex) {
        Instant start = Instant.now();
        Map<String, Duration> phaseDurations = new LinkedHashMap<>();

        for (int i = startIndex; i < phases.size(); i++) {
            InitPhase phase = phases.get(i);

            // 检查总超时
            Duration elapsed = Duration.between(start, Instant.now());
            if (elapsed.compareTo(initConfig.totalTimeout()) > 0) {
                context.setLastError(
                        "总超时: 已耗时 "
                                + elapsed.toSeconds()
                                + "s，超过限制 "
                                + initConfig.totalTimeout().toSeconds()
                                + "s");
                context.recordEvent(phase.name(), InitEvent.EventType.PHASE_FAIL, "总超时终止");
                return InitResult.failure(
                        phase.name(),
                        context.getLastError(),
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            // 检查 shouldExecute
            if (!phase.shouldExecute(context)) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.SKIPPED);
                context.recordEvent(phase.name(), InitEvent.EventType.PHASE_SKIP, "条件不满足，跳过");
                logger.info("[Pipeline] 跳过阶段: {}", phase.name());
                continue;
            }

            context.getPhaseStatuses().put(phase.name(), PhaseStatus.EXECUTING);
            context.recordEvent(phase.name(), InitEvent.EventType.PHASE_START, "开始执行");
            logger.info("[Pipeline] 开始阶段: {}", phase.name());
            Instant phaseStart = Instant.now();

            boolean success = executeWithRetry(phase, context);
            phaseDurations.put(phase.name(), Duration.between(phaseStart, Instant.now()));

            if (!success) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.FAILED);
                context.recordEvent(
                        phase.name(), InitEvent.EventType.PHASE_FAIL, context.getLastError());
                logger.error("[Pipeline] 阶段失败: {} - {}", phase.name(), context.getLastError());
                return InitResult.failure(
                        phase.name(),
                        context.getLastError(),
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            // 验证阶段
            context.getPhaseStatuses().put(phase.name(), PhaseStatus.VERIFYING);
            if (initConfig.enableVerification() && !phase.verify(context)) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.FAILED);
                String error = "阶段 " + phase.name() + " 验证失败";
                context.setLastError(error);
                context.recordEvent(phase.name(), InitEvent.EventType.VERIFY_FAIL, error);
                logger.error("[Pipeline] 阶段验证失败: {}", phase.name());
                return InitResult.failure(
                        phase.name(),
                        error,
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            context.getPhaseStatuses().put(phase.name(), PhaseStatus.COMPLETED);
            context.recordEvent(phase.name(), InitEvent.EventType.PHASE_COMPLETE, "执行完成");
            logger.info(
                    "[Pipeline] 阶段完成: {} (耗时 {}ms)",
                    phase.name(),
                    phaseDurations.get(phase.name()).toMillis());
        }

        return InitResult.success(
                Duration.between(start, Instant.now()), phaseDurations, context.getEvents());
    }

    /** 按 RetryPolicy 执行重试逻辑。 */
    private boolean executeWithRetry(InitPhase phase, InitContext context) {
        RetryPolicy policy = phase.retryPolicy();
        int maxAttempts = policy.maxRetries() + 1;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                phase.execute(context);
                return true;
            } catch (InitPhaseException e) {
                context.setLastError(e.getMessage());

                if (attempt < maxAttempts && e.isRetryable()) {
                    context.getPhaseStatuses().put(phase.name(), PhaseStatus.RETRYING);
                    context.recordEvent(
                            phase.name(),
                            InitEvent.EventType.PHASE_RETRY,
                            "第 " + attempt + " 次失败，准备重试: " + e.getMessage());
                    logger.warn(
                            "[Pipeline] 阶段 {} 第 {}/{} 次失败，准备重试: {}",
                            phase.name(),
                            attempt,
                            maxAttempts,
                            e.getMessage());

                    long delayMs =
                            (long)
                                    (policy.initialDelay().toMillis()
                                            * Math.pow(policy.backoffMultiplier(), attempt - 1));
                    long maxDelayMs = policy.maxDelay().toMillis();
                    if (maxDelayMs > 0) {
                        delayMs = Math.min(delayMs, maxDelayMs);
                    }

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        context.setLastError("重试等待被中断");
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (Exception e) {
                context.setLastError(e.getMessage());
                return false;
            }
        }
        return false;
    }
}
