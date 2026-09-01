---
name: release-client
description: >-
  Выпустить релиз клиента VJsTB_reborn: бамп версии, тег, GitHub Release,
  Windows + Linux/macOS артефакты, обновление versions.txt и VERSION_MANIFEST.
  Применять когда просят подготовить / собрать / выпустить релиз или поднять
  версию. Содержит обязательный предрелизный чеклист из 14 пунктов и уже
  пойманные грабли GitHub CLI / админ-консоли.
---

# Релиз клиента VJsTB_reborn

Составлено по итогам реальных выпусков (каждый пункт — либо явное требование
пользователя, либо реально пойманный баг). **Сверяйся с чеклистом при каждом
релизе и напоминай пользователю о пропущенных шагах** — не молчи и не
предполагай, что он помнит.

## Разрешения

«Готовь релиз» = разрешение на весь процесс текущего релиза (шаги 1–14). Это
НЕ разрешение на будущие релизы. Каждый шаг, меняющий состояние снаружи (пуш,
тег, `gh release`, `gh workflow run`), всё равно требует, чтобы пользователь
явно инициировал этот релиз.

## Предрелизный чеклист (14)

1. **Функционал по плану добавлен?** Сверься с активным планом
   (`.claude/plans/*.md`, если есть) и `git log <предыдущий тег>..HEAD
   --oneline` — ничего запланированного не потеряно, ничего не «отложено» без
   явного согласия пользователя.
2. **У каждой добавленной/заметно изменённой фичи есть документация для
   пользователя** — раздел в «Руководстве» (`GUIDE_TEXT`, вкладка в
   ledscheme-admin) ИЛИ шаг в «Интерактивных сценариях» (`INTERACTIVE_SCENARIOS`).
   Легко забыть — отдельный явный пункт.
   - Пустые сценарии (0 шагов), которые дополнять первыми: `scn-setup`,
     `scn-signal`, `scn-output`, `scn-libraries`.
   - **Баг админки «Руководство» (не пофикшен)**: кнопки «Вверх»/«Вниз» иногда
     ДУБЛИРУЮТ секцию поверх другой, стирая её. Перед каждым «Сохранить» на
     вкладке — свериться `curl -sk
     https://138.16.177.176:8443/api/library/singleton/GUIDE_TEXT`, что список
     секций не задвоился и ничего не потерял. Если задвоение — «Загрузить с
     сервера» и повторить точечно, без «Вверх»/«Вниз».
   - **Баг админки «Интерактивные сценарии» (`ScenarioEditorPanel`, не
     пофикшен)**: переключение выбора между сценариями в левом списке ПОРТИТ
     шаги только что покинутого сценария в памяти редактора
     (`lastScenarioIndex` обновляется до `commitScenarioStepsIntoModel()`).
     Обходной путь: (1) «Загрузить с сервера» перед работой; (2) редактировать
     РОВНО ОДИН сценарий за заход, не кликая на другие строки ни до, ни после;
     (3) сразу «Сохранить»; (4) свериться `curl -sk
     .../singleton/INTERACTIVE_SCENARIOS`, что счётчики шагов у ВСЕХ сценариев
     совпадают с ожидаемыми. Чужой сценарий смотреть только свежим `curl`.
3. **`ledscheme-model` синхронизирован с GitHub, не только с `~/.m2`.**
   `cd C:\Development\ledscheme-model && git status` — новые/untracked классы
   закоммитить и запушить ДО релизной сборки, иначе CI (клонирует
   `ledscheme-model` с нуля через SSH deploy-key) их не увидит и упадёт на
   компиляции. `mvn install` локально этого НЕ покрывает. (Поймано 2026-08-15:
   5 классов жили только локально — CI была глухо сломана на всех платформах.)
4. **Версия проставлена**: `AppInfo.VERSION` обновлена под релиз.
5. **Тесты зелёные**: полный `mvn test` в `VJsTB_reborn` — 0 failures (скилл
   `run-tests`). Не выборочный прогон.
6. **Windows-сборка локально** (НЕ в CI):
   ```bash
   mvn -DskipTests package
   jpackage --type app-image --input dist-input --dest dist \
     --name "AVE_ToolBox" --main-jar led-scheme.jar \
     --main-class com.vjstb.ledscheme.App --app-version "X.Y" \
     --vendor "AVE_ToolBox" --icon packaging/icon-main.ico
   # затем упаковать dist/ в .zip
   ```
   Иконка `packaging/icon-main.{ico,icns,png}` закоммичена. Рантайм-иконка окна
   — `ui.AppIcons.loadAppIconImages()` + `setIconImages(...)` в `MainFrame`.
7. **Тег** — новый `vX.Y` или перенесён на актуальный коммит (см. ниже) — и
   **запушен**.
8. **GitHub Release создан** (`gh release create` с содержательными notes —
   список реальных изменений, не «релиз vX.Y») **и НЕ draft/prerelease**:
   `gh release view vX.Y --json isDraft,isPrerelease` — проверить явно.
9. **Windows-артефакты загружены**: `gh release upload vX.Y <zip> <jar>`.
10. **Linux/macOS собраны и загружены**: `gh workflow run release-linux.yml
    --ref master -f tag=vX.Y` и аналогично `release-macos.yml`. **Дождаться
    завершения** (`gh run list` / `gh run view <id>`), проверить `conclusion:
    success`. Первые автопрогоны (от `push: tags: v*`) почти наверняка упадут
    на «Загрузить в релиз» (релиза ещё не было в момент пуша тега) — это
    ожидаемо; перезапустить `workflow_dispatch` ПОСЛЕ создания Release (шаг 8).
11. **Финальная проверка**: `gh release view vX.Y --json assets` — все 4
    артефакта (`.zip`, `.jar`, `.deb`, `.dmg`), релиз отмечен `Latest`
    (`gh release list`).
12. **`versions.txt`** (корень репо) — добавлена строка новой версии,
    закоммичено и запушено (легаси, см. ниже).
13. **`VERSION_MANIFEST` на сервере** — через ledscheme-admin (вкладка
    «Версии»): строка версии добавлена/обновлена, галочка «Доступна» СТОИТ.
    (Баг: при правке соседней ячейки галочка иногда сама снимается; версия без
    неё невидима для проверки обновлений.) После «Сохранить» — перепроверить
    `curl -sk https://138.16.177.176:8443/api/library/singleton/VERSION_MANIFEST`,
    что `"available":true` у новой версии.
14. **Разрешение пользователя на каждый коммит/пуш/релиз-шаг** — см. выше.

## CI

- Воркфлоу: `.github/workflows/release-linux.yml`, `release-macos.yml`.
- Триггер: `push: tags: v*` ИЛИ `workflow_dispatch` (поле `tag`).
- **`workflow_dispatch` берёт определение воркфлоу из `master`, но собирает код
  из коммита тега.** Обновить только workflow-файл недостаточно, если тег не
  двигали — нужные изменения должны быть в САМОМ коммите тега.
- Оба воркфлоу тянут `ledscheme-model` через SSH: `webfactory/ssh-agent` +
  секрет `LEDSCHEME_MODEL_DEPLOY_KEY` (read-only deploy-key), `git clone` →
  `mvn install` → `mvn package` клиента.
- **Windows-сборка НЕ в CI** — только руками (шаг 6).

## Перенос тега на новый коммит

```bash
git tag -f vX.Y <commit>
git push origin :refs/tags/vX.Y   # обязательно удалить с сервера
git push origin vX.Y              # и только потом создать заново
```

`git push --force` на тег не проходит (не fast-forward).

## Грабли GitHub Releases

- После переноса/пересоздания тега `gh release upload` иногда падает с `HTTP
  404` (кэш ID релиза) — просто повторить.
- После переноса тега или `gh release edit` без явных флагов релиз может
  незаметно откатиться в `draft`/`prerelease` — **всегда** проверяй `gh
  release view vX.Y --json isDraft,isPrerelease`, при необходимости `gh
  release edit vX.Y --draft=false --prerelease=false`.

## `versions.txt` — легаси, не удалять

Новые клиенты (≥ той версии, где это ввели) читают список версий с сервера
(синглтон `VERSION_MANIFEST`). Но ВСЕ клиенты старее (1.3 — исходный билд 2.0)
дёргают `versions.txt` напрямую с `raw.githubusercontent.com` — без него их
проверка обновлений падает с 404. **При каждом релизе обновляй ОБА места**:
`versions.txt` (старые клиенты) и `VERSION_MANIFEST` через админку (новые).

## `.bat`-скрипт обновления на Windows

`UpdateManager.applyAndRestartWindows` — **не используй `timeout /t`**: у
detached-процесса от `ProcessBuilder` с выводом в `NUL` (нет консоли) `timeout`
мгновенно падает («Input redirection is not supported») и не ждёт. Используй
`ping -n 2 127.0.0.1 >nul`.
