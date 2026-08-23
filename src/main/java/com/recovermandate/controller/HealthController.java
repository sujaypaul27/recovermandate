package com.recovermandate.controller;

import com.recovermandate.dto.HealthResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<HealthResponseDto> getHealth() {
        return ResponseEntity.ok(HealthResponseDto.builder()
                .status("UP")
                .service("recovermandate")
                .timestamp(Instant.now())
                .build());
    }
}
