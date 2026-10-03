package com.triagain.crew.infra.redis;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 Redis 응답 한 건을 받은 뒤 클라이언트에 전달하지 않고 TCP를 닫는 테스트 전용 프록시. */
final class RedisReplyDropProxy implements AutoCloseable {

	private final ServerSocket listener = new ServerSocket(0);
	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final List<Socket> sockets = new CopyOnWriteArrayList<>();
	private final AtomicReference<String> dropCommand = new AtomicReference<>();
	private final AtomicReference<Throwable> failure = new AtomicReference<>();
	private final AtomicInteger claims = new AtomicInteger();
	private final AtomicInteger acknowledgements = new AtomicInteger();
	private final AtomicInteger recoveries = new AtomicInteger();
	private final AtomicInteger dropRecoveryAt = new AtomicInteger();
	private final AtomicInteger dropped = new AtomicInteger();

	RedisReplyDropProxy() throws IOException {
		executor.submit(this::accept);
	}

	int port() {
		return listener.getLocalPort();
	}

	void dropNext(String command) {
		dropCommand.set(command);
	}

	/** n번째 LMOVE(복구 명령)의 실제 응답을 버리고 TCP를 닫는다 */
	void dropRecoveryReply(int ordinal) {
		dropRecoveryAt.set(ordinal);
	}

	int recoveries() {
		return recoveries.get();
	}

	int claims() {
		return claims.get();
	}

	int acknowledgements() {
		return acknowledgements.get();
	}

	int dropped() {
		return dropped.get();
	}

	Throwable failure() {
		return failure.get();
	}

	private void accept() {
		while (!listener.isClosed()) {
			try {
				Socket client = listener.accept();
				sockets.add(client);
				executor.submit(() -> forward(client));
			} catch (IOException exception) {
				if (!listener.isClosed()) {
					failure.set(exception);
				}
			}
		}
	}

	private void forward(Socket client) {
		try (client; Socket server = new Socket(RedisTestContainer.getHost(), RedisTestContainer.getPort())) {
			sockets.add(server);
			while (!client.isClosed()) {
				byte[] request = readFrame(client.getInputStream());
				String command = new String(request, StandardCharsets.UTF_8).split("\r\n", 4)[2];
				int recovery = countCommand(command);
				server.getOutputStream().write(request);
				server.getOutputStream().flush();
				byte[] reply = readFrame(server.getInputStream());
				String target = dropCommand.get();
				if (command.equalsIgnoreCase(target) && dropCommand.compareAndSet(target, null)
					|| recovery > 0 && dropRecoveryAt.compareAndSet(recovery, 0)) {
					dropped.incrementAndGet();
					return; // Redis의 실행 완료 응답을 실제로 읽었지만 클라이언트에는 0바이트 전달.
				}
				client.getOutputStream().write(reply);
				client.getOutputStream().flush();
			}
		} catch (EOFException | java.net.SocketException expectedDisconnect) {
			// 클라이언트 연결 반환·응답 유실·fixture 종료의 정상 TCP 단절.
		} catch (IOException | RuntimeException exception) {
			failure.set(exception);
		}
	}

	private int countCommand(String command) {
		if (command.equalsIgnoreCase("BLMOVE")) {
			claims.incrementAndGet();
		} else if (command.equalsIgnoreCase("LREM")) {
			acknowledgements.incrementAndGet();
		} else if (command.equalsIgnoreCase("LMOVE")) {
			return recoveries.incrementAndGet();
		}
		return 0;
	}

	private static byte[] readFrame(InputStream input) throws IOException {
		ByteArrayOutputStream frame = new ByteArrayOutputStream();
		int type = input.read();
		if (type == -1) {
			throw new EOFException();
		}
		frame.write(type);
		String line = readLine(input, frame);
		if (type == '$' || type == '=' || type == '!') {
			int length = Integer.parseInt(line);
			if (length >= 0) {
				byte[] content = input.readNBytes(length + 2);
				if (content.length != length + 2) {
					throw new EOFException();
				}
				frame.write(content);
			}
		} else if (type == '*' || type == '%' || type == '~' || type == '>') {
			int count = Integer.parseInt(line) * (type == '%' ? 2 : 1);
			for (int i = 0; i < count; i++) {
				frame.write(readFrame(input));
			}
		}
		return frame.toByteArray();
	}

	private static String readLine(InputStream input, ByteArrayOutputStream frame) throws IOException {
		StringBuilder line = new StringBuilder();
		int previous = -1;
		while (true) {
			int value = input.read();
			if (value == -1) {
				throw new EOFException();
			}
			frame.write(value);
			if (previous == '\r' && value == '\n') {
				return line.substring(0, line.length() - 1);
			}
			line.append((char)value);
			previous = value;
		}
	}

	/** fixture가 연 모든 TCP 소켓과 전달 스레드를 종료한다. */
	@Override
	public void close() throws IOException {
		listener.close();
		for (Socket socket : sockets) {
			socket.close();
		}
		executor.shutdownNow();
	}
}
