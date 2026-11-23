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
            .comment("揺れの強さ。1.0が現実の人間に近い値")
            .defineInRange("headBobIntensity", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue LANDING = B
            .comment("着地時の衝撃で視点が沈む")
            .define("landingImpact", true);
    public static final ModConfigSpec.BooleanValue BREATHING = B
            .comment("立ち止まっている時の呼吸による微細な揺れ")
            .define("breathing", true);

    public static final ModConfigSpec.BooleanValue SHOW_BODY = B
            .comment("一人称視点で自分の体を表示する(真下を見ると見える)")
            .define("showBody", true);
    public static final ModConfigSpec.DoubleValue BODY_BACK_OFFSET = B
            .comment("下を向いた時に体を後ろへずらす量(ブロック)。胸の前面が見えるようになる")
            .defineInRange("bodyBackOffset", 0.14, 0.0, 0.5);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
