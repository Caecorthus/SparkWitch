package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Owns only the passenger relation and temporary movement modifier. / 只负责乘客关系与临时移速修正。 */
public final class KidnapperDragService {
    public static final String NO_TARGET_MESSAGE = "message.sparkwitch.kidnapper.no_target";
    public static final String FALSE_BODY_MESSAGE = "message.sparkwitch.kidnapper.false_body";

    private KidnapperDragService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!KidnapperRules.isKidnapper(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        ServerPlayerEntity player = context.player();
        PlayerBodyEntity draggedBody = findDraggedBody(player);
        if (draggedBody != null) {
            release(player);
            return WitchSkillUseResult.success(0);
        }

        PlayerBodyEntity body = KidnapperTargeting.findAimedBody(player);
        if (body == null || player.hasPassengers() || body.hasVehicle()) {
            return WitchSkillUseResult.fail(NO_TARGET_MESSAGE);
        }
        if (!KidnapperFalseBodyPolicy.canDrag(body)) {
            return WitchSkillUseResult.fail(FALSE_BODY_MESSAGE);
        }
        // Occupancy is validated above; force only bypasses vanilla's 60-tick remount cooldown.
        // 占用关系已在上方校验；强制挂载仅用于绕过原版 60 tick 的再次挂载冷却。
        if (!body.startRiding(player, true)) {
            return WitchSkillUseResult.fail(NO_TARGET_MESSAGE);
        }
        KidnapperPassengerSync.send(player);
        applySpeedModifier(player);
        return WitchSkillUseResult.success(0);
    }

    public static void reconcile(ServerPlayerEntity player) {
        PlayerBodyEntity body = findDraggedBody(player);
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (body == null) {
            removeSpeedModifier(player);
            return;
        }
        if (!KidnapperRules.isKidnapper(role)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || body.isRemoved()) {
            release(player);
            return;
        }
        applySpeedModifier(player);
    }

    public static void release(ServerPlayerEntity player) {
        PlayerBodyEntity body = findDraggedBody(player);
        if (body != null) {
            body.stopRiding();
            KidnapperPassengerSync.send(player);
        }
        removeSpeedModifier(player);
    }

    @Nullable
    static PlayerBodyEntity findDraggedBody(ServerPlayerEntity player) {
        for (Entity passenger : player.getPassengerList()) {
            if (passenger instanceof PlayerBodyEntity body) {
                return body;
            }
        }
        return null;
    }

    private static void applySpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null || movementSpeed.hasModifier(KidnapperRules.SPEED_MODIFIER_ID)) {
            return;
        }
        movementSpeed.addTemporaryModifier(new EntityAttributeModifier(
                KidnapperRules.SPEED_MODIFIER_ID,
                KidnapperRules.SPEED_MODIFIER_AMOUNT,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void removeSpeedModifier(ServerPlayerEntity player) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(KidnapperRules.SPEED_MODIFIER_ID);
        }
    }
}
