package com.tiers.misc;

import com.tiers.textures.Icons;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;

import java.awt.*;
import java.util.ArrayList;
import java.util.Locale;

public enum Mode {
    FORMOSA_CRYSTAL(Category.FORMOSA, "\uF000", "Crystal"),
    FORMOSA_UHC(Category.FORMOSA, "\uF001", "UHC"),
    FORMOSA_POT(Category.FORMOSA, "\uF002", "Pot"),
    FORMOSA_NETH_POT(Category.FORMOSA, "\uF003", "Neth Pot"),
    FORMOSA_SMP(Category.FORMOSA, "\uF004", "Smp"),
    FORMOSA_SWORD(Category.FORMOSA, "\uF005", "Sword"),
    FORMOSA_AXE(Category.FORMOSA, "\uF006", "Axe"),
    FORMOSA_MACE(Category.FORMOSA, "\uF007", "Mace");

    private final Category category;
    private final String unicode;
    private final String label;

    Mode(Category category, String unicode, String label) {
        this.category = category;
        this.unicode = unicode;
        this.label = label;
    }

    public enum Category {
        FORMOSA
    }

    public Component getIcon() {
        return Component.literal(unicode).setStyle(Style.EMPTY.withFont(Icons.identifierFormosa).withColor(CommonColors.WHITE));
    }

    public Component getIconTag() {
        return Component.literal(unicode).setStyle(Style.EMPTY.withFont(Icons.identifierFormosaTags).withColor(CommonColors.WHITE));
    }

    public Component getTextLabel() {
        return Icons.colorText(label, name().toLowerCase(Locale.ROOT));
    }

    public static Mode[] getFormosaValues() {
        ArrayList<Mode> modeArrayList = new ArrayList<>();
        for (Mode mode : values())
            if (mode.toString().contains("FORMOSA"))
                modeArrayList.add(mode);
        return modeArrayList.toArray(new Mode[0]);
    }

    public static Mode fromApiName(String apiName) {
        for (Mode mode : values()) {
            String parsingName = mode.name().replace("FORMOSA_", "").toLowerCase(Locale.ROOT);
            if (parsingName.equals(apiName.toLowerCase(Locale.ROOT)))
                return mode;
        }
        return null;
    }
}