package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishItem;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishKind;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritService;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
        if (!FisherTargeting.isLivingParticipant(player) || stack.isEmpty()
                || !(stack.getItem() instanceof FisherFishItem fish) || fish.kind() != kind
                || fishItems().stream().anyMatch(player.getItemCooldownManager()::isCoolingDown)) {
            return false;
        }
        stack.decrement(1);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(moodAfter(kind, mood.getMood()));
        switch (kind) {
            case CLOWNFISH -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,
                    FisherRules.CLOWNFISH_SPEED_TICKS, FisherRules.CLOWNFISH_SPEED_AMPLIFIER, false, false, true));
            case GOLDFISH -> PlayerShopComponent.KEY.get(player).addToBalance(FisherRules.GOLDFISH_COINS);
            case GLIMMERFISH -> FisherSpiritService.start(player);
            default -> { }
        }
        for (Item item : fishItems()) {
            player.getItemCooldownManager().set(item, FisherRules.FISH_USE_COOLDOWN_TICKS);
        }
        player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1f, 1f);
        FisherInventory.sync(player);
        return true;
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
