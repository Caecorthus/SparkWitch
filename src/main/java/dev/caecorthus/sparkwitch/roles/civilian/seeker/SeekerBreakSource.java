package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import java.util.Locale;

/**
 * Every source that can break a Seeker device (owner decision Q4). Firecrackers, the Bomber timed bomb and poison
 * gas have no hit geometry and are deliberately absent. Battery depletion, recall and Taotie swallow are not breaks.
 * 所有能损坏搜寻者设备的来源（所有者决定 Q4）。鞭炮、定时炸弹与毒气没有命中几何，刻意不列入。
 * 电量耗尽、回收与饕餮吞噬都不算损坏。
 */
public enum SeekerBreakSource {
    MELEE(Kind.MELEE),
    REVOLVER(Kind.RAY),
    DERRINGER(Kind.RAY),
    SHOTGUN(Kind.RAY),
    DEMON_HUNTER_PISTOL(Kind.RAY),
    TASER(Kind.RAY),
    KNIFE_STAB(Kind.RAY),
    /** Piercing: the ray is cut at the nearest device. / 穿透射线：在最近的设备处截断。 */
    DEATH_RAY(Kind.RAY),
    /** Black Raven's right-click "throw" is a server hitscan (no projectile entity). / 黑鸦右键“投掷”是服务端即时射线（无投射物实体）。 */
    FEATHER_BLADE(Kind.RAY),
    /** Time Stealer Clock: a server hitscan (no projectile entity). / 窃时者时钟：服务端即时射线（无投射物实体）。 */
    CLOCK(Kind.RAY),
    THROWING_AXE(Kind.PROJECTILE),
    SHURIKEN(Kind.PROJECTILE),
    SHOCK_DEVICE(Kind.PROJECTILE),
    GRENADE(Kind.BLAST),
    M67(Kind.BLAST),
    /** Fell out of the train or left the play area: 180 s, never marks. / 掉出列车或离开游戏区域：180 秒，不标记。 */
    VOID(Kind.ENVIRONMENT),
    /** Independent single-use Fisher weapon; keeps its own item/payload identity. / 钓鱼佬独立的一次性武器与数据包。 */
    SWORDFISH(Kind.RAY),
    /**
     * Abyss Listener Shriek Gun: a server hitscan through vanilla's use-item packet, appended after the pre-existing sources so every earlier
     * replay id keeps its value. / 聆渊者啸音铳：经原版使用物品数据包的服务端即时射线，追加在既有来源之后，既有回放 id 不变。
     */
    SHRIEK_GUN(Kind.RAY),
    /**
     * Potion Gunner shell, recorded for its impact blast (sphere, never blocking), its in-flight sweep (a device in
     * the path breaks and the shell bursts there), and the launcher backblast lane (nearest wins).
     * 药炮手炮弹：命中爆炸（球形，不遮挡）、飞行扫掠（路径上的设备被打坏，炮弹在该处爆炸）以及炮筒尾焰通道（最近者命中）
     * 都记录为此来源。
     */
    POTION_SHELL(Kind.BLAST);

    /** Geometry family; rays and projectiles use nearest-wins blocking, blasts do not. / 几何类别。 */
    public enum Kind {
        MELEE,
        RAY,
        PROJECTILE,
        BLAST,
        ENVIRONMENT
    }

    private final Kind kind;

    SeekerBreakSource(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether a player breaker is recorded and may be marked. / 是否记录并可能标记损坏者。 */
    public boolean attributable() {
        return kind != Kind.ENVIRONMENT;
    }

    /** Nearest-wins: a nearer device absorbs the hit and shields the player behind it. / 最近者命中。 */
    public boolean blocksPlayerBehind() {
        return kind == Kind.RAY || kind == Kind.PROJECTILE;
    }

    public boolean isBlast() {
        return kind == Kind.BLAST;
    }

    /** Stable replay NBT value. / 稳定的回放 NBT 值。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
