# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM node:26-bookworm-slim AS qmd
WORKDIR /qmd
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*
COPY qmd/package.json qmd/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    NODE_LLAMA_CPP_SKIP_DOWNLOAD=true npm_config_nodedir=/usr/local npm ci --omit=dev \
    && rm -rf node_modules/@node-llama-cpp/*-cuda \
              node_modules/@node-llama-cpp/*-cuda-ext \
              node_modules/@node-llama-cpp/*-vulkan

FROM node:26-bookworm-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates openjdk-17-jre-headless ffmpeg fonts-noto-cjk python3 python3-venv \
    && rm -rf /var/lib/apt/lists/* \
    && install -d -o node -g node /var/lib/whznt
COPY subtitle/requirements.txt ./subtitle/
RUN python3 -m venv /opt/subtitle && /opt/subtitle/bin/pip install --no-cache-dir -r subtitle/requirements.txt
RUN HF_HUB_DISABLE_XET=1 /opt/subtitle/bin/python -c "from huggingface_hub import snapshot_download; snapshot_download('Systran/faster-whisper-small', local_dir='/opt/whisper', allow_patterns=['config.json', 'model.bin', 'tokenizer.json', 'vocabulary.*'])"
COPY --from=qmd /qmd/node_modules /app/qmd/node_modules
COPY subtitle/transcribe.py ./subtitle/
COPY --from=build /build/target/whznt-backend-0.1.0.jar /app/whznt.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    WHZNT_STORAGE_DIR=/var/lib/whznt \
    WHZNT_SUBTITLE_PYTHON=/opt/subtitle/bin/python \
    WHZNT_SUBTITLE_MODEL=/opt/whisper \
    QMD_FORCE_CPU=1 \
    HF_HUB_OFFLINE=1
USER node
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-jar", "/app/whznt.jar"]
