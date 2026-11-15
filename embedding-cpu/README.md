# CPU BGE-M3 embedding API

This service loads a local BGE-M3 checkpoint with FlagEmbedding on CPU and
exposes the dense vector endpoint Spring AI already expects:
`POST /v1/embeddings`. It returns 1024-dimensional vectors and keeps no model
cache or input text outside process memory.

Mount a complete local BGE-M3 model directory at `/models/bge-m3` before
starting the `local-embedding-cpu` Compose profile. The service runs offline
and reports unhealthy if the model files are absent or cannot be loaded.

CPU mode trades throughput for lower GPU memory use. Tune batch size and thread
count to the host CPU; the default is intentionally conservative for local
development.
