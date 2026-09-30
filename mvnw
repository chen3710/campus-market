#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 项目自带 Maven 启动器（Git Bash / WSL / Linux / macOS 通用）
#
# 为什么需要它：
#   官方 mvn 脚本在 MinGW(Git Bash) 下存在路径转换缺陷 —— 第 88-95 行把
#   MAVEN_HOME 转成 Unix 形式(/e/Maven/...)，但把路径转回 Windows 形式的
#   逻辑只在 cygwin 分支执行，导致传给 java 的 classpath 是 Unix 路径，
#   最终报 ClassNotFoundException: plexus.classworlds.launcher.Launcher。
#   本脚本直接以 Windows 路径调用 java 内核，绕开该缺陷。
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

to_win() {
    local p="$1"
    if [[ "$p" =~ ^/([a-zA-Z])/(.*)$ ]]; then
        printf '%s:/%s' "${BASH_REMATCH[1]^^}" "${BASH_REMATCH[2]}"
    else
        printf '%s' "$p"
    fi
}

if [ -z "${MAVEN_HOME:-}" ]; then
    MVN_BIN="$(command -v mvn || true)"
    if [ -z "$MVN_BIN" ]; then
        echo "[mvnw] 未找到 mvn 命令，请先设置 MAVEN_HOME 环境变量指向 Maven 安装目录。" >&2
        exit 1
    fi
    MAVEN_HOME="$(cd "$(dirname "$MVN_BIN")/.." && pwd)"
fi

CLASSWORLDS="$(ls "$MAVEN_HOME"/boot/plexus-classworlds-*.jar 2>/dev/null | head -n 1)"
if [ -z "$CLASSWORLDS" ]; then
    echo "[mvnw] 在 $MAVEN_HOME/boot 下未找到 plexus-classworlds jar，MAVEN_HOME 可能不正确。" >&2
    exit 1
fi

SETTINGS="$SCRIPT_DIR/.mvn/settings.xml"
EXTRA_SETTINGS=()
if [ -f "$SETTINGS" ]; then
    EXTRA_SETTINGS=(-s "$(to_win "$SETTINGS")")
fi

exec java ${MAVEN_OPTS:-} \
    -classpath "$(to_win "$CLASSWORLDS")" \
    "-Dclassworlds.conf=$(to_win "$MAVEN_HOME")/bin/m2.conf" \
    "-Dmaven.home=$(to_win "$MAVEN_HOME")" \
    "-Dmaven.multiModuleProjectDirectory=$(to_win "$SCRIPT_DIR")" \
    -Dfile.encoding=UTF-8 \
    org.codehaus.plexus.classworlds.launcher.Launcher \
    "${EXTRA_SETTINGS[@]+"${EXTRA_SETTINGS[@]}"}" "$@"
