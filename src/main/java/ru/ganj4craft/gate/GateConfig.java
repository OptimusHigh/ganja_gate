package ru.ganj4craft.gate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Конфигурация GanjaGate.
 *
 * Автоматически генерирует whitelist из:
 * 1. Загруженных серверных модов (ModList.get())
 * 2. Всех .jar файлов в mods/ и client_mods/ (включая вложенные JarJar моды)
 *
 * Формат: config/ganjagate.json
 */
public class GateConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("ganjagate.json");

    // Моды, которые ВСЕГДА разрешены (системные/ванильные/библиотеки)
    private static final Set<String> SYSTEM_MODS = Set.of(
        "minecraft", "neoforge", "ganjagate",
        "fml", "forge", "neoforged", "java"
    );

    private static Set<String> whitelist = new HashSet<>();
    private static boolean strictMode = true;
    private static boolean autoUpdateWhitelist = true;
    private static String kickMessage = "§c[GanjaGate] Обнаружены неразрешённые моды!\\n\\n§fЗапрещённые моды:\\n§e{mods}\\n\\n§7Удалите их и перезайдите.";

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject root = GSON.fromJson(json, JsonObject.class);

                strictMode = root.has("strict") ? root.get("strict").getAsBoolean() : true;
                autoUpdateWhitelist = root.has("auto_update_whitelist") ? root.get("auto_update_whitelist").getAsBoolean() : true;
                
                if (root.has("kick_message")) {
                    kickMessage = root.get("kick_message").getAsString();
                }

                if (root.has("whitelist")) {
                    whitelist.clear();
                    for (JsonElement el : root.getAsJsonArray("whitelist")) {
                        whitelist.add(el.getAsString().toLowerCase());
                    }
                }

                GanjaGate.LOGGER.info("[GanjaGate] Config loaded from {}", CONFIG_PATH);
            } catch (Exception e) {
                GanjaGate.LOGGER.error("[GanjaGate] Failed to load config, regenerating", e);
                generateDefault();
            }
        } else {
            generateDefault();
        }

        if (autoUpdateWhitelist) {
            updateWhitelistFromServer();
        }

        whitelist.addAll(SYSTEM_MODS);
    }

    /**
     * Сканирует моды на сервере:
     * 1. Загруженные NeoForge
     * 2. Все jar файлы в папке mods/ (включая client-*.jar и JarJar)
     * 3. Все jar файлы в папке client_mods/
     */
    private static void updateWhitelistFromServer() {
        int before = whitelist.size();

        // 1. Загруженные сервером моды
        ModList.get().getMods().forEach(mod -> {
            whitelist.add(mod.getModId().toLowerCase());
        });

        // 2. Сканируем папку mods/ и client_mods/
        Path gameDir = FMLPaths.GAMEDIR.get();
        scanDirectoryForMods(gameDir.resolve("mods"));
        scanDirectoryForMods(gameDir.resolve("client_mods"));

        int added = whitelist.size() - before;
        if (added > 0) {
            GanjaGate.LOGGER.info("[GanjaGate] Added {} new mods to whitelist (total: {})", added, whitelist.size());
            save();
        }
    }

    private static void scanDirectoryForMods(Path dir) {
        if (!Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jarPath : stream) {
                try {
                    scanJarForMods(jarPath);
                } catch (Exception e) {
                    GanjaGate.LOGGER.debug("[GanjaGate] Could not scan {}: {}", jarPath.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            GanjaGate.LOGGER.warn("[GanjaGate] Failed to scan directory {}: {}", dir, e.getMessage());
        }
    }

    private static void scanJarForMods(Path jarPath) {
        try (InputStream is = Files.newInputStream(jarPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("META-INF/neoforge.mods.toml") || name.equals("META-INF/mods.toml")) {
                    extractModIdsFromToml(zis);
                }
                else if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                    byte[] nestedBytes = readAllBytes(zis);
                    scanNestedJar(nestedBytes);
                }
                else if (name.startsWith("META-INF/jars/") && name.endsWith(".jar")) {
                    byte[] nestedBytes = readAllBytes(zis);
                    scanNestedJar(nestedBytes);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scanNestedJar(byte[] jarBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("META-INF/neoforge.mods.toml") || name.equals("META-INF/mods.toml")) {
                    extractModIdsFromToml(zis);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void extractModIdsFromToml(InputStream is) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("modId") && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String modId = parts[1].trim()
                            .replace("\"", "")
                            .replace("'", "")
                            .toLowerCase();
                        if (!modId.isEmpty() && !modId.contains("${")) {
                            whitelist.add(modId);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static void generateDefault() {
        whitelist.clear();
        whitelist.addAll(SYSTEM_MODS);
        updateWhitelistFromServer();
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("strict", strictMode);
            root.addProperty("auto_update_whitelist", autoUpdateWhitelist);
            root.addProperty("kick_message", kickMessage);

            JsonArray arr = new JsonArray();
            whitelist.stream().sorted().forEach(arr::add);
            root.add("whitelist", arr);

            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
        } catch (IOException e) {
            GanjaGate.LOGGER.error("[GanjaGate] Failed to save config", e);
        }
    }

    public static Set<String> getWhitelist() {
        return Collections.unmodifiableSet(whitelist);
    }

    public static boolean isStrictMode() {
        return strictMode;
    }

    public static String getKickMessage() {
        return kickMessage;
    }

    public static List<String> checkClientMods(Collection<String> clientModIds) {
        List<String> forbidden = new ArrayList<>();
        for (String modId : clientModIds) {
            String lower = modId.toLowerCase();
            if (!whitelist.contains(lower) && !SYSTEM_MODS.contains(lower)) {
                forbidden.add(modId);
            }
        }
        return forbidden;
    }
}
