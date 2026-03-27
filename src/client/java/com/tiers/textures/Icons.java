package com.tiers.textures;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public class Icons {
    public static Identifier identifierFormosa = Identifier.fromNamespaceAndPath("minecraft", "gamemodes/pvptiers");
    public static Identifier identifierFormosaTags = Identifier.fromNamespaceAndPath("minecraft", "gamemodes/pvptiers-tags");

    private static final FontDescription fontDescription = new FontDescription.Resource((Identifier.fromNamespaceAndPath("minecraft", "misc")));

    public static Component GLOBE = Component.literal("\uF000").setStyle(Style.EMPTY.withColor(ColorControl.getColorMinecraftStandard("region")).withFont(fontDescription));
    public static Component OVERALL = Component.literal("\uF001").setStyle(Style.EMPTY.withColor(ColorControl.getColorMinecraftStandard("overall")).withFont(fontDescription));
    public static final Component CYCLE = Component.literal("\uF002").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component ICONS = Component.literal("\uF004").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component ICONS_DISABLED = Component.literal("\uF005").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component TAB = Component.literal("\uF006").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component TAB_DISABLED = Component.literal("\uF007").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component CHAT = Component.literal("\uF008").setStyle(Style.EMPTY.withFont(fontDescription));
    public static final Component CHAT_DISABLED = Component.literal("\uF009").setStyle(Style.EMPTY.withFont(fontDescription));

    public enum Type {
        CLASSIC,
        PVPTIERS,
        MCTIERS
    }

    public static Component colorText(String string, String color) {
        return Component.literal(string).setStyle(Style.EMPTY.withColor(ColorControl.getColor(color)));
    }

    public static Component colorText(String string, int color) {
        return Component.literal(string).setStyle(Style.EMPTY.withColor(color));
    }
}