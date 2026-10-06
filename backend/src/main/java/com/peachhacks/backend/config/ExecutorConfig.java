package com.peachhacks.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

	@Bean
	ThreadPoolTaskExecutor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(2000);
		return executor;
	}

	/** Single threaded so campaigns run one at a time and the provider rate limit is respected. */
	@Bean
	ThreadPoolTaskExecutor campaignExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("campaign-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		return executor;
	}

}
