package com.recovermandate.controller;

import com.recovermandate.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Controller to expose real-time Server-Sent Events stream for the RecoverMandate dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    /**
     * Subscribes to live event stream.
     *
     * @return SseEmitter for continuous event streaming
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        // Infinite timeout, reconnect managed on frontend client
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseService.addEmitter(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "message", "SSE stream connected",
                            "timestamp", Instant.now().toString()
                    ), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send initial connected event to SSE subscriber", e);
        }

        return emitter;
    }
}
