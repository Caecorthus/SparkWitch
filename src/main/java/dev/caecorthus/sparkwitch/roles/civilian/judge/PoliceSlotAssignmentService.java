package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleSelectionContext;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only Vigilante variants share this budget; police shop membership does not change allocation.
 * 这里只共享义警变体的警位预算，警类商店资格不改变其他身份的分配。 */
public final class PoliceSlotAssignmentService {
    public static final Identifier EMMA_ID = Identifier.of("sparkwitch", "emma");
    private static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");
    private static final List<Identifier> VARIANT_IDS = List.of(JudgeRules.ROLE_ID, EMMA_ID);

    private PoliceSlotAssignmentService() {
    }

    public static boolean isVariant(Role role) {
        return role != null && VARIANT_IDS.contains(role.identifier());
    }

    public static Role choose(RoleSelectionContext context) {
        GameWorldComponent game = context.gameComponent();
        Set<Identifier> eligible = new HashSet<>();
        Set<Identifier> assigned = new HashSet<>();
        for (Identifier id : VARIANT_IDS) {
            Role role = WatheRoles.getRole(id);
            if (role == null || !id.equals(role.identifier())) {
                continue;
            }
            if (game.isRoleEnabled(role) && role.shouldAppear(context)) {
                eligible.add(id);
            }
            if (!game.getAllWithRole(role).isEmpty()) {
                assigned.add(id);
            }
        }
        List<Identifier> choices = candidates(eligible, assigned);
        Identifier selected = choices.get(context.world().getRandom().nextInt(choices.size()));
        return VIGILANTE_ID.equals(selected) ? WatheRoles.VIGILANTE : WatheRoles.getRole(selected);
    }

    static List<Identifier> candidates(Set<Identifier> eligible, Set<Identifier> assigned) {
        List<Identifier> result = new ArrayList<>();
        result.add(VIGILANTE_ID);
        for (Identifier variant : VARIANT_IDS) {
            if (eligible.contains(variant) && !assigned.contains(variant)) {
                result.add(variant);
            }
        }
        return List.copyOf(result);
    }
}
