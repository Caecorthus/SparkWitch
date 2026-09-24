package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Reads independent server kill/factor timers and the existing item dash timer. / 分别读取服务端击杀、因子计时和现有物品冲刺计时。 */
public final class GrandWitchClientPresentation {
    public static final Identifier FACTOR_ID = SparkWitch.id("witch_factor");
    public static final int COLOR = 0xF2DFF7;

    private GrandWitchClientPresentation() {
    }

    public static boolean isGrandWitch(ClientPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && SparkWitchRoles.GRAND_WITCH_ID.equals(role.identifier());
    }

    public static Text factorState(ClientPlayerEntity player) {
        WitchPlayerComponent state = WitchPlayerComponent.KEY.get(player);
        int ticks = state.getCooldownTicks();
        if (ticks == 0 && state.getMana() < WitchFactorService.MANA_COST) {
            return Text.translatable("hud.sparkwitch.skill.not_enough_mana",
                    WitchFactorService.MANA_COST);
        }
        return ticks > 0
                ? Text.translatable("hud.sparkwitch.grand_witch.factor.cooldown", seconds(ticks))
                : Text.translatable("hud.sparkwitch.grand_witch.factor.ready", SparkWitchClient.abilityKeyText());
    }

    public static Text recruitmentState(ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.hasUnlockedGrandWitchCeremonialSword()) {
            return Text.translatable("hud.sparkwitch.grand_witch.recruit.locked", component.getGrandWitchCeremonialSwordTasks());
        }
        GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(player);
        // Quota is cumulative and based on the round snapshot, never the current living count. / 名额按开局人口及累计招募计算，不按当前存活人数。
        int capacity = Math.max(0, (runtime.getRoundParticipants() - 18) / 6);
        int remaining = Math.max(0, capacity - runtime.getRecruitmentCount());
        return Text.translatable("hud.sparkwitch.grand_witch.recruit.ready",
                remaining, capacity, SecondaryAbilityController.secondaryKeyText());
    }

    public static List<Text> swordStates(ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.hasUnlockedGrandWitchCeremonialSword()) {
            return List.of(Text.translatable("hud.sparkwitch.grand_witch.sword.locked", component.getGrandWitchCeremonialSwordTasks()));
        }
        int killTicks = GrandWitchRuntimeComponent.KEY.get(player).getSwordKillCooldownTicks();
        // Read the actual item timer, including any external extension; never infer it from kill cooldown.
        // 读取实际物品计时（包括外部延长），绝不从击杀冷却推算冲刺冷却。
        ItemCooldownManagerAccessor manager = (ItemCooldownManagerAccessor) player.getItemCooldownManager();
        Object entry = manager.sparkwitch$getEntries().get(SparkWitchItems.ceremonialSword());
        int dashTicks = entry instanceof ItemCooldownEntryAccessor cooldown
                ? Math.max(0, cooldown.sparkwitch$getEndTick() - manager.sparkwitch$getTick()) : 0;
        return List.of(
                Text.translatable("hud.sparkwitch.grand_witch.sword.kill", timer(killTicks)),
                Text.translatable("hud.sparkwitch.grand_witch.sword.dash", timer(dashTicks))
        );
    }

    public static void renderHud(DrawContext context, TextRenderer renderer, ClientPlayerEntity player) {
        List<Text> lines = new ArrayList<>();
        lines.add(factorState(player));
        lines.add(recruitmentState(player));
        lines.addAll(swordStates(player));
        int y = context.getScaledWindowHeight() - 5 - lines.size() * (renderer.fontHeight + 2);
        for (Text line : lines) {
            context.drawTextWithShadow(renderer, line, context.getScaledWindowWidth() - 5 - renderer.getWidth(line), y, COLOR);
            y += renderer.fontHeight + 2;
        }
    }

    private static Text timer(int ticks) {
        return ticks > 0 ? Text.translatable("hud.sparkwitch.grand_witch.timer", seconds(ticks))
                : Text.translatable("gui.sparkwitch.skill.ready");
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}
