FROM python:3.12-slim

RUN useradd --system --uid 10001 --home /app gateway
WORKDIR /app

COPY causalguard_gateway /app/causalguard_gateway

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    GATEWAY_HOST=0.0.0.0 \
    GATEWAY_PORT=8080

USER gateway
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import json, urllib.request; json.load(urllib.request.urlopen('http://127.0.0.1:8080/healthz', timeout=2))"

CMD ["python", "-m", "causalguard_gateway"]
