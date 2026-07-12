package com.kuinone.messageapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;

public class ApiServerConfig {
    public String apiKey;

    public static ApiServerConfig load(File file) {
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                return new Gson().fromJson(reader, ApiServerConfig.class);
            } catch (IOException e) {
                ApiServerMod.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        return new ApiServerConfig();
    }

    public void save(File file) {
        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(this, writer);
        } catch (IOException e) {
            ApiServerMod.LOGGER.error("Failed to save config", e);
        }
    }
}