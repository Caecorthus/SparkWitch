package dev.caecorthus.sparkwitch.item;

import dev.caecorthus.sparkwitch.impl.CeremonialSwordDashService;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

    public static Settings createSettings() {
        return new Settings()
                .maxCount(1)
                .attributeModifiers(createAttributeModifiers());
    }

    public static AttributeModifiersComponent createAttributeModifiers() {
        // Keep the 16-damage vanilla attribute, but do not add attack speed so left-click has no sword cooldown.
        // 保留 16 点原版攻击伤害属性，但不添加攻击速度修饰器，让左键没有原版剑冷却。
        return AttributeModifiersComponent.builder()
                .add(
                        EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(
                                BASE_ATTACK_DAMAGE_MODIFIER_ID,
                                ATTACK_DAMAGE_BONUS_VALUE,
                                EntityAttributeModifier.Operation.ADD_VALUE
                        ),
                        AttributeModifierSlot.MAINHAND
                )
                .build();
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
