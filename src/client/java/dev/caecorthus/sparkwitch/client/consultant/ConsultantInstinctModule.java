package dev.caecorthus.sparkwitch.client.consultant;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.UndercoverDisguiseComponent;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Set;

/**
 * 顾问's special instinct: only threats and armed players glow.
 * <p>Shown: killers, neutrals / third parties, police roles, killers carrying a Conscience-like trait, and ordinary
 * passengers who are carrying a gun or a knife. Hidden: unarmed passengers, the Black Raven, and the consultant
 * themselves. An undercover glows with the colour of the random killer identity it was given.
 * 顾问的特殊本能：只有威胁与持械者会发光。显示：杀手、中立/第三方、警类、带善良类词条的杀手、持枪刀的普通乘客；
 * 隐藏：无武装乘客、黑羽鸦与顾问本人。卧底用其随机杀手身份的配色。
 */
public final class ConsultantInstinctModule {

    /** Police-family roles that always glow. 始终发光的警类。 */
    private static final Set<Identifier> POLICE_ROLES = Set.of(
            Identifier.of("wathe", "vigilante"),
            Identifier.of("wathe", "veteran"),
            Identifier.of("noellesroles", "detective"),
            Identifier.of("noellesroles", "bodyguard"),
            Identifier.of("noellesroles", "coroner"),
            Identifier.of("sparkwitch", "judge"),
            Identifier.of("sparkwitch", "usec"),
            Identifier.of("sparkwitch", "knight")
    );

    /** Colour used for every non-killer highlight: police roles and armed passengers read as one "safe" green. */
    /** 非杀手目标统一用同一种"安全"绿色：警类与持械乘客都归为这一类。 */
    private static final int SAFE_COLOR = 0x55FF55;

    /** Roles that must never glow. 永不可被透视。 */
    private static final Set<Identifier> HIDDEN_ROLES = Set.of(
            Identifier.of("sparkwitch", "black_raven"),
            // Survival Master never glows, same as for the ordinary killer instinct.
            // 生存大师不发光，与普通杀手本能一致。
            Identifier.of("noellesroles", "survival_master")
    );

    private ConsultantInstinctModule() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(ConsultantInstinctModule::highlight);
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity viewer = client.player;
        if (viewer == null || !(target instanceof PlayerEntity subject) || subject == viewer) {
            return null;
        }
        // A dead consultant falls back to the ordinary instinct.
        // 死亡后的顾问退回原版本能透视。
        if (!viewer.isAlive() || viewer.isSpectator()) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role viewerRole = game.getRole(viewer);
        if (viewerRole == null || !SparkWitchRoles.CONSULTANT_ID.equals(viewerRole.identifier())) {
            // Not the consultant: leave Wathe's default behaviour untouched.
            // 不是顾问：保持 Wathe 默认逻辑。
            return null;
        }
        Role role = game.getRole(subject);
        if (role == null) {
            return GetInstinctHighlight.HighlightResult.skip();
        }
        // The undercover shows the killer colour it is disguised as.
        // 卧底显示其伪装杀手身份的配色。
        if (isUndercover(role)) {
            Identifier disguise = UndercoverDisguiseComponent.KEY.get(subject).disguise();
            return GetInstinctHighlight.HighlightResult.always(
                    disguise == null ? role.color() : colorOf(disguise, role));
        }
        if (HIDDEN_ROLES.contains(role.identifier())) {
            return GetInstinctHighlight.HighlightResult.skip();
        }
        // Armed first, alignment second: a gun or knife in the bag always reads green, even on a killer ally, a
        // neutral, or a disguised coroner. This is what makes the Veteran (who carries a knife) visible.
        // 先看持械、再看阵营：背包里有枪或刀的人一律绿色，即使是杀手队友、中立或换了身份的验尸官。
        // 老兵带刀，就是靠这一条被看见的。
        if (carriesWeapon(subject)) {
            return GetInstinctHighlight.HighlightResult.always(SAFE_COLOR);
        }
        Faction faction = role.getFaction();
        if (faction == Faction.KILLER || faction == Faction.NEUTRAL) {
            return GetInstinctHighlight.HighlightResult.always(role.color());
        }
        // Police roles do not glow on their own: seeing every officer from spawn would hand the consultant the whole
        // board. Only the weapon matters, and it applies to any non-killer alignment.
        // 警类不再无条件发光：一出生就能锁定所有警职等于把整张牌桌交给顾问。
        // 只有持械才亮，且对任何非杀手阵营一视同仁。
        // Someone revived by Last Stand is shielded from instinct for a while; respect that here too.
        // 被背水一战复活的人在一段时间内不受本能照射，这里同样遵守。
        if (isLastStandRevived(subject)) {
            return GetInstinctHighlight.HighlightResult.skip();
        }
        return GetInstinctHighlight.HighlightResult.skip();
    }

    /** Asks SparkTraits whether this player was recently revived by Last Stand (soft dependency). */
    /** 询问 SparkTraits 该玩家是否刚被背水一战复活（软依赖）。 */
    private static boolean isLastStandRevived(PlayerEntity player) {
        try {
            Class<?> cls = Class.forName("dev.caecorthus.sparktraits.impl.traits.civilian.laststand.LastStandService");
            Object value = cls.getMethod("isProtectedFromNoellesRoleUtility", PlayerEntity.class)
                    .invoke(null, player);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isUndercover(Role role) {
        return Identifier.of("noellesroles", "undercover").equals(role.identifier());
    }

    /**
     * Stable colour for a disguised killer identity: every id keeps its own hue, so different disguises read as
     * different killers without needing the real role instance on the client.
     * 为伪装杀手身份生成稳定颜色：同一 id 恒为同一色相，不同伪装读起来就是不同杀手，无需在客户端持有真实角色实例。
     */
    private static int colorOf(Identifier disguiseId, Role fallback) {
        String path = disguiseId.getPath();
        if (path.isEmpty()) {
            return fallback.color();
        }
        float hue = Math.floorMod(path.hashCode(), 360) / 360.0F;
        return net.minecraft.util.math.MathHelper.hsvToRgb(hue, 0.65F, 0.95F) & 0xFFFFFF;
    }

    /** Whether the player is carrying a gun or a knife. 是否携带枪或刀。 */
    private static boolean carriesWeapon(PlayerEntity player) {
        // Offhand counts too: a gun or knife picked up mid-round often sits there before the hotbar.
        // 副手也算：中途捡到的枪刀经常先落在副手，还没放进快捷栏。
        if (isWeapon(player.getOffHandStack())) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isWeapon(inventory.getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Any gun tag, plus the melee weapons Wathe ships. 所有枪标签，加上 Wathe 自带的近战武器。 */
    private static boolean isWeapon(net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.isOf(WatheItems.KNIFE) || stack.isOf(WatheItems.DERRINGER) || stack.isOf(WatheItems.REVOLVER)) {
            return true;
        }
        // NoellesRoles and SparkStrength ship their own guns/knives; match the shared tags first.
        // 诺艾尔与加强模组另有枪刀，先按共享标签匹配。
        try {
            if (stack.isIn(dev.doctor4t.wathe.index.tag.WatheItemTags.GUNS)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through to the name check
        }
        // Last resort: the item id itself, so modded weapons are still caught.
        // 兜底：按物品 id 名称判断，改装武器也能命中。
        String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        return path.contains("knife")
                || path.contains("gun")
                || path.contains("revolver")
                || path.contains("derringer")
                || path.contains("pistol")
                || path.contains("axe");
    }
}
