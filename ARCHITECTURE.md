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

## 5. Инфраструктура сервера — TLS / reverse-proxy / сетевой мост

Рутинный деплой — скилл `deploy-server`. Здесь — как устроено и почему; менять
что-либо из этого только по явному запросу.

### Caddy перед сервером

**Сервер БОЛЬШЕ не слушает открытый порт напрямую.** `ledscheme-server` привязан
только к `127.0.0.1:8081` (`SERVER_ADDRESS=127.0.0.1` в `app.env`, relaxed-
биндинг Spring Boot, без правок кода). Снаружи — **отдельный выделенный**
экземпляр Caddy (НЕ шарится с nginx "dxvfi"), слушает `:8443`,
`/etc/caddy/Caddyfile`:

```
{
    auto_https off
    admin localhost:2019
}

https://138.16.177.176:8443, https://ledschemedesigner.ru {
    tls /etc/caddy/certs/server.crt /etc/caddy/certs/server.key
    reverse_proxy 127.0.0.1:8081
}
```

- Сертификат самоподписанный (`/etc/caddy/certs/server.{crt,key}`, EC
  prime256v1, CN/SAN=`138.16.177.176`, 10 лет, `openssl req -x509 -newkey ec
  ...`). Встроенный `tls internal` НЕ используется (его CA упирается в то, что
  пользователь `caddy` не в sudoers, issuance зависает без ошибки).
- `ufw` открывает только `8443/tcp` (v4+v6); `8081/tcp` закрыт.

### Certificate pinning на клиенте и в админке

Т.к. сертификат самоподписанный, обычный `HttpClient.newBuilder().build()` его
отверг бы. Класс `TrustedHttp` (`sync.TrustedHttp` в клиенте,
`admin.sync.TrustedHttp` в админке — независимые копии по конвенции проекта)
грузит встроенный публичный сертификат
(`src/main/resources/certs/dxv-server.crt`, коммитится — не секрет, приватный
ключ только на сервере) в `TrustManagerFactory` и строит `SSLContext`,
доверяющий именно ему.

- **`TrustedHttp.clientFor(String baseUrl)`** — pinned-клиент ТОЛЬКО когда
  `baseUrl` равен `LibrarySyncClient.DEFAULT_BASE_URL` (наш self-signed
  IP-сертификат), иначе обычный клиент с системным доверием (для настоящего
  CA-сертификата за Cloudflare). Все sync-клиенты (`LibrarySyncClient`,
  `AuthClient`, `ProposalClient`, `ProjectArchiveClient`, `CabinetConfigClient`)
  + `update.VersionManifest`/`UpdateManager`/`UpdateDialog` обязаны брать
  `HttpClient` через `clientFor(baseUrl)`, не собирать сами.
- Единственное осознанное исключение — `update.UpdateManager` в части похода на
  GitHub (не на ledscheme-server), там обычный `HttpClient` корректен.
- Баг-репорт 2026-08-19: после включения override на
  `https://ledschemedesigner.ru` синхронизация падала с `unable to find valid
  certification path` — sync-клиенты жёстко звали `TrustedHttp.client()`
  (pinning на IP-сертификат) независимо от адреса; мост через Cloudflare
  терминирует TLS на Cloudflare (настоящий CA-сертификат). Фикс — переход на
  `clientFor(baseUrl)`. Попутно в `ProposalClient` (`pending()`/`decide()`)
  нашлись два места с голым `HttpClient.newBuilder().build()` — были сломаны и
  для pinned-адреса ещё раньше.
- Если/когда у сервера появится домен с сертификатом от публичного CA — весь
  pinning-механизм (`TrustedHttp` в обоих репо + встроенный `.crt`) можно
  убрать.

### Сетевой мост (для сетей, блокирующих порт 8443)

- У клиента есть поле «Адрес сервера (переопределение)» в Настройки →
  Предпочтения → Синхронизация (`ui.PreferencesDialog.buildSyncGroup`,
  `AppSettings.syncServerUrlOverride`, роутер
  `SettingsManager.get/setSyncServerUrlOverride`). Все sync-клиенты резолвят
  адрес через `LibrarySyncClient.resolveBaseUrl(SettingsManager)`. Пусто/`null`
  — адрес по умолчанию.
- Домен `ledschemedesigner.ru` (reg.ru, NS на Cloudflare), Cloudflare DNS
  A-запись (proxied) → `138.16.177.176`, **Origin Rule** переписывает порт
  назначения на `8443` (без неё Cloudflare шёл бы на 443/80), SSL/TLS режим
  **Full** (не Full Strict — ориджин отдаёт self-signed на IP). Проверено
  сквозным curl: `via: 1.1 Caddy` в ответе подтверждает, что трафик доходит до
  нашего Caddy, а не оседает на nginx/"dxvfi".
- **Известный пробел**: `CabinetConfigPickerDialog` подключён к override только
  на тех call site, где `SettingsManager` был доступен по цепочке без широкого
  рефакторинга («Скачать конфиг приёмной карты…» и постэкспортный проброс из
  «Экспорт NovaLCT для контроллера…»). Появится ещё один call site без доступа
  к settings — либо протащить `settings`, либо явно решить, что override не
  нужен.
