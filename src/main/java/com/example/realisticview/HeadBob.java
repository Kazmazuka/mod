package com.example.realisticview;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 人間の歩行・体調・環境に基づくカメラ演出。
 *  - 歩行: 上下(1歩ごと+接地の衝撃)、左右、前後、斜めのロール/ヨー/ピッチ、ストレイフ傾き
 *  - 着地/ジャンプの衝撃(バネ)
 *  - 呼吸(走り続けると息切れ)、空腹で疲れ、体力低下で心拍
 *  - 高速落下の風圧振動、水中のゆらぎ
 * 位置オフセットは CameraMixin、角度は ComputeCameraAngles で適用する。
 */
public final class HeadBob {
    private static final double WALK_SPEED = 4.317;
    private static final double STEP_LENGTH = 1.45;
    private static final float LAND_K = 140f;
    private static final float LAND_C = 11.8f;

    private static boolean active;
    private static float outYaw, outPitch, outRoll;
    private static float handX, handY;

    private static long lastNs;
    private static double lastX, lastZ;
    private static boolean hasLast;
    private static double phase;
    private static float amp, spd, lat, water, exertion;
    private static double time, breathPhase, heartPhase;
    private static float sp, sv;
    private static boolean wasOnGround = true;
    private static double prevVy;

    private HeadBob() {}

    private static float sinf(double x) { return (float) Math.sin(x); }
    private static float cosf(double x) { return (float) Math.cos(x); }

    public static void markInactive() {
        active = false;
        hasLast = false;
    }

    public static float handX() { return active ? handX : 0f; }
    public static float handY() { return active ? handY : 0f; }
    public static float yawOut() { return active ? outYaw : 0f; }
    public static float pitchOut() { return active ? outPitch : 0f; }

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
        time += dt;

        Vec3 pos = p.getPosition(partial);
        double dx = hasLast ? pos.x - lastX : 0;
        double dz = hasLast ? pos.z - lastZ : 0;
        lastX = pos.x;
        lastZ = pos.z;
        hasLast = true;
        double hd = Math.hypot(dx, dz);
        if (hd > 3.0) { hd = 0; dx = 0; dz = 0; }

        float I = Config.HEAD_BOB_INTENSITY.get().floatValue();
        float D = Config.SWAY_DIAGONAL.get().floatValue();
        boolean env = Config.ENVIRONMENT.get();
        boolean vitals = Config.VITALS.get();

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

        // 息切れ(走り続けると溜まる)、空腹の疲れ、体力
        exertion += (grounded && p.isSprinting()) ? dt / 12f : -dt / 8f;
        exertion = Mth.clamp(exertion, 0f, 1f);
        float hunger = p.getFoodData().getFoodLevel() <= 6 ? 0.3f : 0f;
        float exert = vitals ? Math.max(exertion, hunger) : 0f;
        float low = vitals ? Mth.clamp((8f - p.getHealth()) / 6f, 0f, 1f) : 0f;

        float a = amp * I;
        double ph = phase;
        float s = sinf(ph);
        float c2 = cosf(2 * ph);
        float pulse = (float) Math.pow(Math.max(0f, -c2), 5); // 接地の瞬間だけ立ち上がる

        // --- 歩行(斜め成分を含む) ---
        float up = a * 0.032f * c2 - a * 0.014f * pulse + a * D * 0.010f * s;
        float side = a * 0.030f * D * s;
        float fwd = a * 0.012f * cosf(2 * ph - 0.8);
        float yawOff = a * D * 0.80f * sinf(ph + 0.4);
        float rollOff = a * D * 1.00f * s;
        float pitchOff = a * (0.45f * sinf(2 * ph + 0.6) + 0.45f * pulse) + a * D * 0.45f * sinf(ph + 0.9);

        // スプリントで前傾
        float lean = Mth.clamp(amp - 1f, 0f, 0.8f);
        pitchOff += lean * I;
        fwd += lean * 0.03f * I;

        // ストレイフ時に体が傾く
        rollOff += I * Mth.clamp(lat / (float) WALK_SPEED, -1.5f, 1.5f) * 0.9f;

        // --- 呼吸 ---
        if (Config.BREATHING.get()) {
            float calm = 1f - Mth.clamp(amp * 2f, 0f, 1f);
            float bw = Math.max(calm, exert);
            breathPhase += dt * Math.PI * 2 * (0.25f + 0.75f * exert);
            float boost = 1f + 3.4f * exert;
            up += bw * 0.0035f * boost * sinf(breathPhase) * I;
            pitchOff += bw * 0.12f * boost * sinf(breathPhase + 0.5) * I;
            rollOff += bw * 0.05f * sinf(time * Math.PI * 2 * 0.11) * I;
        }

        // --- 心拍(体力が低い時) ---
        if (low > 0f) {
            heartPhase += dt * (1.1 + 1.3 * low);
            float h = (float) (heartPhase - Math.floor(heartPhase));
            float d1 = Math.min(h, 1f - h);
            float d2 = h - 0.3f;
            float sig = 0.035f;
            float beat = (float) (Math.exp(-(d1 * d1) / (2 * sig * sig)) + 0.6 * Math.exp(-(d2 * d2) / (2 * sig * sig)));
            pitchOff += low * 0.45f * beat * I;
            up -= low * 0.006f * beat * I;
        }

        // --- 環境 ---
        water += (((env && p.isInWater()) ? 1f : 0f) - water) * (1f - (float) Math.exp(-dt * 3f));
        if (env) {
            if (!p.onGround() && !p.isInWater() && !p.getAbilities().flying) {
                float fall = Mth.clamp((float) (-p.getDeltaMovement().y - 0.7) / 0.9f, 0f, 1f);
                if (fall > 0f) {
                    float t = (float) time;
                    pitchOff += fall * 0.30f * (sinf(t * 37.1) + 0.7f * sinf(t * 53.3 + 1.2)) * I;
                    rollOff += fall * 0.35f * (sinf(t * 41.7 + 0.4) + 0.6f * sinf(t * 61.9)) * I;
                    yawOff += fall * 0.25f * (sinf(t * 47.3 + 2.1) + 0.5f * sinf(t * 67.7)) * I;
                }
            }
            if (water > 0.01f) {
                up += water * 0.020f * sinf(time * Math.PI * 2 * 0.30) * I;
                side += water * 0.015f * sinf(time * Math.PI * 2 * 0.22 + 1.0) * I;
                rollOff += water * 0.8f * sinf(time * Math.PI * 2 * 0.17) * I;
                pitchOff += water * 0.5f * sinf(time * Math.PI * 2 * 0.26 + 2.0) * I;
            }
        }

        // --- 着地・ジャンプの衝撃 ---
        if (Config.LANDING.get()) {
            up -= sp * I;
            pitchOff += sp * 28f * I;
        }

        outYaw = yawOff;
        outPitch = pitchOff;
        outRoll = rollOff;
        handX = -0.6f * side;
        handY = -0.6f * up;
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
        if (wasOnGround && !og && p.getDeltaMovement().y > 0.2) {
            sv += 0.7f; // ジャンプ直前に少し沈む
        }
        prevVy = p.getDeltaMovement().y;
        wasOnGround = og;
    }
}
