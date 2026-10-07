package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishKind;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritService;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;

/** Server-side effect of using one fish; returns whether one was consumed. / 服务端吃鱼效果；返回是否消耗了一条。 */
public final class FisherFishUse {
    private FisherFishUse() {
    }

    public static boolean use(ServerPlayerEntity player, ItemStack stack, FisherFishKind kind) {
        if (!FisherParticipants.isLivingParticipant(player) || stack.isEmpty()
                || !(stack.getItem() instanceof FisherFishItem fish) || fish.kind() != kind
                || fishItems().stream().anyMatch(player.getItemCooldownManager()::isCoolingDown)) {
            return false;
        }
        // Complete the fallible window start before any cost or mood reward; both calls share the same gate.
        // 先完成可能拒绝的窗口启动，再扣鱼或恢复理智；预检与启动使用同一门槛。
        if (kind == FisherFishKind.GLIMMERFISH
                && (!FisherSpiritService.canStart(player) || !FisherSpiritService.start(player))) {
            return false;
        }
        stack.decrement(1);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(moodAfter(kind, mood.getMood()));
        switch (kind) {
            case CLOWNFISH -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
                    FisherRules.CLOWNFISH_SPEED_TICKS, FisherRules.CLOWNFISH_SPEED_AMPLIFIER, false, false, true));
            case GOLDFISH -> PlayerShopComponent.KEY.get(player).addToBalance(FisherRules.GOLDFISH_COINS);
            default -> { }
        }
        for (Item item : fishItems()) {
            player.getItemCooldownManager().set(item, FisherRules.FISH_USE_COOLDOWN_TICKS);
        }
        player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1f, 1f);
        FisherInventory.sync(player);
        return true;
    }

    /**
     * Any cooling fish refuses every fish, so removing one fish's cooldown (SparkFactionAPI {@code clearCooldown})
     * removes the others too. Only cooling fish are removed, so the removals this triggers stop by themselves.
     * 任一鱼冷却中都会拒绝所有鱼，因此移除一条鱼的冷却（SparkFactionAPI {@code clearCooldown}）时一并移除其余鱼的冷却。
     * 只移除冷却中的鱼，因此由此引发的移除会自行终止。
     */
    public static void releaseSharedCooldown(ServerPlayerEntity player) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        for (Item item : fishItems()) {
            if (manager.isCoolingDown(item)) {
                manager.remove(item);
            }
        }
    }

    static float moodAfter(FisherFishKind kind, float mood) {
        return switch (kind) {
            case SALMON -> Math.min(1f, mood + FisherRules.SALMON_MOOD);
            case COD -> Math.min(1f, mood + FisherRules.COD_MOOD);
            case CLOWNFISH -> Math.min(1f, mood + FisherRules.CLOWNFISH_MOOD);
            case GOLDFISH -> Math.min(1f, mood + FisherRules.GOLDFISH_MOOD);
            case GLIMMERFISH -> FisherRules.GLIMMERFISH_MOOD;
        };
    }

    private static List<Item> fishItems() {
        return List.of(SparkWitchItems.salmon(), SparkWitchItems.cod(), SparkWitchItems.clownfish(),
                SparkWitchItems.goldfish(), SparkWitchItems.glimmerfish());
    }
}
