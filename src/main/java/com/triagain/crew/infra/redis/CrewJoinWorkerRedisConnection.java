package com.triagain.crew.infra.redis;

import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.lettuce.core.ClientOptions;

/** worker 전용 클라이언트의 수명 소유 — Boot producer factory를 대체하는 bean으로 등록하지 않는다. */
final class CrewJoinWorkerRedisConnection implements AutoCloseable {

	private final LettuceConnectionFactory factory;

	CrewJoinWorkerRedisConnection(LettuceConnectionFactory producer) {
		factory = new LettuceConnectionFactory(producer.getStandaloneConfiguration(),
			workerConfiguration(producer.getClientConfiguration()));
		factory.afterPropertiesSet();
	}

	StringRedisTemplate template() {
		return new StringRedisTemplate(factory);
	}

	private static LettuceClientConfiguration workerConfiguration(LettuceClientConfiguration source) {
		ClientOptions options = source.getClientOptions().orElseGet(ClientOptions::create).mutate()
			.autoReconnect(false)
			.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
			.build();
		var builder = LettuceClientConfiguration.builder().clientOptions(options)
			.commandTimeout(source.getCommandTimeout())
			.shutdownTimeout(source.getShutdownTimeout()).shutdownQuietPeriod(source.getShutdownQuietPeriod());
		source.getClientResources().ifPresent(builder::clientResources);
		source.getClientName().ifPresent(builder::clientName);
		source.getReadFrom().ifPresent(builder::readFrom);
		source.getRedisCredentialsProviderFactory().ifPresent(builder::redisCredentialsProviderFactory);
		copySsl(source, builder);
		return builder.build();
	}

	private static void copySsl(LettuceClientConfiguration source, LettuceClientConfigurationBuilder builder) {
		if (source.isUseSsl()) {
			var ssl = builder.useSsl().verifyPeer(source.getVerifyMode());
			if (source.isStartTls()) {
				ssl.startTls();
			}
		}
	}

	/** worker 종료 뒤 전용 연결만 해제 — producer와 공유한 ClientResources는 factory가 소유하지 않음 */
	@Override
	public void close() {
		factory.destroy();
	}
}
