param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$PublicHost,
    [string]$OldPublicHost,
    [string]$ServiceName = 'mysql',
    [string]$Database = 'scenary',
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-PublicHost([string]$Value, [string]$Name) {
    $pattern = '^[A-Za-z][A-Za-z0-9+.-]*://[^\s]+$'
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -notmatch $pattern -or $Value.Contains("'") -or $Value.Contains('"')) {
        throw "$Name must be an absolute http(s) URL without whitespace or quotes."
    }
    $uri = [Uri]$Value
    if ($uri.Scheme -notin @('http', 'https') -or [string]::IsNullOrWhiteSpace($uri.Host)) {
        throw "$Name must use http or https."
    }
}

Assert-PublicHost $PublicHost 'PublicHost'
if (-not [string]::IsNullOrWhiteSpace($OldPublicHost)) {
    Assert-PublicHost $OldPublicHost 'OldPublicHost'
}

$newHost = $PublicHost.TrimEnd('/').Replace("'", "''")
$statements = @(
    "SET @public_host = '$newHost';",
    "UPDATE media SET url = CONCAT(@public_host, '/', bucket, '/', object_key), thumb_url = CASE WHEN thumb_object_key IS NULL THEN NULL ELSE CONCAT(@public_host, '/', bucket, '/', thumb_object_key) END WHERE object_key IS NOT NULL;",
    "UPDATE notes n JOIN media m ON m.note_id = n.id AND m.order_no = 1 SET n.cover_url = CASE WHEN m.thumb_object_key IS NULL THEN NULL ELSE CONCAT(@public_host, '/', m.bucket, '/', m.thumb_object_key) END WHERE n.id IS NOT NULL;"
)

if (-not [string]::IsNullOrWhiteSpace($OldPublicHost)) {
    $oldHost = $OldPublicHost.TrimEnd('/').Replace("'", "''")
    $statements += "UPDATE users SET avatar_url = CONCAT(@public_host, SUBSTRING(avatar_url, LENGTH('$oldHost') + 1)) WHERE avatar_url LIKE CONCAT('$oldHost', '/%');"
}

$sql = $statements -join [Environment]::NewLine
if ($Preview) {
    Write-Host "Would rebuild historical media/note URLs to $PublicHost (database=$Database, compose=$ComposeFile)."
    if (-not [string]::IsNullOrWhiteSpace($OldPublicHost)) {
        Write-Host "Would also migrate avatar URLs whose prefix is $OldPublicHost."
    }
    return
}

$sql | & docker compose -f $ComposeFile exec -T $ServiceName sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' migrate-media-urls $Database
if ($LASTEXITCODE -ne 0) { throw 'Media URL migration failed.' }
Write-Host "Historical media URLs migrated to $PublicHost."
