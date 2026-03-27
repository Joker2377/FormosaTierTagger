package com.tiers.screens;

import com.mojang.blaze3d.platform.NativeImage;
import com.tiers.TiersClient;
import com.tiers.profile.types.FormosaProfile;
import com.tiers.textures.ColorControl;
import com.tiers.textures.Icons;
import com.tiers.profile.GameMode;
import com.tiers.profile.PlayerProfile;
import com.tiers.profile.Status;
import com.tiers.profile.types.SuperProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;

public class PlayerSearchResultScreen extends Screen {
    private final PlayerProfile playerProfile;
    private final ResourceLocation playerAvatarTexture = ResourceLocation.parse("");

    Button dimensionsWarning;

    private int separator;
    private boolean small;
    private boolean tooSmall;
    private boolean imageReady;
    private boolean toastShown;

    public PlayerSearchResultScreen(PlayerProfile playerProfile) {
        super(Component.literal(playerProfile.name));
        this.playerProfile = playerProfile;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!playerProfile.isPlayerValid()) {
            onClose();
            return;
        }

        if (playerProfile.nameChanged && !toastShown) {
            SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.literal("Recent name change"), Component.literal("(" + playerProfile.name + " to " + playerProfile.inGameName + ") Data should be accurate"));
            toastShown = true;
        }

        int centerX = width / 2;
        int listY = (int) (height / 2.65);
        separator = height / 23;
        small = width < 575 || height < 420;
        tooSmall = width < 430 || height < 262;
        int firstListX = (int) (centerX - width / 3.5) - 25;
        int thirdListX = (int) (centerX + width / 3.5) + 25;
        int avatarY = height / 55 + 12;

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        dimensionsWarning.visible = small;
        if (tooSmall) {
            dimensionsWarning.setMessage(Component.literal("⚠"));
            dimensionsWarning.setTooltip(Tooltip.create(Component.literal("Your window dimensions (" + width + "x" + height + ") are too small\nLower the GUI scale or make the window bigger! (min: 430x262)")));
        }

        if (playerProfile.status == Status.SEARCHING) {
            guiGraphics.drawCenteredString(font, Component.literal("Searching for " + playerProfile.name + "..."), centerX, listY, ColorControl.getColorMinecraftStandard("green"));
            return;
        }

        if (playerProfile.numberOfImageRequests == 0)
            playerProfile.savePlayerImage();

        drawPlayerAvatar(guiGraphics, centerX, avatarY);
        if (!imageReady)
            guiGraphics.drawCenteredString(font, Component.literal("Loading " + playerProfile.name + "'s skin"), centerX, avatarY + 50, ColorControl.getColorMinecraftStandard("green"));

        guiGraphics.drawCenteredString(font, playerProfile.getFullName(), centerX, height / 55, CommonColors.WHITE);

        drawCategoryList(guiGraphics, FormosaProfile.FORMOSA_IMAGE, playerProfile.profileFormosa, centerX, listY);
    }

    private void drawCategoryList(GuiGraphics guiGraphics, ResourceLocation image, SuperProfile superProfile, int x, int y) {
        if (superProfile == null) {
            guiGraphics.drawCenteredString(font, "Loading from API...", x, (int) (y + 2.8 * separator), ColorControl.getColorMinecraftStandard("green"));
            return;
        }

        guiGraphics.blit(image, x - 12, (int) (y + 2.4 * separator) + 4 - 38, 0, 0, 24, 24, 24, 24);

        if (superProfile.status == Status.SEARCHING) {
            guiGraphics.drawCenteredString(font, "Searching...", x, (int) (y + 2.8 * separator), ColorControl.getColorMinecraftStandard("green"));
            return;
        } else if (superProfile.status == Status.NOT_EXISTING) {
            guiGraphics.drawCenteredString(font, "Unranked", x, (int) (y + 2.8 * separator), ColorControl.getColorMinecraftStandard("red"));
            return;
        } else if (superProfile.status == Status.TIMEOUTED) {
            guiGraphics.drawCenteredString(font, "Search timeouted. Clear cache and retry", x, (int) (y + 2.8 * separator), ColorControl.getColorMinecraftStandard("red"));
            return;
        } else if (superProfile.status == Status.API_ISSUE) {
            guiGraphics.drawCenteredString(font, "Search failed: API issue", x, (int) (y + 2.8 * separator), ColorControl.getColorMinecraftStandard("red"));
            guiGraphics.drawCenteredString(font, "Update Tiers or retry in a while", x, (int) (y + 2.8 * separator + 15), ColorControl.getColorMinecraftStandard("red"));
            return;
        }

        if (!superProfile.drawn) {
            drawTierList(superProfile, x - 64, (int) (y + 2.4 * separator));

            superProfile.drawn = true;
        }
    }

    private void drawTierList(SuperProfile superProfile, int x, int y) {
        int originalX = x;
        if (small) {
            y -= 7;
            int count = 1;
            int stage = 0;
            for (GameMode gameMode : superProfile.gameModes) {
                if (drawGameModeTiers(gameMode, x + 5, y + stage * 36)) {
                    x += 35;
                    if (count % 4 == 0) {
                        stage++;
                        x = originalX;
                    }
                    count++;
                }
            }
        } else {
            for (GameMode gameMode : superProfile.gameModes)
                if (drawGameModeTiers(gameMode, x, y)) y += 15;
        }
    }

    private boolean drawGameModeTiers(GameMode mode, int x, int y) {
        if (mode.drawn || mode.status != Status.READY)
            return false;

        StringWidget icon = new StringWidget(mode.gamemode.getIcon(), font);
        icon.setPosition(x, y + 3);
        if (small)
            icon.setPosition(x, y + 3);
        icon.setTooltip(Tooltip.create(mode.gamemode.getTextLabel()));
        addRenderableWidget(icon);

        StringWidget label = new StringWidget(mode.gamemode.getTextLabel(), font);
        label.setPosition(x + 20, y);
        if (!small)
            addRenderableWidget(label);

        StringWidget tier = new StringWidget(mode.displayedTier, font);
        tier.setPosition(x + 114 - (mode.displayedTier.getString().length() - 3) * 3, y);
        if (small)
            tier.setPosition(x - 2 - (mode.displayedTier.getString().length() - 3) * 2, y + 14);
        tier.setTooltip(Tooltip.create(mode.tierTooltip));
        addRenderableWidget(tier);

        if (mode.hasPeak && mode.peakTierTooltip.getStyle().getColor() != null) {
            StringWidget peakTier = new StringWidget(mode.displayedPeakTier, font);
            peakTier.setPosition(x + 136, y);
            if (small)
                peakTier.setPosition(x - 6, y + 24);
            peakTier.setTooltip(Tooltip.create(mode.peakTierTooltip));
            addRenderableWidget(peakTier);
        }

        mode.drawn = true;

        return true;
    }

    private void drawPlayerAvatar(GuiGraphics guiGraphics, int x, int y) {
        if (imageReady) {
            if (playerProfile.imageSaved == 1 || playerProfile.imageSaved == 2)
                guiGraphics.blit(playerAvatarTexture, x - width / 32, y, 0, 0, width / 16, (int) (width / 6.666), width / 16, (int) (width / 6.666));
            else if (playerProfile.imageSaved < 6 && playerProfile.imageSaved > 2)
                guiGraphics.blit(playerAvatarTexture, (int) (x - width / 22.5), y, 0, 0, (int) (width / 11.25), (int) (width / 6.666), (int) (width / 11.25), (int) (width / 6.666));
        } else if (playerProfile.imageSaved != 0) {
            loadPlayerAvatar();
        } else if (playerProfile.numberOfImageRequests == 6)
            guiGraphics.drawCenteredString(font, Component.literal(playerProfile.name + "'s skin failed to load. Clear cache and retry"), x, y + 50, ColorControl.getColorMinecraftStandard("red"));
    }

    private void loadPlayerAvatar() {
        if (imageReady)
            return;

        try (FileInputStream fileInputStream = new FileInputStream(FabricLoader.getInstance().getGameDir().resolve("cache/tiers/players/" + playerProfile.uuid + ".png").toFile())) {
            Minecraft.getInstance().getTextureManager().register(playerAvatarTexture, new DynamicTexture(NativeImage.read(fileInputStream)));
            imageReady = true;
        } catch (IOException ignored) {
            TiersClient.LOGGER.warn("Error loading player skin");
        }
    }

    @Override
    protected void init() {
        playerProfile.resetDrawnStatus();

        dimensionsWarning = Button.builder(Component.literal("ℹ"), (_) -> {}).bounds(width - 20 - 5, 5, 20, 20).tooltip(Tooltip.create(Component.literal("Your window dimensions (" + width + "x" + height + ") are small\nLower the GUI scale or make the window bigger to have a better experience (ideal: 575x420)"))).build();
        dimensionsWarning.active = false;
        dimensionsWarning.visible = small;
        if (tooSmall) {
            dimensionsWarning.setMessage(Component.literal("⚠"));
            dimensionsWarning.setTooltip(Tooltip.create(Component.literal("Your window dimensions (" + width + "x" + height + ") are too small\nLower the GUI scale or make the window bigger! (min: 430x262)")));
        }

        addRenderableWidget(dimensionsWarning);

        addRenderableWidget(Button.builder(Component.literal("Update"), (_) -> TiersClient.showUpdatedPlayerProfile(playerProfile, true)).bounds(5, height - 20 - 5, 50, 20).tooltip(Tooltip.create(Component.literal("Update the player profile"))).build());
        addRenderableWidget(Button.builder(Icons.CYCLE, (_) -> playerProfile.updateTierlistProfiles()).bounds(5 + 54, height - 20 - 5, 20, 20).tooltip(Tooltip.create(Component.literal("Refresh Formosa results"))).build());

    }
}