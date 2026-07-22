# LED Scheme Designer

Приложение для проектирования схем коммутации LED-экранов и видеосопровождения:
**проект → сцена → экран → LED cabinets**, с библиотекой кабинетов и схемами
расключения питания/сигнала по цепочкам (daisy chain).

Backend: Java 21 + Spring Boot 3 (Web, Data JPA, H2). Frontend: статический
веб-клиент на чистом HTML/CSS/JS (без сборки), раздаётся тем же приложением.

## Возможности (текущий этап)

- Проекты → сцены → экраны (CRUD).
- Библиотека LED cabinets (ширина/высота физическая и в пикселях, потребление,
  вес, опционально глубина), общая для всех проектов, с импортом/экспортом в JSON.
- Экран собирается из сетки rows × cols кабинетов одного типа; характеристики
  экрана (разрешение, габариты, вес, суммарное потребление) считаются автоматически.
- Схема расключения **питания**: назначение фазы (L1/L2/L3) и построение цепочек
  (daisy chain) кликами по кабинетам на схеме.
- Схема расключения **сигнала**: цепочки кабинетов, опционально привязанные
  к порту контроллера, с пометкой резервной цепочки.
- Статистика по экрану: разрешение, размер, вес, мощность, нагрузка по фазам.

Дальше по плану (не входит в этот этап): раскладка нескольких экранов на одной
сцене с учётом их взаимного положения, автораскладка цепочек (змейкой/по
строкам/столбцам), конструктор контроллеров и портов, экспорт схемы в PNG/PDF.

## Стек и структура

```
src/main/java/com/vjstb/ledscheme/
  domain/      JPA-сущности (Project, Scene, Screen, CabinetType, CabinetInstance, PowerChain, SignalChain)
  repository/  Spring Data репозитории
  service/     бизнес-логика, пересчёт характеристик экрана
  web/         REST-контроллеры (/api/...)
  dto/         DTO для запросов/ответов (Java records)
  exception/   обработка ошибок API
src/main/resources/
  application.yml
  static/      веб-клиент (index.html, css/app.js, js/app.js)
src/test/      контекстный тест + тест базового сценария (проект→сцена→экран→цепочка)
```

## Запуск в IntelliJ IDEA

1. `File → Open...` → выбрать папку проекта (там, где лежит `pom.xml`).
   IntelliJ распознает Maven-проект автоматически и подтянет зависимости,
   используя собственный встроенный Maven — устанавливать Maven отдельно не нужно.
2. Дождаться индексации/загрузки зависимостей (правый сайдбар Maven → Reload).
3. Открыть `LedSchemeApplication` (`src/main/java/com/vjstb/ledscheme`) и запустить
   через зелёную стрелку (Run), либо создать Run Configuration типа
   Spring Boot / Application с main-классом `com.vjstb.ledscheme.LedSchemeApplication`.
4. После старта приложение доступно на <http://localhost:8080>.

Требуется JDK 21+ (в Project Structure → SDK указать соответствующий JDK).

## Запуск из терминала

Если локально установлен Maven:

```
mvn spring-boot:run
```

Тесты:

```
mvn test
```

## Данные

Используется файловая база H2 (`./data/led-scheme.mv.db`, создаётся автоматически
при первом запуске, в `.gitignore`). Для смены на PostgreSQL/MySQL в дальнейшем
достаточно поменять `spring.datasource.*` в `application.yml` и добавить драйвер
в `pom.xml` — доменная модель и REST API не привязаны к H2.

## Основные REST-эндпоинты

- `GET/POST /api/cabinet-types`, `PUT/DELETE /api/cabinet-types/{id}`,
  `GET /api/cabinet-types/export`, `POST /api/cabinet-types/import`
- `GET/POST /api/projects`, `GET/PUT/DELETE /api/projects/{id}`
- `POST /api/projects/{projectId}/scenes`, `PUT/DELETE /api/scenes/{id}`
- `POST /api/scenes/{sceneId}/screens`, `GET/PUT/DELETE /api/screens/{id}`,
  `PUT /api/screens/{id}/position`
- `PATCH /api/screens/{id}/cabinets/{cabinetId}` — фаза/скрытие отдельного кабинета
- `PUT /api/screens/{id}/power-chains`, `PUT /api/screens/{id}/signal-chains` —
  замена всех цепочек экрана (клиент отправляет полный список цепочек режима)
