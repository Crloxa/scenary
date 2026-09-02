param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$OutputDir = (Join-Path $PSScriptRoot '..\backups'),
    [string]$ServiceName = 'mysql',
    [string]$Database = 'scenary',
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$timestamp = Get-Date -Format 'yyyy-MM-dd_HHmmss'
$outputFile = Join-Path $OutputDir "backup_$timestamp.sql"

if ($Preview) {
    $previewCommand = 'docker compose -f "' + $ComposeFile + '" exec -T ' + $ServiceName + ' sh -lc ''MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot "$1"'' backup-mysql "' + $Database + '" > "' + $outputFile + '"'
    Write-Host $previewCommand
    return
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$dump = & docker compose -f $ComposeFile exec -T $ServiceName sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot "$1"' backup-mysql $Database
[System.IO.File]::WriteAllText($outputFile, ($dump -join [Environment]::NewLine), [System.Text.UTF8Encoding]::new($false))

Write-Host "Backup written to $outputFile"
