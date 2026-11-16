package com.example.campaign.campaign.benchmark;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing real-time benchmark data for
 * active and completed campaigns.
 */
@RestController
@RequestMapping("/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    /**
     * GET /api/benchmark/live
     * Returns live metrics for ALL tracked campaigns.
     */
    @GetMapping("/live")
    public ResponseEntity<List<Map<String, Object>>> getLiveBenchmarks() {
        List<Map<String, Object>> result = new ArrayList<>();

        benchmarkService.getAllRecords().forEach((campaignId, r) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("campaignId", r.getCampaignId());
            map.put("status", r.getStatus());
            map.put("totalContacts", r.getTotalContacts());
            map.put("processed", r.getProcessedContacts().get());
            map.put("failed", r.getFailedContacts().get());

            int total = r.getTotalContacts();
            int processed = r.getProcessedContacts().get();
            double progressPercent = total > 0 ? (processed * 100.0) / total : 0;
            map.put("progressPercent", Math.min(progressPercent, 100.0));

            map.put("currentMsgPerSec", benchmarkService.getCurrentMsgPerSec(campaignId));
            map.put("avgMsgPerSec", benchmarkService.getAvgMsgPerSec(campaignId));

            long elapsedMs = System.currentTimeMillis() - r.getStartTime();
            map.put("elapsedMs", elapsedMs);
            map.put("totalDurationMs", r.getTotalDurationMs());
            map.put("formattedDuration", benchmarkService.formatDuration(r.getTotalDurationMs() > 0 ? r.getTotalDurationMs() : elapsedMs));

            // Phase breakdown
            Map<String, Long> phases = new LinkedHashMap<>();
            phases.put("dbFetchMs", r.getDbFetchTimeMs().get());
            phases.put("redisOpsMs", r.getRedisOpsTimeMs().get());
            phases.put("whatsappApiMs", r.getWhatsappApiTimeMs().get());
            phases.put("semaphoreWaitMs", r.getSemaphoreWaitTimeMs().get());
            phases.put("rabbitPublishMs", r.getRabbitPublishTimeMs().get());
            map.put("phases", phases);

            result.add(map);
        });

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/benchmark/report
     * Triggers the global report to be printed to application logs.
     */
    @PostMapping("/report")
    public ResponseEntity<String> triggerGlobalReport() {
        benchmarkService.printGlobalReport();
        return ResponseEntity.ok("Global report printed to logs");
    }

    /**
     * GET /api/benchmark/campaign/{campaignId}
     * Returns benchmark metrics for a single campaign.
     */
    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<Map<String, Object>> getCampaignBenchmark(@PathVariable Long campaignId) {
        CampaignBenchmarkRecord r = benchmarkService.getAllRecords().get(campaignId);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("campaignId", r.getCampaignId());
        map.put("status", r.getStatus());
        map.put("totalContacts", r.getTotalContacts());
        map.put("processed", r.getProcessedContacts().get());
        map.put("failed", r.getFailedContacts().get());

        int total = r.getTotalContacts();
        int processed = r.getProcessedContacts().get();
        double progressPercent = total > 0 ? (processed * 100.0) / total : 0;
        map.put("progressPercent", Math.min(progressPercent, 100.0));

        map.put("currentMsgPerSec", benchmarkService.getCurrentMsgPerSec(campaignId));
        map.put("avgMsgPerSec", benchmarkService.getAvgMsgPerSec(campaignId));

        long elapsedMs = System.currentTimeMillis() - r.getStartTime();
        map.put("elapsedMs", elapsedMs);
        map.put("totalDurationMs", r.getTotalDurationMs());
        map.put("formattedDuration", benchmarkService.formatDuration(r.getTotalDurationMs() > 0 ? r.getTotalDurationMs() : elapsedMs));

        Map<String, Long> phases = new LinkedHashMap<>();
        phases.put("dbFetchMs", r.getDbFetchTimeMs().get());
        phases.put("redisOpsMs", r.getRedisOpsTimeMs().get());
        phases.put("whatsappApiMs", r.getWhatsappApiTimeMs().get());
        phases.put("semaphoreWaitMs", r.getSemaphoreWaitTimeMs().get());
        phases.put("rabbitPublishMs", r.getRabbitPublishTimeMs().get());
        map.put("phases", phases);

        return ResponseEntity.ok(map);
    }
}
