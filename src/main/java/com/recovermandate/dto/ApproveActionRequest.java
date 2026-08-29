package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveActionRequest {
    private String tone; // gentle, balanced, urgent
    private String message; // customized text if edited
    private String approvedBy;
}
