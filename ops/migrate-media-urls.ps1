<#
.SYNOPSIS
    历史 URL 运维脚本（E2-02 扩展，docs/05 §6.4）：-ToKeys 把持久化直链 URL 回填为 object key；
    默认模式保留原有"按新旧 publicHost 重建 URL"能力。
.DESCRIPTION
    -ToKeys：media.url/thumb_url、notes.cover_url、users.avatar_url 中以 http 开头的旧值
    统一剥离 "{publicHost}/{bucket}/" 前缀改存 key（E2 读路径签名所需的持久化语义）。
    notes/users 无 bucket 列，桶名经 -Bucket 传入（默认 scenary-media）。
.PARAMETER ToKeys
    执行 URL→key 回填（建议先 -Preview 核对命中行数）。
.EXAMPLE
    pwsh -File ops/migrate-media-urls.ps1 -ToKeys -Preview
#>
param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$PublicHost,
    [string]$OldPublicHost,
    [string]$ServiceName = 'mysql',
    [string]$Database = 'scenary',
    [string]$Bucket = 'scenary-media',
    [switch]$ToKeys,
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($ToKeys) {
    $safeBucket = $Bucket.Replace("'", "''")
    $marker = "CONCAT('/', '$safeBucket', '/')"
    $statements = @(
        "SELECT CONCAT('media.url -> ', COUNT(*)) FROM media WHERE url LIKE 'http%';",
        "SELECT CONCAT('media.thumb_url -> ', COUNT(*)) FROM media WHERE thumb_url LIKE 'http%';",
        "SELECT CONCAT('notes.cover_url -> ', COUNT(*)) FROM notes WHERE cover_url LIKE 'http%';",
        "SELECT CONCAT('users.avatar_url -> ', COUNT(*)) FROM users WHERE avatar_url LIKE 'http%';",
        "UPDATE media SET url = SUBSTRING(url, LOCATE($marker, url) + CHAR_LENGTH($marker)) WHERE url LIKE 'http%' AND LOCATE($marker, url) > 0;",
        "UPDATE media SET thumb_url = SUBSTRING(thumb_url, LOCATE($marker, thumb_url) + CHAR_LENGTH($marker)) WHERE thumb_url LIKE 'http%' AND LOCATE($marker, thumb_url) > 0;",
        "UPDATE notes SET cover_url = SUBSTRING(cover_url, LOCATE($marker, cover_url) + CHAR_LENGTH($marker)) WHERE cover_url LIKE 'http%' AND LOCATE($marker, cover_url) > 0;",
        "UPDATE users SET avatar_url = SUBSTRING(avatar_url, LOCATE($marker, avatar_url) + CHAR_LENGTH($marker)) WHERE avatar_url LIKE 'http%' AND LOCATE($marker, avatar_url) > 0;"
    )
    $sql = $statements -join [Environment]::NewLine
    if ($Preview) {
        Write-Host "Preview（-ToKeys，database=$Database, bucket=$Bucket）："
        Write-Host $sql
        return
    }
    $sql | & docker compose -f $ComposeFile exec -T $ServiceName sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' migrate-media-urls $Database
    if ($LASTEXITCODE -ne 0) { throw 'URL -> key 回填失败。' }
    Write-Host '回填完成：持久化值已统一为 object key。'
    return
}

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
