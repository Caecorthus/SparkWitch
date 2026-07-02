package dev.caecorthus.sparkwitch.item;

import dev.caecorthus.sparkwitch.impl.CeremonialSwordDashService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class CeremonialSwordItem extends Item {
    public static final int DASH_COOLDOWN_TICKS = 100;
    public static final int ATTACK_DAMAGE = 16;
    // Player-facing vanilla damage = player base damage + material damage + item bonus.
    // 玩家看到/实际原版伤害 = 玩家基础伤害 + 材料伤害 + 物品 bonus。
    public static final int ATTACK_DAMAGE_BONUS_VALUE = (int) (ATTACK_DAMAGE
            - 1
            - ToolMaterials.IRON.getAttackDamage());
    public static final double ATTACK_SPEED = 2.0;
    public static final float ATTACK_SPEED_MODIFIER_VALUE = -2.0f;

    public static Settings createSettings() {
        return new Settings()
                .maxCount(1)
                .attributeModifiers(SwordItem.createAttributeModifiers(
                        ToolMaterials.IRON,
                        ATTACK_DAMAGE_BONUS_VALUE,
                        ATTACK_SPEED_MODIFIER_VALUE
                ));
    }

    public CeremonialSwordItem(Settings settings) {
        super(settings);
    }

    public static boolean shouldStartDash(
            boolean serverPlayer,
            boolean alive,
            boolean spectator,
            boolean itemCoolingDown
    ) {
        return serverPlayer
                && alive
                && !spectator
                && !itemCoolingDown;
    }

    @Override
    public TypedActionResult<ItemStack> use(@NotNull World world, @NotNull PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player) || !shouldStartDash(
                true,
                player.isAlive(),
                player.isSpectator(),
                player.getItemCooldownManager().isCoolingDown(this)
        )) {
            return TypedActionResult.fail(stack);
        }

        CeremonialSwordDashService.start(player);
        player.getItemCooldownManager().set(this, DASH_COOLDOWN_TICKS);
        return TypedActionResult.consume(stack);
    }
}
