package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {
    private String id;
    private String type; // PAYMENT_EVENT, AUDIT_LOG, RECOVERY_ACTION
    private String title;
    private String subtitle;
    private String timestamp;
}
