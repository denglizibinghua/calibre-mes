import base64
import binascii
import logging
import os
from dataclasses import dataclass
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field


logger = logging.getLogger("face-service")


def env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() not in {"0", "false", "no", "off"}


MAX_IMAGE_BYTES = int(os.getenv("FACE_MAX_IMAGE_BYTES", str(4 * 1024 * 1024)))
MAX_IMAGE_SIDE = 4096
ANTISPOOF_ENABLED = env_flag("FACE_ANTISPOOF_ENABLED", True)
ANTISPOOF_DISABLED_MARKER = "demo-antispoof-disabled"
ANTISPOOF_SIZE = int(os.getenv("FACE_ANTISPOOF_INPUT_SIZE", "80"))
ANTISPOOF_REAL_CLASS = int(os.getenv("FACE_ANTISPOOF_REAL_CLASS_INDEX", "-1"))
ANTISPOOF_THRESHOLD = float(os.getenv("FACE_ANTISPOOF_THRESHOLD", "0.80"))


class EmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    imageBase64: str = Field(min_length=1, max_length=5_600_000)


@dataclass
class FaceModels:
    detector: Any
    recognizer: Any
    antispoof: Any | None


class FaceRuntime:
    def __init__(self) -> None:
        self.models: FaceModels | None = None
        self.error = "Models are not loaded."
        self.antispoof_enabled = ANTISPOOF_ENABLED
        self.loaded_models: dict[str, str | None] = {
            "detector": None,
            "recognizer": None,
            "antispoof": None,
        }
        if not self.antispoof_enabled:
            logger.warning(
                "FACE_ANTISPOOF_ENABLED is false: anti-spoofing is DISABLED. "
                "Liveness will always be reported as true for demo use only."
            )
        self.load_models()

    def load_models(self) -> None:
        try:
            if MAX_IMAGE_BYTES < 1:
                raise ValueError("Maximum image size is invalid.")
            detector_path = os.getenv(
                "FACE_DETECTOR_MODEL",
                "/models/face_detection_yunet_2023mar.onnx",
            )
            recognizer_path = os.getenv(
                "FACE_RECOGNIZER_MODEL",
                "/models/face_recognition_sface_2021dec.onnx",
            )
            required_paths = [detector_path, recognizer_path]
            antispoof_path: str | None = None
            if self.antispoof_enabled:
                if ANTISPOOF_REAL_CLASS < 0:
                    raise ValueError("Live class index has not been calibrated.")
                if ANTISPOOF_SIZE < 16 or ANTISPOOF_SIZE > 512:
                    raise ValueError("Anti-spoof input size is invalid.")
                if not 0.0 <= ANTISPOOF_THRESHOLD <= 1.0:
                    raise ValueError("Anti-spoof threshold is invalid.")
                antispoof_path = os.getenv(
                    "FACE_ANTISPOOF_MODEL",
                    "/models/minifasnet_v2.onnx",
                )
                required_paths.append(antispoof_path)
            for path in required_paths:
                if not os.path.isfile(path):
                    raise FileNotFoundError("A required face model is missing.")

            cv2.setNumThreads(1)
            detector = cv2.FaceDetectorYN.create(
                detector_path, "", (320, 320), 0.85, 0.30, 5000
            )
            recognizer = cv2.FaceRecognizerSF.create(recognizer_path, "")
            antispoof = (
                cv2.dnn.readNetFromONNX(antispoof_path)
                if antispoof_path is not None
                else None
            )
            self.models = FaceModels(detector, recognizer, antispoof)
            self.loaded_models = {
                "detector": os.path.basename(detector_path),
                "recognizer": os.path.basename(recognizer_path),
                "antispoof": (
                    os.path.basename(antispoof_path)
                    if antispoof_path is not None
                    else None
                ),
            }
            self.error = ""
        except Exception as error:
            self.models = None
            self.loaded_models = {
                "detector": None,
                "recognizer": None,
                "antispoof": None,
            }
            self.error = error.__class__.__name__

    def extract(self, encoded: str) -> dict[str, Any]:
        models = self.models
        if models is None:
            raise HTTPException(status_code=503, detail="Face recognition is not configured.")
        marker = (
            {}
            if self.antispoof_enabled
            else {"antispoof": ANTISPOOF_DISABLED_MARKER}
        )

        image_bytes = decode_image(encoded)
        image = cv2.imdecode(np.frombuffer(image_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
        if image is None or image.size == 0:
            raise HTTPException(status_code=400, detail="Invalid image.")
        height, width = image.shape[:2]
        if width > MAX_IMAGE_SIDE or height > MAX_IMAGE_SIDE:
            raise HTTPException(status_code=413, detail="Image dimensions are too large.")

        models.detector.setInputSize((width, height))
        _, faces = models.detector.detect(image)
        face_count = 0 if faces is None else int(faces.shape[0])
        if face_count != 1:
            return {
                "faceCount": face_count,
                "liveness": False,
                "embedding": [],
                **marker,
            }

        face = faces[0]
        if self.antispoof_enabled:
            live_score = self.liveness_score(models.antispoof, image, face)
            if live_score < ANTISPOOF_THRESHOLD:
                return {"faceCount": 1, "liveness": False, "embedding": []}

        aligned = models.recognizer.alignCrop(image, face)
        feature = np.asarray(models.recognizer.feature(aligned), dtype=np.float64).reshape(-1)
        norm = float(np.linalg.norm(feature))
        if feature.size < 64 or feature.size > 4096 or not np.isfinite(feature).all() or norm == 0:
            raise HTTPException(status_code=503, detail="Face model returned an invalid feature.")
        feature = feature / norm
        return {
            "faceCount": 1,
            "liveness": True,
            "embedding": feature.tolist(),
            **marker,
        }

    @staticmethod
    def liveness_score(net: Any, image: np.ndarray, face: np.ndarray) -> float:
        x, y, width, height = (float(value) for value in face[:4])
        margin_x = width * 0.15
        margin_y = height * 0.15
        left = max(0, int(x - margin_x))
        top = max(0, int(y - margin_y))
        right = min(image.shape[1], int(x + width + margin_x))
        bottom = min(image.shape[0], int(y + height + margin_y))
        crop = image[top:bottom, left:right]
        if crop.size == 0:
            return 0.0

        # MiniFASNet checkpoints vary in crop and channel conventions; calibrate
        # this adapter against the exact ONNX file and target cameras.
        blob = cv2.dnn.blobFromImage(
            cv2.resize(crop, (ANTISPOOF_SIZE, ANTISPOOF_SIZE)),
            1.0 / 127.5,
            (ANTISPOOF_SIZE, ANTISPOOF_SIZE),
            (127.5, 127.5, 127.5),
            swapRB=True,
            crop=False,
        )
        net.setInput(blob)
        scores = np.asarray(net.forward(), dtype=np.float64).reshape(-1)
        if not np.isfinite(scores).all() or scores.size <= ANTISPOOF_REAL_CLASS:
            raise HTTPException(status_code=503, detail="Anti-spoof model returned invalid scores.")
        if scores.size == 1:
            return float(1.0 / (1.0 + np.exp(-np.clip(scores[0], -80, 80))))
        scores -= scores.max()
        probabilities = np.exp(scores)
        probabilities /= probabilities.sum()
        return float(probabilities[ANTISPOOF_REAL_CLASS])


def decode_image(value: str) -> bytes:
    encoded = value.split(",", 1)[1] if value.startswith("data:") and "," in value else value
    if len(encoded) > 5_600_000:
        raise HTTPException(status_code=413, detail="Image is too large.")
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="Invalid base64 image.") from None
    if not raw or len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image is too large.")
    return raw


runtime = FaceRuntime()
app = FastAPI(docs_url=None, redoc_url=None)


@app.get("/health")
def health() -> Any:
    payload: dict[str, Any] = {
        "status": "UP" if runtime.models is not None else "DOWN",
        "ready": runtime.models is not None,
        "antispoofEnabled": runtime.antispoof_enabled,
        "models": runtime.loaded_models,
    }
    if not runtime.antispoof_enabled:
        payload["antispoof"] = ANTISPOOF_DISABLED_MARKER
    if runtime.models is None:
        payload["error"] = runtime.error
        return JSONResponse(status_code=503, content=payload)
    return payload


@app.post("/v1/face/embedding")
def embedding(request: EmbeddingRequest) -> dict[str, Any]:
    return runtime.extract(request.imageBase64)
