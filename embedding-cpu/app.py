import os
import threading
from pathlib import Path
from typing import Any

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict


MODEL_PATH = Path(os.getenv("EMBEDDING_MODEL_PATH", "/models/bge-m3"))
MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-m3")
MAX_BATCH_SIZE = max(1, int(os.getenv("EMBEDDING_MAX_BATCH_SIZE", "4")))
MAX_INPUT_CHARS = max(1, int(os.getenv("EMBEDDING_MAX_INPUT_CHARS", "12000")))


class EmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str | None = None
    input: str | list[str]
    encoding_format: str | None = "float"


class EmbeddingRuntime:
    def __init__(self) -> None:
        self.model: Any | None = None
        self.error = "Model is not loaded."
        self.lock = threading.Lock()

    def load(self) -> None:
        if not MODEL_PATH.is_dir():
            self.error = "Local embedding model directory is missing."
            return
        try:
            from FlagEmbedding import BGEM3FlagModel

            self.model = BGEM3FlagModel(
                str(MODEL_PATH),
                use_fp16=False,
                devices=["cpu"],
            )
            self.error = ""
        except Exception as error:
            self.model = None
            self.error = error.__class__.__name__

    def encode(self, texts: list[str]) -> list[list[float]]:
        if self.model is None:
            raise HTTPException(status_code=503, detail="Embedding model is not ready.")
        if any(not text.strip() or len(text) > MAX_INPUT_CHARS for text in texts):
            raise HTTPException(status_code=400, detail="Embedding input is empty or too long.")

        vectors: list[list[float]] = []
        with self.lock:
            for offset in range(0, len(texts), MAX_BATCH_SIZE):
                batch = texts[offset:offset + MAX_BATCH_SIZE]
                result = self.model.encode(
                    batch,
                    batch_size=MAX_BATCH_SIZE,
                    max_length=8192,
                    return_dense=True,
                    return_sparse=False,
                    return_colbert_vecs=False,
                )
                dense = np.asarray(result["dense_vecs"], dtype=np.float32)
                if dense.ndim != 2 or dense.shape[1] != 1024 or not np.isfinite(dense).all():
                    raise HTTPException(status_code=503, detail="Embedding model returned invalid vectors.")
                vectors.extend(vector.tolist() for vector in dense)
        return vectors


runtime = EmbeddingRuntime()
app = FastAPI(docs_url=None, redoc_url=None)


@app.on_event("startup")
def load_model() -> None:
    runtime.load()


@app.get("/health")
def health() -> dict[str, Any]:
    if runtime.model is None:
        raise HTTPException(status_code=503, detail="Local embedding model is not ready.")
    return {"status": "UP", "ready": True}


@app.post("/v1/embeddings")
def embeddings(request: EmbeddingRequest) -> dict[str, Any]:
    inputs = [request.input] if isinstance(request.input, str) else request.input
    if not inputs or len(inputs) > 256 or any(not isinstance(text, str) for text in inputs):
        raise HTTPException(status_code=400, detail="Invalid embedding input.")
    vectors = runtime.encode(inputs)
    return {
        "object": "list",
        "data": [
            {"object": "embedding", "index": index, "embedding": vector}
            for index, vector in enumerate(vectors)
        ],
        "model": request.model or MODEL_NAME,
        "usage": {"prompt_tokens": 0, "total_tokens": 0},
    }
