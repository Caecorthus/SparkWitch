package dev.caecorthus.sparkwitch.roles.civilian.judge;

import java.util.UUID;

/** Implemented only by the pinned Voodoo pending-death adapter. / 仅由固定版本巫毒待结算适配器实现。 */
public interface JudgeVoodooCause {
    void sparkwitch$setPendingVoodooActor(UUID actor);
}
