package com.recovermandate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service to manage Server-Sent Events (SSE) subscribers and broadcast live platform updates.
 */
@Slf4j
@Service
public class SseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers a new SSE emitter subscriber and binds lifecycle cleanup hooks.
     *
     * @param emitter the client SseEmitter
     */
    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed");
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out");
            emitters.remove(emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE emitter error: {}", e.getMessage());
            emitters.remove(emitter);
        });
        log.info("New SSE client connected. Active subscribers: {}", emitters.size());
    }

    /**
     * Broadcasts an event with JSON payload to all active SSE subscribers.
     *
     * @param eventType name of the event (e.g. webhook.received, draft.generated)
     * @param data      event payload data object
     */
    public void broadcast(String eventType, Object data) {
        if (emitters.isEmpty()) {
            return;
        }

        log.debug("Broadcasting SSE event '{}' to {} subscribers", eventType, emitters.size());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.debug("Failed to deliver SSE event to subscriber, removing emitter: {}", e.getMessage());
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Returns count of currently active subscribers.
     */
    public int getActiveSubscriberCount() {
        return emitters.size();
    }
}
