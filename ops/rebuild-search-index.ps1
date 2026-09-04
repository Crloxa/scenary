param(
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [string]$ServiceName = 'mysql',
    [string]$Database = 'scenary',
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sql = @'
ANALYZE TABLE notes, users, media;
SHOW INDEX FROM notes WHERE Key_name LIKE 'idx_search_%';
SHOW INDEX FROM users WHERE Key_name = 'idx_search_status_id';
SHOW INDEX FROM media WHERE Key_name = 'idx_media_note_order';
EXPLAIN SELECT n.id
FROM notes n
INNER JOIN users u ON u.id = n.user_id AND u.status = 1
WHERE n.visibility = 1
  AND (n.title LIKE '%scenary%' OR n.content LIKE '%scenary%'
       OR n.place_name LIKE '%scenary%' OR u.nickname LIKE '%scenary%')
ORDER BY n.created_at DESC, n.id DESC
LIMIT 11;
'@

if ($Preview) {
    Write-Host "Would analyze and inspect search indexes in database '$Database' via Compose service '$ServiceName'."
    return
}

$sql | & docker compose -f $ComposeFile exec -T $ServiceName sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' rebuild-search-index $Database
if ($LASTEXITCODE -ne 0) { throw 'Search index analysis failed.' }
Write-Host 'Search index analysis and EXPLAIN completed.'
