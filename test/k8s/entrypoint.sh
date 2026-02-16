#!/usr/bin/env sh
set -eu

TOKEN_FILE="/var/run/tokens/user_token"
REFRESH_SECONDS=$((3*3600 + 30*60))  # 3h30m

fetch_token() {
  # Verwacht dat jouw backend een JSON met access_token teruggeeft.
  # Gebruik hier je interne endpoint + de K8s SA JWT (of whatever je al doet).
  token="$(curl -fsS \
    -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
    "http://backend.internal/token?execId=${EXEC_ID}" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"

  if [ -z "$token" ]; then
    echo "ERROR: no token received" >&2
    return 1
  fi

  umask 077
  printf '%s' "$token" > "$TOKEN_FILE"
}

# 1) initial
fetch_token

# 2) refresher background
(
  while true; do
    sleep "$REFRESH_SECONDS"
    fetch_token || true
  done
) &

# 3) run your real command
exec "$@"

=======
# syntax=docker/dockerfile:1

FROM python:3.11-slim

# (optioneel) basis tools voor git clone + curl naar backend
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      git \
 && rm -rf /var/lib/apt/lists/*

# Werkmap
WORKDIR /work

# Entrypoint + helper scripts
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# (optioneel) non-root user (aanrader)
RUN useradd -m -u 10001 runner \
 && mkdir -p /var/run/tokens /work \
 && chown -R runner:runner /var/run/tokens /work

USER runner

# Zorg dat python stdout direct flushed (handig voor logs)
ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
# Default command kan je overschrijven in de K8s Job spec
CMD ["python", "--version"]
