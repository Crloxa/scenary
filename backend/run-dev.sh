#!/usr/bin/env bash
# 本地启动后端：把仓库根 .env 逐行导入为进程环境变量再起 Spring Boot。
# 不用 `source ../.env`：compose 风格的值可含空格（如 REDIS_ARGS=--appendonly no），
# source 会把空格后的词当命令执行。
set -e
cd "$(dirname "$0")"
if [ -f ../.env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"
    val="${line#*=}"
    [ "$key" = "$line" ] && continue   # 无等号的行跳过
    export "$key=$val"
  done < ../.env
fi

# 版本守卫：项目基线 JDK 21。长生命周期的终端可能仍持有改默认前的旧 JAVA_HOME，
# 此处检测到 <21 就回落到本机 .jdks 下最高版本的 ms-21 目录。
cur_major="$("${JAVA_HOME:+$JAVA_HOME/bin/java}" -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')"
if [ -z "$cur_major" ] || [ "$cur_major" -lt 21 ]; then
  fallback=""
  for cand in "$HOME"/.jdks/ms-21*; do
    [ -d "$cand" ] && fallback="$cand"
  done
  if [ -z "$fallback" ]; then
    echo "[run-dev] 需要 JDK>=21：JAVA_HOME=${JAVA_HOME:-未设置} 且未找到 ~/.jdks/ms-21*" >&2
    exit 1
  fi
  echo "[run-dev] JAVA_HOME 指向 JDK ${cur_major:-未知}，切换到 $fallback"
  export JAVA_HOME="$(cygpath -w "$fallback")"
fi

exec mvn spring-boot:run -Dspring-boot.run.profiles=dev "$@"
