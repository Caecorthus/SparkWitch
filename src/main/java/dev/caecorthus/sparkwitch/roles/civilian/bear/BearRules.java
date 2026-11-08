package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.headset.CensEarplugsItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32A1Item;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.SmokeRoundItem;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Frozen contract (WP0, 2026-11-05): every BEAR number, literal id and side-neutral predicate. BEAR
 * ({@code sparkwitch:bear}) is a plain CIVILIAN, never a police variant. Every duration is in ticks. Other BEAR
 * modules, client and server, read these values and never redefine them; later work packages may ADD constants here,
 * never change one. The {@code isBear*Type} checks are by class, so they are safe before registry lookups and in unit
 * tests (loading this class never registers anything).
 * 冻结契约（WP0，2026-11-05）：BEAR 的所有数值、字面量 id 与两端通用的判定。BEAR（{@code sparkwitch:bear}）是普通平民，
 * 从不是警察变体。所有时长均以刻为单位。其他 BEAR 模块（客户端与服务端）只读取这些值、从不重新定义；后续工作包只能在此
 * 追加常量，不得修改已有常量。{@code isBear*Type} 按类判断，因此在注册表查询之前与单元测试中都安全（加载本类不会注册任何东西）。
 */
public final class BearRules {
    // ---- Role ----
    public static final Identifier ROLE_ID = SparkWitch.id("bear");
    /** Olive drab. / 橄榄绿。 */
    public static final int COLOR = 0x6E7F3F;

    // ---- Items and entity (literal ids; BearItems / BearEntities register them) ----
    public static final Identifier M32A1_ITEM_ID = SparkWitch.id("m32a1");
    public static final Identifier SMOKE_ROUND_ITEM_ID = SparkWitch.id("smoke_round_40mm");
    public static final Identifier RYS_T_ITEM_ID = SparkWitch.id("rys_t");
    public static final Identifier EARPLUGS_ITEM_ID = SparkWitch.id("cens_proflex_dx5");
    public static final Identifier SMOKE_ROUND_ENTITY_ID = SparkWitch.id("smoke_round_40mm");

    // ---- Economy (owner: start at 0, +5 every 10 s with no cap, +50 per task; USEC model) ----
    public static final int INITIAL_MONEY = 0;
    public static final int PASSIVE_INCOME = 5;
    public static final int PASSIVE_INTERVAL_TICKS = 200;
    public static final int TASK_PAY = 50;

    // ---- Shop (D9: the helmet and the earplugs are stock 1, the smoke round is unlimited) ----
    public static final String RYS_T_ENTRY_ID = "bear_rys_t";
    public static final String EARPLUGS_ENTRY_ID = "bear_cens_proflex_dx5";
    public static final String SMOKE_ROUND_ENTRY_ID = "bear_smoke_round_40mm";
    public static final int PRICE_RYS_T = 100;
    public static final int PRICE_EARPLUGS = 50;
    public static final int PRICE_SMOKE_ROUND = 75;
    public static final int RYS_T_STOCK = 1;
    public static final int EARPLUGS_STOCK = 1;

    // ---- M32A1 launcher (G3, G4, D8) ----
    /** 1 s between shots. / 两次射击间隔 1 秒。 */
    public static final int M32_COOLDOWN_TICKS = 20;
    public static final int M32_CHAMBERS = 6;
    public static final int SMOKE_ROUND_MAX_STACK = 6;
    /** Blocks of flight before an impact may burst the round (G3). / 飞行多少格后撞击才能引爆榴弹（G3）。 */
    public static final double ARMING_DISTANCE = 4.0;
    /** The backup fuse always bursts the round after 3 s (G3). / 备用引信 3 秒后必定引爆（G3）。 */
    public static final int BACKUP_FUSE_TICKS = 60;

    // ---- Rys-T helmet (H1-H8, D4) ----
    public static final float RICOCHET_CHANCE = 0.25F;
    public static final int MAX_VISOR_CRACKS = 8;

    // ---- Hearing (H7, E2: additive volume factors, never range) ----
    public static final float HEARING_HELMET = -0.75F;
    public static final float HEARING_EARPLUGS = 0.5F;

    private BearRules() {
    }

    public static boolean isBearId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isBear(@Nullable Role role) {
        return role != null && isBearId(role.identifier());
    }

    /**
     * The player's live Wathe role is BEAR (either side; the client reads the synced role).
     * 玩家当前 Wathe 职业为 BEAR（两端均可用；客户端读取同步的职业）。
     */
    public static boolean isBear(@Nullable PlayerEntity player) {
        return player != null && isBear(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** The player {@code uuid} holds the BEAR role in {@code world}. / {@code world} 中 {@code uuid} 玩家的职业为 BEAR。 */
    public static boolean isBear(@Nullable World world, @Nullable UUID uuid) {
        return world != null && uuid != null && isBear(GameWorldComponent.KEY.get(world).getRole(uuid));
    }

    /**
     * Pure class check behind {@code BearItems.isBearItem}: the four BEAR items (launcher, smoke round, helmet,
     * earplugs). Testable without a bootstrapped registry. / {@code BearItems.isBearItem} 背后的纯类判断：四种 BEAR 物品
     * （发射器、烟雾榴弹、头盔、耳塞）。无需引导注册表即可测试。
     */
    public static boolean isBearItemType(@Nullable Class<?> itemType) {
        return itemType != null
                && (M32A1Item.class.isAssignableFrom(itemType)
                || SmokeRoundItem.class.isAssignableFrom(itemType)
                || RysTHelmetItem.class.isAssignableFrom(itemType)
                || CensEarplugsItem.class.isAssignableFrom(itemType));
    }
}
