package com.shieldudaram.fightcaves.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonIo {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonIo() {
    }

    public static <T> T read(Path path, Class<T> type) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void write(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        }
    }
}
