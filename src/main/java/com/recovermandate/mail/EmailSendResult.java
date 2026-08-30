package com.recovermandate.mail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Encapsulates the execution result of an email dispatch attempt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendResult {

    public enum Status {
        REAL_SENT,
        SIMULATED,
        FAILED
    }

    private Status status;
    private String providerMessageId;
    private String errorMessage;

    public boolean isSuccess() {
        return status == Status.REAL_SENT || status == Status.SIMULATED;
    }

    public boolean isRealSent() {
        return status == Status.REAL_SENT;
    }

    public boolean isSimulated() {
        return status == Status.SIMULATED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public static EmailSendResult realSent(String messageId) {
        return EmailSendResult.builder()
                .status(Status.REAL_SENT)
                .providerMessageId(messageId)
                .build();
    }

    public static EmailSendResult simulated(String messageId) {
        return EmailSendResult.builder()
                .status(Status.SIMULATED)
                .providerMessageId(messageId)
                .build();
    }

    public static EmailSendResult failed(String errorMessage) {
        return EmailSendResult.builder()
                .status(Status.FAILED)
                .errorMessage(errorMessage)
                .build();
    }
}
