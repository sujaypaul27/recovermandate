import { useEffect, useRef } from "react";

export type SseEventHandler = (eventType: string, data: any) => void;

/**
 * Custom React hook for connecting to Server-Sent Events (SSE) stream
 * with automatic reconnection logic and event dispatching.
 */
export function useEventSource(url: string, onEvent?: SseEventHandler) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!url) return;

    let eventSource: EventSource | null = null;
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
    let isMounted = true;

    const connect = () => {
      if (!isMounted) return;

      try {
        eventSource = new EventSource(url);

        eventSource.onopen = () => {
          console.log("[SSE] Connected to event stream:", url);
        };

        const handleIncoming = (eventType: string) => (event: MessageEvent) => {
          try {
            const data = JSON.parse(event.data);
            if (onEventRef.current) {
              onEventRef.current(eventType, data);
            }
          } catch {
            if (onEventRef.current) {
              onEventRef.current(eventType, event.data);
            }
          }
        };

        // Standard registered events
        eventSource.addEventListener("connected", handleIncoming("connected"));
        eventSource.addEventListener("webhook.received", handleIncoming("webhook.received"));
        eventSource.addEventListener("classification.complete", handleIncoming("classification.complete"));
        eventSource.addEventListener("draft.generated", handleIncoming("draft.generated"));
        eventSource.addEventListener("action.approved", handleIncoming("action.approved"));
        eventSource.addEventListener("recovery.dispatched", handleIncoming("recovery.dispatched"));
        eventSource.addEventListener("recovery.completed", handleIncoming("recovery.completed"));

        // Fallback default message listener
        eventSource.onmessage = handleIncoming("message");

        eventSource.onerror = (err) => {
          console.warn("[SSE] Stream disconnected, attempting reconnect in 3s...", err);
          if (eventSource) {
            eventSource.close();
            eventSource = null;
          }
          if (isMounted) {
            reconnectTimeout = setTimeout(connect, 3000);
          }
        };
      } catch (e) {
        console.error("[SSE] Connection initialization error:", e);
        if (isMounted) {
          reconnectTimeout = setTimeout(connect, 3000);
        }
      }
    };

    connect();

    return () => {
      isMounted = false;
      if (reconnectTimeout) clearTimeout(reconnectTimeout);
      if (eventSource) {
        eventSource.close();
        console.log("[SSE] Connection closed on unmount.");
      }
    };
  }, [url]);
}
