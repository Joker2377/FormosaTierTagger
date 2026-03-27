package com.tiers.profile.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiers.misc.Mode;
import com.tiers.profile.GameMode;
import com.tiers.profile.Status;
import net.minecraft.resources.ResourceLocation;

import static com.tiers.TiersClient.updateAllTags;

public class FormosaProfile extends SuperProfile {
    public static final ResourceLocation FORMOSA_IMAGE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/formosa_logo.png");

    public FormosaProfile() {
        super();
        addGamemodes();
    }

    private void addGamemodes() {
        gameModes.add(new GameMode(Mode.FORMOSA_CRYSTAL, "crystal"));
        gameModes.add(new GameMode(Mode.FORMOSA_SWORD, "sword"));
        gameModes.add(new GameMode(Mode.FORMOSA_UHC, "uhc"));
        gameModes.add(new GameMode(Mode.FORMOSA_POT, "pot"));
        gameModes.add(new GameMode(Mode.FORMOSA_NETH_POT, "neth_pot"));
        gameModes.add(new GameMode(Mode.FORMOSA_SMP, "smp"));
        gameModes.add(new GameMode(Mode.FORMOSA_AXE, "axe"));
        gameModes.add(new GameMode(Mode.FORMOSA_MACE, "mace"));
    }

    public void parseFormosaArray(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (element.isJsonNull() || !element.isJsonArray()) {
            status = Status.NOT_EXISTING;
            return;
        }

        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            status = Status.NOT_EXISTING;
            return;
        }

        boolean anyFound = false;
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();

            String mode = obj.has("mode") ? obj.get("mode").getAsString() : "";
            String subtier = obj.has("subtier") ? obj.get("subtier").getAsString() : "";
            int tier = obj.has("tier") ? obj.get("tier").getAsInt() : 0;
            String tierRank = obj.has("tier_rank") ? obj.get("tier_rank").getAsString() : "";

            for (GameMode gameMode : gameModes) {
                if (gameMode.parsingName.equalsIgnoreCase(mode)) {
                    gameMode.parseFormosaTier(tierRank, subtier, tier);
                    anyFound = true;
                    break;
                }
            }
        }

        if (!anyFound) {
            status = Status.NOT_EXISTING;
            return;
        }

        // Mark unmatched modes as not existing
        for (GameMode gameMode : gameModes) {
            if (gameMode.status == Status.SEARCHING)
                gameMode.status = Status.NOT_EXISTING;
        }

        highest = getHighestMode();
        status = Status.READY;
        originalJson = json;
        updateAllTags();
    }

    private GameMode getHighestMode() {
        GameMode highest = null;
        int highestPoints = 0;
        for (GameMode gameMode : gameModes) {
            if (gameMode.status == Status.READY && gameMode.getTierPoints(false) > highestPoints) {
                highest = gameMode;
                highestPoints = gameMode.getTierPoints(false);
            }
        }
        return highest;
    }
}
