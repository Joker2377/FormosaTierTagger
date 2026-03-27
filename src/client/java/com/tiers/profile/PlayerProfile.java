package com.tiers.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiers.TiersClient;
import com.tiers.misc.Mode;
import com.tiers.profile.types.FormosaProfile;
import com.tiers.profile.types.SuperProfile;
import com.tiers.textures.ColorControl;
import com.tiers.textures.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.tiers.TiersClient.*;

public class PlayerProfile {
    public Status status;
    public int imageSaved;
    public int numberOfImageRequests;
    public String inGameName;
    public boolean nameChanged;

    public String name = "";
    public String uuid = "";

    public FormosaProfile profileFormosa;

    public Component toAppendLeft;
    public Component toAppendRight;
    private Component fullName;
    private Component deepReplaceName;

    private int numberOfRequests;
    private final boolean regular;

    private static final String FORMOSA_API_BASE = "https://formosa-tier-list-database-api.vercel.app/api/player";
    private static final String UUID_API_1 = "https://playerdb.co/api/player/minecraft/";
    private static final String UUID_API_2 = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_API_3 = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static boolean forceNewRequest;

    public PlayerProfile(String name, String uuid, boolean regular) {
        if (name.contains("-force")) {
            String[] content = name.split("-");
            if (content.length == 2) {
                name = content[0];
                forceNewRequest = true;
            }
        }

        this.regular = regular;
        this.name = name;
        this.uuid = uuid != null ? uuid : "";
        inGameName = name;

        status = !name.matches("^[a-zA-Z0-9_]{3,16}$") ? Status.NOT_PLAYER : Status.SEARCHING;
    }

    // Constructor for default profile in config screen
    public PlayerProfile(String mojangJson, String formosaJson) {
        regular = false;

        if (JsonParser.parseString(mojangJson).isJsonNull()) {
            status = Status.API_ISSUE;
            return;
        }

        JsonObject jsonObject = JsonParser.parseString(mojangJson).getAsJsonObject();

        if (jsonObject.has("name") && jsonObject.has("id")) {
            name = jsonObject.get("name").getAsString();
            uuid = jsonObject.get("id").getAsString();
        } else {
            status = Status.NOT_EXISTING;
            return;
        }

        Path path = FabricLoader.getInstance().getGameDir().resolve("cache/tiers/06ec3577329945fabbdf613b1f86c8ab.png");

        try (InputStream inputStream = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/default.png")).orElseThrow().open()) {
            Files.createDirectories(path.getParent());
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            LOGGER.warn("Error copying default skin");
        }

        profileFormosa = new FormosaProfile();
        profileFormosa.parseFormosaArray(formosaJson);

        updateAppendingText();

        status = Status.READY;
    }

    public void buildRequest() {
        if (status != Status.SEARCHING)
            return;

        if (!uuid.isEmpty()) {
            buildFormosaRequestByUuid(uuid);
        } else {
            buildFormosaRequestByName(name);
        }
    }

    /**
     * Primary method: Query Formosa API by UUID.
     * Currently the UUID endpoint is not yet implemented in the API,
     * so this will fall back to name-based query on failure.
     */
    private void buildFormosaRequestByUuid(String playerUuid) {
        if (numberOfRequests >= 10 || status != Status.SEARCHING) {
            status = Status.TIMEOUTED;
            return;
        }

        numberOfRequests++;

        String url = FORMOSA_API_BASE + "?uuid=" + playerUuid;
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    parseFormosaResponse(response.body());
                } else {
                    // UUID endpoint not available yet, fall back to name
                    buildFormosaRequestByName(name);
                }
            }).exceptionally(ignored -> {
                // Network error, fall back to name
                buildFormosaRequestByName(name);
                return null;
            });
        }
    }

    /**
     * Fallback method: Query Formosa API by player name.
     * Currently the primary working method.
     */
    private void buildFormosaRequestByName(String playerName) {
        if (numberOfRequests >= 10 || status != Status.SEARCHING) {
            status = Status.TIMEOUTED;
            return;
        }

        numberOfRequests++;

        String url = FORMOSA_API_BASE + "?name=" + playerName;
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                int statusCode = response.statusCode();

                if (statusCode == 404 || statusCode == 400) {
                    status = Status.NOT_EXISTING;
                    return;
                } else if (statusCode != 200) {
                    status = Status.API_ISSUE;
                    return;
                }

                parseFormosaResponse(response.body());
            }).exceptionally(ignored -> {
                CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> buildFormosaRequestByName(playerName));
                return null;
            });
        }
    }

    private void parseFormosaResponse(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (element.isJsonNull()) {
            status = Status.NOT_EXISTING;
            return;
        }

        // Handle both array and non-array responses
        JsonArray array;
        if (element.isJsonArray()) {
            array = element.getAsJsonArray();
        } else {
            status = Status.API_ISSUE;
            return;
        }

        if (array.isEmpty()) {
            status = Status.NOT_EXISTING;
            return;
        }

        // Extract UUID and name from first entry
        JsonObject first = array.get(0).getAsJsonObject();
        if (first.has("uuid") && uuid.isEmpty()) {
            uuid = first.get("uuid").getAsString();
        }
        if (first.has("player")) {
            String apiName = first.get("player").getAsString();
            if (!apiName.equalsIgnoreCase(name) && !apiName.equalsIgnoreCase(inGameName)) {
                nameChanged = true;
            }
            name = apiName;
        }

        if (!regular)
            savePlayerImage();

        // Create and populate FormosaProfile
        profileFormosa = new FormosaProfile();
        profileFormosa.parseFormosaArray(json);

        updateAppendingText();

        status = Status.READY;
    }

    public void updateTierlistProfiles() {
        new Thread(() -> {
            String extra = forceNewRequest ? "&t=" + System.currentTimeMillis() : "";
            forceNewRequest = false;

            numberOfRequests = 0;
            profileFormosa = null;

            if (!uuid.isEmpty()) {
                // Try UUID first when refreshing
                String url = FORMOSA_API_BASE + "?uuid=" + uuid + extra;
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", userAgent)
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();

                try (HttpClient httpClient = HttpClient.newHttpClient()) {
                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        profileFormosa = new FormosaProfile();
                        profileFormosa.parseFormosaArray(response.body());
                        updateAppendingText();
                        return;
                    }
                } catch (Exception ignored) {
                    // Fall through to name-based
                }
            }

            // Fallback to name
            String nameToUse = !inGameName.isEmpty() ? inGameName : name;
            String url = FORMOSA_API_BASE + "?name=" + nameToUse + extra;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    profileFormosa = new FormosaProfile();
                    profileFormosa.parseFormosaArray(response.body());
                }
            } catch (Exception ignored) {
                // Leave profileFormosa as null
            }

            updateAppendingText();
            TiersClient.showUpdatedPlayerProfile(this, false);
        }).start();
    }

    public void savePlayerImage() {
        String apiUrl = "https://mc-heads.net/body/";

        if (numberOfImageRequests == 2)
            apiUrl = "https://visage.surgeplay.com/full/432/";
        else if (numberOfImageRequests == 4)
            apiUrl = "https://render.crafty.gg/3d/full/";
        else if (numberOfImageRequests == 6)
            return;

        final String finalApiUrl = apiUrl + uuid;

        numberOfImageRequests++;

        String path = FabricLoader.getInstance().getGameDir() + "/cache/tiers/" + (regular ? "players/" : "");
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(Paths.get(path));
                URL uri = new URI(finalApiUrl).toURL();
                HttpURLConnection httpURLConnection = (HttpURLConnection) uri.openConnection();
                httpURLConnection.setRequestProperty("User-Agent", userAgent);
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(5000);

                try (InputStream inputStream = httpURLConnection.getInputStream()) {
                    ImageIO.write(ImageIO.read(inputStream), "png", new File(path + uuid + ".png"));
                    imageSaved = numberOfImageRequests;
                }
            } catch (IOException | URISyntaxException ignored) {
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(this::savePlayerImage);
            }
        });
    }

    public void updateAppendingText() {
        toAppendRight = Component.empty();
        toAppendLeft = Component.empty();

        if (positionFormosa == DisplayStatus.RIGHT)
            toAppendRight = updateProfileNameRight(profileFormosa, activeFormosaMode);
        else if (positionFormosa == DisplayStatus.LEFT)
            toAppendLeft = updateProfileNameLeft(profileFormosa, activeFormosaMode);

        updateTextDisplayEntities();
    }

    public Component getFullName() {
        Component playerText = nameChanged ? Component.literal(inGameName + " (AKA " + name + ")") : Component.literal(name);

        if (!toggleMod)
            return playerText;

        updateAppendingText();
        return Component.empty()
                .append(toAppendLeft.copy())
                .append(playerText)
                .append(toAppendRight.copy());
    }

    public Component getFullName(Component original) {
        original = original.copy();

        if (status != Status.READY)
            return original;

        return fullName = Component.empty()
                .append(toAppendLeft.copy())
                .append(original)
                .append(toAppendRight.copy());
    }

    private Component updateProfileNameRight(SuperProfile superProfile, Mode activeMode) {
        MutableComponent returnValue = Component.empty();

        if (superProfile != null && superProfile.status == Status.READY) {
            GameMode shown = superProfile.getGameMode(activeMode);

            if ((shown == null || shown.status == Status.SEARCHING) || (shown.status == Status.NOT_EXISTING && displayMode == ModesTierDisplay.SELECTED))
                return returnValue;

            if (displayMode == ModesTierDisplay.ADAPTIVE_HIGHEST && shown.status == Status.NOT_EXISTING && superProfile.highest != null)
                shown = superProfile.highest;

            if (displayMode == ModesTierDisplay.HIGHEST && superProfile.highest != null && superProfile.highest.getTierPoints(false) > shown.getTierPoints(false))
                shown = superProfile.highest;

            if (shown == null || shown.status != Status.READY)
                return returnValue;

            MutableComponent separator = Component.literal(" | ").setStyle(toggleAdaptiveSeparator ? shown.displayedTier.getStyle() : Style.EMPTY.withColor(ColorControl.getColor("static_separator")));
            returnValue.append(Component.empty().append(separator).append(shown.displayedTier));

            if (toggleIcons)
                returnValue.append(Component.literal(" ").append(shown.gamemode.getIconTag()));
        }
        return returnValue;
    }

    private Component updateProfileNameLeft(SuperProfile superProfile, Mode activeMode) {
        MutableComponent returnValue = Component.empty();

        if (superProfile != null && superProfile.status == Status.READY) {
            GameMode shown = superProfile.getGameMode(activeMode);

            if ((shown == null || shown.status == Status.SEARCHING) || (shown.status == Status.NOT_EXISTING && displayMode == ModesTierDisplay.SELECTED))
                return returnValue;

            if (displayMode == ModesTierDisplay.ADAPTIVE_HIGHEST && shown.status == Status.NOT_EXISTING && superProfile.highest != null)
                shown = superProfile.highest;

            if (displayMode == ModesTierDisplay.HIGHEST && superProfile.highest != null && superProfile.highest.getTierPoints(false) > shown.getTierPoints(false))
                shown = superProfile.highest;

            if (shown == null || shown.status != Status.READY)
                return returnValue;

            MutableComponent separator = Component.literal(" | ").setStyle(toggleAdaptiveSeparator ? shown.displayedTier.getStyle() : Style.EMPTY.withColor(ColorControl.getColor("static_separator")));

            if (toggleIcons)
                returnValue = Component.empty().append(shown.gamemode.getIconTag()).append(" ");
            returnValue.append(Component.empty().append(shown.displayedTier).append(separator));
        }
        return returnValue;
    }

    public void resetDrawnStatus() {
        if (profileFormosa == null)
            return;
        profileFormosa.drawn = false;
        for (GameMode mode : profileFormosa.gameModes)
            mode.drawn = false;
    }

    public boolean isPlayerValid() {
        if (status == Status.NOT_EXISTING) {
            sendMessageToPlayer(Icons.colorText(name + " was not found or isn't a premium account", "red"), false);
            return false;
        } else if (status == Status.NOT_PLAYER) {
            sendMessageToPlayer(Icons.colorText("Not a valid player name", "red"), false);
            return false;
        } else if (status == Status.TIMEOUTED) {
            sendMessageToPlayer(Icons.colorText(name + "'s search was timeouted. Clear cache and retry", "red"), false);
            return false;
        } else if (status == Status.API_ISSUE) {
            sendMessageToPlayer(Icons.colorText(name + "'s search failed: API Issue. Update Tiers or retry in a while", "red"), false);
            return false;
        }
        return true;
    }

    public Component deepReplace(Component original) {
        String targetName = nameChanged ? inGameName : name;

        Style originalStyle = original.getStyle();
        MutableComponent newText;
        ComponentContents content = original.getContents();

    // TODO: Verify for 1.21.1 compatibility - PlainTextContents vs PlainTextContents.LiteralContents
        if (content instanceof PlainTextContents plain) {
            String string = plain.text();

            if (string.contains(targetName)) {
                newText = Component.empty();
                int lastIndex = 0;
                int index;

                while ((index = string.indexOf(targetName, lastIndex)) != -1) {
                    if (index > lastIndex)
                        newText.append(Component.literal(string.substring(lastIndex, index)).setStyle(originalStyle));

                    MutableComponent namePart = Component.literal(targetName).setStyle(originalStyle);
                    newText.append(getFullName(namePart));

                    lastIndex = index + targetName.length();
                }

                if (lastIndex < string.length())
                    newText.append(Component.literal(string.substring(lastIndex)).setStyle(originalStyle));
            } else {
                newText = Component.literal(string).setStyle(originalStyle);
            }
        } else if (content instanceof TranslatableContents translatableTextContent) {
            Object[] args = translatableTextContent.getArgs();
            Object[] newArgs = new Object[args.length];

            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component text)
                    newArgs[i] = deepReplace(text);
                else if (args[i] instanceof String string)
                    newArgs[i] = deepReplace(Component.literal(string).setStyle(originalStyle));
                else
                    newArgs[i] = args[i];
            }
            newText = Component.translatable(translatableTextContent.getKey(), newArgs).setStyle(originalStyle);
        } else {
            newText = original.plainCopy().setStyle(originalStyle);
        }

        for (Component sibling : original.getSiblings())
            newText.append(deepReplace(sibling));

        return deepReplaceName = newText;
    }

    @Override
    public String toString() {
        return name + "'sPlayerProfile{" +
                "\nstatus=" + status +
                "\nimageSaved=" + imageSaved +
                "\nnumberOfImageRequests=" + numberOfImageRequests +
                "\ninGameName=" + (inGameName != null ? inGameName : "null") +
                "\nnameChanged=" + nameChanged +
                "\nuuid=" + (uuid != null ? uuid : "null") +
                "\ntoAppendLeft=" + (toAppendLeft != null ? toAppendLeft.getString() : "null") +
                "\ntoAppendRight=" + (toAppendRight != null ? toAppendRight.getString() : "null") +
                "\nfullName=" + (fullName != null ? fullName.getString() : "null") +
                "\ndeepReplaceName=" + (deepReplaceName != null ? deepReplaceName.getString() : "null") +
                "\nnumberOfRequests=" + numberOfRequests +
                "\nregular=" + regular +
                "\n\nprofileFormosa=" + (profileFormosa != null ? profileFormosa : "null") +
                "}\n\n\n--- NEXT ---\n\n\n";
    }
}