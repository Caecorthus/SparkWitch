package dev.caecorthus.sparkwitch.client.scope;

/**
 * The player's scope picture (owner decision Q3, Options → Accessibility). {@link #ZOOM_BLUR} is the default: the
 * whole view zooms and the area outside the lens is blurred. {@link #PICTURE_IN_PICTURE} keeps a sharp 1x view around a
 * second, magnified render inside the lens ({@link ScopePictureInPicture}); it is experimental and costs FPS, and falls
 * back to {@link #ZOOM_BLUR} under an Iris shader pack, Fabulous! graphics, the Blind echo view, or while its renderer
 * backs off after a failure ({@link ScopeClient#effectiveMode()} reports what actually renders).
 * 玩家的开镜画面（所有者决定 Q3，选项 → 辅助功能）。{@link #ZOOM_BLUR} 为默认：整个画面放大、镜外模糊。
 * {@link #PICTURE_IN_PICTURE} 镜外保持清晰的 1 倍画面，镜内再渲染一次放大画面（{@link ScopePictureInPicture}）；属于实验功能且
 * 会掉帧，在 Iris 光影包、「极佳」画质、盲人回声视图下，或其渲染器失败后的退避期间退回 {@link #ZOOM_BLUR}
 * （{@link ScopeClient#effectiveMode()} 报告实际渲染的模式）。
 */
public enum ScopeMode {
    ZOOM_BLUR,
    PICTURE_IN_PICTURE
}
