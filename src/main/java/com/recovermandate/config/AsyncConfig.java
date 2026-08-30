package com.recovermandate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring asynchronous task execution for event listeners and background processors.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
