---
name: add-library-singleton
description: >-
  Добавить новый вид-синглтон в механизм синхронизации общей библиотеки (сервер
  LibraryItemKind + панель админки + клиентский applyXxx). Применять когда нужен
  новый серверный конфиг-блоб в одном экземпляре, синхронизируемый в клиент
  (как VERSION_MANIFEST, INTERACTIVE_SCENARIOS, CALC_DEFAULTS). НЕ для обычных
  многострочных видов (CABINET, CABLE, CONTROLLER и т.п.).
---

# Добавить новый синглтон-вид в синхронизацию библиотеки

Контекст механизма целиком — `ARCHITECTURE.md` §2. Рецепт проверен дважды
(`INTERACTIVE_SCENARIOS`, `VERSION_MANIFEST`).

## Синглтон vs обычный вид

- **Синглтон** — ровно одна строка на весь сервер, фиксированный id, форма
  payload известна только клиенту и админке. Существующие: `GUIDE_TEXT`,
  `ONBOARDING_TEXT`, `EQUIPMENT_CATEGORY_LABELS`, `CALC_DEFAULTS`,
  `INTERACTIVE_SCENARIOS`, `VERSION_MANIFEST`.
- **Обычный вид** — много строк, id при создании. Если тебе нужно именно это
  (новый тип оборудования и т.п.) — этот скилл НЕ подходит, там другой путь
  (новая сущность в `ledscheme-model`, форма в админке, ветка в
  `applyLibrarySyncItems`, при FK-ссылках из проекта — защита синк-удаления по
  образцу `AppModel.isHoistTypeReferenced`).

## Шаги

### 1. Сервер (`ledscheme-server`)

- Новая константа в enum `LibraryItemKind`.
- Запись в карты `SINGLETON_IDS` / `SINGLETON_NAMES` (они централизованы в
  `LibraryItemKind`, доступны и `AdminLibraryController`, и `LibraryController`).
- Class-javadoc `LibraryItemKind` — дописать точную JSON-форму payload.
- **Больше на сервере обычно ничего не нужно**: общие CRUD/upsert-эндпоинты
  (`POST /api/admin/library/singleton/{kind}`, `GET
  /api/library/singleton/{kind}`, дельта `GET /api/library/changes`)
  подхватывают новый `kind` автоматически.

### 2. Админка (`ledscheme-admin`)

- Новая `XxxPanel implements LibraryFormPanel` (`clear` / `loadFromJson` /
  `toJson` / `currentName`) в `.../admin/ui/`.
- Добавить вкладку в `AdminMainFrame`.
- `AdminLibraryClient` трогать не нужно — он kind-agnostic.
- Приватный вложенный record под форму payload (НЕ общий класс с клиентом —
  конвенция проекта).

### 3. Клиент (`VJsTB_reborn`)

- `case "XXX" -> applyXxx(dto);` в `AppModel.applyLibrarySyncItems`.
- `applyXxx(dto)` со своим приватным вложенным record'ом под форму payload.

### 4. (Опционально) отдельный способ чтения

Если запись нужна раньше/легче, чем через общую дельту (как `VERSION_MANIFEST`
при старте — тянуть всю библиотеку ради одной записи расточительно): добавить
отдельный публичный GET на `LibraryController` по образцу `/singleton/{kind}`
(404 если удалена/не сохранялась, 400 если вид не синглтон).

## Проверка

- `curl -sk https://138.16.177.176:8443/api/library/singleton/XXX` после
  первого «Сохранить» из админки — payload сериализуется/десериализуется без
  потерь.
- Тест разбора на клиенте по образцу `LibrarySyncClientTest` /
  `AppModelTest.librarySyncApplies*`.
- Серверный `ledscheme-server` — деплой руками (скилл `deploy-server`).
