param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$ServiceName = 'rabbitmq',
    [string]$VHost = '/',
    [string]$Queue = 'media.dlq',
    [int]$WarnAbove = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$queues = & docker compose -f $ComposeFile exec -T $ServiceName rabbitmqctl list_queues -q -p $VHost name messages
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to query RabbitMQ queues through Docker Compose.'
}

$line = $queues | Where-Object { $_ -match ('^' + [regex]::Escape($Queue) + '\s+(\d+)$') } | Select-Object -First 1
if ($null -eq $line -or $line -notmatch '\s+(\d+)$') {
    throw "Queue '$Queue' was not found in vhost '$VHost'."
}
$depth = [int]$Matches[1]

if ($depth -gt $WarnAbove) {
    Write-Host "ALERT: DLQ depth $Queue = $depth (threshold $WarnAbove)"
    exit 2
}

Write-Host "OK: DLQ depth $Queue = $depth (threshold $WarnAbove)"
