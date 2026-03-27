package com.tiers.textures;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tiers.PlayerProfileQueue;
import com.tiers.TiersClient;
import com.tiers.profile.PlayerProfile;
import com.tiers.screens.ConfigScreen;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.tiers.TiersClient.LOGGER;

public class ColorLoader implements IdentifiableResourceReloadListener {
    public static ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath("minecraft", "colors/pvptiers.json");

    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.parse("tiers:color_loader");
    }

    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull PreparationBarrier preparationBarrier, @NotNull ResourceManager resourceManager, @NotNull Executor backgroundExecutor, @NotNull Executor gameExecutor) {
        if (resourceManager.getResource(identifier).isPresent()) {
            try {
                ColorControl.updateColors(GsonHelper.fromJson(new Gson(), new InputStreamReader(resourceManager.getResource(identifier).get().open(), StandardCharsets.UTF_8), JsonObject.class));
                TiersClient.restyleAllTexts(TiersClient.playerProfiles);
                TiersClient.updateAllTags();
            } catch (IOException ignored) {
                LOGGER.warn("Error loading colors info");
            }
        }

        if (ConfigScreen.ownProfile == null) {
            ConfigScreen.ownProfile = new PlayerProfile(Minecraft.getInstance().getUser().getName(), null, false);
            PlayerProfileQueue.putFirstInQueue(ConfigScreen.ownProfile);

            String defaultProfileMojang = loadStringFromResources("json/defaultProfileMojang.json");
            String defaultProfileFormosa = loadStringFromResources("json/defaultProfileFormosa.json");

            ConfigScreen.defaultProfile = new PlayerProfile(defaultProfileMojang, defaultProfileFormosa);

        } else {
            ArrayList<PlayerProfile> configProfiles = new ArrayList<>();
            configProfiles.add(ConfigScreen.defaultProfile);
            configProfiles.add(ConfigScreen.ownProfile);
            TiersClient.restyleAllTexts(configProfiles);
        }

        return CompletableFuture.runAsync(() -> {}, backgroundExecutor).thenCompose(preparationBarrier::wait).thenRunAsync(() -> {}, gameExecutor);
    }

    private static String loadStringFromResources(String path) {
        try (InputStream inputStream = ColorLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream != null) {
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    StringBuilder stringBuilder = new StringBuilder();
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append(System.lineSeparator());
                    }

                    return stringBuilder.toString();
                }
            }
        } catch (IOException ignored) {
            LOGGER.warn("Error loading default jsons");
        }

        return "";
    }
}