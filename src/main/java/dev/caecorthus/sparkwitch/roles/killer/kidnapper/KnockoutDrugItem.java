package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.NotNull;

/** 绑架者专属迷药；实际劫持状态由目标玩家身上的 KidnapperControlComponent 保存。 */
public final class KnockoutDrugItem extends Item {
    public KnockoutDrugItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(
            ItemStack stack,
            @NotNull PlayerEntity player,
            @NotNull LivingEntity entity,
            @NotNull Hand hand
    ) {
        boolean ignoresCooldown = GameFunctions.isPlayerSpectatingOrCreative(player);
        if ((!ignoresCooldown && player.getItemCooldownManager().isCoolingDown(this))
                || player.isSneaking()
                || !(entity instanceof PlayerEntity targetPlayer)) {
            return ActionResult.FAIL;
        }
        if (player.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!ignoresCooldown && (!KidnapperKnockoutService.canUseKnockoutDrug(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameFunctions.isPlayerAliveAndSurvival(player))) {
            return ActionResult.FAIL;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || !GameFunctions.isPlayerAliveAndSurvival(targetPlayer)) {
            player.sendMessage(Text.translatable("message.sparkwitch.kidnapper.invalid_target")
                    .withColor(KidnapperRules.COLOR), true);
            return ActionResult.FAIL;
        }

        KidnapperControlComponent targetControl = KidnapperControlComponent.KEY.get(targetPlayer);
        if (targetControl.isControlled()) {
            return ActionResult.PASS;
        }

        /*
         * 目标确认可劫持后再消耗迷药并写冷却。
         * 旁观/创造玩家保留调试便利：可连续使用，不消耗物品也不进入冷却。
         */
        if (!ignoresCooldown) {
            player.getItemCooldownManager().set(this, KidnapperRules.KNOCKOUT_DRUG_COOLDOWN_TICKS);
            player.getStackInHand(hand).decrement(1);
            KidnapperControlComponent.KEY.get(player).clearKnockoutDrugStartCooldown();
        }

        targetControl.startControl(player);
        if (player instanceof ServerPlayerEntity serverPlayer && targetPlayer instanceof ServerPlayerEntity serverTarget) {
            GameRecordManager.recordItemUse(
                    serverPlayer,
                    Registries.ITEM.getId(SparkWitchItems.knockoutDrug()),
                    serverTarget,
                    null
            );
        }
        player.playSoundToPlayer(SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 4; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.knockout_drug.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
