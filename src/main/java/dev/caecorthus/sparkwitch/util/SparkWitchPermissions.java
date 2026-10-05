package dev.caecorthus.sparkwitch.util;

/**
 * Permission nodes for SparkWitch admin commands.
 * SparkWitch 管理员命令的权限节点，未安装权限插件时回退到默认 op 等级。
 */
public final class SparkWitchPermissions {
    public static final int DEFAULT_COMMAND_LEVEL = 2;
    public static final String COMMAND_SET_MANA = "sparkwitch.command.setmana";
    public static final String COMMAND_FORCE_ABILITY = "sparkwitch.command.forceability";
    public static final String COMMAND_FORCE_PROMOTION = "sparkwitch.command.forcepromotion";
    /** {@code /sparkwitch:forceAccompliceRole} (D4). / 锁定魔化使晋升身份的命令（D4）。 */
    public static final String COMMAND_FORCE_ACCOMPLICE_ROLE = "sparkwitch.command.forceaccomplicerole";
    public static final String COMMAND_GHOST_CHANCE = "sparkwitch.command.ghostchance";
    public static final String COMMAND_GHOST_MIN_REQUIREMENT = "sparkwitch.command.ghostminrequirement";
    /** Using the Rift Gate Remover (传送门清除工具). / 使用传送门清除工具。 */
    public static final String ITEM_RIFT_GATE_REMOVER = "sparkwitch.item.riftgateremover";

    private SparkWitchPermissions() {
    }
}
