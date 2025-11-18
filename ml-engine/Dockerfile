FROM python:3.11-slim

WORKDIR /app

# Install system dependencies + protoc 3.21.12
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    ca-certificates \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

RUN curl -L -o protoc.zip https://github.com/protocolbuffers/protobuf/releases/download/v21.12/protoc-21.12-linux-x86_64.zip \
    && unzip protoc.zip -d /usr/local \
    && rm protoc.zip

# Add protoc binary to PATH
ENV PATH="/usr/local/bin:${PATH}"

# Copy requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Generate protobuf Python code INSIDE DOCKER
RUN protoc --python_out=. payment_status_updated.proto

# Copy supervisor config
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Create non-root user
RUN addgroup --system --gid 1001 ml && \
    adduser --system --uid 1001 --gid 1001 ml && \
    chown -R ml:ml /app

USER ml

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8086

HEALTHCHECK --interval=20s --timeout=5s --retries=5 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

EXPOSE 8086

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
