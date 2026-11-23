package com.example.realisticview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * ドロップアイテムの描画を「本物が床に置かれている」ように変える。
 *  - 静止時: 回転・浮遊なし。2Dアイテムは平らに寝て、ブロックは面を下にして置かれる。向きはアイテムごとにランダム
 *  - 投げた/落下中: 進行方向に応じて回転しながら飛ぶ(空中では地面にめり込まない高さを保つ)
 *  - 着地: 勢いに応じて弾み、回転が減衰して寝た姿勢へ滑らかに落ち着く
 *  - 水中: ゆらゆら漂う
 */
public final class GroundItems {
    private static final class State {
        final Quaternionf q = new Quaternionf();
        final Vector3f w = new Vector3f();
        boolean init, wasGround;
        long last, lastUsed;
        float vyPrev, bh, bv, restYaw, seed;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long lastPurge;

    private static boolean active;
    private static float shadowR = 0.15f;
    private static final Quaternionf outQ = new Quaternionf();
    private static final Vector3f outPos = new Vector3f();
    private static final Quaternionf REST = new Quaternionf();
    private static final Quaternionf DQ = new Quaternionf();

    private GroundItems() {}

    public static boolean isActive() { return active; }
    public static float shadowRadius() { return shadowR; }

    public static void apply(PoseStack ps) {
        ps.translate(outPos.x, outPos.y, outPos.z);
        ps.mulPose(outQ);
    }

    private static void buildRest(State s, boolean flat) {
        REST.rotationY(s.restYaw);
        if (flat) REST.rotateX(-Mth.HALF_PI); // 絵を上向きに寝かせる
    }

    public static void begin(ItemEntity e, float pt) {
        active = false;
        if (!Config.GROUND_ITEMS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BakedModel model = mc.getItemRenderer().getModel(e.getItem(), e.level(), null, e.getId());
        boolean flat = !model.isGui3d();
        ItemTransform t = model.getTransforms().getTransform(ItemDisplayContext.GROUND);
        float sx = t.scale.x(), sy = t.scale.y(), sz = t.scale.z();

        long now = Util.getNanos();
        State s = STATES.computeIfAbsent(e.getId(), k -> new State());
        s.lastUsed = now;
        buildRest(s, flat);

        if (!s.init) {
            init(s, e);
            s.last = now;
        } else if (now - s.last >= 800_000L) {
            float dt = Mth.clamp((now - s.last) / 1.0e9f, 0.0005f, 0.1f);
            step(s, e, dt);
            s.last = now;
        }
        purge(now);

        // ---- 出力: 向き ----
        outQ.set(s.q);
        float bob = 0f;
        if (e.isInWater()) {
            float tt = (e.tickCount + pt) / 20f;
            float id = s.seed * 6.2831f;
            DQ.rotationXYZ(0.14f * Mth.sin(tt * 2.1f + id), 0f, 0.14f * Mth.cos(tt * 1.7f + id));
            outQ.mul(DQ);
            bob = 0.03f * Mth.sin(tt * 2.6f + id) + 0.02f;
        }

        // ---- 出力: 高さ(傾いた箱の垂直方向の半分の広さ。地面にめり込まない) ----
        float a = 0.5f * sx;
        float b = 0.5f * sy;
        float c = flat ? 0.5f * sz * 0.0625f : 0.5f * sz;
        DQ.set(outQ).invert();
        Vector3f ul = new Vector3f(0f, 1f, 0f).rotate(DQ);
        float extent = Math.abs(ul.x) * a + Math.abs(ul.y) * b + Math.abs(ul.z) * c;
        float h = extent + 0.004f;

        // 描画中心 C = O0 + Q*T を (0, h + 弾み, 0) に合わせる。O0 は vanilla の translate 分
        Vector3f tr = new Vector3f(t.translation).rotate(outQ);
        float o0y = 0.25f * sy;
        outPos.set(-tr.x, h + s.bh + bob - tr.y - o0y, -tr.z);

        shadowR = flat ? 0.5f * sx * 0.95f + 0.02f : 0.5f * Math.max(sx, sz) + 0.06f;
        active = true;
    }

    private static void init(State s, ItemEntity e) {
        UUID u = e.getUUID();
        s.restYaw = ((u.hashCode() & 0xFFFF) / 65536f) * Mth.TWO_PI;
        s.seed = ((u.getLeastSignificantBits() >>> 7) & 0xFF) / 255f;
        boolean ground = e.onGround();
        if (ground || e.isInWater()) {
            s.q.set(REST);
        } else {
            s.q.rotationY(s.restYaw); // 縦向きで放たれ、そこから回り始める
            Vec3 v = e.getDeltaMovement();
            double hs = Math.hypot(v.x, v.z);
            if (hs > 0.02) {
                float mag = Mth.clamp((float) hs * 30f, 3f, 11f);
                s.w.set((float) (v.z / hs) * mag, (s.seed - 0.5f) * 3f, (float) (-v.x / hs) * mag);
            } else {
                float ang = s.seed * Mth.TWO_PI;
                s.w.set(Mth.sin(ang) * 4f, (s.seed - 0.5f) * 3f, Mth.cos(ang) * 4f);
            }
        }
        s.wasGround = ground;
        s.vyPrev = (float) e.getDeltaMovement().y;
        s.init = true;
    }

    private static void step(State s, ItemEntity e, float dt) {
        Vec3 v = e.getDeltaMovement();
        boolean water = e.isInWater();
        boolean ground = e.onGround() && !water;

        // 着地: 勢いに応じて弾み、回転の勢いも付く
        if (ground && !s.wasGround) {
            float vd = -s.vyPrev * 20f;
            if (vd > 2.0f) {
                s.bv = Math.min(vd * 0.30f, 2.5f);
                s.bh = 0.0001f;
                float ang = s.seed * Mth.TWO_PI + vd;
                s.w.add(Mth.sin(ang) * s.bv * 2.5f, 0f, Mth.cos(ang) * s.bv * 2.5f);
            }
            s.vyPrev = 0f;
        }
        if (!ground && !water) {
            s.vyPrev = (float) v.y;
        }
        s.wasGround = ground;

        // 弾み(放物運動)
        if (ground) {
            if (s.bh > 0f || s.bv != 0f) {
                s.bv -= 24f * dt;
                s.bh += s.bv * dt;
                if (s.bh <= 0f) {
                    s.bh = 0f;
                    s.bv = (s.bv < -0.8f) ? -s.bv * 0.35f : 0f;
                }
            }
        } else {
            s.bh = 0f;
            s.bv = 0f;
        }

        // 回転の積分(ワールド軸まわり)
        float wl = s.w.length();
        if (wl > 1e-3f) {
            DQ.rotationAxis(wl * dt, s.w.x / wl, s.w.y / wl, s.w.z / wl);
            DQ.mul(s.q, s.q);
            s.q.normalize();
        }

        if (water) {
            s.w.mul((float) Math.exp(-dt * 3f));
            s.q.slerp(REST, 1f - (float) Math.exp(-dt * 3f));
        } else if (ground) {
            boolean hopping = s.bh > 0f;
            s.w.mul((float) Math.exp(-dt * (hopping ? 3f : 12f)));
            s.q.slerp(REST, 1f - (float) Math.exp(-dt * (hopping ? 5f : 12f)));
            if (!hopping) {
                float dot = s.q.x * REST.x + s.q.y * REST.y + s.q.z * REST.z + s.q.w * REST.w;
                if (Math.abs(dot) > 0.99995f) s.q.set(REST);
            }
        }
        // 空中は回転しっぱなし
    }

    private static void purge(long now) {
        if (now - lastPurge < 5_000_000_000L) return;
        lastPurge = now;
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastUsed > 10_000_000_000L) it.remove();
        }
    }
}
