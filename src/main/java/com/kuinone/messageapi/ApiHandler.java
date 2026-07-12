package com.kuinone.messageapi;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.ParseResults;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiHandler implements HttpHandler {
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // ---- 处理 GET /info ----
            if ("GET".equalsIgnoreCase(method) && "/info".equals(path)) {
                String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (apiKey == null || !apiKey.equals(ApiServerMod.config.apiKey)) {
                    sendResponse(exchange, 401, error("Invalid or missing API Key"));
                    return;
                }
                handleInfo(exchange);
                return;
            }

            // ---- 其余必须是 POST ----
            if (!"POST".equalsIgnoreCase(method)) {
                sendResponse(exchange, 405, error("Method not allowed"));
                return;
            }
            Map<String, Object> body;
            try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                body = gson.fromJson(reader, Map.class);
            } catch (JsonSyntaxException e) {
                sendResponse(exchange, 400, error("Invalid JSON"));
                return;
            }

            switch (path) {
                case "/send/all":
                    handleSendAll(body, exchange);
                    break;
                case "/send/user":
                    handleSendUser(body, exchange);
                    break;
                case "/command":
                    handleCommand(body, exchange);
                    break;
                default:
                    sendResponse(exchange, 404, error("Not found"));
            }
        } catch (Exception e) {
            ApiServerMod.LOGGER.error("Error handling request", e);
            sendResponse(exchange, 500, error("Internal server error"));
        }
    }

    // ---------- 各端点处理 ----------

    private void handleSendAll(Map<String, Object> body, HttpExchange exchange) throws IOException {
        String message = getString(body, "message");
        if (message == null || message.isEmpty()) {
            sendResponse(exchange, 400, error("Missing 'message' field"));
            return;
        }
        final String finalMessage = format(message);
        ApiServerMod.server.execute(() -> {
            Text text = Text.literal(finalMessage);
            for (ServerPlayerEntity player : ApiServerMod.server.getPlayerManager().getPlayerList()) {
                player.sendMessage(text, false);
            }
        });
        sendResponse(exchange, 200, success("Message sent to all players"));
    }

    private void handleSendUser(Map<String, Object> body, HttpExchange exchange) throws IOException {
        String message = getString(body, "message");
        String playerName = getString(body, "player");
        if (message == null || message.isEmpty()) {
            sendResponse(exchange, 400, error("Missing 'message' field"));
            return;
        }
        if (playerName == null || playerName.isEmpty()) {
            sendResponse(exchange, 400, error("Missing 'player' field"));
            return;
        }
        final String finalMessage = format(message);
        final String finalPlayer = playerName;
        // 先检查玩家是否存在（在 API 线程中粗略检查）
        ServerPlayerEntity target = ApiServerMod.server.getPlayerManager().getPlayer(finalPlayer);
        if (target == null) {
            sendResponse(exchange, 400, error("Player not found"));
            return;
        }
        ApiServerMod.server.execute(() -> {
            ServerPlayerEntity player = ApiServerMod.server.getPlayerManager().getPlayer(finalPlayer);
            if (player != null) {
                player.sendMessage(Text.literal(finalMessage), false);
            }
        });
        sendResponse(exchange, 200, success("Message sent to player " + finalPlayer));
    }
    private void handleCommand(Map<String, Object> body, HttpExchange exchange) throws IOException {
        String command = getString(body, "command");
        if (command == null || command.isEmpty()) {
            sendResponse(exchange, 400, error("Missing 'command' field"));
            return;
        }
        final String finalCommand = command;
        ApiServerMod.server.execute(() -> {
            CommandManager commandManager = ApiServerMod.server.getCommandManager();
            ServerCommandSource source = ApiServerMod.server.getCommandSource();
            ParseResults<ServerCommandSource> parseResults = commandManager.getDispatcher().parse(finalCommand, source);
            commandManager.execute(parseResults, finalCommand);
        });
        sendResponse(exchange, 200, success("Command executed"));
    }

    private void handleInfo(HttpExchange exchange) throws IOException {
        MinecraftServer server = ApiServerMod.server;

        // ----- 玩家信息 -----
        int online = server.getPlayerManager().getPlayerList().size();
        List<String> playerNames = server.getPlayerManager().getPlayerList().stream()
                .map(p -> p.getName().getString())
                .collect(Collectors.toList());
        int maxPlayers = server.getPlayerManager().getMaxPlayerCount();

        // ----- TPS 计算 -----
        // getAverageTickTime() 返回平均 tick 耗时（毫秒）
        double avgTickTime = server.getAverageTickTime();
        double tps = avgTickTime > 0 ? 1000.0 / avgTickTime : 20.0;
        if (tps > 20.0) tps = 20.0; // 上限为 20

        // ----- 内存信息 -----
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // ----- 服务器运行时间（需要在启动时记录 startTime）-----
        long uptime = System.currentTimeMillis() - ApiServerMod.startTime;

        // ----- 组装响应 -----
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("online", online);
        response.put("maxPlayers", maxPlayers);
        response.put("playerList", playerNames);
        response.put("tps", Math.round(tps * 100.0) / 100.0); // 保留两位小数
        response.put("mspt", avgTickTime);
        response.put("uptime", uptime);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("maxBytes", maxMemory);
        memory.put("totalBytes", totalMemory);
        memory.put("freeBytes", freeMemory);
        memory.put("usedBytes", usedMemory);
        response.put("memory", memory);

        // 可选的系统负载（CPU）——需要 ManagementFactory 支持
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                response.put("systemLoad", sunOsBean.getSystemLoadAverage());
                response.put("cpuUsage", sunOsBean.getProcessCpuLoad());
            }
        } catch (Exception e) {
            ApiServerMod.LOGGER.error("Error collecting advanced info", e);
            //sendResponse(exchange, 500, error("Internal server error"));
        }

        sendResponse(exchange, 200, response);
    }

    // ---------- 工具方法 ----------

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }

    // 将 & 替换为 § 以支持颜色代码，同时保留 §
    private String format(String input) {
        return input.replace('&', '§');
    }

    private Map<String, Object> success(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        return resp;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("message", message);
        return resp;
    }

    private void sendResponse(HttpExchange exchange, int status, Map<String, Object> data) throws IOException {
        byte[] bytes = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}