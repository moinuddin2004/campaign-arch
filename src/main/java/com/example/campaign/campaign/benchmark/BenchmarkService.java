package com.example.campaign.campaign.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service for tracking campaign benchmark metrics.
 * <p>
 * Every public method is null-safe — if the campaignId is not found
 * in the internal map the call is silently ignored (or returns 0).
 */
@Service
@Slf4j
public class BenchmarkService {

    private final ConcurrentHashMap<Long, CampaignBenchmarkRecord> records = new ConcurrentHashMap<>();

    // ─── Lifecycle events ────────────────────────────────────

    public void onCampaignStart(Long campaignId, int totalContacts) {
        CampaignBenchmarkRecord record = CampaignBenchmarkRecord.create(campaignId, totalContacts);
        records.put(campaignId, record);
        log.info("🚀 CAMPAIGN STARTED | ID: {} | Contacts: {}", campaignId, totalContacts);
    }

    public void onCampaignComplete(Long campaignId) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;

        r.setEndTime(System.currentTimeMillis());
        r.setTotalDurationMs(r.getEndTime() - r.getStartTime());
        r.setStatus("COMPLETED");
        printCampaignReport(r);
    }

    public void onCampaignFailed(Long campaignId, String reason) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;

        r.setEndTime(System.currentTimeMillis());
        r.setTotalDurationMs(r.getEndTime() - r.getStartTime());
        r.setStatus("FAILED: " + reason);
        log.error("❌ CAMPAIGN FAILED | ID: {} | Duration: {} ms | Processed: {} | Reason: {}",
                campaignId, r.getTotalDurationMs(), r.getProcessedContacts().get(), reason);
    }

    // ─── Contact-level events ────────────────────────────────

    public void onMessageSent(Long campaignId) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;

        r.getProcessedContacts().incrementAndGet();
        r.getSentMessages().incrementAndGet();

        long now = System.currentTimeMillis();
        r.getMessageTimestamps().add(now);

        // Cleanup entries older than 60 seconds to prevent unbounded growth
        r.getMessageTimestamps().removeIf(t -> now - t > 60_000);
    }

    public void onContactFailed(Long campaignId) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;

        r.getProcessedContacts().incrementAndGet();
        r.getFailedContacts().incrementAndGet();
    }

    // ─── Phase timing recorders ──────────────────────────────

    public void recordDbFetchTime(Long campaignId, long ms) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;
        r.getDbFetchTimeMs().addAndGet(ms);
    }

    public void recordRedisTime(Long campaignId, long ms) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;
        r.getRedisOpsTimeMs().addAndGet(ms);
    }

    public void recordWhatsappApiTime(Long campaignId, long ms) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;
        r.getWhatsappApiTimeMs().addAndGet(ms);
    }

    public void recordSemaphoreWait(Long campaignId, long ms) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;
        r.getSemaphoreWaitTimeMs().addAndGet(ms);
    }

    public void recordRabbitPublishTime(Long campaignId, long ms) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return;
        r.getRabbitPublishTimeMs().addAndGet(ms);
    }

    // ─── Throughput queries ──────────────────────────────────

    /**
     * Returns the number of messages sent in the last 1 second.
     */
    public double getCurrentMsgPerSec(Long campaignId) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return 0;

        long now = System.currentTimeMillis();
        return r.getMessageTimestamps().stream()
                .filter(t -> now - t <= 1000)
                .count();
    }

    /**
     * Average msgs/sec over the entire campaign lifetime so far.
     */
    public double getAvgMsgPerSec(Long campaignId) {
        CampaignBenchmarkRecord r = records.get(campaignId);
        if (r == null) return 0;

        long elapsedMs = System.currentTimeMillis() - r.getStartTime();
        if (elapsedMs <= 0) return 0;
        return (r.getSentMessages().get() * 1000.0) / elapsedMs;
    }

    /**
     * Helper to format milliseconds into "Xm Ys" string.
     */
    public String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        if (min > 0) {
            return String.format("%dm %ds", min, sec);
        } else {
            return String.format("%ds", sec);
        }
    }

    // ─── Reports ─────────────────────────────────────────────

    private void printCampaignReport(CampaignBenchmarkRecord r) {
        long dbMs = r.getDbFetchTimeMs().get();
        long redisMs = r.getRedisOpsTimeMs().get();
        long apiMs = r.getWhatsappApiTimeMs().get();
        long semMs = r.getSemaphoreWaitTimeMs().get();
        long rabbitMs = r.getRabbitPublishTimeMs().get();
        long totalPhaseTime = dbMs + redisMs + apiMs + semMs + rabbitMs;

        double dbPct = totalPhaseTime > 0 ? (dbMs * 100.0) / totalPhaseTime : 0;
        double redisPct = totalPhaseTime > 0 ? (redisMs * 100.0) / totalPhaseTime : 0;
        double apiPct = totalPhaseTime > 0 ? (apiMs * 100.0) / totalPhaseTime : 0;
        double semPct = totalPhaseTime > 0 ? (semMs * 100.0) / totalPhaseTime : 0;
        double rabbitPct = totalPhaseTime > 0 ? (rabbitMs * 100.0) / totalPhaseTime : 0;

        int sent = r.getSentMessages().get();
        int processed = r.getProcessedContacts().get();
        int failed = r.getFailedContacts().get();
        double successPct = processed > 0 ? (sent * 100.0) / processed : 0;
        double avgMsgPerSec = r.getTotalDurationMs() > 0
                ? (sent * 1000.0) / r.getTotalDurationMs()
                : 0;

        // Determine bottleneck
        String bottleneckPhase = "N/A";
        double bottleneckPct = 0;
        if (totalPhaseTime > 0) {
            if (dbPct >= redisPct && dbPct >= apiPct && dbPct >= semPct && dbPct >= rabbitPct) {
                bottleneckPhase = "DB Fetch"; bottleneckPct = dbPct;
            } else if (redisPct >= dbPct && redisPct >= apiPct && redisPct >= semPct && redisPct >= rabbitPct) {
                bottleneckPhase = "Redis Ops"; bottleneckPct = redisPct;
            } else if (apiPct >= dbPct && apiPct >= redisPct && apiPct >= semPct && apiPct >= rabbitPct) {
                bottleneckPhase = "WhatsApp API"; bottleneckPct = apiPct;
            } else if (semPct >= dbPct && semPct >= redisPct && semPct >= apiPct && semPct >= rabbitPct) {
                bottleneckPhase = "Semaphore Wait"; bottleneckPct = semPct;
            } else {
                bottleneckPhase = "RabbitMQ Publish"; bottleneckPct = rabbitPct;
            }
        }

        String report = """
                
                ╔══════════════════════════════════════════════╗
                ║        CAMPAIGN BENCHMARK REPORT             ║
                ╠══════════════════════════════════════════════╣
                ║ Campaign : %d
                ║ Status   : %s
                ╠══════════════════════════════════════════════╣
                ║ TIMING
                ║ Total Duration   : %d ms (%s)
                ║ Avg Msg/sec      : %.2f
                ╠══════════════════════════════════════════════╣
                ║ CONTACTS
                ║ Total    : %d
                ║ Processed: %d
                ║ Failed   : %d
                ║ Success%% : %.1f%%
                ╠══════════════════════════════════════════════╣
                ║ PHASE BREAKDOWN
                ║ DB Fetch        : %d ms  (%.1f%%)
                ║ Redis Ops       : %d ms  (%.1f%%)
                ║ WhatsApp API    : %d ms  (%.1f%%)  ← highest %% = bottleneck
                ║ Semaphore Wait  : %d ms  (%.1f%%)
                ║ RabbitMQ Publish: %d ms  (%.1f%%)
                ╚══════════════════════════════════════════════╝
                """.formatted(
                r.getCampaignId(),
                r.getStatus(),
                r.getTotalDurationMs(), formatDuration(r.getTotalDurationMs()),
                avgMsgPerSec,
                r.getTotalContacts(),
                processed,
                failed,
                successPct,
                dbMs, dbPct,
                redisMs, redisPct,
                apiMs, apiPct,
                semMs, semPct,
                rabbitMs, rabbitPct
        );

        log.info(report);
        log.info("⚠️ BOTTLENECK: {} is taking {}% of time", bottleneckPhase, String.format("%.1f", bottleneckPct));
    }

    /**
     * Prints a global report comparing all tracked campaigns.
     * Should be called after ALL campaigns complete.
     */
    public void printGlobalReport() {
        if (records.isEmpty()) {
            log.info("📊 No benchmark records available.");
            return;
        }

        List<CampaignBenchmarkRecord> sorted = records.values().stream()
                .sorted(Comparator.comparingLong(CampaignBenchmarkRecord::getTotalDurationMs).reversed())
                .toList();

        StringBuilder table = new StringBuilder();
        table.append("\n╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        table.append("║                              GLOBAL BENCHMARK REPORT                                              ║\n");
        table.append("╠════════════╦═══════════════╦════════════╦═══════════╦════════╦════════════════╣\n");
        table.append("║ CampaignID ║ Duration      ║ AvgMsg/sec ║ Processed ║ Failed ║ Status         ║\n");
        table.append("╠════════════╬═══════════════╬════════════╬═══════════╬════════╬════════════════╣\n");

        for (CampaignBenchmarkRecord r : sorted) {
            double durationSec = r.getTotalDurationMs() / 1000.0;
            double avgMps = r.getTotalDurationMs() > 0
                    ? (r.getSentMessages().get() * 1000.0) / r.getTotalDurationMs()
                    : 0;
            String status = r.getStatus();
            if (status != null && status.length() > 14) status = status.substring(0, 14);

            table.append(String.format("║ %-10d ║ %13s ║ %10.2f ║ %9d ║ %6d ║ %-14s ║%n",
                    r.getCampaignId(),
                    formatDuration(r.getTotalDurationMs()),
                    avgMps,
                    r.getProcessedContacts().get(),
                    r.getFailedContacts().get(),
                    status != null ? status : "N/A"));
        }
        table.append("╚════════════╩═══════════════╩════════════╩═══════════╩════════╩════════════════╝\n");

        log.info(table.toString());

        // Slowest / Fastest
        CampaignBenchmarkRecord slowest = sorted.getFirst();
        CampaignBenchmarkRecord fastest = sorted.getLast();

        log.info("🐌 SLOWEST: {} — {}", slowest.getCampaignId(), formatDuration(slowest.getTotalDurationMs()));
        log.info("⚡ FASTEST: {} — {}", fastest.getCampaignId(), formatDuration(fastest.getTotalDurationMs()));

        // Overall throughput
        int totalMsgs = records.values().stream()
                .mapToInt(r -> r.getSentMessages().get())
                .sum();
        double maxDurationSec = slowest.getTotalDurationMs() / 1000.0;
        double overallThroughput = maxDurationSec > 0 ? totalMsgs / maxDurationSec : 0;

        log.info("📊 OVERALL THROUGHPUT: {} msgs in {} = {} msgs/sec",
                totalMsgs, formatDuration(slowest.getTotalDurationMs()), String.format("%.1f", overallThroughput));
    }

    // ─── Accessor ────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all benchmark records.
     * Used by the REST controller for live monitoring.
     */
    public Map<Long, CampaignBenchmarkRecord> getAllRecords() {
        return Collections.unmodifiableMap(records);
    }
}
