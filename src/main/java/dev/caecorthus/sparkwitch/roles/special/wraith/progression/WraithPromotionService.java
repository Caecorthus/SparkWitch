package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaLifecycleService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Single validated entry point for natural and administrative Wraith promotion.
 * 自然晋升与管理员晋升共用的唯一校验入口。
 */
public final class WraithPromotionService {
    public enum Failure {
        INACTIVE("command.sparkwitch.force_promotion.failure.inactive"),
        ALREADY_PROMOTED("command.sparkwitch.force_promotion.failure.already_promoted"),
        MISSING_ALIGNMENT("command.sparkwitch.force_promotion.failure.missing_alignment"),
        WRONG_ALIGNMENT("command.sparkwitch.force_promotion.failure.wrong_alignment"),
        VENDETTA_INELIGIBLE("command.sparkwitch.force_promotion.failure.vendetta_ineligible");

        private final String translationKey;

        Failure(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Result(boolean promoted, Failure failure) {
        private static final Result SUCCESS = new Result(true, null);

        static Result failed(Failure failure) {
            return new Result(false, failure);
        }
    }

    private WraithPromotionService() {
    }

    public static Result promote(ServerPlayerEntity player, Role role) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        boolean vendettaEligible = VendettaLifecycleService.canPromote(player);
        Failure failure = validate(
                wraith.isActive(),
                wraith.isPromoted(),
                wraith.getAlignment(),
                role.identifier(),
                vendettaEligible
        );
        if (failure != null) {
            return Result.failed(failure);
        }

        wraith.promote();
        WraithLifecycle.promotePlayer(player, role);
        return Result.SUCCESS;
    }

    public static Result promoteForced(ServerPlayerEntity player, Role role) {
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        Failure failure = validateState(
                wraith.isActive(),
                wraith.isPromoted(),
                wraith.getAlignment()
        );
        if (failure != null) {
            return Result.failed(failure);
        }
        if (SparkWitchRoles.VENDETTA_ID.equals(role.identifier())
                && !VendettaLifecycleService.canPromote(player)) {
            return Result.failed(Failure.VENDETTA_INELIGIBLE);
        }

        wraith.promote();
        WraithLifecycle.promotePlayer(player, role);
        return Result.SUCCESS;
    }

    static Failure validate(
            boolean active,
            boolean promoted,
            WraithState.Alignment alignment,
            Identifier roleId,
            boolean vendettaEligible
    ) {
        Failure stateFailure = validateState(active, promoted, alignment);
        if (stateFailure != null) {
            return stateFailure;
        }
        return validateAgainstAllowedIds(
                active,
                promoted,
                alignment,
                roleId,
                vendettaEligible,
                WraithPromotionRoles.pool(alignment, vendettaEligible).stream().map(Role::identifier).toList()
        );
    }

    static Failure validateState(
            boolean active,
            boolean promoted,
            WraithState.Alignment alignment
    ) {
        if (!active) {
            return Failure.INACTIVE;
        }
        if (promoted) {
            return Failure.ALREADY_PROMOTED;
        }
        if (alignment == null) {
            return Failure.MISSING_ALIGNMENT;
        }
        return null;
    }

    static Failure validateAgainstAllowedIds(
            boolean active,
            boolean promoted,
            WraithState.Alignment alignment,
            Identifier roleId,
            boolean vendettaEligible,
            List<Identifier> allowedRoleIds
    ) {
        if (!active) {
            return Failure.INACTIVE;
        }
        if (promoted) {
            return Failure.ALREADY_PROMOTED;
        }
        if (alignment == null) {
            return Failure.MISSING_ALIGNMENT;
        }
        if (!allowedRoleIds.contains(roleId)) {
            if (SparkWitchRoles.VENDETTA_ID.equals(roleId)
                    && alignment == WraithState.Alignment.GOOD
                    && !vendettaEligible) {
                return Failure.VENDETTA_INELIGIBLE;
            }
            return Failure.WRONG_ALIGNMENT;
        }
        return null;
    }
}
