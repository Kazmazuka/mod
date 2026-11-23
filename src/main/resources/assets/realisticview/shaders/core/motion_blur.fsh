#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform mat4 PrevViewProj;
uniform vec3 CamDelta;   // 今フレームのカメラ位置 - 前フレームのカメラ位置
uniform vec4 Params;     // x: 露光/フレーム時間 比, y: 最大ブラー長(UV), z: サンプル数

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 base = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;

    // 画素のカメラ相対ワールド座標を復元
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 rel = InvViewProj * ndc;
    vec3 relPos = rel.xyz / rel.w;

    // 前フレームのカメラ基準へ(空は無限遠なので平行移動を無視)
    vec3 prevRel = relPos + (depth < 0.99999 ? CamDelta : vec3(0.0));
    vec4 pc = PrevViewProj * vec4(prevRel, 1.0);

    if (pc.w <= 0.0001) {
        fragColor = vec4(base.rgb, 1.0);
        return;
    }

    vec2 prevUV = (pc.xy / pc.w) * 0.5 + 0.5;
    vec2 vel = (texCoord - prevUV) * Params.x;
    float len = length(vel);

    if (len < 0.0004) {
        fragColor = vec4(base.rgb, 1.0);
        return;
    }
    if (len > Params.y) {
        vel *= Params.y / len;
    }

    int n = clamp(int(Params.z), 2, 64);
    float jitter = hash(gl_FragCoord.xy);
    vec3 sum = vec3(0.0);

    for (int i = 0; i < n; i++) {
        float t = (float(i) + jitter) / float(n) - 0.5;
        vec2 uv = clamp(texCoord + vel * t, vec2(0.0005), vec2(0.9995));
        float sd = texture(DepthSampler, uv).r;
        vec3 c = texture(DiffuseSampler, uv).rgb;
        // 手前の物体が奥の背景に滲まないようにする
        if ((1.0 - sd) > (1.0 - depth) * 1.6 + 0.00002) {
            c = base.rgb;
        }
        sum += c;
    }

    fragColor = vec4(sum / float(n), 1.0);
}
