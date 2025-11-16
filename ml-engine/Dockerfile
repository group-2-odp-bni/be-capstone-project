FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create non-root user for security
RUN addgroup --system --gid 1001 ml && \
    adduser --system --uid 1001 --gid 1001 ml && \
    chown -R ml:ml /app

USER ml

# Environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8086

# Health check
HEALTHCHECK --interval=20s --timeout=5s --retries=5 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

EXPOSE 8086

# Run with gunicorn for production (better than Flask dev server)
CMD ["gunicorn", "--bind", "0.0.0.0:8086", "--workers", "2", "--timeout", "120", "--access-logfile", "-", "--error-logfile", "-", "app:app"]
