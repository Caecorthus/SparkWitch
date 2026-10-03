package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One server-only death fact; {@code responsible == null} means no killer. Names are captured at death time.
 * {@code serial} identifies this particular death: the ledger book assigns a fresh, strictly increasing value on every
 * write, so a victim revived (e.g. Last Stand) and killed again gets a new serial and Prophecy records made against the
 * earlier death are recognised as stale.
 * 一条仅服务端的死亡事实；{@code responsible == null} 表示无人行凶。名字在死亡时刻抓取。
 * {@code serial} 标识这一次死亡：账本每次写入都分配一个严格递增的新值，因此被复活（如背水一战）后再次死亡的受害者会得到新序号，
 * 针对上一次死亡做出的预言记录会被识别为过期。
 */
public record ProphetDeathRecord(
        UUID victim,
        String victimName,
        Identifier deathReason,
        ProphetDeathCauseGroup group,
        @Nullable UUID responsible,
        @Nullable String responsibleName,
        long serial
) {
    /** An unnumbered fact; {@link ProphetDeathBook#record} assigns the serial. / 未编号的事实；序号由账本写入时分配。 */
    public ProphetDeathRecord(
            UUID victim,
            String victimName,
            Identifier deathReason,
            ProphetDeathCauseGroup group,
            @Nullable UUID responsible,
            @Nullable String responsibleName
    ) {
        this(victim, victimName, deathReason, group, responsible, responsibleName, 0L);
    }

    ProphetDeathRecord withSerial(long newSerial) {
        return new ProphetDeathRecord(victim, victimName, deathReason, group, responsible, responsibleName, newSerial);
    }
}
