package com.peachhacks.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

	private static final int SHUTDOWN_WAIT_SECONDS = 20;

	@Bean
	ThreadPoolTaskExecutor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(2000);
		finishQueuedWorkOnShutdown(executor);
		return executor;
	}

	/** Single threaded so campaigns run one at a time and the provider rate limit is respected. */
	@Bean
	ThreadPoolTaskExecutor campaignExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("campaign-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		finishQueuedWorkOnShutdown(executor);
		return executor;
	}

	/**
	 * A redeploy must not drop a confirmation that is already queued. The long runs on the
	 * campaign executor stop themselves after the email in hand when the context closes,
	 * so the wait only has to cover one send.
	 */
	private static void finishQueuedWorkOnShutdown(ThreadPoolTaskExecutor executor) {
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
	}

}
