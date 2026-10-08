package com.peachhacks.backend.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.peachhacks.backend.config.DiscordProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * PeachBot's one standing connection to Discord. Button presses reach the backend over
 * HTTP, but Discord only tells a bot that someone joined the server over its Gateway
 * websocket, so this keeps one open, answers its heartbeats, and hands each new member to
 * DiscordWelcome. It asks for the Server Members intent alone and listens for nothing
 * else. It runs only when a welcome channel is configured.
 *
 * Every step after the socket opens happens on one scheduler thread, so no state here
 * needs locking. A dropped connection is replaced with a fresh one (not resumed): a join
 * that happens in the gap is missed, which costs that person a welcome and nothing more.
 */
@Component
public class DiscordGateway implements SmartLifecycle, WebSocket.Listener {

	private static final int DISPATCH = 0;

	private static final int HEARTBEAT = 1;

	private static final int IDENTIFY = 2;

	private static final int RECONNECT = 7;

	private static final int INVALID_SESSION = 9;

	private static final int HELLO = 10;

	private static final int HEARTBEAT_ACK = 11;

	/** GUILD_MEMBERS, which must also be switched on for the bot in the Developer Portal. */
	private static final int INTENTS = 1 << 1;

	private static final int DISALLOWED_INTENTS = 4014;

	/** Close codes Discord sends when trying again cannot help. */
	private static final Set<Integer> FATAL = Set.of(4004, 4010, 4011, 4012, 4013, DISALLOWED_INTENTS);

	private static final long LONGEST_WAIT_SECONDS = 120;

	private static final Logger log = LoggerFactory.getLogger(DiscordGateway.class);

	private final JsonMapper json = JsonMapper.builder().build();

	private final DiscordProperties properties;

	private final DiscordWelcome welcome;

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final StringBuilder incoming = new StringBuilder();

	private final AtomicBoolean reconnecting = new AtomicBoolean();

	private volatile boolean running;

	private volatile ScheduledExecutorService scheduler;

	private volatile WebSocket socket;

	private ScheduledFuture<?> heartbeat;

	private Integer sequence;

	private boolean acknowledged;

	private int failures;

	public DiscordGateway(DiscordProperties properties, DiscordWelcome welcome) {
		this.properties = properties;
		this.welcome = welcome;
	}

	@Override
	public void start() {
		if (!properties.welcomeConfigured()) {
			return;
		}
		running = true;
		scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "discord-gateway");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.execute(this::connect);
	}

	@Override
	public void stop() {
		running = false;
		WebSocket open = socket;
		if (open != null) {
			open.sendClose(WebSocket.NORMAL_CLOSURE, "");
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void connect() {
		if (!running) {
			return;
		}
		reconnecting.set(false);
		incoming.setLength(0);
		sequence = null;
		try {
			socket = httpClient.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(properties.gatewayUrl() + "/?v=10&encoding=json"), this)
				.get(20, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (Exception ex) {
			log.warn("PeachBot could not reach Discord's gateway: {}", ex.toString());
			reconnect();
		}
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		webSocket.request(1);
	}

	/** A message can arrive in pieces; it is handled once the last piece is in. */
	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		incoming.append(data);
		if (last) {
			String message = incoming.toString();
			incoming.setLength(0);
			submit(() -> handle(webSocket, message));
		}
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		if (FATAL.contains(statusCode)) {
			running = false;
			log.error("PeachBot's gateway connection was refused for good (close code {} {}). {}", statusCode, reason,
					(statusCode == DISALLOWED_INTENTS)
							? "Switch on Server Members Intent for the bot in the Discord Developer Portal, then"
									+ " redeploy."
							: "Check DISCORD_BOT_TOKEN, then redeploy.");
			return null;
		}
		log.info("PeachBot's gateway connection closed ({} {}); reconnecting", statusCode, reason);
		submit(() -> dropped(webSocket));
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		log.info("PeachBot's gateway connection failed ({}); reconnecting", error.toString());
		submit(() -> dropped(webSocket));
	}

	private void handle(WebSocket from, String message) {
		if (from != socket) {
			return;
		}
		JsonNode payload;
		try {
			payload = json.readTree(message);
		}
		catch (JacksonException ex) {
			return;
		}
		if (payload.path("s").isNumber()) {
			sequence = payload.path("s").asInt();
		}
		switch (payload.path("op").asInt(-1)) {
			case HELLO -> {
				beat(payload.path("d").path("heartbeat_interval").asLong(41_250));
				send("{\"op\":" + IDENTIFY + ",\"d\":{\"token\":" + json.writeValueAsString(properties.botToken())
						+ ",\"intents\":" + INTENTS
						+ ",\"properties\":{\"os\":\"linux\",\"browser\":\"peachbot\",\"device\":\"peachbot\"}}}");
			}
			case HEARTBEAT_ACK -> acknowledged = true;
			case HEARTBEAT -> send(heartbeatMessage());
			case RECONNECT, INVALID_SESSION -> dropped(from);
			case DISPATCH -> dispatch(payload.path("t").asString(""), payload.path("d"));
			default -> {
			}
		}
	}

	private void dispatch(String event, JsonNode data) {
		if (event.equals("READY")) {
			failures = 0;
			log.info("PeachBot is connected to Discord's gateway and will welcome new members");
		}
		else if (event.equals("GUILD_MEMBER_ADD") && properties.guildId().equals(data.path("guild_id").asString(""))) {
			JsonNode user = data.path("user");
			if (!user.path("bot").asBoolean(false)) {
				welcome.memberJoined(user.path("id").asString(""), user.path("username").asString(""),
						user.path("global_name").asString(""), user.path("avatar").asString(""));
			}
		}
	}

	/** Discord drops a connection that stops heartbeating, and one that it stops answering is dead. */
	private void beat(long intervalMillis) {
		cancelHeartbeat();
		acknowledged = true;
		heartbeat = scheduler.scheduleAtFixedRate(() -> {
			if (!acknowledged) {
				log.info("PeachBot's gateway stopped answering heartbeats; reconnecting");
				dropped(socket);
				return;
			}
			acknowledged = false;
			send(heartbeatMessage());
		}, intervalMillis / 2, intervalMillis, TimeUnit.MILLISECONDS);
	}

	private String heartbeatMessage() {
		return "{\"op\":" + HEARTBEAT + ",\"d\":" + sequence + "}";
	}

	private void send(String message) {
		WebSocket open = socket;
		if (open == null) {
			return;
		}
		try {
			open.sendText(message, true).get(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		catch (Exception ex) {
			dropped(open);
		}
	}

	private void dropped(WebSocket which) {
		if (which == socket) {
			reconnect();
		}
	}

	/** Waits longer after each failure in a row, so a Discord outage is not hammered. */
	private void reconnect() {
		if (!running || !reconnecting.compareAndSet(false, true)) {
			return;
		}
		cancelHeartbeat();
		WebSocket old = socket;
		socket = null;
		if (old != null) {
			old.abort();
		}
		long wait = Math.min(LONGEST_WAIT_SECONDS, 5L << Math.min(failures, 5));
		failures++;
		scheduler.schedule(this::connect, wait, TimeUnit.SECONDS);
	}

	private void cancelHeartbeat() {
		if (heartbeat != null) {
			heartbeat.cancel(false);
			heartbeat = null;
		}
	}

	private void submit(Runnable task) {
		ScheduledExecutorService executor = scheduler;
		if (running && executor != null && !executor.isShutdown()) {
			executor.execute(task);
		}
	}

}
