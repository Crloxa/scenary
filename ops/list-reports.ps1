# P18 举报只读报表（docs/05 §14）：无管理员角色前，人工处置走内部脚本/只读报表（P13 边界）。
# 用法：pwsh -File ops/list-reports.ps1 [-SinceDays 7]
param(
    [int]$SinceDays = 7
)

$ErrorActionPreference = 'Stop'

function Invoke-ComposeSql([string]$Query) {
    docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"' 'list-reports' 'scenary' $Query
    if ($LASTEXITCODE -ne 0) { throw 'mysql query failed' }
}

Write-Host "== 最近 $SinceDays 天举报记录（最新 100 条） =="
Invoke-ComposeSql @"
SELECT r.id, r.created_at, r.reporter_id,
       COALESCE(r.target_note_id, '-') AS note_id,
       COALESCE(r.target_comment_id, '-') AS comment_id,
       r.reason_code, COALESCE(r.reason_text, '')
FROM reports r
WHERE r.created_at >= NOW(3) - INTERVAL $SinceDays DAY
ORDER BY r.id DESC
LIMIT 100
"@

Write-Host ''
Write-Host "== 按目标聚合（去重举报人计数 TOP 20） =="
Invoke-ComposeSql @"
SELECT 'note' AS type, target_note_id AS target_id, COUNT(DISTINCT reporter_id) AS reporters
FROM reports WHERE target_note_id IS NOT NULL
  AND created_at >= NOW(3) - INTERVAL $SinceDays DAY
GROUP BY target_note_id
UNION ALL
SELECT 'comment', target_comment_id, COUNT(DISTINCT reporter_id)
FROM reports WHERE target_comment_id IS NOT NULL
  AND created_at >= NOW(3) - INTERVAL $SinceDays DAY
GROUP BY target_comment_id
ORDER BY reporters DESC
LIMIT 20
"@

Write-Host ''
Write-Host "== 已达阈值隐藏的笔记（visibility=3） =="
Invoke-ComposeSql @"
SELECT id, user_id, report_count, LEFT(title, 32), updated_at
FROM notes WHERE visibility = 3
ORDER BY updated_at DESC
LIMIT 50
"@

Write-Host ''
Write-Host '本脚本为只读报表，不做任何修改；处置动作（如恢复 visibility）须人工核对后在库内执行。'
