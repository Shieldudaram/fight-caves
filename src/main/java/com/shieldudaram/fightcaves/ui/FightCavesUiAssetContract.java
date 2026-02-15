package com.shieldudaram.fightcaves.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FightCavesUiAssetContract {
    public static final String HUD_WAVE = "Hud/FightCaves/Wave.ui";

    private static final String UI_ROOT = "Common/UI/Custom/";
    private static final String MANIFEST_RESOURCE = "manifest.json";
    private static final Pattern INCLUDES_ASSET_PACK_PATTERN =
            Pattern.compile("\"IncludesAssetPack\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private static final List<String> REQUIRED_UI_DOCUMENTS = List.of(HUD_WAVE);

    private FightCavesUiAssetContract() {
    }

    public record ValidationResult(boolean manifestIncludesAssetPack, List<String> missingUiDocuments) {
        public boolean ready() {
            return manifestIncludesAssetPack && missingUiDocuments != null && missingUiDocuments.isEmpty();
        }
    }

    public static String toClasspathResourcePath(String uiDocumentPath) {
        if (uiDocumentPath == null) return null;
        return UI_ROOT + uiDocumentPath;
    }

    public static ValidationResult validate(ClassLoader classLoader) {
        boolean includesAssetPack = readIncludesAssetPack(classLoader);
        List<String> missingUiDocuments = new ArrayList<>();
        for (String uiDocument : REQUIRED_UI_DOCUMENTS) {
            if (!hasUiDocument(classLoader, uiDocument)) {
                missingUiDocuments.add(uiDocument);
            }
        }
        return new ValidationResult(includesAssetPack, List.copyOf(missingUiDocuments));
    }

    public static boolean hasUiDocument(ClassLoader classLoader, String uiDocumentPath) {
        if (classLoader == null || uiDocumentPath == null || uiDocumentPath.isBlank()) return false;
        String classpathPath = toClasspathResourcePath(uiDocumentPath);
        return classpathPath != null && classLoader.getResource(classpathPath) != null;
    }

    public static boolean readIncludesAssetPack(ClassLoader classLoader) {
        if (classLoader == null) return false;
        try (InputStream stream = classLoader.getResourceAsStream(MANIFEST_RESOURCE)) {
            if (stream == null) return false;
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = INCLUDES_ASSET_PACK_PATTERN.matcher(json);
            if (!matcher.find()) return false;
            return Boolean.parseBoolean(matcher.group(1).toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
