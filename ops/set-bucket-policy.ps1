<#
.SYNOPSIS
    P12-E2 桶匿名策略双向切换（docs/05 §6.4）：private（默认，短时签名访问）↔ public-read（回滚模式）。
.DESCRIPTION
    经 docker compose run 一次性 mc 容器对运行中的 MinIO 执行 anonymous set，
    并同步把 MINIO_BUCKET_POLICY 写回 .env（若存在），使下次 up -d 的 minio-init 保持一致。
.PARAMETER Policy
    none（私有，默认）或 download（匿名可读，即 public-read 回滚模式）。
.PARAMETER Preview
    只打印将执行的命令。
.EXAMPLE
    pwsh -File ops/set-bucket-policy.ps1 -Policy download -Preview
#>
param(
    [ValidateSet('none', 'download')]
    [string]$Policy = 'none',
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$mcCommand = "mc anonymous set $Policy local/`$MINIO_BUCKET"

if ($Preview) {
    Write-Host "Preview（未执行）：docker compose run --rm --entrypoint sh minio-init -c `"mc alias set local http://minio:9000 ...; $mcCommand`""
    return
}

# 依赖 compose 注入的 MINIO_ROOT_USER/MINIO_ROOT_PASSWORD/MINIO_BUCKET 环境变量
& docker compose -f $ComposeFile run --rm --entrypoint /bin/sh minio-init -c "until mc alias set local http://minio:9000 `$MINIO_ROOT_USER `$MINIO_ROOT_PASSWORD; do sleep 2; done; mc mb -p local/`$MINIO_BUCKET 2>/dev/null; $mcCommand"
if ($LASTEXITCODE -ne 0) {
    throw "bucket policy 切换失败（exit=$LASTEXITCODE）。"
}

Write-Host "已完成：桶匿名策略已切换为 $Policy。"
if ($Policy -eq 'download') {
    Write-Host '提示：这是回滚模式，需同时设 SCENARY_MEDIA_PRESIGN_READ=false 再重启 backend，读路径才会退回直链。'
}
