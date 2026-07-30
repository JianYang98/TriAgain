package com.triagain.common.api;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triagain.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HealthController {

	private final DataSource dataSource;

	@GetMapping("/health")
	public ResponseEntity<ApiResponse<HealthResponse>> health() {
		String dbStatus = checkDatabase();
		return ResponseEntity.ok(ApiResponse.ok(new HealthResponse("UP", dbStatus)));
	}

	private String checkDatabase() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(1) ? "UP" : "DOWN";
		} catch (Exception e) {
			return "DOWN";
		}
	}
}
