package dev.caecorthus.sparkwitch.client.bear.launcher;

/**
 * The two M2A1 reticle designs of the BEAR art package (README §7); {@link M32ReticleRules#STYLE} picks the one that
 * ships. Both share the paint, the holdover rows and the range-dial drum.
 * BEAR 美术包中的两种 M2A1 分划设计（README 第 7 节）；由 {@link M32ReticleRules#STYLE} 选择实际使用的一种。两者共用配色、
 * 抬高量刻度行与射程刻度鼓。
 */
public enum M32ReticleStyle {
    /** Option 1: a quadrant grid whose lower arm is a range ladder; the dialled row is lit. / 方案 1：象限网格。 */
    QUADRANT_GRID,
    /** Option 2 (recommended): a red dot and ring at the dialled holdover, with a side ladder. / 方案 2（推荐）：红点与刻度尺。 */
    RED_DOT_LADDER
}
