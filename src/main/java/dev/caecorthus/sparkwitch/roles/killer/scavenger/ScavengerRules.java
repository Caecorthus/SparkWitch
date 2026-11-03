package dev.caecorthus.sparkwitch.roles.killer.scavenger;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 拾荒者 Ragpicker — killer whose knife needs no charge and whose victims vanish from most players' eyes.
 * <p>Ported from NoellesRoles' Parox build (owner, 2026-11-01) under a SparkWitch-owned id so it can coexist with
 * that mod's own 清道夫 ({@code noellesroles:scavenger}): different role ids, different body-hidden storage, no shared
 * mixin target.
 * 拾荒者——下刀无需蓄力、被杀者尸体会从大多数玩家眼里消失的杀手。
 * <p>按所有者要求（2026-11-01）从 NoellesRoles 的 Parox 版本移植，并改用 SparkWitch 自己的 id，
 * 以便与该模组自带的清道夫（{@code noellesroles:scavenger}）共存：角色 id 不同、尸体隐藏存储不同、
 * 不共用同一个 mixin 注入点。
 */
public final class ScavengerRules {

    public static final Identifier ROLE_ID = SparkWitch.id("ragpicker");

    /** Identity colour from the source build: (101, 67, 33). 沿用原实现的身份色 (101, 67, 33)。 */
    public static final int COLOR = 0x654321;

    /** NoellesRoles' noise maker: its corpse stays visible even after a Ragpicker kill. 诺艾尔的噪音制造者：尸体不隐藏。 */
    public static final Identifier NOISEMAKER_ID = Identifier.of("noellesroles", "noisemaker");

    /** Knife cooldown reset price in the shop. 商店里重置刀冷却的价格。 */
    public static final int KNIFE_RESET_PRICE = 150;

    private ScavengerRules() {
    }
}
