package com.example.campaign.campaign.benchmark;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory data holder for a single campaign's benchmark metrics.
 * <p>
 * NOT a Spring bean, NOT a JPA entity — just a plain POJO with
 * thread-safe counters so multiple virtual threads can update it
 * concurrently without synchronization.
 */
@Data
@Builder
public class CampaignBenchmarkRecord {

    // ── Identity ──────────────────────────────────────────────
    private Long campaignId;
    private int totalContacts;

    // ── Timing (plain long — set only once from a single thread) ──
    private long startTime;
    private long endTime;
    private long totalDurationMs;

    // ── Contact counters (AtomicInteger for thread safety) ────
    @Builder.Default
    private AtomicInteger processedContacts = new AtomicInteger(0);
    @Builder.Default
    private AtomicInteger sentMessages = new AtomicInteger(0);
    @Builder.Default
    private AtomicInteger failedContacts = new AtomicInteger(0);

    // ── Per-second tracking ───────────────────────────────────
    // Stores timestamp of each sent message; used to compute
    // msgs/sec in the last 1 second. Old entries (>60s) are
    // cleaned up regularly in onMessageSent().
    @Builder.Default
    private ConcurrentLinkedQueue<Long> messageTimestamps = new ConcurrentLinkedQueue<>();

    // ── Phase timings — cumulative ms spent in each phase ─────
    // AtomicLong so multiple virtual threads can addAndGet safely
    @Builder.Default
    private AtomicLong dbFetchTimeMs = new AtomicLong(0);
    @Builder.Default
    private AtomicLong redisOpsTimeMs = new AtomicLong(0);
    @Builder.Default
    private AtomicLong whatsappApiTimeMs = new AtomicLong(0);
    @Builder.Default
    private AtomicLong semaphoreWaitTimeMs = new AtomicLong(0);
    @Builder.Default
    private AtomicLong rabbitPublishTimeMs = new AtomicLong(0);

    // ── Status ────────────────────────────────────────────────
    private volatile String status;

    // ── Static factory method ─────────────────────────────────

    /**
     * Creates a new record with all Atomic fields properly initialised to 0,
     * status = "RUNNING", startTime = now.
     */
    public static CampaignBenchmarkRecord create(Long campaignId, int totalContacts) {
        return CampaignBenchmarkRecord.builder()
                .campaignId(campaignId)
                .totalContacts(totalContacts)
                .startTime(System.currentTimeMillis())
                .processedContacts(new AtomicInteger(0))
                .sentMessages(new AtomicInteger(0))
                .failedContacts(new AtomicInteger(0))
                .messageTimestamps(new ConcurrentLinkedQueue<>())
                .dbFetchTimeMs(new AtomicLong(0))
                .redisOpsTimeMs(new AtomicLong(0))
                .whatsappApiTimeMs(new AtomicLong(0))
                .semaphoreWaitTimeMs(new AtomicLong(0))
                .rabbitPublishTimeMs(new AtomicLong(0))
                .status("RUNNING")
                .build();
    }
}
