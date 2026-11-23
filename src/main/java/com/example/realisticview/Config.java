package com.example.realisticview;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue MOTION_BLUR = B
            .comment("モーションブラーを有効にする")
            .define("motionBlur", true);
    public static final ModConfigSpec.DoubleValue EXPOSURE_MS = B
            .comment("シャッター(露光)時間 ms。実カメラの180度シャッター60fps相当 = 8.3。大きいほどブラーが強い")
            .defineInRange("exposureMs", 8.3, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue MAX_BLUR_PERCENT = B
            .comment("ブラー長さの上限(画面幅に対する%)")
            .defineInRange("maxBlurPercent", 8.0, 1.0, 30.0);
    public static final ModConfigSpec.IntValue BLUR_SAMPLES = B
            .comment("ブラーのサンプル数(多いほど滑らかで重い)")
            .defineInRange("blurSamples", 16, 4, 48);

    public static final ModConfigSpec.BooleanValue HEAD_BOB = B
            .comment("リアルな歩行時の頭の揺れ(バニラのview bobbingは自動で無効化)")
            .define("headBob", true);
    public static final ModConfigSpec.DoubleValue HEAD_BOB_INTENSITY = B
            .comment("揺れ全体の強さ。1.0が現実の人間に近い値。初期値はやや控えめ")
            .defineInRange("headBobIntensity", 0.7, 0.0, 3.0);
    public static final ModConfigSpec.DoubleValue SWAY_DIAGONAL = B
            .comment("斜め方向の揺れ(ロール・ヨー・左右・斜めのピッチ)の強さ")
            .defineInRange("swayDiagonal", 0.85, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue LANDING = B
            .comment("着地・ジャンプの衝撃で視点が沈む")
            .define("landingImpact", true);
    public static final ModConfigSpec.BooleanValue BREATHING = B
            .comment("呼吸による微細な揺れ(息切れ時は大きく速くなる)")
            .define("breathing", true);
    public static final ModConfigSpec.BooleanValue VITALS = B
            .comment("体調の演出: 走り続けると息切れ、空腹で疲れ、体力が低いと心拍で視点が脈打つ")
            .define("vitals", true);
    public static final ModConfigSpec.BooleanValue ENVIRONMENT = B
            .comment("環境の演出: 高速落下時の風圧による振動、水中のゆらゆらした揺れ")
            .define("environment", true);
    public static final ModConfigSpec.BooleanValue HAND_SWAY = B
            .comment("手(持ち物)の慣性・スイングの弧・アイテムを投げる時の腕の動き")
            .define("handSway", true);

    public static final ModConfigSpec.BooleanValue SMOOTH_ANIMATION = B
            .comment("人型モブ・プレイヤーの歩行アニメを滑らかで自然にする(体のひねり、上下動、足上げ、腕の遅れ)")
            .define("smoothAnimation", true);
    public static final ModConfigSpec.DoubleValue ANIMATION_STRENGTH = B
            .comment("歩行アニメ補正の強さ")
            .defineInRange("animationStrength", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue GROUND_ITEMS = B
            .comment("ドロップアイテムを本物のように床に置く(回転・浮遊なし)。投げた時は回転しながら飛び、着地で弾んで静止する")
            .define("groundItems", true);

    // 一人称ボディ表示は不具合が多いため現在は無効(イベントを登録していません)
    public static final ModConfigSpec.BooleanValue SHOW_BODY = B
            .comment("(現在は無効化されています)一人称で体を表示する")
            .define("showBody", false);
    public static final ModConfigSpec.DoubleValue BODY_MIN_PITCH = B
            .comment("(無効化中)")
            .defineInRange("bodyMinPitch", 35.0, 0.0, 89.0);
    public static final ModConfigSpec.DoubleValue BODY_BACK_OFFSET = B
            .comment("(無効化中)")
            .defineInRange("bodyBackOffset", 0.14, 0.0, 0.5);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
