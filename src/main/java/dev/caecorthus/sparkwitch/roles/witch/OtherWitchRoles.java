package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoRules;
import dev.doctor4t.wathe.api.Role;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The one list of roles the Grand Witch's instinct specially recognises as witches outside her own faction and
 * outlines in {@link #INSTINCT_COLOR} (owner 2026-11-13: one class to hold them, so later callers ask here):
 * <ul>
 *   <li>the Apprentice Witch and the Murderous Witch;</li>
 *   <li>every 魔法少女的魔女裁判 (魔裁) character role, whatever its own faction (owner 2026-11-11): Emma, Noa, Tono
 *   Hanna, Natsume Anan, Tachibana Sherry, Nikaido Hiro and Sawatari Koko.</li>
 * </ul>
 * Role ids, not {@link Role} instances, so the sets need no registration order and work on both sides. A new 魔裁 role
 * adds its id to {@link #WITCH_TRIAL_GIRL_IDS}; a new non-魔裁 witch role adds its id to {@link #IDS}.
 * <p>This is instinct classification only. Membership grants nothing else: no Witch faction membership, mana, factor
 * or kill-mana bonus, Rift Gate access, trait pool or {@code gui.sparkwitch.skills} panel. 二阶堂希罗 is in it before
 * and after her 魔女化: her role-colour factor-network view is a client fallback that only fills when no instinct
 * colour answered, so the Grand Witch's held instinct shows this class colour.
 * 大魔女本能特殊识别为「阵营外的魔女」并以 {@link #INSTINCT_COLOR} 描边的职业的唯一名单（所有者 2026-11-13：用一个类集中
 * 保存，之后的调用方都来这里查询）：预备魔女与杀意魔女；以及所有《魔法少女的魔女裁判》（魔裁）角色职业，无论自身阵营
 * （所有者 2026-11-11）：艾玛、诺亚、远野汉娜、夏目安安、橘雪莉、二阶堂希罗与泽渡可可。
 * 使用职业 ID 而非 {@link Role} 实例，因此不依赖注册顺序，两端均可使用。新增魔裁职业时把 ID 加到
 * {@link #WITCH_TRIAL_GIRL_IDS}；新增非魔裁的魔女职业时把 ID 加到 {@link #IDS}。
 * <p>这只是本能分类。除此之外不授予任何东西：不是魔女阵营成员，不影响魔力、因子与击杀魔力加成、裂隙门权限、词条池，也不进入
 * {@code gui.sparkwitch.skills} 面板。二阶堂希罗在魔女化前后都属于此名单：她的职业色因子网络视图只是客户端后备，仅在没有本能
 * 颜色时补缺，因此大魔女按住本能键时看到的是此分类颜色。
 */
public final class OtherWitchRoles {
    /** The Grand Witch's outline colour for every role in this class. / 大魔女对此分类中所有职业的描边颜色。 */
    public static final int INSTINCT_COLOR = 0x7AB8FF;

    private static final Set<Identifier> WITCH_TRIAL_GIRL_IDS = Set.of(
            EmmaRules.ROLE_ID,
            NoaRules.ROLE_ID,
            HannaRules.ROLE_ID,
            NatsumeRules.ROLE_ID,
            SherryRules.ROLE_ID,
            HiroRules.ROLE_ID,
            KokoRules.ROLE_ID
    );

    private static final Set<Identifier> IDS = Stream.concat(
            Stream.of(SparkWitchRoles.APPRENTICE_WITCH_ID, SparkWitchRoles.MURDEROUS_WITCH_ID),
            WITCH_TRIAL_GIRL_IDS.stream()
    ).collect(Collectors.toUnmodifiableSet());

    private OtherWitchRoles() {
    }

    /** Every role id in the class (immutable). / 此分类中的全部职业 ID（不可变）。 */
    public static Set<Identifier> ids() {
        return IDS;
    }

    /** The 魔裁 character role ids (immutable), a subset of {@link #ids()}. / 魔裁角色职业 ID（不可变），是 {@link #ids()} 的子集。 */
    public static Set<Identifier> witchTrialGirlIds() {
        return WITCH_TRIAL_GIRL_IDS;
    }

    /** Whether the Grand Witch's instinct shows {@code role} in {@link #INSTINCT_COLOR}. / 大魔女本能是否以该分类颜色显示此职业。 */
    public static boolean contains(@Nullable Role role) {
        return role != null && IDS.contains(role.identifier());
    }

    public static boolean contains(@Nullable Identifier roleId) {
        return roleId != null && IDS.contains(roleId);
    }

    /** Whether {@code role} is a 魔裁 character role. / 该职业是否为魔裁角色。 */
    public static boolean isWitchTrialGirl(@Nullable Role role) {
        return role != null && WITCH_TRIAL_GIRL_IDS.contains(role.identifier());
    }

    public static boolean isWitchTrialGirl(@Nullable Identifier roleId) {
        return roleId != null && WITCH_TRIAL_GIRL_IDS.contains(roleId);
    }
}
