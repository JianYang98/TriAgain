package com.triagain.acceptance;

import org.testcontainers.containers.PostgreSQLContainer;

public final class TestContainers {

	private static final PostgreSQLContainer<?> POSTGRES;

	static {
		POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
				.withDatabaseName("triagain_test")
				.withUsername("test")
				.withPassword("test");
		POSTGRES.start();
	}

	private TestContainers() {
	}

	public static String getJdbcUrl() {
		return POSTGRES.getJdbcUrl();
	}

	public static String getUsername() {
		return POSTGRES.getUsername();
	}

	public static String getPassword() {
		return POSTGRES.getPassword();
	}

	/** 준비 스크립트를 docker exec로 이 컨테이너에 붙일 때 쓴다 */
	public static String getContainerId() {
		return POSTGRES.getContainerId();
	}
}
