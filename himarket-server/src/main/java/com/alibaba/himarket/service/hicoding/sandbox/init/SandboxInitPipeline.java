package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sandbox initialization pipeline.
 *
 * <p>Executes registered {@link InitPhase} instances in order. Each phase owns prerequisite
 * checks, execution logic, and readiness verification. The pipeline is shared by all sandbox types.
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

    /**
     * Executes the full initialization pipeline.
     */
    public InitResult execute(InitContext context) {
        return executeFromIndex(context, 0);
    }

    /**
     * Resumes execution from the specified phase.
     */
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

            // Check the total pipeline timeout.
            Duration elapsed = Duration.between(start, Instant.now());
            if (elapsed.compareTo(initConfig.totalTimeout()) > 0) {
                context.setLastError(
                        "Total timeout: elapsed "
                                + elapsed.toSeconds()
                                + "s, limit "
                                + initConfig.totalTimeout().toSeconds()
                                + "s");
                context.recordEvent(
                        phase.name(), InitEvent.EventType.PHASE_FAIL, "Stopped by total timeout");
                return InitResult.failure(
                        phase.name(),
                        context.getLastError(),
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            // Check whether this phase should execute.
            if (!phase.shouldExecute(context)) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.SKIPPED);
                context.recordEvent(
                        phase.name(), InitEvent.EventType.PHASE_SKIP, "Condition not met, skipped");
                logger.info("Skipped sandbox initialization phase, phase={}", phase.name());
                continue;
            }

            context.getPhaseStatuses().put(phase.name(), PhaseStatus.EXECUTING);
            context.recordEvent(phase.name(), InitEvent.EventType.PHASE_START, "Started");
            logger.info("Started sandbox initialization phase, phase={}", phase.name());
            Instant phaseStart = Instant.now();

            boolean success = executeWithRetry(phase, context);
            phaseDurations.put(phase.name(), Duration.between(phaseStart, Instant.now()));

            if (!success) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.FAILED);
                context.recordEvent(
                        phase.name(), InitEvent.EventType.PHASE_FAIL, context.getLastError());
                logger.error(
                        "Sandbox initialization phase failed, phase={}, errorMessage={}",
                        phase.name(),
                        context.getLastError());
                return InitResult.failure(
                        phase.name(),
                        context.getLastError(),
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            // Verify the phase result.
            context.getPhaseStatuses().put(phase.name(), PhaseStatus.VERIFYING);
            if (initConfig.enableVerification() && !phase.verify(context)) {
                context.getPhaseStatuses().put(phase.name(), PhaseStatus.FAILED);
                String error = "Phase " + phase.name() + " verification failed";
                context.setLastError(error);
                context.recordEvent(phase.name(), InitEvent.EventType.VERIFY_FAIL, error);
                logger.error(
                        "Sandbox initialization phase verification failed, phase={}", phase.name());
                return InitResult.failure(
                        phase.name(),
                        error,
                        Duration.between(start, Instant.now()),
                        phaseDurations,
                        context.getEvents());
            }

            context.getPhaseStatuses().put(phase.name(), PhaseStatus.COMPLETED);
            context.recordEvent(phase.name(), InitEvent.EventType.PHASE_COMPLETE, "Completed");
            logger.info(
                    "Sandbox initialization phase completed, phase={}, durationMs={}",
                    phase.name(),
                    phaseDurations.get(phase.name()).toMillis());
        }

        return InitResult.success(
                Duration.between(start, Instant.now()), phaseDurations, context.getEvents());
    }

    /**
     * Executes the retry loop according to {@link RetryPolicy}.
     */
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
                            "Attempt " + attempt + " failed, retrying: " + e.getMessage());
                    logger.warn(
                            "Sandbox initialization phase failed, retrying, phase={}, attempt={},"
                                    + " maxAttempts={}, errorType={}, errorMessage={}",
                            phase.name(),
                            attempt,
                            maxAttempts,
                            e.getClass().getSimpleName(),
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
                        context.setLastError("Retry wait was interrupted");
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
