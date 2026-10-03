#!/usr/bin/env bash
# Пересборка тестового билда с сохранением ПРЕДЫДУЩЕЙ версии (скилл `after-edit`).
#
#   tools/test-build/snapshot-and-build.sh "что изменено — одной строкой" [--restart]
#
# 1. Кладёт текущий testbuild/led-scheme-test.jar в testbuild/previous/<метка>/ вместе с
#    patch'ем незакоммиченных правок (git diff HEAD) и списком новых (untracked) файлов —
#    чтобы правку можно было откатить: либо запустить старый jar, либо вернуть код.
# 2. Дописывает запись в testbuild/previous/INDEX.md (метка, HEAD, описание).
# 3. Оставляет только KEEP последних снимков.
# 4. mvn -DskipTests package -> копия в testbuild/led-scheme-test.jar.
# 5. С --restart закрывает ТОЛЬКО процессы приложения (командная строка содержит led-scheme,
#    vjstb, ave_tb или AVE_ToolBox) и запускает свежий jar. Чужие java/javaw (Minecraft,
#    IDE, Maven и т.д.) не трогаются — НИКОГДА не блочный taskkill по имени процесса.
#
# Ничего не коммитит и не пушит (Золотое правило 1): снимок — обычные файлы в testbuild/ (вне git).
set -euo pipefail

KEEP=10
DESC="${1:-без описания}"
RESTART="${2:-}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
TESTBUILD="$ROOT/testbuild"
JAR="$TESTBUILD/led-scheme-test.jar"
PREV="$TESTBUILD/previous"
mkdir -p "$PREV"

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk-21.0.10}"
IDEA_MVN="$(ls -d "/c/Program Files/JetBrains/"IntelliJ*/plugins/maven/lib/maven3/bin 2>/dev/null | tail -1 || true)"
export PATH="$IDEA_MVN:$JAVA_HOME/bin:$PATH"

STAMP="$(date +%Y%m%d-%H%M%S)"
HEAD_SHA="$(git rev-parse --short HEAD)"
SNAP="$PREV/$STAMP-$HEAD_SHA"

# --- 1. снимок предыдущего состояния ---------------------------------------------------
if [ -f "$JAR" ]; then
  mkdir -p "$SNAP"
  cp "$JAR" "$SNAP/led-scheme-test.jar"
  git diff HEAD > "$SNAP/uncommitted.patch" || true
  git ls-files --others --exclude-standard > "$SNAP/untracked.txt" || true
  # новые (untracked) исходники копируем целиком — в diff HEAD их нет
  while IFS= read -r f; do
    case "$f" in
      src/*|tools/*|*.md|*.html) mkdir -p "$SNAP/untracked/$(dirname "$f")"; cp "$f" "$SNAP/untracked/$f" ;;
    esac
  done < "$SNAP/untracked.txt"
  printf '%s | HEAD %s | предыдущий билд ПЕРЕД правкой: %s\n' "$STAMP" "$HEAD_SHA" "$DESC" >> "$PREV/INDEX.md"
  echo "[snapshot] предыдущий билд сохранён: $SNAP"
else
  echo "[snapshot] предыдущего тестового jar нет — снимок пропущен"
fi

# --- 3. ротация ------------------------------------------------------------------------
# shellcheck disable=SC2012
ls -1d "$PREV"/*/ 2>/dev/null | sort | head -n -"$KEEP" | while IFS= read -r old; do rm -rf "$old"; done

# --- 4. сборка -------------------------------------------------------------------------
mvn -q -DskipTests package
cp target/led-scheme.jar "$JAR"
echo "[build] $JAR обновлён ($(date +%H:%M:%S))"

# --- 5. перезапуск (только свои процессы) ------------------------------------------------
if [ "$RESTART" = "--restart" ]; then
  powershell.exe -NoProfile -Command '
    Get-CimInstance Win32_Process |
      Where-Object { $_.Name -match "^javaw?\.exe$" -and $_.CommandLine -match "led-scheme|vjstb|ave_tb|AVE_ToolBox" -and $_.CommandLine -notmatch "maven" } |
      ForEach-Object { Write-Host ("[restart] закрываю PID " + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force }
  '
  sleep 1
  nohup javaw.exe -jar "$(cygpath -w "$JAR")" >/dev/null 2>&1 &
  disown
  echo "[restart] запущен свежий процесс"
fi
