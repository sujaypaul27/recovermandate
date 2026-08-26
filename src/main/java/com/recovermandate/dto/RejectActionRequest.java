package com.recovermandate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectActionRequest {
    
    @NotBlank(message = "Reason is required")
    private String reason;
}
