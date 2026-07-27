#!/usr/bin/env sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$repository_root"

env_value() {
    name=$1
    default_value=${2:-}
    value=$(sed -n "s/^[[:space:]]*${name}=//p" .env | head -n 1 | tr -d '\r')
    if [ -z "$value" ]; then
        value=$default_value
    fi
    case "$value" in
        \"*\") value=${value#\"}; value=${value%\"} ;;
        \'*\') value=${value#\'}; value=${value%\'} ;;
    esac
    printf '%s' "$value"
}

assert_configured_key() {
    provider=$1
    case "$provider" in
        openai) key_name=OPENAI_API_KEY ;;
        google-genai) key_name=GEMINI_API_KEY ;;
        *) echo "Unsupported AI provider '$provider' in .env." >&2; exit 1 ;;
    esac
    value=$(env_value "$key_name")
    if [ -z "$value" ] || [ "$value" = "replace-me" ]; then
        echo "Set $key_name in .env for the active provider '$provider'." >&2
        exit 1
    fi
}

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is not installed or is not available on PATH." >&2
    exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo "Docker is installed but the Docker engine is not running." >&2
    exit 1
fi

if [ ! -f .env ]; then
    cp .env.example .env
    echo "Created .env from .env.example. Add the required API key, then run this command again." >&2
    exit 1
fi

embedding_provider=$(env_value RAG_EMBEDDING_PROVIDER openai)
chat_provider=$(env_value RAG_CHAT_PROVIDER openai)
assert_configured_key "$embedding_provider"
assert_configured_key "$chat_provider"

if ! docker compose up --build --detach --wait --wait-timeout 180; then
    docker compose ps
    docker compose logs --tail 100 app postgres
    exit 1
fi

app_port=$(env_value APP_PORT 8080)
adminer_port=$(env_value ADMINER_PORT 8081)

echo
echo "RAG service is ready: http://localhost:$app_port"
echo "Health:               http://localhost:$app_port/actuator/health"
echo "Database viewer:      http://localhost:$adminer_port"
