---
name: test-build
description: >-
  Собрать shaded jar клиента и запустить его для живой проверки (в т.ч. через
  computer-use). Применять когда просят «запусти / покажи / сделай скриншот
  приложения», проверить, что изменение работает в реальном UI (а не только в
  тестах), или пересобрать тестовый билд. Описывает единый путь тестового jar
  (общий для всех worktree) и безопасный перезапуск javaw.
---

# Тестовый билд и живой запуск

## Сборка

```bash
mvn -DskipTests package     # -> target/led-scheme.jar (shaded)
```

Окружение Maven — как в скилле `run-tests` (JDK 21 + Maven из плагина IDEA).

## Единый путь тестового билда

```bash
cp target/led-scheme.jar "C:\Development\VJsTB_reborn\testbuild\led-scheme-test.jar"
```

- **Один и тот же путь для ВСЕХ сессий/агентов**, независимо от того, в каком
  worktree (`.claude/worktrees/<имя>`) шла работа. Файл лежит в ГЛАВНОМ
  чекауте, вне git (`testbuild/` в `.gitignore`), не внутри worktree и не
  внутри `target/` (тот стирается `mvn clean`).
- Это **НЕ `dist/`** — та зарезервирована под релизный `jpackage`-вывод (скилл
  `release-client`).
- **Ярлык на рабочем столе** (`led-scheme.jar — ярлык.lnk`) указывает сюда:
  `TargetPath` = `javaw.exe`, `Arguments` = `-jar
  "...\testbuild\led-scheme-test.jar"`. Восстановить/пересоздать — PowerShell
  `New-Object -ComObject WScript.Shell` → `CreateShortcut(...)` →
  `TargetPath`/`Arguments`/`WorkingDirectory`/`Save()`.

## Перезапуск для живой / computer-use проверки

Пересобранный jar **не подхватывается, пока не перезапущен процесс**.

1. **Предупреди пользователя**, прежде чем управлять его рабочим столом через
   computer-use.
2. Убей только процессы ЭТОГО приложения: ищи `javaw.exe`, в командной строке
   которых есть `led-scheme-test.jar`. **НЕ блочный `taskkill` по имени
   процесса** — на машине пользователя есть посторонние `javaw.exe` (напр.
   Minecraft), их трогать нельзя.
3. Запусти ровно один свежий процесс:
   ```bash
   nohup "javaw.exe" -jar "C:\Development\VJsTB_reborn\testbuild\led-scheme-test.jar" &
   disown
   ```
   Не `open_application` — та может поднять старый ярлык вместо свежего билда.

## Связанные скиллы

- Только автотесты, без запуска UI — скилл `run-tests`.
- Релизная упаковка (`jpackage`, `.zip`) — скилл `release-client`.
