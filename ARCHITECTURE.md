# Архитектура — синхронизация библиотеки, сложные фичи, конвенции

Вынесено из `CLAUDE.md`. Процедуры (сборка, тесты, деплой, релиз) — в скиллах
`.claude/skills/` (`run-tests`, `test-build`, `deploy-server`, `release-client`,
`add-library-singleton`). Карта тестов — в [`TESTS.md`](TESTS.md).

---

## 1. Четыре репозитория

| Репозиторий | Что это | Расположение | Git |
|---|---|---|---|
| `VJsTB_reborn` | Десктоп-клиент (Java 21, Swing, Maven) | `C:\Development\VJsTB_reborn` | публичный, `SergioPimonno/VJsTB_reborn` |
| `ledscheme-admin` | Админ-консоль (Java 21, Swing, Maven) | `C:\Development\ledscheme-admin` | **НЕ git-репозиторий** — только локально |
| `ledscheme-server` | Сервер (Spring Boot 3.3.4, Maven, PostgreSQL, JWT) | `C:\Development\ledscheme-server` | **НЕ git-репозиторий** — только локально |
| `ledscheme-model` | Общие доменные POJO | `C:\Development\ledscheme-model` | приватный, `SergioPimonno/ledscheme-model` |

`ledscheme-model` — НЕ reactor-модуль и не submodule: клиент подключает его как
обычную Maven-зависимость `com.vjstb.ledscheme:ledscheme-model:0.1.0`,
резолвится из `~/.m2`. **После любого изменения — сначала `mvn -DskipTests
install` в нём самом** (для релизной CI — ещё и `git commit && git push` в
`ledscheme-model`, см. скилл `release-client`, шаг 3).

Админ-консоль НЕ шарит Java-код с клиентом или моделью напрямую (кроме редких
мест явной зависимости от `ledscheme-model`, см. её CLAUDE.md) — синглтон-панели
админки просто сериализуют/десериализуют JSON той же формы, что ждёт клиент,
каждая сторона держит свою копию record'а с полями. **Осознанная конвенция
проекта — не пытайся «убрать дублирование» через общий класс без явного
запроса.**

---

## 2. Синхронизация общей библиотеки — как устроена и как расширять

Центральный механизм: общая библиотека (типы кабинетов, контроллеров, кабелей +
несколько «служебных» синглтонов вроде текстов Руководства) живёт на сервере и
синхронизируется в клиент.

**Таблица** `library_item` (сущность `LibraryItem` на сервере): `id`, `kind`
(enum `LibraryItemKind`), `name`, `payload_json` (просто `TEXT`, сервер его **не
парсит и не валидирует** — форма целиком на совести клиента и админки),
монотонный `global_seq` (append-only счётчик — на нём строится дельта-
синхронизация), `deleted` (soft delete).

### Два вида элементов `LibraryItemKind`

- **Обычные** (много строк): `CABINET, CONTROLLER, EQUIPMENT, CABLE, INTERFACE,
  CABLE_LENGTH_PROFILE, HOIST, STRUCTURE_FRAME, CASE, VEHICLE,
  EQUIPMENT_CUSTOM_CATEGORY` — создаются/удаляются свободно, id генерируется при
  создании.
  - `HOIST` (модели лебёдок/талей с паспортной WLL) и `STRUCTURE_FRAME`
    (элементы наземного конструктива — рама/короткая рама/стакан/контейнер
    балласта, один вид с дискриминатором `kind` внутри payload) — единственные
    из списка (кроме CABINET/CONTROLLER), где на id реально ссылается FK в
    данных проекта (`Screen.riggingHoistTypeId` и четыре
    `Screen.structureXxxTypeId`) — их синк-удаление защищено так же, как
    CABINET/CONTROLLER (`AppModel.isHoistTypeReferenced` /
    `isStructureFrameTypeReferenced`), а не безусловно, как у
    CABLE/INTERFACE/CABLE_LENGTH_PROFILE.
  - `CASE` (кофры — габариты, вес, лимит штабелирования) и `VEHICLE` (машины —
    габариты кузова, грузоподъёмность) — на их id НИГДЕ в проектных данных нет
    персистентных FK, калькулятор транспорта (`service.VehicleCalc`)
    одноразовый, ничего не сохраняет по ссылке.
  - Подробности и мотивация — `RIGGING_CALC_NOTES.md`, `STRUCTURE_CALC_NOTES.md`,
    `VEHICLE_CALC_NOTES.md`.
- **Синглтоны** (ровно одна строка на сервер, фиксированный id): `GUIDE_TEXT,
  ONBOARDING_TEXT, EQUIPMENT_CATEGORY_LABELS, CALC_DEFAULTS,
  INTERACTIVE_SCENARIOS, VERSION_MANIFEST`. Карты `SINGLETON_IDS` /
  `SINGLETON_NAMES` — централизованно в `LibraryItemKind` (сервер). Точная
  JSON-форма каждого синглтона — в class-javadoc `LibraryItemKind`.

### Два контроллера на сервере

- `AdminLibraryController` (`/api/admin/library/**`, JWT с ролью ADMIN) —
  полный CRUD + `POST /singleton/{kind}` (upsert).
- `LibraryController` (`/api/library/**`, **единственный полностью открытый без
  токена набор путей** — `SecurityConfig`, `GET /api/library/**` анонимно):
  `GET /changes?since=N` (полная дельта, `since=0` — вся библиотека) и `GET
  /singleton/{kind}` (одна запись; 404 если удалена/не сохранялась, 400 если
  вид не синглтон).

### На клиенте

`sync.LibrarySyncClient` (анонимный, `fetchChanges(since)`),
`AppModel.applyLibrarySyncItems(...)` — маршрутизирует каждый элемент по `kind`
в `switch`, у каждого синглтона свой `applyXxx(dto)` с приватным локальным
record'ом под форму JSON (НЕ общий класс с админкой — так везде, кроме обычных
видов типа `CableType`/`CableLengthProfile`, которые настоящие классы из
`ledscheme-model`).

### В админке

У каждого вида своя Swing-панель, реализующая `LibraryFormPanel`
(`clear/loadFromJson/toJson/currentName`) — `CalcDefaultsPanel`,
`VersionManifestPanel`, `ContentEditorPanel` (Guide + Onboarding) и т.д., в
`ledscheme-admin/.../admin/ui/`. `AdminLibraryClient` полностью kind-agnostic.

### Рецепт «добавить новый синглтон-вид» (использован для `INTERACTIVE_SCENARIOS`, `VERSION_MANIFEST`)

1. **Сервер**: новая константа в `LibraryItemKind` + запись в `SINGLETON_IDS` /
   `SINGLETON_NAMES` + javadoc с формой JSON. Обычно **больше ничего на сервере
   менять не нужно** — общие CRUD/upsert-эндпоинты подхватывают новый `kind`
   автоматически.
2. **Админка**: новая `XxxPanel implements LibraryFormPanel`, вкладка в
   `AdminMainFrame`.
3. **Клиент**: `case "XXX" -> applyXxx(dto);` в `AppModel.applyLibrarySyncItems`,
   свой приватный record.
4. Если нужен новый способ ЧТЕНИЯ (не через общую дельту) — отдельный публичный
   GET на `LibraryController` по образцу `/singleton/{kind}`.

---

## 3. Сложные фичи — как устроены

### Экспорт NovaLCT (`service.NovaLctScrWriter` и окружение)

Полностью reverse-engineered бинарный формат `.scr`. **Отдельный подробный
документ `NOVALCT_EXPORT.md`** (корень репо): карта файлов, структура формата
(Standard/Complex/мультиэкранный), маппинг доменных понятий в номера карт/портов
NovaLCT, что подтверждено реальной загрузкой, что нет (главный риск —
`writeStandardMultiScreen`), статус импорта (заготовка, отключена из меню).
Начинай оттуда, а не с чтения кода.

### Спецификация коммутации и сплайсовка кабеля (`service.CableSpecCalc`)

Библиотека кабелей разделена на два независимых смысла (не путать при правках):
- `CableType` (`ledscheme-model`) — **только переходники** (разные разъёмы на
  концах), опциональная `fixedLengthM` (готовое изделие фикс. длины, не
  каталог).
- `CableLengthProfile` (`ledscheme-model`) — **только однородный кабель**
  (одинаковый разъём на обоих концах), каталог доступных длин + запас %, для
  него считается реальная сплайсовка.

Оба несут `SchemaMode mode` (POWER/SIGNAL; `null` — старая запись до появления
поля, видна в обоих режимах до первого явного редактирования).

`WireLabelDialog` строит список типов **строго** из этих двух библиотек
(`AppModel.cableLengthProfilesForMode` + `cableTypesForMode`), без хардкодных
пресетов. Комбобокс редактируемый (свободный текст как запасной вариант), с
кнопками сохранить как переходник / как каталог длин, которые предлагают
отправить сохранённое в общую библиотеку на модерацию (`ProposeDialog`).

`CableSpecCalc.minimalKit(rawLengthM, profile)` — если ни один кусок каталога не
покрывает линию одним куском, ищет МИНИМАЛЬНЫЙ по числу кусков набор (DP по
сантиметрам). `OutputStagePanel` формирует спецификацию как многолистовой
`.xlsx` (`service.SpecXlsxWriter`) — листы «Коммутация — закупка» и «Коммутация —
сплайсовка».

### Блок-схема площадки и автозаполнение (`AppModel.autoPopulateSchema`)

Отдельная от поэкранной сетки кабинетов модель — общая схема питания/сигнала
всей площадки (`SchemaNode`/`SchemaEdge`, `SchemaMode`).
`autoPopulateSchema(mode, autoConnectSockets)` при первом заходе добавляет
расключенные экраны (+ использованные контроллеры для сигнала) и опционально
автосоединяет гнёзда кабинетов с портами. **Ключевой инвариант**: трогает только
экраны, добавленные В ЭТОМ ЖЕ вызове (`freshlyAddedScreenIds`) — уже
существующий в схеме экран никогда не пересканируется, иначе ручное отключение
автосвязи откатывалось бы при следующем заходе.

Режим разъёмов (сокеты кабинетов как физические точки подключения) и его
настройки (`socketWiringEnabled`, `chainEndpointSocketsEnabled`,
`connectorDisplayMode`, `schemaAutoPopulateEnabled`) — независимы для питания и
сигнала (4 отдельных пары полей в `UserProfile`, у каждой свой setter в
`SettingsManager` и роутер-метод `xxx(SchemaMode)`).

### Раскладка и трассировка общей схемы (`service.schemalayout.*`, `SchemaCanvasPanel`)

Переработка гнёзд/связей общей схемы (`docs/schema-ports-rework/`, ветка
`claude/schema-ports-rework`, обоснование — `DIALOG.md`, план для агентов —
`PLAN.md`). Полная спецификация — в `PLAN.md` §2, здесь только карта того, что
где лежит и как это соотносится друг с другом.

- **`InterfaceRole`** (`ledscheme-model`) — смысловая роль гнезда (VIDEO,
  LED_DATA, SYNC, NETWORK, CONTROL, AUDIO, POWER, OTHER). Источник для
  конкретного гнезда — `service.schemalayout.PortRoleResolver`, порядок
  приоритета `PortPlacement.roleOverride` → `CardPort.role` →
  `InterfaceType.defaultRole` (совпадение начала `connectorType`) → эвристика
  по regex на `connectorType` (таблица — PLAN.md §2.3). Роль всегда решает, на
  какой стороне блока рисуется группа гнёзд (`SideRules`, PLAN.md §2.2) и каким
  цветом рисуется линия связи (`SchemaStyle.roleLineColor`, D9 — пользовательский
  цвет связи важнее роли, роль важнее цвета по умолчанию).
- **`ThruResolver`** — аналогичная резолюция для транзита (`CardPort.thru`):
  явное значение важнее авто-детекции (ровно одна пара IN/OUT одного
  `connectorType` с `count == 1` в пределах карты/списка разъёмов питания).
- **`NodePortLayout`** — чистая функция (без Swing) «узел + библиотека + правила
  сторон → прямоугольники гнёзд и подписи», используется и живым холстом, и
  экспортом. Тестируется `NodePortLayoutTest` headless через
  `TextMeasure`/`AwtTextMeasure` (обёртка `FontRenderContext`, не требует
  дисплея). Раскладка знает про 4 ориентации блока (`NodeOrientation`),
  свёртку незадействованных групп (`GroupDisplayMode`), ручную сторону/порядок
  группы (`PortPlacement`, не поворачивается сменой ориентации — «пользователь
  поставил руками»).
- **`OrthogonalRouter`/`LaneNudger`/`EdgeBundles`** — трассировка связей под
  90° (алгоритм Wybrow et al. 2009, ссылка — `DIALOG.md` «Источники»),
  разнесение коллинеарных параллельных связей на канал, общий ствол пучка на
  одно свёрнутое гнездо. Применяется только к рёбрам с `SchemaEdge.routeMode ==
  AUTO`; `MANUAL` (сохранённые изломы, старые схемы после открытия — legacy-
  резолв `routeMode == null` → ломаная непуста → `MANUAL`) и `STRAIGHT` считаются
  напрямую в `SchemaCanvasPanel`, роутер не участвует.
- **Сетевое оборудование на схеме (D11)** — блоки из библиотеки
  `NetworkDeviceType` (`AppModel.addSchemaNodeFromNetworkDevice`) — обычный
  `SchemaNode(CUSTOM)` с картой «Сеть» (Ethernet/Fiber, `IN_OUT`, роль всегда
  `NETWORK`) и `networkDeviceTypeId` для «Обновить порты из библиотеки»
  (`refreshNetworkDevicePorts`). «Защита от дурака» (направление IN/OUT) не
  проверяется, если у любого конца роль `NETWORK`. Автосборка сетевой карты из
  этих блоков — вне этой переработки, только заготовка API:
  `AppModel.networkGraphFromScene(scene)` возвращает устройства (блоки с
  задействованной NETWORK-связью), связи (только NETWORK-рёбра сигнала) и
  коммутаторы (все блоки с `networkDeviceTypeId`, независимо от подключения) —
  без какого-либо UI сетевого менеджера поверх этого графа.
- **«Легенда линий»** (`AppModel.addLineLegendNode`/`lineLegendRoles`/
  `lineLegendPowerNominals`) — авто-блок (`SchemaNode.autoLineLegend`, тот же
  паттерн, что «Легенда портов»/`autoPortLegend`) со списком РЕАЛЬНО
  используемых на текущей сцене ролей (сигнал) или номиналов разъёма (питание)
  и их цветов из активного `SchemaStyle` — не статический список всех
  возможных ролей.
- **`SchemaRenderMode` (`MODERN`/`CLASSIC`, `UserProfile.schemaRenderMode`,
  глобально в профиле, по умолчанию `MODERN`)** — переключатель «Предпочтения →
  Способ отрисовки общей схемы» (добавлен пользователем 2026-09-18 уже ПОСЛЕ
  замены старого рендера новым, DIALOG.md реплика 4, PLAN.md D16/T5.5).
  `CLASSIC` — дорефакторинговые приватные методы `SchemaCanvasPanel` (суффикс
  `Classic`: `computeSocketRectsClassic`, `drawConnectorRowsClassic`,
  `socketPositionClassic`/`socketAtClassic` и т.д.), восстановленные из версии
  `master` до начала этого плана — своя раскладка (строка гнёзд у края блока,
  без ролей/ориентации/орто-трассировки/перетаскивания групп/сетевых блоков) и
  свой хит-тестинг, полностью параллельные `NodePortLayout`/`OrthogonalRouter`,
  а не косметическая перекраска одного и того же геометрического пути.
  Диспетчеризация — `SchemaCanvasPanel.classicMode()`, одна проверка в каждой
  точке входа (`paint`, `socketPosition`, `socketAt`, обработчики мыши).
  Известный пробел: `AppModel.autoFitNodeToPorts` считает авто-размер НОВОГО
  узла через `NodePortLayout` независимо от режима — в `CLASSIC` это может дать
  чуть неоптимальный размер для новых блоков (существующие проекты не
  затронуты, у них уже сохранённый размер).

### Проверка обновлений (`update.*`, `ui.UpdateNoticeDialog`)

`VersionManifest.fetch()` читает JSON синглтона `VERSION_MANIFEST` с сервера (не
`versions.txt` — тот легаси для старых клиентов, см. скилл `release-client`).
`isNewer(a, b)` — простое
точечное сравнение по числовым сегментам (не semver, этого достаточно).
`App.java` после `frame.setVisible(true)` фоново (SwingWorker) проверяет версию
и показывает немодальный `UpdateNoticeDialog`, если доступна версия новее и
пользователь ещё не закрывал уведомление именно про НЕЁ
(`AppSettings.dismissedUpdateVersion` — тот же паттерн, что `onboardingCompleted`).
Ручной путь «Настройки → Обновить версию…» (`UpdateDialog`) не изменился в
поведении, только источник данных.

### Публичная веб-страница — содержимое

(Деплой едет внутри того же fat jar сервера — скилл `deploy-server`.) Заведена
2026-08-18: описание функционала, скачивание конфигов приёмных карт, простой
редактор масок в браузере, «мост синхронизации».

- API страницы — только уже публичные (anonymous GET) эндпоинты: `GET
  /api/cabinet-configs` (без `cabinetTypeName` — список ВСЕХ конфигов; параметр
  стал `required = false`, `SummaryDto` получил поле `cabinetTypeName`), `GET
  /api/cabinet-configs/{id}` (JSON с `contentBase64` → `Blob` + `<a download>`,
  реального файлового эндпоинта нет), `GET /api/library/changes?since=0` (пинг
  «жив ли сервер», ответ игнорируется).
- **Редактор масок** — полностью клиентский (`<canvas>` + `toBlob()`), без
  сети. 12 цветовых пресетов в `app.js` (`MASK_PRESETS`) — РУЧНАЯ копия
  `model.MaskColorPreset` (`VJsTB_reborn`), при правке одного поправить и
  другое. Функционал базовый (сетка кабинетов + чек-борд + сетка/растр), БЕЗ
  вырезов формы экрана и прочего из `ui.PixelGridRenderer` — осознанное сужение.
- **Мост синхронизации** (клиентское поле override, Cloudflare) — см. §5 ниже.

---

## 4. Общие конвенции

- **Стиль javadoc — часть документации, не мусор.** Комментарии к
  классам/методам часто объясняют ПОЧЕМУ (баг-репорт, что было раньше, что
  сломалось), а не только что делает код. Не вычищай их «ради краткости» —
  следующий агент опирается именно на них.
- Java `record` активно используется для DTO/payload-форм; для
  синглтон-payload'ов — приватный вложенный record в каждом
  `applyXxx`/`FormPanel`, НЕ общий класс между репозиториями (осознанная
  дублирующая конвенция).
- Тесты: JUnit 5, `mvn test` в каждом репо отдельно. У сервера —
  `@SpringBootTest` + `MockMvc` + H2 in-memory, **общий Spring-контекст на весь
  прогон surefire, БЕЗ отката между тестами** — если тест должен проверить
  «записи гарантированно нет», не полагайся на порядок выполнения, создай и
  сразу удали её сам перед проверкой.
- Никогда не трогать "dxvfi" (Node/PM2/nginx) на VPS dxv.
- Не коммитить/не пушить без явного указания пользователя.

---

## 5. Инфраструктура сервера — TLS / reverse-proxy / DNS

Рутинный деплой — скилл `deploy-server`. Здесь — как устроено и почему; менять
что-либо из этого только по явному запросу.

**Переход на домен как основной адрес — 2026-09-11.** Раньше адресация шла по
голому IP с самоподписанным сертификатом и client-side pinning; Cloudflare был
опциональным "мостом" для сетей, блокирующих порт 8443. Теперь домен —
**основной** адрес, а Cloudflare полностью убран из пути трафика (был источником
двух проблем: блокировки Cloudflare-edge в РФ и периодические ошибки
синхронизации при поднятом VPN — Cloudflare детектит VPN-exit-ноды как
подозрительные). IP с self-signed остаётся только ради уже установленных старых
клиентов.

### Caddy перед сервером

**Сервер БОЛЬШЕ не слушает открытый порт напрямую.** `ledscheme-server` привязан
только к `127.0.0.1:8081` (`SERVER_ADDRESS=127.0.0.1` в `app.env`, relaxed-
биндинг Spring Boot, без правок кода). Снаружи — **отдельный выделенный**
экземпляр Caddy (НЕ шарится с nginx "dxvfi"), слушает `:8443`,
`/etc/caddy/Caddyfile`:

```
{
    auto_https disable_redirects
    admin localhost:2019
    acme_dns cloudflare {env.CF_API_TOKEN}
}

https://138.16.177.176:8443 {
    tls /etc/caddy/certs/server.crt /etc/caddy/certs/server.key
    reverse_proxy 127.0.0.1:8081
}

https://ledschemedesigner.ru:8443 {
    reverse_proxy 127.0.0.1:8081
}

https://ledschemedesigner.ru {
    bind 83.217.212.64
    reverse_proxy 127.0.0.1:8081
}

http://ledschemedesigner.ru {
    bind 83.217.212.64
    redir https://{host}{uri} permanent
}
```

- **ACME-политика — глобальная (`acme_dns` в верхнем блоке), не per-site `tls
  {dns cloudflare ...}`.** Если один и тот же hostname (`ledschemedesigner.ru`)
  фигурирует в нескольких site-блоках (у нас их два — `:8443` и `:443`/`bind`),
  Caddyfile-адаптер отказывается собирать конфиг с ошибкой `hostname appears in
  more than one automation policy` — даже если оба блока описывают ОДИНАКОВЫЙ
  DNS-01 issuer. Единственный рабочий вариант — вынести issuer в глобальные
  опции ОДИН раз (`acme_dns cloudflare ...`), а сами site-блоки для этого
  хоста вообще не декларируют `tls` — оба подхватывают единую политику и делят
  один и тот же управляемый сертификат.
- **`bind 83.217.212.64`** — явно привязывает слушатель к конкретному IP.
  Без этого Caddy попытался бы `0.0.0.0:443`/`0.0.0.0:80` — а это уже занято
  nginx "dxvfi" (см. ниже, «Порт 443/80 для домена»).

- **`auto_https disable_redirects`, НЕ `off`** — это принципиально: `off`
  полностью выключает автоматическое управление сертификатами (в т.ч. explicit
  ACME DNS-01), не только implicit-редиректы. `disable_redirects` оставляет
  автоматизацию сертификатов включённой, но НЕ трогает порт 80 (он занят
  "dxvfi" — Caddy не должен даже пытаться его слушать).
- **IP-блок** — как раньше, self-signed сертификат (`/etc/caddy/certs/
  server.{crt,key}`, EC prime256v1, CN/SAN=`138.16.177.176`, 10 лет). Держим
  ради уже установленных клиентов со старым `DEFAULT_BASE_URL`.
- **Домен-блок** — настоящий сертификат от Let's Encrypt через DNS-01 challenge
  (плагин `github.com/caddy-dns/cloudflare`, токен в `CF_API_TOKEN`). DNS-01, а
  не HTTP-01, ИМЕННО потому что порт 80 занят "dxvfi" — HTTP-01 в принципе не
  вариант на этом хосте. Caddy сам продлевает сертификат (Let's Encrypt, ~90
  дней), руками ничего делать не нужно.
- Бинарник `/usr/bin/caddy` — **кастомная сборка через `xcaddy`** (стандартный
  apt-пакет не несёт DNS-плагинов), собран прямо на VPS: `xcaddy build v2.11.4
  --with github.com/caddy-dns/cloudflare`. **Важно**: `apt upgrade` перезатрёт
  его обратно на ванильную сборку без плагина — при обновлении Caddy через apt
  нужно пересобирать заново тем же способом (Go + xcaddy уже стоят на сервере).
  Бэкапы прежних бинарников — `/usr/bin/caddy.bak-<timestamp>`.
- `CF_API_TOKEN` — в `/etc/caddy/caddy.env` (права 600, `caddy:caddy`),
  подключается в systemd-юнит через drop-in
  `/etc/systemd/system/caddy.service.d/override.conf`
  (`EnvironmentFile=...`). Токен scoped только на `Zone:DNS:Edit` для зоны
  `ledschemedesigner.ru`.
- **Тот же drop-in убирает флаг `--environ` из `ExecStart`** — ванильный юнит
  Caddy его использует, а он печатает ВСЕ переменные окружения процесса в
  journal, включая `CF_API_TOKEN` открытым текстом. Если пересобираешь юнит с
  нуля — не верни этот флаг.
- `ufw` открывает только `8443/tcp` (v4+v6); `8081/tcp` и `80/443` (кроме
  "dxvfi") закрыты.

### DNS и путь трафика

- Домен `ledschemedesigner.ru` (регистратор reg.ru), NS — Cloudflare, A-запись
  **DNS only** (не proxied — серое, не оранжевое облако) → `83.217.212.64`
  (см. ниже — не основной IP сервера, отдельный доп. IPv4 именно под 443/80).
  Cloudflare участвует ТОЛЬКО в DNS-резолвинге и как DNS-провайдер для ACME
  DNS-01 (API-вызов с самого сервера, не подвержен блокировкам Cloudflare-edge
  у конечных пользователей в РФ) — HTTPS-трафик идёт напрямую клиент→сервер,
  Cloudflare не видит и не проксирует ни байта.
- Если DNS у Cloudflare когда-нибудь тоже станет проблемой в РФ (пока не
  наблюдалось — блокируют обычно именно edge/proxy IP, не NS) — можно увести
  NS-делегацию на другого провайдера; переиздание сертификата тогда потребует
  Caddy DNS-плагин под нового провайдера вместо `caddy-dns/cloudflare`.

### Порт 443/80 для домена — второй IPv4 + точечная правка nginx (2026-09-11)

Голый `https://ledschemedesigner.ru` (без `:8443`) теперь работает по-настоящему
— не просто задокументирован как "не работает", а реально отвечает на
стандартном порту. Потребовало два шага, оба сделаны с явного разрешения
пользователя (второй — исключение из golden rule 2, см. ниже):

**1. Доп. IPv4 на сервере** — VPS изначально имел один публичный IP
(`138.16.177.176`). У хостера (vdsina.ru, панель → Мои серверы → хостнейм →
вкладка «IP») заказаны ещё два адреса:
- `138.16.179.82` (шлюз `138.16.179.1`) — **не заработал**: локальная
  маршрутизация корректна (ICMP и трафик до своего же сервера проходят), но
  TCP наружу/снаружи не ходит вообще (SYN уходит, ответа нет — подтверждено
  `tcpdump`) ни до, ни после ребута сервера. Поддержка vdsina подтвердила
  проблему на их стороне, предложила заказать другой IP. Оставлен
  сконфигурированным (не мешает), реально не используется.
- `83.217.212.64` (шлюз `83.217.212.1`) — **рабочий**, используется.

Оба прописаны в `/etc/netplan/01-netcfg.yaml` (Ubuntu 24.04, `renderer:
networkd`, NetworkManager не установлен) как доп. адреса на `ens3` с
**policy-based routing**: у каждого доп. IP свой шлюз, отличный от основного,
поэтому недостаточно просто добавить адрес — нужна отдельная таблица
маршрутизации на каждый шлюз + `routing-policy` правило "трафик С этого IP —
через его же таблицу", иначе обратные пакеты уходят не через тот шлюз и
теряются (ISP режет ответы с несовпадающим source/gateway). Пример на рабочем
IP:
```yaml
routes:
  - to: default
    via: 83.217.212.1
    on-link: true
    table: 212
routing-policy:
  - from: 83.217.212.64
    table: 212
```
(основной IP как был в главной таблице маршрутизации без изменений).

**2. Правка nginx "dxvfi" — единственное исключение из golden rule 2, с явным
разрешением пользователя, при условии сохранности функционала dxvfi и
привязок его текущих клиентов.** Причина, по которой доп. IP сам по себе НЕ
решал задачу: у "dxvfi" nginx `listen 443 ssl;` / `listen 80 default_server;`
— **wildcard-бинд** (`0.0.0.0`), который на Linux блокирует ЛЮБОЙ другой
процесс от бинда порта 443/80 на ЛЮБОМ IP этой машины, сколько бы адресов ни
добавили (проверено на практике: Caddy падал с `bind: address already in use`
при попытке слушать `:443` на новом IP, пока wildcard-бинд nginx оставался
активным). Единственный чистый выход без второй физической машины — сделать
бинд nginx явным на его же собственный, уже используемый IP.

Правка (ровно 3 строки, `nginx -t` перед применением, backup сохранён в
`/root/nginx-backup-<timestamp>/`):
- `sites-available/default`: `listen 80 default_server;` →
  `listen 138.16.177.176:80 default_server;`
- `sites-available/dxvfix1`: `listen 443 ssl;` → `listen 138.16.177.176:443
  ssl;`, `listen 80;` → `listen 138.16.177.176:80;`

`dxv-frame-doctor.ru`/`www.dxv-frame-doctor.ru` и так резолвятся именно на
`138.16.177.176` (проверено перед правкой) — для их пользователей ничего не
изменилось, IP тот же. **Важно**: `reload` (SIGHUP) НЕ пересоздаёт слушающие
сокеты при смене адреса в `listen` — старый wildcard-сокет остаётся открытым
и блокирует новый bind (nginx логирует `emerg`, но остаётся жить на старом
конфиге, сайт не падает). Нужен именно `restart` (секундный разрыв, дальше
сразу проверять живой ответ, не только `systemctl is-active`).

После этой правки `0.0.0.0:443`/`0.0.0.0:80` свободны на всех IP, КРОМЕ
`138.16.177.176` (там по-прежнему nginx) — Caddy получил `83.217.212.64:443`
и `83.217.212.64:80` без конфликта, IP:8443 и домен:8443 (wildcard-слушатель,
без правок) продолжили работать всё это время без единой секунды простоя.

### Certificate pinning на клиенте и в админке — только для legacy IP

Класс `TrustedHttp` (`sync.TrustedHttp` в клиенте, `admin.sync.TrustedHttp` в
админке — независимые копии по конвенции проекта) по-прежнему грузит встроенный
публичный сертификат (`src/main/resources/certs/dxv-server.crt`, коммитится —
не секрет, приватный ключ только на сервере) в `TrustManagerFactory`, но
**pinned-клиент теперь используется только для легаси IP-адреса**, не для
дефолтного.

- **`TrustedHttp.clientFor(String baseUrl)`** — pinned-клиент ТОЛЬКО когда
  `baseUrl` равен `LibrarySyncClient.LEGACY_PINNED_IP_URL`
  (`https://138.16.177.176:8443`, старый self-signed); любой другой адрес,
  включая новый `DEFAULT_BASE_URL` (домен, настоящий CA-сертификат) и любой
  override — обычный клиент с системным доверием. Та же логика зеркалом в
  `admin.sync.TrustedHttp` (`AdminSettings.serverUrl` по умолчанию — тоже
  домен). Все sync-клиенты обязаны брать `HttpClient` через `clientFor(baseUrl)`,
  не собирать сами.
- Единственное осознанное исключение — `update.UpdateManager` в части похода на
  GitHub (не на ledscheme-server), там обычный `HttpClient` корректен.
- Баг-репорт 2026-08-19 (устарел после перехода на прямой домен, но контекст
  сохранён в javadoc `TrustedHttp`): `unable to find valid certification path`
  при override на Cloudflare-домен — sync-клиенты жёстко звали pinned-клиент
  независимо от адреса. `clientFor(baseUrl)` тогда исправил это, сделав pinned
  условным; сам баг больше не воспроизводим, т.к. Cloudflare не в пути трафика
  по умолчанию.
- Если/когда все установленные клиенты перейдут на новые версии (домен) — весь
  pinning-механизм (`TrustedHttp` в обоих репо + встроенный `.crt` + IP-блок в
  Caddyfile) можно будет убрать. Не раньше.

### SSH-хардening VPS (2026-09-11)

Аудит выявил: `PermitRootLogin yes` + `PasswordAuthentication yes` +
неактивный `fail2ban`, при десятках тысяч попыток брутфорса в `/var/log/
auth.log`. Исправлено: `/etc/ssh/sshd_config.d/50-cloud-init.conf` →
`PasswordAuthentication no`, `PermitRootLogin prohibit-password` (ключ
по-прежнему работает); `fail2ban` установлен и активен (`jail.d/sshd.local`,
`maxretry=5 bantime=3600`). Не относится к led-scheme напрямую, но затрагивает
тот же VPS — учитывать при следующем аудите.

### Ручной override адреса (для сетей, блокирующих порт 8443)

- У клиента есть поле «Адрес сервера (переопределение)» в Настройки →
  Предпочтения → Синхронизация (`ui.PreferencesDialog.buildSyncGroup`,
  `AppSettings.syncServerUrlOverride`, роутер
  `SettingsManager.get/setSyncServerUrlOverride`). Все sync-клиенты резолвят
  адрес через `LibrarySyncClient.resolveBaseUrl(SettingsManager)`. Пусто/`null`
  — адрес по умолчанию (теперь домен, не IP).
- **Известный пробел**: `CabinetConfigPickerDialog` подключён к override только
  на тех call site, где `SettingsManager` был доступен по цепочке без широкого
  рефакторинга («Скачать конфиг приёмной карты…» и постэкспортный проброс из
  «Экспорт NovaLCT для контроллера…»). Появится ещё один call site без доступа
  к settings — либо протащить `settings`, либо явно решить, что override не
  нужен.
