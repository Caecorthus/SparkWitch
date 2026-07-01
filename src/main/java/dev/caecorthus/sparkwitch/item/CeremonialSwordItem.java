package dev.caecorthus.sparkwitch.item;

import dev.caecorthus.sparkwitch.impl.CeremonialSwordDashService;
import dev.doctor4t.wathe.game.GameFunctions;
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
    public static final double ATTACK_SPEED = 2.0;
    public static final float ATTACK_SPEED_MODIFIER_VALUE = -2.0f;
    private static final int ATTACK_DAMAGE = 3;

    public static Settings createSettings() {
        return new Settings()
                .maxCount(1)
                .attributeModifiers(SwordItem.createAttributeModifiers(
                        ToolMaterials.IRON,
                        ATTACK_DAMAGE,
                        ATTACK_SPEED_MODIFIER_VALUE
                ));
    }

    public CeremonialSwordItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(@NotNull World world, @NotNull PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        CeremonialSwordDashService.start(player);
        player.getItemCooldownManager().set(this, DASH_COOLDOWN_TICKS);
        return TypedActionResult.consume(stack);
    }
}
