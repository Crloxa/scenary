<#
.SYNOPSIS
    P15-03a 密码找回运维脚本（docs/05 §7.2）：管理员为本机用户重置登录密码。
.DESCRIPTION
    用本机 JDK jshell + spring-security-crypto 生成 BCrypt 哈希（与服务端同算法），
    再经 docker compose exec 在 mysql 容器内执行 UPDATE。支持 -Preview 干跑与
    -Reactivate 顺带恢复被禁用/注销的账号。真实密码与 .env 严禁入库。
.PARAMETER Username
    目标用户名（4~20 位字母/数字/下划线）。
.PARAMETER NewPassword
    新密码（至少 8 位且同时含字母和数字，与服务端注册规则一致）。
.PARAMETER Reactivate
    同时把 users.status 恢复为 1（注销/禁用账号恢复属运维动作，docs/02 §3.8）。
.PARAMETER Preview
    只打印将执行的 SQL 与命令，不落库。
.EXAMPLE
    pwsh -File ops/reset-user-password.ps1 -Username hill_walker -NewPassword 'Passw0rd!2026' -Preview
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9_]{4,20}$')]
    [string]$Username,
    [Parameter(Mandatory = $true)]
    [ValidateLength(8, 64)]
    [string]$NewPassword,
    [string]$ComposeFile = (Join-Path $PSScriptRoot '..\docker-compose.yml'),
    [switch]$Reactivate,
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($NewPassword -notmatch '[A-Za-z]' -or $NewPassword -notmatch '\d') {
    throw '新密码需至少同时包含字母和数字（与注册规则一致）'
}

# ---- 定位本机 JDK jshell 与 spring-security-crypto ----
$jshell = Get-Command jshell -ErrorAction SilentlyContinue
if (-not $jshell) {
    throw '未找到 jshell（需 JDK 11+，与 backend/run-dev.sh 同一环境）。可手动生成哈希后在 MySQL 执行 UPDATE。'
}
# 本地仓库优先由 Maven 自身解析（支持自定义 localRepository），失败回退 ~/.m2/repository
$backendPom = Join-Path $PSScriptRoot '..\backend\pom.xml'
function Get-LocalMavenRepo {
    try {
        $repo = (& mvn -q '-f' $backendPom 'help:evaluate' '-Dexpression=settings.localRepository' '-DforceStdout' 2>$null |
            Select-Object -Last 1)
        if ($repo -and (Test-Path $repo)) { return $repo }
    } catch {
        # 忽略，走回退
    }
    return (Join-Path $env:USERPROFILE '.m2\repository')
}
$repo = Get-LocalMavenRepo
$cryptoRoot = Join-Path $repo 'org\springframework\security\spring-security-crypto'
if (-not (Test-Path $cryptoRoot)) {
    throw "未找到本地 Maven 仓库中的 spring-security-crypto（$cryptoRoot）。先在 backend 目录执行过 mvn 编译即可。"
}
$cryptoJar = Get-ChildItem $cryptoRoot -Recurse -Filter 'spring-security-crypto-*.jar' |
    Sort-Object Name -Descending | Select-Object -First 1
$classPath = $cryptoJar.FullName
# BCryptPasswordEncoder 依赖 commons-logging（spring-jcl 提供），存在则一并加入
$jclRoot = Join-Path $repo 'org\springframework\spring-jcl'
if (Test-Path $jclRoot) {
    $jclJar = Get-ChildItem $jclRoot -Recurse -Filter 'spring-jcl-*.jar' |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jclJar) { $classPath = "$classPath;$($jclJar.FullName)" }
}

# ---- 生成 BCrypt 哈希（$2a$10，与 BCryptPasswordEncoder 默认一致）----
$escaped = $NewPassword.Replace('\', '\\').Replace('"', '\"')
$jshellScript = Join-Path $env:TEMP ("scenary-reset-" + [guid]::NewGuid().ToString('N') + ".jsh")
@"
var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
System.out.println("SCENARY_HASH=" + encoder.encode("$escaped"));
System.out.println("SCENARY_MATCH=" + encoder.matches("$escaped", encoder.encode("$escaped")));
/exit
"@ | Set-Content -Path $jshellScript -Encoding ASCII

try {
    $jshellOutput = & jshell --class-path $classPath -q $jshellScript 2>&1
    $hashLine = $jshellOutput | Select-String -SimpleMatch 'SCENARY_HASH=' | Select-Object -First 1
    if (-not $hashLine) {
        throw "jshell 未输出哈希。原始输出：`n$($jshellOutput -join "`n")"
    }
    $hash = ($hashLine.ToString() -split 'SCENARY_HASH=')[1].Trim()
    if ($hash -notmatch '^\$2[aby]\$10\$') {
        throw "生成的哈希格式异常：$hash"
    }
}
finally {
    Remove-Item $jshellScript -ErrorAction SilentlyContinue
}

# ---- 组装并执行 SQL ----
$statusClause = if ($Reactivate) { ", status = 1" } else { "" }
$guard = if ($Reactivate) { "status IN (0, 1, 2)" } else { "status IN (0, 1)" }
$sql = "UPDATE users SET password_hash = '$hash'$statusClause WHERE username = '$Username' AND $guard;"

if ($Preview) {
    Write-Host "Preview（未执行）："
    Write-Host "  classpath: $classPath"
    Write-Host "  SQL: $sql"
    return
}

$mysqlArgs = @('compose', '-f', $ComposeFile, 'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "$1"',
    'reset-user-password', $sql)
& docker @mysqlArgs
if ($LASTEXITCODE -ne 0) {
    throw "MySQL 更新失败（exit=$LASTEXITCODE），未修改任何行。"
}

Write-Host "已完成：用户 $Username 的密码已重置。新密码仅在本次命令行传入，请妥善告知用户。"
if ($Reactivate) {
    Write-Host "账号状态已恢复为正常（status=1）。"
}
