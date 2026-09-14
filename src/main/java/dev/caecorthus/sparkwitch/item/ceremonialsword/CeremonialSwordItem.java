package dev.caecorthus.sparkwitch.item.ceremonialsword;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
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
    private static final net.minecraft.util.Identifier MOVEMENT_SPEED_MODIFIER_ID =
            SparkWitch.id("ceremonial_sword_movement_speed");
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
        // Combat attributes apply only to main-hand attacks; movement is reconciled across both hands.
        // 战斗属性仅用于主手攻击；移速单独汇总主副手持有状态。
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
                .add(
                        EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(
                                BASE_ATTACK_SPEED_MODIFIER_ID,
                                GrandWitchRules.CEREMONIAL_SWORD_ATTACK_SPEED - 4.0,
                                EntityAttributeModifier.Operation.ADD_VALUE
                        ),
                        AttributeModifierSlot.MAINHAND
                )
                .build();
    }

    /** One player-owned modifier survives hand swaps without stacking or removing the other hand's bonus.
     * 玩家仅持有一个移速修饰器，换手或放下一把剑不会叠加或误删另一只手的加成。 */
    public static void updateMovementSpeed(PlayerEntity player) {
        var movement = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        boolean holding = player.getMainHandStack().getItem() instanceof CeremonialSwordItem
                || player.getOffHandStack().getItem() instanceof CeremonialSwordItem;
        if (holding && !movement.hasModifier(MOVEMENT_SPEED_MODIFIER_ID)) {
            movement.addTemporaryModifier(new EntityAttributeModifier(
                    MOVEMENT_SPEED_MODIFIER_ID,
                    GrandWitchRules.CEREMONIAL_SWORD_MOVEMENT_SPEED_BONUS,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!holding) {
            movement.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
        }
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
        if (SparkTraitsKillerBridge.blocksWeaponAction(user, stack)) {
            return TypedActionResult.fail(stack);
        }
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
