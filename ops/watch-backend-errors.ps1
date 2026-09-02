param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$ServiceName = 'backend',
    [string]$Pattern = 'ERROR',
    [switch]$Follow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$dockerArgs = @('compose', '-f', $ComposeFile, 'logs')
if ($Follow) {
    $dockerArgs += '-f'
}
$dockerArgs += $ServiceName

& docker @dockerArgs 2>&1 | Select-String -Pattern $Pattern
