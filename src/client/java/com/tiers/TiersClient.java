package com.tiers;

import com.mojang.brigadier.context.CommandContext;
import com.tiers.misc.*;
import com.tiers.mixin.client.DataTrackerAccessor;
import com.tiers.mixin.client.TextDisplayAccessor;
import com.tiers.profile.PlayerProfile;
import com.tiers.profile.Status;
import com.tiers.screens.ConfigScreen;
import com.tiers.screens.PlayerSearchResultScreen;
import com.tiers.textures.ColorLoader;
import com.tiers.textures.Icons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.commons.io.FileUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class TiersClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(TiersClient.class);
    public static String userAgent = "Tiers (modrinth.com/mod/tiers)";
    public static final ArrayList<PlayerProfile> playerProfiles = new ArrayList<>();

    public static boolean toggleMod = true;
    public static boolean toggleIcons = true;
    public static boolean toggleTab = true;
    public static boolean toggleChat = true;
    public static boolean toggleAdaptiveSeparator = true;
    public static boolean toggleAutoKitDetect = false;
    public static ModesTierDisplay displayMode = ModesTierDisplay.ADAPTIVE_HIGHEST;
    public static Icons.Type activeIcons = Icons.Type.PVPTIERS;

    public static DisplayStatus positionFormosa = DisplayStatus.LEFT;
    public static Mode activeFormosaMode = Mode.FORMOSA_SWORD;

    public static KeyMapping autoDetectKey;
    public static KeyMapping openClosestPlayerProfile;
    public static KeyMapping cycleRightKey;
    public static KeyMapping cycleLeftKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.loadConfig();
        changeIcons(activeIcons, false);
        clearCache(true);
        CommandRegister.registerCommands();

        Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer("tiers");

        modContainer.ifPresent(tiers -> {
            ResourceManagerHelper.registerBuiltinResourcePack(ResourceLocation.fromNamespaceAndPath("resourcepacks", "tiers-resources"), tiers, ResourcePackActivationType.ALWAYS_ENABLED);
            userAgent += " v" + tiers.getMetadata().getVersion().getFriendlyString();
        });

        String category = "key.categories.tiers";
        autoDetectKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tiers.autodetect", GLFW.GLFW_KEY_Y, category));
        openClosestPlayerProfile = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tiers.closest_player", GLFW.GLFW_KEY_H, category));
        cycleRightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tiers.cycle_right", GLFW.GLFW_KEY_I, category));
        cycleLeftKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tiers.cycle_left", GLFW.GLFW_KEY_U, category));

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ColorLoader());
        ClientTickEvents.END_CLIENT_TICK.register(TiersClient::checkKeys);
        ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
            if (toggleAutoKitDetect)
                InventoryChecker.checkInventory(minecraftClient, false);
        });

        LOGGER.info("Tiers initialized | User agent: {}", userAgent);
    }

    public static PlayerProfile addGetPlayer(String playerName, String uuid, boolean priority) {
        for (PlayerProfile playerProfile : playerProfiles) {
            if (playerProfile.name.equalsIgnoreCase(playerName) || playerProfile.inGameName.equalsIgnoreCase(playerName)) {
                if (priority)
                    PlayerProfileQueue.changeToFirstInQueue(playerProfile);
                return playerProfile;
            }
        }
        PlayerProfile newProfile = new PlayerProfile(playerName, uuid, true);

        if (priority)
            PlayerProfileQueue.putFirstInQueue(newProfile);
        else
            PlayerProfileQueue.enqueue(newProfile);

        playerProfiles.add(newProfile);
        return newProfile;
    }

    public static void updateAllTags() {
        for (PlayerProfile playerProfile : playerProfiles)
            playerProfile.updateAppendingText();

        if (ConfigScreen.ownProfile != null && ConfigScreen.defaultProfile != null) {
            ConfigScreen.ownProfile.updateAppendingText();
            ConfigScreen.defaultProfile.updateAppendingText();
        }
    }

    public static void restyleAllTexts(ArrayList<PlayerProfile> playerProfiles) {
        for (PlayerProfile playerProfile : playerProfiles) {
            if (playerProfile.status == Status.READY) {
                if (playerProfile.profileFormosa != null && playerProfile.profileFormosa.status == Status.READY)
                    playerProfile.profileFormosa.parseFormosaArray(playerProfile.profileFormosa.originalJson);
            }
        }
    }

    public static String getNearestPlayerName() {
        Minecraft minecraftClient = Minecraft.getInstance();
        Player self = minecraftClient.player;
        if (self == null)
            return null;

        Level level = self.level();
        Player playerEntity = level.players().stream()
                .filter(player -> player != self)
                .filter(player -> self.distanceTo(player) < Minecraft.getInstance().options.renderDistance().get() * 16)
                .min(Comparator.comparingDouble(self::distanceTo))
                .orElse(null);

        if (playerEntity != null)
            return playerEntity.getScoreboardName();

        return null;
    }

    private static void checkKeys(Minecraft minecraftClient) {
        if (autoDetectKey.consumeClick())
            InventoryChecker.checkInventory(minecraftClient, true);

        if (openClosestPlayerProfile.consumeClick()) {
            String nearestPlayerName = getNearestPlayerName();
            if (nearestPlayerName != null)
                tiersCommand(nearestPlayerName);
            else
                sendMessageToPlayer(Icons.colorText("No players in render distance", "red"), true);
        }

        if (cycleRightKey.consumeClick()) {
            Component message = cycleRightMode();

            sendMessageToPlayer(message != null ? message : Icons.colorText("There's nothing on the right display", "red"), true);
        }

        if (cycleLeftKey.consumeClick()) {
            Component message = cycleLeftMode();

            sendMessageToPlayer(message != null ? message : Icons.colorText("There's nothing on the left display", "red"), true);
        }
    }

    public static Component cycleRightMode() {
        if (toggleAutoKitDetect) {
            toggleAutoKitDetect = false;
            sendMessageToPlayer(Icons.colorText("Auto kit detect has been disabled due to manual gamemode changes", "red"), false);
        }

        if (positionFormosa.toString().equalsIgnoreCase("RIGHT"))
            return Component.literal("Right (Formosa) is now displaying ").setStyle(Style.EMPTY.withColor(CommonColors.WHITE)).append(cycleFormosaMode());

        return null;
    }

    public static Component cycleLeftMode() {
        if (toggleAutoKitDetect) {
            toggleAutoKitDetect = false;
            sendMessageToPlayer(Icons.colorText("Auto kit detect has been disabled due to manual gamemode changes", "red"), false);
        }

        if (positionFormosa.toString().equalsIgnoreCase("LEFT"))
            return Component.literal("Left (Formosa) is now displaying ").setStyle(Style.EMPTY.withColor(CommonColors.WHITE)).append(cycleFormosaMode());

        return null;
    }

    public static Component getRightIcon() {
        if (positionFormosa.toString().equalsIgnoreCase("RIGHT"))
            return activeFormosaMode.getIcon();

        return Component.empty();
    }

    public static Component getLeftIcon() {
        if (positionFormosa.toString().equalsIgnoreCase("LEFT"))
            return activeFormosaMode.getIcon();

        return Component.empty();
    }

    public static void sendMessageToPlayer(Component message, boolean overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            if (overlay)
                minecraft.player.displayClientMessage(message, true);
            else
                minecraft.player.displayClientMessage(message, false);
        }
    }

    public static void toggleMod(CommandContext<FabricClientCommandSource> ignoredFabricClientCommandSourceCommandContext) {
        toggleMod = !toggleMod;
        ConfigManager.saveConfig();
        sendMessageToPlayer(Icons.colorText("Tiers is now " + (toggleMod ? "enabled" : "disabled"), toggleMod ? "green" : "red"), true);
    }

    public static void toggleMod() {
        toggleMod = !toggleMod;
        ConfigManager.saveConfig();
    }

    public static void toggleIcons() {
        toggleIcons = !toggleIcons;
        ConfigManager.saveConfig();
    }

    public static void toggleTab() {
        toggleTab = !toggleTab;
        ConfigManager.saveConfig();
    }

    public static void toggleChat() {
        toggleChat = !toggleChat;
        ConfigManager.saveConfig();
    }

    public static void toggleAdaptiveSeparator() {
        toggleAdaptiveSeparator = !toggleAdaptiveSeparator;
        ConfigManager.saveConfig();
    }

    public static void toggleAutoKitDetect() {
        toggleAutoKitDetect = !toggleAutoKitDetect;
        ConfigManager.saveConfig();
    }

    public static void tiersCommand(String playerName) {
        if (playerName.equalsIgnoreCase("-toggle"))
            toggleMod(null);
        else if (playerName.equalsIgnoreCase("-config"))
            setScreen(ConfigScreen.getConfigScreen(null));
        else if (playerName.equalsIgnoreCase("-help") || playerName.equalsIgnoreCase("-debug")) {
            sendMessageToPlayer(Icons.colorText("--- Tiers help ---", 0xFFFFFF55), false);
            sendMessageToPlayer(Component.literal("- General contact: ").append(Component.literal("flavio6561 on Discord").withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discordapp.com/users/715189608085716992")))), false);
            sendMessageToPlayer(Component.literal("- Report a bug: ").append(Component.literal("Tiers GitHub issues").withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Joker2377/FormosaTierTagger/issues")))), false);
            sendMessageToPlayer(Component.literal("- It's not advisable to create tickets in PvPTiers support"), false);
            sendMessageToPlayer(Component.literal("- ").append(Component.literal("Changelogs").withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Joker2377/FormosaTierTagger/wiki/Version-changelogs")))), false);
            sendMessageToPlayer(Component.literal("- ").append(Component.literal("GitHub page").withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Joker2377/FormosaTierTagger")))), false);

            String[] debugInfo = getDebugInfo();
            sendMessageToPlayer(Icons.colorText("\n" + debugInfo[0], 0xFFFFFF55), false);
            Minecraft.getInstance().keyboardHandler.setClipboard(debugInfo[1]);

            try (PrintWriter printWriter = new PrintWriter(FabricLoader.getInstance().getGameDir() + "/cache/tiers/debug.log")) {
                printWriter.println("--- Tiers Debug log ---");
                printWriter.println("--- Section 1 ---");
                printWriter.println(debugInfo[0]);
                printWriter.println("--- End of section 1 ---");
                printWriter.println("--- Section 2 ---");
                printWriter.println(debugInfo[1]);
                printWriter.println("--- End of section 2 ---");
                printWriter.println("--- Section 3 ---");
                printWriter.println(debugInfo[2]);
                printWriter.println("--- End of section 3 ---");
                printWriter.println("--- Section 4 ---");
                printWriter.println(debugInfo[3]);
                printWriter.println("--- End ---");
            } catch (IOException ignored) {
                LOGGER.warn("An error occurred while trying to write the debug log");
            }
            sendMessageToPlayer(Icons.colorText("A complete debug log has been copied to the clipboard and saved in your cache folder", "green"), false);
        } else if (playerName.equalsIgnoreCase("-clear")) {
            clearCache(false);
            sendMessageToPlayer(Icons.colorText("Cleared player cache", "green"), true);
        } else if (playerName.startsWith("-")) {
            sendMessageToPlayer(Icons.colorText("Not a valid command. Here's a list of valid commands:", "red"), false);
            sendMessageToPlayer(Icons.colorText("/tiers -toggle", 0xFFFFFF55), false);
            sendMessageToPlayer(Icons.colorText("/tiers -config", 0xFFFFFF55), false);
            sendMessageToPlayer(Icons.colorText("/tiers -help | /tiers -debug", 0xFFFFFF55), false);
            sendMessageToPlayer(Icons.colorText("/tiers -clear", 0xFFFFFF55), false);
        } else {
            PlayerProfile playerProfile = addGetPlayer(playerName, null, true);
            if (playerProfile.isPlayerValid())
                setScreen(new PlayerSearchResultScreen(playerProfile));
        }
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().executeIfPossible(() -> Minecraft.getInstance().setScreen(screen));
    }

    public static String[] getDebugInfo() {
        String[] debugInfo = new String[4];

        StringBuilder fullInfo = new StringBuilder();
        for (PlayerProfile playerProfile : playerProfiles)
            fullInfo.append(playerProfile);

        debugInfo[2] = fullInfo.toString();

        debugInfo[3] = ConfigManager.getCurrentConfig();

        final String[] version = new String[1];
        FabricLoader.getInstance().getModContainer("tiers").ifPresent(tiers -> version[0] = "Tiers version: " + tiers.getMetadata().getVersion().getFriendlyString());
        debugInfo[0] = version[0] + "\n";
        debugInfo[1] = debugInfo[0];
        debugInfo[1] += "Game version: " + Minecraft.getInstance().getLaunchedVersion() + "\n";
        debugInfo[1] += "Game profile name: " + Minecraft.getInstance().getUser().getName() + "\n";
        debugInfo[1] += "OS info:\n\t" + System.getProperty("os.name") + "\n\t" + System.getProperty("os.version") + "\n\t" + System.getProperty("os.arch") + "\n";
        Runtime runtime = Runtime.getRuntime();
        debugInfo[1] += "RAM info (MB):\n\tMax: " + runtime.maxMemory() / (1024 * 1024) + "\n\tTotal: " + runtime.totalMemory() / (1024 * 1024) + "\n\tFree: " + runtime.freeMemory() / (1024 * 1024) + "\n\tIn use: " + (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + "\n";
        debugInfo[1] += "Java version: " + System.getProperty("java.version") + "\n";
        debugInfo[1] += "All Fabric mods: " + FabricLoader.getInstance().getAllMods() + "\n";

        return debugInfo;
    }

    public static void changeIcons(Icons.Type iconType, boolean reload) {
        Icons.identifierFormosa = ResourceLocation.fromNamespaceAndPath("minecraft", "gamemodes/" + iconType.name().toLowerCase(Locale.ROOT));
        Icons.identifierFormosaTags = ResourceLocation.fromNamespaceAndPath("minecraft", "gamemodes/" + iconType.name().toLowerCase(Locale.ROOT) + "-tags");
        ColorLoader.identifier = ResourceLocation.fromNamespaceAndPath("minecraft", "colors/" + iconType.name().toLowerCase(Locale.ROOT) + ".json");

        if (reload)
            Minecraft.getInstance().reloadResourcePacks();

        activeIcons = iconType;
        ConfigManager.saveConfig();
    }

    public static void showUpdatedPlayerProfile(PlayerProfile playerProfile, boolean removeOld) {
        if (removeOld) {
            playerProfiles.remove(playerProfile);
            PlayerProfileQueue.removeFromQueue(playerProfile);
        }

        tiersCommand((playerProfile.nameChanged ? playerProfile.inGameName : playerProfile.name) + (removeOld ? "-force" : ""));
    }

    public static void clearCache(boolean start) {
        playerProfiles.clear();
        PlayerProfileQueue.clearQueue();

        try {
            FileUtils.deleteDirectory(new File(FabricLoader.getInstance().getGameDir() + (start ? "/cache/tiers" : "/cache/tiers/players")));
        } catch (IOException ignored) {
            LOGGER.warn("Error deleting cache folder");
        }

        if (toggleMod && Minecraft.getInstance().level != null)
            for (AbstractClientPlayer playerEntity : Minecraft.getInstance().level.players())
                addGetPlayer(playerEntity.getScoreboardName(), null, false);
    }

    public static void updateTextDisplayEntities() {
        Minecraft minecraftClient = Minecraft.getInstance();
        if (minecraftClient.level == null)
            return;

        for (Entity entity : minecraftClient.level.entitiesForRendering())
            if (entity instanceof Display.TextDisplay textDisplay)
                ((DataTrackerAccessor) textDisplay.getEntityData()).invokeSet(TextDisplayAccessor.getTEXT(), textDisplay.getText());
    }

    public static Component cycleFormosaMode() {
        activeFormosaMode = cycleEnum(activeFormosaMode, Mode.getFormosaValues());
        ConfigManager.saveConfig();
        return activeFormosaMode.getTextLabel();
    }

    public static void cycleDisplayMode() {
        displayMode = cycleEnum(displayMode, ModesTierDisplay.values());
        ConfigManager.saveConfig();
    }

    private static <T extends Enum<T>> T cycleEnum(T current, T[] values) {
        return values[(current.ordinal() + 1) % values.length];
    }

    public enum ModesTierDisplay {
        HIGHEST,
        SELECTED,
        ADAPTIVE_HIGHEST;

        public String getCurrentMode() {
            if (toString().equalsIgnoreCase("HIGHEST"))
                return "Displayed Tiers: Highest";
            else if (toString().equalsIgnoreCase("SELECTED"))
                return "Displayed Tiers: Selected";
            return "Displayed Tiers: Adaptive Highest";
        }
    }

    public enum DisplayStatus {
        RIGHT,
        LEFT,
        OFF
    }
}
