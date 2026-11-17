package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of 狮身豪 Sphinx Hao ({@code sparkwitch:sphinx_hao}), a Team Jiahao member in colour {@code FF7F50}
 * (owner spec and answers 2026-11-17).
 * <ul>
 *   <li>Dealt by the Team Jiahao third-faction lineup: when the team has a second seat it is the Insider or Sphinx Hao,
 *   50/50 (owner Q1). Fake tasks, no coins: it pays and earns only 豪度值 ("hao").</li>
 *   <li>Kit: one neutral master key and one EMPTY "手"枪 (spring-fist hand gun); 0 hao at the deal (owner Q3).</li>
 *   <li>Skill 1 嘉豪病毒 (shared ability key, {@code sparkwitch:use_sphinx_hao_infect}): infect the player in the
 *   crosshair within 3 blocks. 60 s at the deal, 10 s after each infection. The victim reads 「你感觉自己有点豪」
 *   15–25 s later.</li>
 *   <li>Skill 2 豪气冲天 (secondary skill key, {@code sparkwitch:use_sphinx_hao_roar}): for 10 s the caster and every
 *   living infected player crawl on land, hidden from every instinct, while 《Fade》 plays for everyone (owner Q2).
 *   +1 hao per infected crawler, the caster not counted (owner Q4). Every infection clears when the window ends.
 *   90 s at the deal, 30 s counted from the window's end.</li>
 *   <li>Shop: 拳头子弹 for 10 hao. The gun shoots one fist 25 blocks: a hit kills; no kill pays +5 hao and Speed III
 *   for 3 s, a kill gives 3 s Glowing and Speed I for 10 s.</li>
 * </ul>
 * Never a Witch-skill role: nothing of it appears in the {@code gui.sparkwitch.skills} panel.
 * 狮身豪（{@code sparkwitch:sphinx_hao}）的纯规则：嘉豪阵营成员，身份色 {@code FF7F50}（所有者规格与答复 2026-11-17）。
 * <ul>
 *   <li>由嘉豪阵营第三阵营阵容发放：阵营有第二个席位时，该席位为内应或狮身豪各 50%（所有者 Q1）。假任务、无金币：只赚取与
 *   支付豪度值。</li>
 *   <li>开局：一把中立万能钥匙与一把空的“手”枪（弹簧拳头枪）；豪度值为 0（所有者 Q3）。</li>
 *   <li>技能一 嘉豪病毒（共享技能键，{@code sparkwitch:use_sphinx_hao_infect}）：感染准星对准的 3 格内玩家。发牌后 60 秒，
 *   之后每次感染后 10 秒。被感染者在 15–25 秒后收到「你感觉自己有点豪」。</li>
 *   <li>技能二 豪气冲天（第二技能键，{@code sparkwitch:use_sphinx_hao_roar}）：10 秒内施法者与所有存活的感染者在陆地上
 *   游泳姿势（陆泳），不会被任何本能看到，全场播放《Fade》（所有者 Q2）。每名被感染的陆泳者 +1 豪度值，施法者不计
 *   （所有者 Q4）。窗口结束时清除全部感染。发牌后 90 秒，之后从窗口结束起 30 秒。</li>
 *   <li>商店：拳头子弹 10 豪度值。手枪发射一个拳头，射程 25 格：命中即击杀；未击杀 +5 豪度值与速度 III 3 秒，击杀获得
 *   3 秒发光与速度 I 10 秒。</li>
 * </ul>
 * 不是魔女技能职业：它的任何内容都不会出现在 {@code gui.sparkwitch.skills} 面板中。
 */
public final class SphinxHaoRules {
    public static final Identifier ROLE_ID = SparkWitch.id("sphinx_hao");
    public static final int COLOR = 0xFF7F50;

    public static final Identifier HAND_GUN_ID = SparkWitch.id("sphinx_hao_hand_gun");
    public static final Identifier FIST_AMMO_ID = SparkWitch.id("sphinx_hao_fist_ammo");
    /** Visual-only spring-fist entity. / 仅用于表现的弹簧拳头实体。 */
    public static final Identifier FIST_ENTITY_ID = SparkWitch.id("sphinx_hao_fist");
    /** Death reason of a fist kill. / 拳头击杀的死亡原因。 */
    public static final Identifier DEATH_REASON = SparkWitch.id("sphinx_hao_fist");
    /** Global music of 豪气冲天. / 豪气冲天的全场音乐。 */
    public static final Identifier FADE_SOUND_ID = SparkWitch.id("music.sphinx_hao_fade");

    /** C2S of skill 1 (ability key G, empty payload). / 技能一的 C2S（G 键，空载荷）。 */
    public static final Identifier INFECT_PAYLOAD_ID = SparkWitch.id("use_sphinx_hao_infect");
    /** C2S of skill 2 (secondary key N, empty payload). / 技能二的 C2S（N 键，空载荷）。 */
    public static final Identifier ROAR_PAYLOAD_ID = SparkWitch.id("use_sphinx_hao_roar");
    /** Forced-cooldown store ids (stores 33 and 34). / 强制冷却存储 id（第 33 与 34 号）。 */
    public static final Identifier INFECT_SKILL_ID = SparkWitch.id("sphinx_hao_infect");
    public static final Identifier ROAR_SKILL_ID = SparkWitch.id("sphinx_hao_roar");
    /** Shop entry id of the fist ammo; unique to its shop. / 拳头子弹的商店条目 id，只出现在它的商店。 */
    public static final String FIST_AMMO_SHOP_ENTRY_ID = "sparkwitch:sphinx_hao_fist_ammo";

    // Skill 1: 嘉豪病毒 / 技能一：嘉豪病毒
    public static final double INFECT_RANGE = 3.0;
    public static final int INFECT_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int INFECT_COOLDOWN_TICKS = 10 * 20;
    public static final int NOTICE_MIN_TICKS = 15 * 20;
    public static final int NOTICE_MAX_TICKS = 25 * 20;

    // Skill 2: 豪气冲天 / 技能二：豪气冲天
    public static final int ROAR_INITIAL_COOLDOWN_TICKS = 90 * 20;
    public static final int ROAR_COOLDOWN_TICKS = 30 * 20;
    public static final int ROAR_TICKS = 10 * 20;
    public static final int ROAR_HAO_PER_INFECTED = 1;
    /** Client fade-out of 《Fade》 when the window ends early. / 窗口提前结束时《Fade》的客户端淡出刻数。 */
    public static final int FADE_OUT_TICKS = 10;

    // Shop and hand gun / 商店与手枪
    public static final int FIST_AMMO_PRICE = 10;
    public static final double GUN_RANGE = 25.0;
    public static final int GUN_FIRE_COOLDOWN_TICKS = 20;
    public static final int GUN_LOAD_COOLDOWN_TICKS = 20;
    /** A shot that kills nobody: +5 hao and Speed III for 3 s. / 未击杀：+5 豪度值与 3 秒速度 III。 */
    public static final int MISS_HAO = 5;
    public static final int MISS_SPEED_AMPLIFIER = 2;
    public static final int MISS_SPEED_TICKS = 3 * 20;
    /** A kill: 3 s Glowing and Speed I for 10 s. / 击杀：3 秒发光与 10 秒速度 I。 */
    public static final int KILL_GLOW_TICKS = 3 * 20;
    public static final int KILL_SPEED_AMPLIFIER = 0;
    public static final int KILL_SPEED_TICKS = 10 * 20;
    /**
     * Spring-fist animation: at full reach the instant it appears (owner 2026-11-17), held, then pulled back.
     * 弹簧拳头动画：出现的一瞬间即伸到最远（所有者 2026-11-17），停留后收回。
     */
    public static final int FIST_HOLD_TICKS = 4;
    public static final int FIST_RETRACT_TICKS = 5;
    public static final int FIST_LIFETIME_TICKS = FIST_HOLD_TICKS + FIST_RETRACT_TICKS;

    private SphinxHaoRules() {
    }

    public static boolean isSphinxHao(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean isSphinxHao(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    /**
     * Delay of the infected player's private notice: uniform over 15–25 s inclusive.
     * 被感染者私人提示的延迟：15–25 秒（含两端）均匀分布。
     */
    public static int noticeDelayTicks(Random random) {
        return NOTICE_MIN_TICKS + random.nextInt(NOTICE_MAX_TICKS - NOTICE_MIN_TICKS + 1);
    }

    /**
     * Hao paid when 豪气冲天 starts: one per living infected crawler; the caster never counts (owner Q4).
     * 豪气冲天开始时支付的豪度值：每名存活的被感染陆泳者一点；施法者从不计入（所有者 Q4）。
     */
    public static int roarHao(int livingInfectedCrawlers) {
        return Math.max(0, livingInfectedCrawlers) * ROAR_HAO_PER_INFECTED;
    }
}
