param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$BackupFile,
    [string]$ServiceName = 'mysql',
    [string]$Database = 'scenary',
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($BackupFile)) { throw 'Please provide -BackupFile pointing to a .sql dump.' }
if (-not (Test-Path -LiteralPath $BackupFile)) { throw "Backup file not found: $BackupFile" }

$restoreCommand = 'docker compose -f "' + $ComposeFile + '" exec -T ' + $ServiceName + ' sh -lc ''MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"'' restore-mysql "' + $Database + '" < "' + $BackupFile + '"'
if ($Preview) {
    Write-Host $restoreCommand
    return
}

Get-Content -LiteralPath $BackupFile -Raw | & docker compose -f $ComposeFile exec -T $ServiceName sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' restore-mysql $Database
if ($LASTEXITCODE -ne 0) { throw 'Restore failed.' }
Write-Host "Restore completed from $BackupFile"
