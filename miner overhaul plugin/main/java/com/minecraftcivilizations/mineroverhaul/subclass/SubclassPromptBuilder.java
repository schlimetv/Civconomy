package com.minecraftcivilizations.mineroverhaul.subclass;

import com.minecraftcivilizations.mineroverhaul.MinerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public final class SubclassPromptBuilder {

    private SubclassPromptBuilder() {}

    public static Component buildLevelUpPrompt(MinerConfig config, int minerLevel) {
        List<MinerSubclass> options = MinerSubclass.availableFor(minerLevel);

        Component msg = Component.text("You may sub class into ", NamedTextColor.GRAY);
        for (int i = 0; i < options.size(); i++) {
            MinerSubclass sub = options.get(i);
            msg = msg.append(buildSubclassToken(sub, config.description(sub)));
            if (i < options.size() - 2) {
                msg = msg.append(Component.text(", ", NamedTextColor.GRAY));
            } else if (i == options.size() - 2) {
                msg = msg.append(Component.text(" OR ", NamedTextColor.GRAY));
            }
        }
        msg = msg.append(Component.text(". Note: You cannot change your subclass once you have selected it.",
                NamedTextColor.GRAY));
        return msg;
    }

    public static Component buildSubclassToken(MinerSubclass sub, String description) {
        Component label = Component.text(sub.displayName() + " Prospecting", sub.chatColor());
        Component hover = Component.text(description, NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text("Click to select", NamedTextColor.GRAY));
        return label
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/subclass miner " + sub.lowerName()));
    }

    public static Component buildConfirmPrompt(MinerSubclass sub) {
        Component yes = Component.text("<Yes>", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Confirm and lock this subclass", NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand("/subclass confirm yes"));
        Component no = Component.text("<No>", NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Cancel selection", NamedTextColor.WHITE)))
                .clickEvent(ClickEvent.runCommand("/subclass confirm no"));
        return Component.text("Are you sure you want to choose ", NamedTextColor.GRAY)
                .append(Component.text(sub.displayName() + " Prospecting", sub.chatColor()))
                .append(Component.text("? Subclass cannot be changed once selected. ", NamedTextColor.GRAY))
                .append(yes)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(no);
    }

    public static Component buildSelectedMsg(MinerSubclass sub) {
        return Component.text("You have selected ", NamedTextColor.GRAY)
                .append(Component.text(sub.displayName() + " Prospecting", sub.chatColor()))
                .append(Component.text(".", NamedTextColor.GRAY));
    }

    public static Component buildCancelMsg() {
        return Component.text(
                "No subclass selected. If you change your mind, you may use /subclass miner [ore] to select a subclass.",
                NamedTextColor.GRAY);
    }
}
