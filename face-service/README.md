# Local face service

This CPU service detects one face with YuNet, aligns and embeds it with SFace,
and checks the frame with a MiniFASNet ONNX model before returning a feature.
It does not persist or log image bytes.

## Model weights

Put these files in `models/face/` (mounted as `/models` in Compose):

| File | Source |
|------|--------|
| `face_detection_yunet_2023mar.onnx` | OpenCV Zoo, `models/face_detection_yunet/` — <https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx> |
| `face_recognition_sface_2021dec.onnx` | OpenCV Zoo, `models/face_recognition_sface/` — <https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx> |
| `minifasnet_v2.onnx` | MiniFASNetV2 (2.7_80x80) ONNX export of `minivision-ai/Silent-Face-Anti-Spoofing` — <https://hf-mirror.com/garciafido/minifasnet-v2-anti-spoofing-onnx/resolve/main/minifasnet_v2.onnx> (mirror of <https://huggingface.co/garciafido/minifasnet-v2-anti-spoofing-onnx>) |

The OpenCV Zoo files are stored with Git LFS, so the `raw.githubusercontent.com`
URL returns a pointer file; use the `media.githubusercontent.com` URLs above (or
`git lfs pull`) to get the real ONNX bytes.

The MiniFASNet export above is 1,744,116 bytes with SHA-256
`d7b3cd9ba8a7ceb13baa8c4720902e27ca3112eff52f926c08804af6b6eecc7b`.
It takes a `(1, 3, 80, 80)` float input and returns 3 logits ordered
`[live, print-attack, replay-attack]`. If you use a different checkpoint, verify
its input layout, output order and SHA-256 before trusting it.

## Anti-spoof switch

| Variable | Default | Meaning |
|----------|---------|---------|
| `FACE_ANTISPOOF_ENABLED` | `true` | Load and enforce MiniFASNet liveness. |
| `FACE_ANTISPOOF_REAL_CLASS_INDEX` | `-1` | Index of the "live" class; `-1` deliberately fails closed. |
| `FACE_ANTISPOOF_MODEL` | `/models/minifasnet_v2.onnx` | MiniFASNet file path. |
| `FACE_ANTISPOOF_INPUT_SIZE` | `80` | Square input side. |
| `FACE_ANTISPOOF_THRESHOLD` | `0.80` | Minimum live probability. |

`FACE_ANTISPOOF_ENABLED=true` keeps the fail-closed behaviour: a missing model,
an uncalibrated `FACE_ANTISPOOF_REAL_CLASS_INDEX`, or a failed liveness check
produces HTTP 503 / `liveness=false`.

`FACE_ANTISPOOF_ENABLED=false` is a demo fallback. The anti-spoof model is not
loaded or required, a WARNING is logged once at startup, single-face responses
report `"liveness": true` with `"antispoof": "demo-antispoof-disabled"`, and
`/health` exposes `antispoofEnabled: false`. Use this only for local demos, not
for production identity checks.

## Health

`GET /health` returns `status`, `ready`, `antispoofEnabled` and the loaded model
file names under `models`. When anti-spoofing is disabled it also returns
`antispoof: "demo-antispoof-disabled"`. When the service is not ready it returns
HTTP 503 with the same fields plus `error`.

Run with `docker compose --profile local-face up -d --build face`.

MiniFASNet checkpoints differ in crop, color, normalization, input size, and
output-label conventions. The current adapter uses an 80x80 RGB crop with a
1.3x bounding-box margin normalized to [-1, 1], while the upstream MiniFASNet
pipeline uses a 2.7x crop and BGR `[0, 1]`. Set `FACE_ANTISPOOF_REAL_CLASS_INDEX`
only after checking the exact checkpoint and calibrating it against real
camera/replay samples. This is a local demonstration baseline, not
production-grade liveness protection.
