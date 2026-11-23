package com.example.realisticview;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 人間の歩行モデルに基づく頭の揺れ。
 *  - 上下: 1歩ごと(周波数2倍)+ 接地の瞬間に鋭く沈む
 *  - 左右: 1周期ごとの体重移動
 *  - 前後・ヨー・ロール・ピッチの微小な揺れ
 *  - ストレイフ時の傾き、着地の衝撃(バネ)、停止時の呼吸
 * 位置オフセットは CameraMixin、角度は ComputeCameraAngles で適用する。
 */
public final class HeadBob {
    private static final double WALK_SPEED = 4.317;   // ブロック/秒
    private static final double STEP_LENGTH = 1.45;   // 1歩の長さ(ブロック)。歩行速度4.3m/sで約3歩/秒
    private static final float LAND_K = 140f;
    private static final float LAND_C = 11.8f;

    private static boolean active;
    private static float outYaw, outPitch, outRoll;

    private static long lastNs;
    private static double lastX, lastZ;
    private static boolean hasLast;
    private static double phase;
    private static float amp, spd, lat;
    private static double breathTime;
    private static float sp, sv; // 着地バネ(下方向への沈み量 / 速度)
    private static boolean wasOnGround = true;
    private static double prevVy;

    private HeadBob() {}

    public static void markInactive() {
        active = false;
        hasLast = false;
    }

    /** 毎フレーム Camera.setup の末尾から呼ばれる。カメラ位置のオフセットを返す。 */
    public static Vec3 update(float partial, float camYawDeg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !Config.HEAD_BOB.get() || p.isSleeping()) {
            markInactive();
            return Vec3.ZERO;
        }

        long now = Util.getNanos();
        float dt = hasLast ? Mth.clamp((now - lastNs) / 1.0e9f, 0.0005f, 0.1f) : 0.016f;
        lastNs = now;

        Vec3 pos = p.getPosition(partial);
        double dx = hasLast ? pos.x - lastX : 0;
        double dz = hasLast ? pos.z - lastZ : 0;
        lastX = pos.x;
        lastZ = pos.z;
        hasLast = true;
        double hd = Math.hypot(dx, dz);
        if (hd > 3.0) { hd = 0; dx = 0; dz = 0; }

        float intensity = Config.HEAD_BOB_INTENSITY.get().floatValue();
        boolean grounded = p.onGround() && !p.isPassenger() && !p.getAbilities().flying
                && !p.isInWater() && !p.onClimbable() && !p.isFallFlying();

        float yawRad = camYawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
        double rx = -Mth.cos(yawRad), rz = -Mth.sin(yawRad);

        float k = 1f - (float) Math.exp(-dt * 12f);
        spd += ((float) (hd / dt) - spd) * k;
        lat += ((float) ((dx * rx + dz * rz) / dt) - lat) * k;

        float target = grounded ? Mth.clamp(spd / (float) WALK_SPEED, 0f, 1.8f) : 0f;
        amp += (target - amp) * (1f - (float) Math.exp(-dt * (target > amp ? 14f : 9f)));
        if (grounded) phase += hd * Math.PI / STEP_LENGTH;

        // 着地バネ
        float rem = dt;
        while (rem > 0f) {
            float h = Math.min(rem, 0.004f);
            sv += (-LAND_K * sp - LAND_C * sv) * h;
            sp += sv * h;
            rem -= h;
        }

        float a = amp * intensity;
        float s = (float) Math.sin(phase);
        float c2 = (float) Math.cos(2 * phase);
        float pulse = (float) Math.pow(Math.max(0f, -c2), 5); // 接地の瞬間だけ立ち上がる

        float up = a * 0.032f * c2 - a * 0.014f * pulse;
        float side = a * 0.022f * s;
        float fwd = a * 0.012f * (float) Math.cos(2 * phase - 0.8);
        float yawOff = a * 0.40f * (float) Math.sin(phase + 0.4);
        float rollOff = a * 0.55f * s;
        float pitchOff = a * (0.45f * (float) Math.sin(2 * phase + 0.6) + 0.45f * pulse);

        // ストレイフ時に体が傾く
        rollOff += intensity * Mth.clamp(lat / (float) WALK_SPEED, -1.5f, 1.5f) * 0.9f;

        // 呼吸
        breathTime += dt;
        if (Config.BREATHING.get()) {
            float calm = 1f - Mth.clamp(amp * 2f, 0f, 1f);
            float br = (float) Math.sin(breathTime * Math.PI * 2 * 0.25);
            up += calm * 0.0035f * br * intensity;
            pitchOff += calm * 0.12f * (float) Math.sin(breathTime * Math.PI * 2 * 0.25 + 0.5) * intensity;
            rollOff += calm * 0.05f * (float) Math.sin(breathTime * Math.PI * 2 * 0.11) * intensity;
        }

        // 着地の衝撃
        if (Config.LANDING.get()) {
            up -= sp * intensity;
            pitchOff += sp * 28f * intensity;
        }

        outYaw = yawOff;
        outPitch = pitchOff;
        outRoll = rollOff;
        active = true;

        return new Vec3(rx * side + fx * fwd, up, rz * side + fz * fwd);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!active) return;
        e.setYaw(e.getYaw() + outYaw);
        e.setPitch(e.getPitch() + outPitch);
        e.setRoll(e.getRoll() + outRoll);
    }

    public static void onClientTick(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            wasOnGround = true;
            prevVy = 0;
            return;
        }
        boolean og = p.onGround();
        if (!wasOnGround && og && !p.isInWater()) {
            float impact = (float) Mth.clamp(-prevVy, 0.0, 1.5);
            if (impact > 0.2f) sv += impact * 2.4f;
        }
        prevVy = p.getDeltaMovement().y;
        wasOnGround = og;
    }
}
