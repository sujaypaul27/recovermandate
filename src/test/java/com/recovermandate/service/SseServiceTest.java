package com.recovermandate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @Test
    @DisplayName("Should add emitter and report active subscriber count")
    void addEmitter_increasesSubscriberCount() {
        assertEquals(0, sseService.getActiveSubscriberCount());

        SseEmitter emitter = new SseEmitter(10000L);
        sseService.addEmitter(emitter);

        assertEquals(1, sseService.getActiveSubscriberCount());
    }

    @Test
    @DisplayName("Should broadcast events without throwing when no subscribers exist")
    void broadcast_noSubscribers_safe() {
        assertDoesNotThrow(() -> sseService.broadcast("webhook.received", Map.of("id", 1L)));
    }

    @Test
    @DisplayName("Should broadcast events to connected subscribers")
    void broadcast_withSubscriber_succeeds() {
        SseEmitter emitter = new SseEmitter(10000L);
        sseService.addEmitter(emitter);

        assertDoesNotThrow(() -> sseService.broadcast("draft.generated", Map.of("actionId", 10L, "status", "DRAFTED")));
    }
}
