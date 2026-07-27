$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repositoryRoot

function Get-DotEnvValue([string]$Name, [string]$Default = "") {
    $line = Get-Content -LiteralPath ".env" |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $Default
    }
    $value = ($line -split "=", 2)[1].Trim()
    if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        return $value.Substring(1, $value.Length - 2)
    }
    return $value
}

function Assert-ConfiguredKey([string]$Provider) {
    $keyName = switch ($Provider) {
        "openai" { "OPENAI_API_KEY" }
        "google-genai" { "GEMINI_API_KEY" }
        default { throw "Unsupported AI provider '$Provider' in .env." }
    }
    $value = Get-DotEnvValue $keyName
    if ([string]::IsNullOrWhiteSpace($value) -or $value -eq "replace-me") {
        throw "Set $keyName in .env for the active provider '$Provider'."
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not installed or is not available on PATH."
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is installed but the Docker engine is not running."
}

if (-not (Test-Path -LiteralPath ".env")) {
    Copy-Item -LiteralPath ".env.example" -Destination ".env"
    throw "Created .env from .env.example. Add the required API key, then run this command again."
}

$embeddingProvider = Get-DotEnvValue "RAG_EMBEDDING_PROVIDER" "openai"
$chatProvider = Get-DotEnvValue "RAG_CHAT_PROVIDER" "openai"
Assert-ConfiguredKey $embeddingProvider
Assert-ConfiguredKey $chatProvider

docker compose up --build --detach --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) {
    docker compose ps
    docker compose logs --tail 100 app postgres
    throw "The stack did not become healthy."
}

$appPort = Get-DotEnvValue "APP_PORT" "8080"
$adminerPort = Get-DotEnvValue "ADMINER_PORT" "8081"
$health = Invoke-RestMethod -Uri "http://127.0.0.1:$appPort/actuator/health" -TimeoutSec 10
if ($health.status -ne "UP") {
    throw "The application started but its health status is '$($health.status)'."
}

Write-Host ""
Write-Host "RAG service is ready: http://localhost:$appPort"
Write-Host "Health:               http://localhost:$appPort/actuator/health"
Write-Host "Database viewer:      http://localhost:$adminerPort"
