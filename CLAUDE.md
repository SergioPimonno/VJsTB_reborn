# LED Scheme Designer — гид по системе для агентов

Этот файл — вход в проект для любого агента, который берётся за задачу здесь.
Цель: не заставлять каждого агента заново вычитывать код с нуля, чтобы понять,
как связаны репозитории, где что задеплоено и какие тут неочевидные конвенции.
Копии/сокращённые версии этого файла лежат в соседних репозиториях
(`ledscheme-admin`, `ledscheme-server`, `ledscheme-model`) — этот, в
`VJsTB_reborn`, самый полный, остальные на него ссылаются.

**Правило поддержки**: когда меняешь архитектуру, деплой-процесс или
конвенцию, описанную здесь, — обнови и этот файл. Другие агенты будут ему
доверять буквально, устаревшая строчка тут хуже, чем её отсутствие.

## 1. Четыре репозитория, как они связаны

| Репозиторий | Что это | Расположение | Git |
|---|---|---|---|
| `VJsTB_reborn` | Десктоп-клиент (Java 21, Swing, Maven) | `C:\Development\VJsTB_reborn` | публичный, `SergioPimonno/VJsTB_reborn` |
| `ledscheme-admin` | Админ-консоль (Java 21, Swing, Maven) | `C:\Development\ledscheme-admin` | **НЕ git-репозиторий** (нет `.git`) — только локально, пушить некуда |
| `ledscheme-server` | Сервер (Spring Boot 3.3.4, Maven, PostgreSQL, JWT-авторизация) | `C:\Development\ledscheme-server` | **НЕ git-репозиторий** — только локально |
| `ledscheme-model` | Общие доменные POJO (кабинеты/контроллеры/кабели/...), используются клиентом и (частично, через JSON-конвенцию, не общий код) админкой | `C:\Development\ledscheme-model` | приватный, `SergioPimonno/ledscheme-model` |

`ledscheme-model` — это НЕ reactor-модуль и не submodule: клиент подключает
его как обычную Maven-зависимость `com.vjstb.ledscheme:ledscheme-model:0.1.0`,
резолвится из `~/.m2`. **После любого изменения в `ledscheme-model` нужно
сначала `mvn -DskipTests install` в нём самом**, иначе клиент/админка
соберутся со старой версией классов из локального репозитория.

Админ-консоль НЕ шарит Java-код с клиентом или моделью напрямую (кроме
редких мест, где она явно зависит от `ledscheme-model`, см. её собственный
CLAUDE.md) — синглтон-панели админки просто сериализуют/десериализуют JSON
той же формы, что ожидает клиент, каждая сторона держит свою копию record'а
с полями. Это осознанная конвенция проекта — не пытайся «убрать дублирование»
через общий класс без явного запроса.

## 2. Куда что задеплоено

Сервер и его Postgres живут на VPS **dxv** (138.16.177.176). SSH-алиас `dxv`
уже настроен (`~/.ssh/config`, root, ключ `~/.ssh/id_ed25519`) — можно просто
`ssh dxv ...`.

**КРИТИЧНО — граница ответственности на этой VPS**: на ней же крутится
отдельная система "dxvfi" (Node/PM2/nginx) — **никогда её не трогай**. Разрешено
работать только с systemd-сервисом `ledscheme-server` и Docker-контейнером
`ledscheme-postgres`.

Деплой сервера (нет автоматического CI для этого — руками):
```
# 1. Собрать fat jar локально (spring-boot-maven-plugin репакует автоматически)
cd C:\Development\ledscheme-server && mvn -DskipTests package
#    -> target/ledscheme-server-0.1.0.jar

# 2. Залить на сервер во временное место (jar большой, ~50 МБ — может уйти в фон)
scp target/ledscheme-server-0.1.0.jar dxv:/tmp/app.jar.new

# 3. На сервере: бэкап старого jar, подмена, права, рестарт
ssh dxv "cp /opt/ledscheme-server/app.jar /opt/ledscheme-server/app.jar.bak-$(date +%Y%m%d%H%M%S) \
  && cp /tmp/app.jar.new /opt/ledscheme-server/app.jar \
  && chown ledscheme:ledscheme /opt/ledscheme-server/app.jar \
  && rm /tmp/app.jar.new \
  && systemctl restart ledscheme-server \
  && sleep 5 && systemctl is-active ledscheme-server"
```
Юнит: `/etc/systemd/system/ledscheme-server.service`, `ExecStart=/usr/bin/java
-jar /opt/ledscheme-server/app.jar`, env-файл `/opt/ledscheme-server/app.env`
(секреты — `DB_*`, `JWT_SECRET` и т.п., НЕ в git). После деплоя всегда
проверяй живой эндпоинт (`curl -sk https://138.16.177.176:8443/api/library/changes`
должен дать 200) — не полагайся только на `systemctl is-active`.

**TLS/reverse-proxy (добавлено этой сессией) — сервер БОЛЬШЕ не слушает
открытый порт напрямую.** `ledscheme-server` привязан только к
`127.0.0.1:8081` (`SERVER_ADDRESS=127.0.0.1` в `/opt/ledscheme-server/app.env`,
подхватывается автоматической relaxed-биндингом Spring Boot, без правок кода).
Снаружи стоит **отдельный, выделенный** экземпляр Caddy (НЕ шарится с nginx,
который обслуживает "dxvfi" — см. границу ответственности выше), слушает
`:8443`, конфиг `/etc/caddy/Caddyfile`:
```
{
    auto_https off
    admin localhost:2019
}

https://138.16.177.176:8443 {
    tls /etc/caddy/certs/server.crt /etc/caddy/certs/server.key
    reverse_proxy 127.0.0.1:8081
}
```
Домена у сервера пока нет, поэтому сертификат — самоподписанный
(`/etc/caddy/certs/server.{crt,key}`, EC prime256v1, CN/SAN=`138.16.177.176`,
10 лет, сгенерирован вручную через `openssl req -x509 -newkey ec ...` —
встроенный `tls internal` Caddy НЕ используется, его автономный CA упирается в
то, что пользователь `caddy` не в sudoers и не может доустановить корневой
сертификат в системное хранилище, из-за чего issuance зависает без явной
ошибки). `ufw` открывает только `8443/tcp` (v4+v6) — `8081/tcp` закрыт.

Клиент и админка теперь читают сервер по адресу `https://138.16.177.176:8443`
(`LibrarySyncClient.DEFAULT_BASE_URL` в клиенте, `AdminSettings.serverUrl` по
умолчанию в админке — но у админки это persisted-настройка, старое значение
могло остаться в локальном `settings.json` конкретного пользователя, тогда
поле «Адрес сервера» на экране входа надо поправить руками один раз). Так как
сертификат самоподписанный, обычный `HttpClient.newBuilder().build()` в Java
его бы отверг — оба приложения используют **certificate pinning** (НЕ
`trust-all`): класс `TrustedHttp` (`sync.TrustedHttp` в клиенте,
`admin.sync.TrustedHttp` в админке, независимые копии по обычной конвенции
проекта) грузит встроенный публичный сертификат
(`src/main/resources/certs/dxv-server.crt`, коммитится — это не секрет,
приватный ключ есть только на сервере) в `TrustManagerFactory` и строит
`SSLContext`, доверяющий именно этому сертификату. Все HTTP-клиенты обоих
приложений, ходящие на СВОЙ сервер, обязаны брать `HttpClient` через
`TrustedHttp.client()`, а не собирать его сами (единственное осознанное
исключение — `update.UpdateManager` в клиенте, он ходит на GitHub, не на
ledscheme-server, и обычный `HttpClient` там корректен).

Если/когда у сервера появится домен и сертификат от публичного CA — весь этот
pinning-механизм (`TrustedHttp` в обоих репозиториях + встроенный `.crt`
ресурс) можно будет убрать, вернувшись к обычному `HttpClient.newBuilder().build()`.

Комментарий в `application.yml` про `deploy/README.md` — устарел, такого файла
в репозитории нет, деплой описан только здесь.

Клиент и админка деплоятся по-другому — см. секцию 4 (релизы клиента) и
CLAUDE.md админки (у неё релизов нет вообще, только локальный jar).

## 3. Синхронизация общей библиотеки — как устроена и как расширять

Это центральный механизм: общая библиотека (типы кабинетов, контроллеров,
кабелей, а также несколько «служебных» синглтонов вроде текстов Руководства)
живёт на сервере и синхронизируется в клиент.

**Таблица** `library_item` (сущность `LibraryItem` на сервере): `id`, `kind`
(строка/enum `LibraryItemKind`), `name`, `payload_json` (просто `TEXT`, сервер
его **не парсит и не валидирует** — форма/структура полностью на совести
клиента и админки), монотонный `global_seq` (append-only счётчик — на нём
строится дельта-синхронизация), `deleted` (soft delete).

Два вида элементов `LibraryItemKind`:
- **Обычные** (много строк): `CABINET, CONTROLLER, EQUIPMENT, CABLE, INTERFACE,
  CABLE_LENGTH_PROFILE, HOIST, STRUCTURE_FRAME, EQUIPMENT_CUSTOM_CATEGORY` —
  создаются/удаляются свободно, id генерируется при создании. `HOIST` (модели
  лебёдок/талей с паспортной WLL, добавлен 2026-08-11) и `STRUCTURE_FRAME`
  (элементы наземного конструктива — рама/короткая рама/стакан/контейнер
  балласта, один вид с дискриминатором `kind` внутри payload, добавлен
  2026-08-11) — единственные из этого списка (кроме CABINET/CONTROLLER), где на
  id реально ссылается FK в данных проекта (`Screen.riggingHoistTypeId` и
  четыре `Screen.structureXxxTypeId` соответственно) — поэтому их синк-удаление
  защищено так же, как CABINET/CONTROLLER (см. `AppModel.isHoistTypeReferenced`/
  `isStructureFrameTypeReferenced`), а не безусловно, как у
  CABLE/INTERFACE/CABLE_LENGTH_PROFILE. См. `RIGGING_CALC_NOTES.md` и
  `STRUCTURE_CALC_NOTES.md` за подробностями и мотивацией.
- **Синглтоны** (ровно одна строка на весь сервер, фиксированный id):
  `GUIDE_TEXT, ONBOARDING_TEXT, EQUIPMENT_CATEGORY_LABELS, CALC_DEFAULTS,
  INTERACTIVE_SCENARIOS, VERSION_MANIFEST`. Карты `SINGLETON_IDS`/
  `SINGLETON_NAMES` живут централизованно в `LibraryItemKind` (сервер) — раньше
  были приватными в `AdminLibraryController`, вынесены оттуда, чтобы их мог
  использовать и публичный `LibraryController`. Точная JSON-форма каждого
  синглтона задокументирована в class-javadoc `LibraryItemKind`.

**Два контроллера на сервере**:
- `AdminLibraryController` (`/api/admin/library/**`, нужен JWT с ролью ADMIN) —
  полный CRUD + `POST /singleton/{kind}` (upsert: создаёт с фиксированным id
  при первом обращении, иначе обновляет).
- `LibraryController` (`/api/library/**`, **единственный полностью открытый
  без токена набор путей на сервере** — см. `SecurityConfig`, `GET
  /api/library/**` разрешён анонимно): `GET /changes?since=N` (полная дельта,
  `since=0` — вся библиотека) и `GET /singleton/{kind}` (одна запись синглтона,
  добавлено для случаев вроде проверки версий при запуске — тянуть всю дельту
  ради одной маленькой записи расточительно; 404, если запись удалена или
  вообще не сохранялась, 400 — если вид не синглтон).

**На клиенте**: `sync.LibrarySyncClient` (анонимный, `fetchChanges(since)`),
`AppModel.applyLibrarySyncItems(...)` — маршрутизирует каждый элемент по полю
`kind` в `switch`, у каждого синглтона свой `applyXxx(dto)` с приватным
локальным record'ом под форму JSON (НЕ общий класс с админкой — так везде в
проекте, кроме обычных видов типа `CableType`/`CableLengthProfile`, которые
настоящие классы из `ledscheme-model`).

**В админке**: у каждого вида своя Swing-панель, реализующая
`LibraryFormPanel` (`clear/loadFromJson/toJson/currentName`) —
`CalcDefaultsPanel`, `VersionManifestPanel`, `ContentEditorPanel` (Guide +
Onboarding) и т.д., в `ledscheme-admin/.../admin/ui/`. `AdminLibraryClient`
полностью общий (kind-agnostic), про конкретную JSON-форму ничего не знает.

**Рецепт «добавить новый синглтон-вид»** (использован дважды — для
`INTERACTIVE_SCENARIOS` в прошлой сессии и `VERSION_MANIFEST` в этой):
1. Сервер: новая константа в `LibraryItemKind` + запись в `SINGLETON_IDS`/
   `SINGLETON_NAMES` + javadoc с формой JSON. Обычно **больше ничего на
   сервере менять не нужно** — общие CRUD/upsert-эндпоинты подхватывают новый
   `kind` автоматически.
2. Админка: новая `XxxPanel implements LibraryFormPanel`, добавить вкладку в
   `AdminMainFrame`.
3. Клиент: `case "XXX" -> applyXxx(dto);` в `AppModel.applyLibrarySyncItems`,
   свой приватный record под форму payload.
4. Если нужен новый способ ЧТЕНИЯ (не через общую дельту) — как было с
   `VERSION_MANIFEST` (лёгкая проверка при старте без сети всей библиотеки) —
   добавляй отдельный публичный GET на `LibraryController`, по образцу
   `/singleton/{kind}`.

## 4. Релизы клиента (VJsTB_reborn) — пайплайн и грабли

- **Текущая версия** — константа `AppInfo.VERSION` (сейчас `"2.0"`). Бампать
  перед каждым релизом.
- **CI**: `.github/workflows/release-macos.yml` и `release-linux.yml` —
  триггерятся на `push: tags: v*` ИЛИ вручную (`workflow_dispatch`, поле
  `tag`). ВАЖНО: `workflow_dispatch` берёт определение воркфлоу из `master`,
  но собирает код из коммита, на который указывает переданный тег — значит,
  если тег «старый», а воркфлоу поменялся, всё равно нужно, чтобы **сам код
  на коммите тега** содержал нужные изменения (просто обновить workflow-файл
  недостаточно, если тег не двигали).
- Оба воркфлоу тянут `ledscheme-model` (приватный репозиторий) через SSH:
  `webfactory/ssh-agent` + секрет `LEDSCHEME_MODEL_DEPLOY_KEY` (deploy-key,
  read-only, зарегистрирован в `ledscheme-model`), `git clone` во временную
  папку, `mvn install` там, только потом `mvn package` самого клиента.
- **Windows-сборка НЕ в CI** — только руками локально:
  ```
  mvn -DskipTests package
  jpackage --type app-image --input dist-input --dest dist \
    --name "LED Scheme Designer" --main-jar led-scheme.jar \
    --main-class com.vjstb.ledscheme.App --app-version "X.Y" \
    --vendor "VJsTB" --icon packaging/icon-main.ico
  # затем упаковать в zip и gh release upload
  ```
- **Иконка приложения**: `packaging/icon-main.{ico,icns,png}` — закоммичены в
  репозиторий, передаются в `jpackage --icon`. Иконка окна/панели задач в
  рантайме — `ui.AppIcons.loadAppIconImages()` + `setIconImages(...)` в
  конструкторе `MainFrame`.
- **Перенос тега на новый коммит** (типовая ситуация — доделали фичу, хотим,
  чтобы релиз её включал): `git tag -f vX.Y <commit>`, затем **обязательно**
  `git push origin :refs/tags/vX.Y` (удалить с сервера) и только потом `git
  push origin vX.Y` (создать заново) — просто `git push --force` на тег не
  прокатывает (не fast-forward).
- **Известные грабли с GitHub Releases** (наступали не раз в этой сессии):
  - Сразу после переноса/пересоздания тега `gh release upload` иногда падает
    с `HTTP 404` (кэш ID релиза не успел обновиться) — просто повторить.
  - После переноса тега или `gh release edit` без явных флагов релиз может
    незаметно откатиться в `draft`/`prerelease` — **всегда** проверяй `gh
    release view vX.Y --json isDraft,isPrerelease` после любых манипуляций и
    при необходимости `gh release edit vX.Y --draft=false --prerelease=false`.
- **`versions.txt` (корень репозитория) — ЛЕГАСИ, но живой, не удалять.**
  Актуальный клиент (начиная с этой версии) список версий для «Обновить
  версию…» и автопроверки при запуске читает **с сервера** (синглтон
  `VERSION_MANIFEST`, см. секцию 3), а не отсюда. Но ВСЕ уже выпущенные
  клиенты старее этого изменения (1.3 — исходный билд 2.0) всё ещё дёргают
  этот файл напрямую с `raw.githubusercontent.com` — без него их проверка
  обновлений падает с 404 (наступили на эти грабли и откатили удаление файла
  в этой же сессии). **При каждом релизе обновляй ОБА места**: этот файл (для
  старых клиентов) и `VERSION_MANIFEST` через админку (для новых).
- **Detached .bat-скрипты на Windows (`UpdateManager.applyAndRestartWindows`)
  — НЕ используй `timeout /t`.** У процесса, запущенного через
  `ProcessBuilder` с выводом в `NUL` (нет консоли), `timeout` мгновенно падает
  с «Input redirection is not supported» и НЕ ждёт вообще — воспроизведено и
  проверено локально (см. баг-репорт «обновился, перезапустился, осталась
  старая версия»: retry-цикл на `timeout` сгорал за доли секунды, не давая
  файлу время разблокироваться после выхода JVM/сканирования антивирусом).
  Используй `ping -n 2 127.0.0.1 >nul` для паузы — стандартный приём для
  detached/service-скриптов без консоли, реально ждёт нужное время.

## 5. Сложные фичи этого/предыдущих сеансов — как устроены

### Экспорт NovaLCT (`service.NovaLctScrWriter` и окружение)
Полностью reverse-engineered бинарный формат `.scr` — отдельный подробный
документ `NOVALCT_EXPORT.md` (корень этого репозитория): карта файлов,
структура формата (Standard/Complex/мультиэкранный), маппинг доменных
понятий в номера карт/портов NovaLCT, что уже обработано, что НЕ
подтверждено реальной загрузкой (главный риск — `writeStandardMultiScreen`),
статус импорта (заготовка, отключена из меню). Начинай оттуда, а не с чтения
кода с нуля, если задача касается этой фичи.

### Спецификация коммутации и сплайсовка кабеля (`service.CableSpecCalc`)
Библиотека кабелей разделена на два независимых смысла (важно не путать при
правках):
- `CableType` (`ledscheme-model`) — **только переходники** (разные разъёмы на
  концах), опциональная `fixedLengthM` (одно число — готовое изделие
  фиксированной длины, не каталог).
- `CableLengthProfile` (`ledscheme-model`) — **только однородный кабель**
  (одинаковый разъём на обоих концах), каталог доступных длин + запас %, для
  него считается реальная сплайсовка.
Оба несут `SchemaMode mode` (POWER/SIGNAL, `null` — старая запись до появления
поля, показывается в обоих режимах до первого явного редактирования).

`WireLabelDialog` (диалог подписи связи схемы) строит список типов **строго**
из этих двух библиотек (`AppModel.cableLengthProfilesForMode` +
`cableTypesForMode`) — никаких хардкодных абстрактных пресетов. Комбобокс
остаётся редактируемым (свободный текст как запасной вариант), с двумя
кнопками сохранить как переходник/как каталог длин, которые сразу же
предлагают отправить сохранённое в общую библиотеку на модерацию
(`ProposeDialog`).

`CableSpecCalc.minimalKit(rawLengthM, profile)` — если ни один кусок каталога
не покрывает линию одним куском, ищет МИНИМАЛЬНЫЙ по числу кусков набор (DP по
сантиметрам), не просто помечает «не покрыта». `OutputStagePanel` формирует
спецификацию как многолистовой `.xlsx` (`service.SpecXlsxWriter`) — отдельный
лист «Коммутация — закупка» (итоговые количества кусков на закупку) и
«Коммутация — сплайсовка» (какие именно линии потребовали сплайсовки и из
чего собран их комплект).

### Блок-схема площадки и автозаполнение (`AppModel.autoPopulateSchema`)
Отдельная от поэкранной сетки кабинетов модель — общая схема
питания/сигнала всей площадки (`SchemaNode`/`SchemaEdge`, `SchemaMode`).
`autoPopulateSchema(mode, autoConnectSockets)` при первом заходе в блок-схему
добавляет расключенные экраны (+ использованные контроллеры для сигнала) и,
опционально, автосоединяет гнёзда кабинетов с портами. **Ключевой инвариант**:
трогает только экраны, добавленные В ЭТОМ ЖЕ вызове (`freshlyAddedScreenIds`)
— уже существующий в схеме экран никогда не пересканируется повторно, иначе
ручное отключение автоматически добавленной связи откатывалось бы обратно при
следующем заходе. Режим разъёмов (сокеты кабинетов как физические точки
подключения) и связанные с ним настройки (`socketWiringEnabled`,
`chainEndpointSocketsEnabled`, `connectorDisplayMode`,
`schemaAutoPopulateEnabled`) — независимы для питания и сигнала (4 отдельных
пары полей в `UserProfile`, у каждой пары свой setter в `SettingsManager` и
свой роутер-метод `xxx(SchemaMode)`).

### Проверка обновлений (`update.*`, `ui.UpdateNoticeDialog`)
`VersionManifest.fetch()` читает JSON синглтона `VERSION_MANIFEST` с сервера
(не `versions.txt`, см. секцию 4 про легаси-исключение). `isNewer(a, b)` —
простое точечное сравнение по числовым сегментам (не semver, этого достаточно
для схемы версионирования проекта). `App.java` после
`frame.setVisible(true)` фоново (SwingWorker) проверяет версию и показывает
немодальный `UpdateNoticeDialog`, если доступна версия новее текущей и
пользователь ещё не закрывал уведомление именно про НЕЁ
(`AppSettings.dismissedUpdateVersion`, тот же паттерн, что и
`onboardingCompleted` — персистентный флаг «однажды показали и хватит»).
Ручной путь «Настройки → Обновить версию…» (`UpdateDialog`) не изменился в
поведении, только источник данных.

## 6. Общие конвенции, которые стоит соблюдать

- **Стиль javadoc — это часть документации, не мусор.** В этом кодовом базе
  комментарии к классам/методам часто объясняют ПОЧЕМУ (баг-репорт, что было
  раньше, что сломалось), а не только что делает код. Не вычищай их «ради
  краткости» — следующий агент опирается именно на них при выборе решения.
- Java `record` активно используется для DTO/payload-форм; для
  синглтон-payload'ов — приватный вложенный record в каждом
  `applyXxx`/`FormPanel`, НЕ общий класс между репозиториями (осознанная
  дублирующая конвенция, см. секцию 3).
- Тесты: JUnit 5, `mvn test` в каждом репозитории отдельно. У сервера —
  `@SpringBootTest` + `MockMvc` + H2 in-memory, **общий Spring-контекст на весь
  прогон surefire, БЕЗ отката между тестами** — если тест должен проверить
  «записи гарантированно нет», не полагайся на порядок выполнения, создай и
  сразу удали её сам перед проверкой (наступили на это с тестом 404 для
  `VERSION_MANIFEST`).
- **Локальная сборка на машине разработки (Windows)**: отдельного Maven нет,
  используется версия, вшитая в плагин IntelliJ IDEA:
  ```
  JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
  PATH="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.1.1/plugins/maven/lib/maven3/bin:$JAVA_HOME/bin:$PATH"
  ```
  (номер версии IDEA может со временем измениться — проверить `ls "/c/Program
  Files/JetBrains/"`, если путь не находится).
- **Живая проверка через computer-use**: пересобранный jar не подхватывается
  автоматически, если рядом лежит ярлык/Start Menu запись на
  `target/xxx-latest.jar` — после каждой пересборки `cp target/xxx.jar
  target/xxx-latest.jar`, и всегда убивать все `javaw.exe`/запускать ровно
  один свежий процесс через `nohup ... &; disown`, а не `open_application`
  (та может поднять старый ярлык вместо свежего билда).
- Никогда не трогать "dxvfi" (Node/PM2/nginx) на VPS dxv — см. секцию 2.
- Не коммитить/не пушить без явного указания пользователя (это правило
  действует всегда, независимо от того, что написано в этом файле).
