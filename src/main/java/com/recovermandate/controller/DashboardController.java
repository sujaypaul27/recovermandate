package com.recovermandate.controller;

import com.recovermandate.dto.DashboardSummaryResponse;
import com.recovermandate.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing real-time operational metrics and CSV ledger exports
 * for dashboard visualization and revenue recovery auditing.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
            @org.springframework.web.bind.annotation.RequestParam(name = "includeDemo", required = false, defaultValue = "false") boolean includeDemo) {
        return dashboardService.getSummary(includeDemo);
    }

    @GetMapping(value = "/export-csv", produces = "text/csv; charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> exportCsv() {
        byte[] csvData = dashboardService.exportRecoveryLedgerCsv();
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"recovermandate-recovery-ledger.csv\"")
                .body(csvData);
    }
}
