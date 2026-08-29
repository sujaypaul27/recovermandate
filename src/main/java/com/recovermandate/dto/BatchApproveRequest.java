package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveRequest {
    private List<Long> actionIds;
    private Long maxAmount; // in paise, e.g. 250000L for ₹2,500
    private String tone;
    private String approvedBy;
}
