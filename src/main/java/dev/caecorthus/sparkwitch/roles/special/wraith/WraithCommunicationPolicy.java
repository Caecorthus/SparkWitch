package dev.caecorthus.sparkwitch.roles.special.wraith;

/**
 * Shared policy for active Wraith chat, voice, and Wathe dead-group access.
 *
 * <p>文字聊天仍然沿用三参数方法：未晋升的普通冤魂不能聊天，守护天使和创造模式
 * 保留原有例外。语音聊天另外使用四参数方法，因为已晋升身份已经不再属于“普通冤魂”
 * 的静音范围。</p>
 *
 * <p>这里不直接判断具体职业，只判断冤魂生命周期阶段；破坏者的杀手阵营语音边界
 * 由 {@code SaboteurVoiceRules} 单独负责。</p>
 */
public final class WraithCommunicationPolicy {
    private WraithCommunicationPolicy() {
    }

    public static boolean mayCommunicate(boolean activeWraith, boolean guardianAngel, boolean creative) {
        return !activeWraith || guardianAngel || creative;
    }

    public static boolean shouldBlockCommunication(boolean activeWraith, boolean guardianAngel, boolean creative) {
        return shouldBlockCommunication(activeWraith, false, guardianAngel, creative);
    }

    /**
     * 判断是否仍应按照“普通未晋升冤魂”处理通信。
     *
     * <p>已晋升身份虽然仍保留 active Wraith 状态和 Wathe 的死亡记录，但它们已经
     * 进入新的职业阶段，不能继续被普通冤魂的通信限制拦截。</p>
     */
    public static boolean shouldBlockCommunication(
            boolean activeWraith,
            boolean promotedWraith,
            boolean guardianAngel,
            boolean creative
    ) {
        return activeWraith
                && !promotedWraith
                && !guardianAngel
                && !creative;
    }

    /**
     * 判断已晋升的非破坏者、非守护天使身份是否应被禁止向正常存活玩家发送普通语音。
     *
     * <p>这里故意只屏蔽普通近距离语音，不屏蔽对讲机语音。对讲机事件必须继续
     * 让 Wathe 收到，否则 Wathe 无法完成频道转发。</p>
     */
    public static boolean shouldBlockPromotedCivilianVoiceToLiving(
            boolean activeWraith,
            boolean promotedWraith,
            boolean guardianAngel,
            boolean saboteur,
            boolean creative,
            boolean walkieTalkiePacket,
            boolean recipientPlayingAndAlive
    ) {
        return activeWraith
                && promotedWraith
                && !guardianAngel
                && !saboteur
                && !creative
                && !walkieTalkiePacket
                && recipientPlayingAndAlive;
    }

    /**
     * 判断已晋升诅咒者是否可以把语音直接送达存活的魔女阵营。
     *
     * <p>这里是一个只给大魔女 / 共犯保留的定向例外。这样做的目的是让诅咒者
     * 在转身后仍然能和魔女阵营继续交流，但不会把“能被存活玩家听到”的范围
     * 扩散给其他普通存活玩家。</p>
     */
    public static boolean shouldAllowPromotedCurserVoiceToLivingWitchFaction(
            boolean activeWraith,
            boolean promotedWraith,
            boolean curser,
            boolean creative,
            boolean recipientPlayingAndAlive,
            boolean livingWitchFactionRecipient
    ) {
        return activeWraith
                && promotedWraith
                && curser
                && !creative
                && recipientPlayingAndAlive
                && livingWitchFactionRecipient;
    }

    /**
     * 守护天使始终保留死者组身份，但不能把任何语音包发送给 Wathe 判定为正常存活
     * 的玩家。对死者组接收者不拦截，保证守护天使仍能在隐藏死者组中说话。
     */
    public static boolean shouldBlockGuardianAngelVoiceToLiving(
            boolean activeWraith,
            boolean promotedWraith,
            boolean guardianAngel,
            boolean recipientPlayingAndAlive
    ) {
        return activeWraith
                && promotedWraith
                && guardianAngel
                && recipientPlayingAndAlive;
    }

    public static boolean usesDeadVoiceGroup(boolean activeWraith, boolean guardianAngel) {
        return activeWraith && guardianAngel;
    }
}
