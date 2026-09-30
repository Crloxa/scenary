<#
.SYNOPSIS
    P12-E2 桶匿名策略双向切换（docs/05 §6.4）：none（默认，短时签名访问）↔ download（匿名可读，回滚模式）。
.DESCRIPTION
    经 docker compose run 一次性 aws-cli 容器对运行中的 MinIO 执行桶策略切换。
    mc 官方镜像已从 Hub 移除，工具链自 v2.48 起收敛到 amazon/aws-cli（CHANGELOG v2.48/v2.48.2）。
    桶策略持久化于 scenary-minio-data 数据卷，切换后 up -d 幂等生效，无需重跑 minio-init。
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

# MINIO_BUCKET 只在 minio-init 的 command 文本里做 compose 插值，--entrypoint 覆盖后拿不到，
# 因此从 .env 直接读桶名，凭据仍由 minio-init 服务的 AWS_* 环境注入
$envFile = Join-Path (Split-Path $ComposeFile -Parent) '.env'
if (-not (Test-Path $envFile)) {
    throw "未找到 $envFile：请先按 Phase 0 生成 .env。"
}
$bucket = (Get-Content $envFile | Where-Object { $_ -match '^\s*MINIO_BUCKET\s*=' } |
    Select-Object -First 1) -replace '^\s*MINIO_BUCKET\s*=\s*', ''
if ([string]::IsNullOrWhiteSpace($bucket)) {
    throw '.env 中未配置 MINIO_BUCKET。'
}

$endpoint = 'http://minio:9000'
$shCommand = switch ($Policy) {
    'none' {
        "aws --endpoint-url $endpoint s3api delete-bucket-policy --bucket '$bucket'"
    }
    'download' {
        $downloadPolicy = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::' + $bucket + '/*"]}]}'
        "aws --endpoint-url $endpoint s3api put-bucket-policy --bucket '$bucket' --policy '$downloadPolicy'"
    }
}

if ($Preview) {
    Write-Host "Preview（未执行）：docker compose run --rm minio-init `"$shCommand`"（entrypoint=/bin/sh -c）"
    return
}

# minio-init 的 entrypoint 已是 ["/bin/sh","-c"]，run 参数整体替换 command 即为脚本本体
& docker compose -f $ComposeFile run --rm minio-init $shCommand
if ($LASTEXITCODE -ne 0) {
    throw "bucket policy 切换失败（exit=$LASTEXITCODE）。"
}

Write-Host "已完成：桶匿名策略已切换为 $Policy。"
if ($Policy -eq 'download') {
    Write-Host '提示：这是回滚模式，需同时设 SCENARY_MEDIA_PRESIGN_READ=false 再重启 backend，读路径才会退回直链。'
}
