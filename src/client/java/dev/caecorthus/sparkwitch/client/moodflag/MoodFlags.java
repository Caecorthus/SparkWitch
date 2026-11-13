package dev.caecorthus.sparkwitch.client.moodflag;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.SparkTraitsMoodFlagBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Faction mood flags (owner 2026-11-05, "Extra faction icons"): per-role and per-trait recolours of Wathe's top-left
 * mood flag, drawn by {@code mixin/moodflag/MoodFlagRendererMixin}. Sprites live in the GUI atlas at
 * {@code assets/sparkwitch/textures/gui/sprites/hud/mood_flag/*.png}, 14x17 with no {@code .mcmeta}, like Wathe's
 * {@code wathe:hud/mood_*}. Only roles with drawn art have a flag; every other role keeps Wathe's flag until its art
 * exists (owner: the Grand Witch only, not the whole witch faction).
 * <ul>
 *     <li>Role flags ({@link #ROLE_FLAGS}) replace Wathe's icon on whichever path Wathe takes for the role.</li>
 *     <li>Conscience ({@code kindness} art) replaces the civilian icons of a killer whose SparkTraits Conscience sent
 *     it down {@code renderCivilian}; Impostor replaces the killer icon of a civilian SparkTraits sent down
 *     {@code renderKiller}. Role flags win over trait flags, and so do the role-owned Natsume/Noa icons (both drawn
 *     by {@code renderCivilian} only).</li>
 *     <li>No sanity ({@code MoodType.NONE}, effective after SparkTraits): Wathe draws nothing; the mixin draws
 *     {@link #NO_SANITY} (or the role's own flag) at the killer flag's spot, without a bar.</li>
 * </ul>
 * Only Wathe's own sprites are swapped, so an icon another mod already chose (NoellesRoles' Stimulation, Natsume's
 * sketchbook, Noa's cyan copies) passes through in any wrap order. Display only: reads the local player's synced role
 * and owner-synced traits on a confirmed SparkWitch server, never shows a sanity value and changes no state.
 * 阵营心情旗帜（所有者 2026-11-05，“Extra faction icons”）：Wathe 左上角心情旗帜按职业与词条的重绘版，由
 * {@code mixin/moodflag/MoodFlagRendererMixin} 绘制。sprite 位于 GUI 图集
 * {@code assets/sparkwitch/textures/gui/sprites/hud/mood_flag/*.png}，14x17、无 {@code .mcmeta}，与 Wathe 的
 * {@code wathe:hud/mood_*} 相同。只有已绘制美术的职业才有旗帜；其余职业在美术完成前保留 Wathe 的旗帜（所有者：只给大魔女，
 * 不是整个魔女阵营）。
 * <ul>
 *     <li>职业旗帜（{@link #ROLE_FLAGS}）在 Wathe 为该职业选择的任一路径上替换图标。</li>
 *     <li>善良（{@code kindness} 美术）替换因 SparkTraits 善良而走 {@code renderCivilian} 的杀手的平民图标；内鬼替换被
 *     SparkTraits 送入 {@code renderKiller} 的平民的杀手图标。职业旗帜优先于词条旗帜，夏目安安/诺亚的职业自有图标（都只由
 *     {@code renderCivilian} 绘制）也优先于词条旗帜。</li>
 *     <li>无理智（经 SparkTraits 后的有效 {@code MoodType.NONE}）：Wathe 什么都不画；mixin 在杀手旗帜的位置绘制
 *     {@link #NO_SANITY}（或该职业自己的旗帜），不画理智条。</li>
 * </ul>
 * 只替换 Wathe 自己的 sprite，因此其他模组已选定的图标（NoellesRoles 亢奋、夏目安安素描本、诺亚青色版）在任意包装顺序下都会
 * 原样放行。仅用于展示：只在已确认的 SparkWitch 服务器上读取本地玩家同步的职业与仅同步给本人的词条，从不显示理智值，也不改变
 * 任何状态。
 */
public final class MoodFlags {
    public static final Identifier GRAND_WITCH = flag("grand_witch");
    public static final Identifier BEWITCHED = flag("bewitched");
    public static final Identifier MURDEROUS_WITCH = flag("murderous_witch");
    public static final Identifier INSIDER = flag("insider");
    public static final Identifier CORRUPT_COP = flag("corrupt_cop");
    public static final Identifier FIEND = flag("fiend");
    public static final Identifier JESTER = flag("jester");
    public static final Identifier PATHOGEN = flag("pathogen");
    public static final Identifier TAOTIE = flag("taotie");
    public static final Identifier VULTURE = flag("vulture");
    public static final Identifier CONSCIENCE = flag("conscience");
    public static final Identifier IMPOSTOR = flag("impostor");
    public static final Identifier NO_SANITY = flag("no_sanity");
    /** 泽渡可可 (PR #223): the contributor's recolour of Wathe's face in her lavender. / 泽渡可可（PR #223）：贡献者把 Wathe 表情重新染成她的淡紫色。 */
    public static final Identifier KOKO = flag("koko");

    /**
     * Role id to its own flag. NoellesRoles ids are literal (no class reference), pinned to the shipped jar.
     * 职业 id 到其自有旗帜。NoellesRoles 的 id 为字面量（不引用其类），以随附的 jar 为准。
     */
    static final Map<Identifier, Identifier> ROLE_FLAGS = Map.ofEntries(
            Map.entry(SparkWitchRoles.GRAND_WITCH_ID, GRAND_WITCH),
            Map.entry(SparkWitchRoles.BEWITCHED_ID, BEWITCHED),
            Map.entry(SparkWitchRoles.MURDEROUS_WITCH_ID, MURDEROUS_WITCH),
            Map.entry(SparkWitchRoles.INSIDER_ID, INSIDER),
            Map.entry(InsiderRules.CORRUPT_COP_ID, CORRUPT_COP),
            Map.entry(SparkWitchRoles.FIEND_ID, FIEND),
            Map.entry(SparkWitchRoles.KOKO_ID, KOKO),
            Map.entry(Identifier.of("noellesroles", "jester"), JESTER),
            Map.entry(Identifier.of("noellesroles", "pathogen"), PATHOGEN),
            Map.entry(Identifier.of("noellesroles", "taotie"), TAOTIE),
            Map.entry(Identifier.of("noellesroles", "vulture"), VULTURE)
    );

    private MoodFlags() {
    }

    /** Pure: the role's own flag, else {@code null}. / 纯函数：该职业自己的旗帜，否则为 {@code null}。 */
    public static @Nullable Identifier roleFlag(@Nullable Identifier roleId) {
        return roleId == null ? null : ROLE_FLAGS.get(roleId);
    }

    /**
     * Pure: the flag for {@code renderKiller}'s icon draw, else {@code null} (keep the sprite). Only Wathe's killer icon
     * is swapped: the role's flag first, then Impostor.
     * 纯函数：{@code renderKiller} 图标绘制使用的旗帜，否则为 {@code null}（保留原 sprite）。只替换 Wathe 的杀手图标：先职业
     * 旗帜，再内鬼。
     */
    public static @Nullable Identifier killerFlag(Identifier watheSprite, @Nullable Identifier roleId, boolean impostor) {
        if (!MoodRenderer.MOOD_KILLER.equals(watheSprite)) {
            return null;
        }
        Identifier own = roleFlag(roleId);
        if (own != null) {
            return own;
        }
        return impostor ? IMPOSTOR : null;
    }

    /**
     * Pure: the flag for {@code renderCivilian}'s icon draw, else {@code null}. Only Wathe's three civilian icons are
     * swapped: the role's flag first, then Conscience; one flag stands for all three sanity levels (the bar still
     * shows the level).
     * 纯函数：{@code renderCivilian} 图标绘制使用的旗帜，否则为 {@code null}。只替换 Wathe 的三个平民图标：先职业旗帜，再善良；
     * 一面旗帜代表全部三个理智档位（理智条仍显示档位）。
     */
    public static @Nullable Identifier civilianFlag(Identifier watheSprite, @Nullable Identifier roleId,
                                                    boolean conscience) {
        if (!isWatheCivilianIcon(watheSprite)) {
            return null;
        }
        Identifier own = roleFlag(roleId);
        if (own != null) {
            return own;
        }
        return conscience && !hasOwnMoodArt(roleId) ? CONSCIENCE : null;
    }

    /**
     * Pure: the flag drawn where Wathe draws no mood icon (effective {@code MoodType.NONE}); the role's own flag, else
     * {@link #NO_SANITY}.
     * 纯函数：在 Wathe 不绘制心情图标处（有效 {@code MoodType.NONE}）绘制的旗帜；优先职业自己的旗帜，否则为 {@link #NO_SANITY}。
     */
    public static Identifier noMoodFlag(@Nullable Identifier roleId) {
        Identifier own = roleFlag(roleId);
        return own != null ? own : NO_SANITY;
    }

    /**
     * Pure: roles with their own mood-icon art elsewhere (Natsume's sketchbook, Noa's cyan copies), which a trait flag
     * must never cover.
     * 纯函数：在别处已有职业自有心情图标的职业（夏目安安素描本、诺亚青色版），词条旗帜不得覆盖。
     */
    static boolean hasOwnMoodArt(@Nullable Identifier roleId) {
        return NatsumeRules.ROLE_ID.equals(roleId) || NoaRules.ROLE_ID.equals(roleId);
    }

    /** Pure: one of Wathe's three civilian icons. / 纯函数：是否为 Wathe 的三个平民图标之一。 */
    static boolean isWatheCivilianIcon(Identifier sprite) {
        return MoodRenderer.MOOD_HAPPY.equals(sprite)
                || MoodRenderer.MOOD_MID.equals(sprite)
                || MoodRenderer.MOOD_DEPRESSIVE.equals(sprite);
    }

    /**
     * Client: the sprite {@code renderKiller} should draw. The pure sprite match runs first, so a non-Wathe sprite
     * leaves before any lookup, and the trait is only read when the role has no flag.
     * 客户端：{@code renderKiller} 应绘制的 sprite。先做纯 sprite 匹配，非 Wathe sprite 在任何查询前返回；只在职业没有旗帜时才
     * 读取词条。
     */
    public static Identifier killerSprite(Identifier watheSprite) {
        if (!MoodRenderer.MOOD_KILLER.equals(watheSprite)) {
            return watheSprite;
        }
        PlayerEntity player = localPlayer();
        if (player == null) {
            return watheSprite;
        }
        Identifier roleId = roleId(player);
        boolean impostor = roleFlag(roleId) == null && SparkTraitsMoodFlagBridge.isImpostorActive(player);
        Identifier flag = killerFlag(watheSprite, roleId, impostor);
        return flag != null ? flag : watheSprite;
    }

    /**
     * Client: the sprite {@code renderCivilian} should draw; same order as {@link #killerSprite}.
     * 客户端：{@code renderCivilian} 应绘制的 sprite；顺序同 {@link #killerSprite}。
     */
    public static Identifier civilianSprite(Identifier watheSprite) {
        if (!isWatheCivilianIcon(watheSprite)) {
            return watheSprite;
        }
        PlayerEntity player = localPlayer();
        if (player == null) {
            return watheSprite;
        }
        Identifier roleId = roleId(player);
        boolean conscience = roleFlag(roleId) == null && SparkTraitsMoodFlagBridge.isConscienceActive(player);
        Identifier flag = civilianFlag(watheSprite, roleId, conscience);
        return flag != null ? flag : watheSprite;
    }

    /**
     * Client: the flag to draw after {@code renderHud} dispatched to neither icon path, else {@code null} (no role, or
     * not a confirmed SparkWitch server).
     * 客户端：{@code renderHud} 未进入任何图标路径时要绘制的旗帜，否则为 {@code null}（无职业，或未确认 SparkWitch 服务器）。
     */
    public static @Nullable Identifier noMoodSprite(@Nullable PlayerEntity player) {
        if (player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role == null ? null : noMoodFlag(role.identifier());
    }

    private static @Nullable PlayerEntity localPlayer() {
        return SparkWitchServerConnection.isConfirmedServer() ? MinecraftClient.getInstance().player : null;
    }

    /** The same role Wathe's {@code renderHud} dispatched on. / 与 Wathe {@code renderHud} 分派时相同的职业。 */
    private static @Nullable Identifier roleId(PlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role == null ? null : role.identifier();
    }

    private static Identifier flag(String name) {
        return SparkWitch.id("hud/mood_flag/" + name);
    }
}
