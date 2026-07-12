package com.kuinone.messageapi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.UUID;

public class ApiServerMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("MessageAPI");
	public static MinecraftServer server;
	public static ApiServerConfig config;
	public static long startTime;
	private static com.sun.net.httpserver.HttpServer httpServer;

	@Override
	public void onInitialize() {
		// 加载或创建配置文件
		File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "messageapi.json");
		config = ApiServerConfig.load(configFile);
		if (config.apiKey == null || config.apiKey.isEmpty()) {
			config.apiKey = UUID.randomUUID().toString();
			config.save(configFile);
			LOGGER.info("Generated new API key: {}", config.apiKey);
		}
		if (config.port < 1 || config.port > 65535 || config.apiKey.isEmpty()) {
			config.port = 7789;
			config.save(configFile);
			LOGGER.info("Use default api port {}", config.port);
		}

		startTime = System.currentTimeMillis();

		// 服务端启动完成时启动 HTTP 服务器
		ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
			server = serverInstance;
			try {
				startHttpServer();
			} catch (IOException e) {
				LOGGER.error("Failed to start HTTP server", e);
			}
		});

		// 服务端停止时关闭 HTTP 服务器
		ServerLifecycleEvents.SERVER_STOPPING.register(serverInstance -> stopHttpServer());
	}

	private void startHttpServer() throws IOException {
		httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(config.port), 0);
		httpServer.createContext("/", new ApiHandler());
		httpServer.setExecutor(null); // 使用默认线程池
		httpServer.start();
		LOGGER.info("API server listening on port {}", config.port);
	}

	private void stopHttpServer() {
		if (httpServer != null) {
			httpServer.stop(0);
			LOGGER.info("API server stopped");
		}
	}
}