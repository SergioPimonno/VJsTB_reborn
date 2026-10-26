# Трек B: справочник API (модель и логика карт и блоков питания)

Для следующих треков: C (виды), D (расключение), E (экспорт). Решения — `PLAN.md` (D1…D26), алгоритмы — порт
`prototype-flow.html`. Весь код — `model` и `service`, без Swing. Тесты — `ModularFixture` + классы рядом (карта в
`TESTS.md` пока не внесена).

## Данные (`com.vjstb.ledscheme.model`)
- `Screen`: `getReceiverCards()` (`ReceiverCardInstance`: `id, receiverCardTypeId, moduleIds, manualPortOrder|null,
  hostModuleId|null`), `getModuleNumbering()` (`ModuleNumberingPattern`: `corner, axis, snake`; `palette()` — 8 плиток),
  `getPowerSupplies()` (`PowerSupplyInstance`: `id, powerSupplyTypeId, moduleIds, hostModuleId|null`),
  `getPowerAutoAreas()` (`PowerAutoArea`: `GridRect rect, powerSupplyTypeId`), `receiverCardById`, `powerSupplyById`.
  Модуль = id ячейки (`CabinetInstance`). `GridRect(r0,c0,r1,c1)` — включительно, углы нормализуются.
- `SignalChain/PowerChain.cabinetInstanceIds` = id ЗВЕНЬЕВ (кабинет / карта / блок). Формат файла не менялся.
- Предпочтения (`UserProfile` + `SettingsManager`): `getModuleNumberFormat()` («{n}», `{card}`), `getReceiverCardNumbering()`
  (`model.ReceiverCardNumbering`: 8 вариантов + `AS_MODULES`, умолчание `TL_ROWS`, `getLabel()` для списка). Сеттеры
  `setModuleNumberFormat(String)`, `setReceiverCardNumbering(...)`. UI — трек C.

## Вычисляется (чистые функции, `service`)
- `ScreenModules.of(screen, ws)` — снимок: `isModular()` (D1), `modules()`, `module(id)`, `moduleAt(r,c)`, `modulesIn(rect)`,
  `ordinaryCabinets()`, `centerMm(cab)`, `distanceMm(a,b)`. Строить один раз на отрисовку/серию вопросов.
- `ModuleNumbering`: `order(card, screen)` (id модулей по номерам), `numberOf(card, screen, moduleId)` (0 = «?»),
  `unnumbered(card, screen)`, `sequence(w,h,pattern)` (для плиток палитры), `label(format, cardName, n)` (D14, «?» при 0).
- `UnitNames`: `cardNames(screen, numbering)` / `supplyNames(...)` — `LinkedHashMap id→«К1»/«БП1»` в порядке нумерации;
  `orderedCards/orderedSupplies`; `placeOf(screen, card|supply)` — id модуля-«места» (явный хост либо ближайший к центру).
- `ReceiverCardChecks.check(screen, ws)` → `modulesWithoutCard()` (оранжевые) и `cards()`: `CardCheck` (`moduleCount`,
  `outputs`, `overOutputs` — «9/8», `overLimitModuleIds` — красные номера, `unnumbered…`, `longRibbonModuleIds` /
  `longRibbonCount()` — «⚠N» и красные линии, `resolutionWidthPx/HeightPx`, `pixels`, флаги `…Exceeded`, `hasProblem()`).
- `PowerSupplyLayout.compute(screen, ws, numbering)` → `Result.supplies()` (в порядке БП1…): `name()`, `type()`,
  `placeModuleId()`, `modules()` (`ModuleFeed` — номер шлейфа на модуле), `cards()` (`CardFeed.label()` — «К» или номер),
  `used()/outputs()/over()`, `longModuleIds()`, `hasDedicatedCard()`, `feedsCards()` («+К»), `hasProblem()` (красный);
  обратные поиски `ofModule(id)`, `ofCard(id)` (null = нет питания), `ofSupply(id)`.
- `PowerSupplyChecks.check(screen, ws, numbering)` → `modulesWithoutSupply`, `cardsWithoutSupply`, `problems` (перегруз /
  длинный кабель), `layout`, `ok()`. Для чек-листа: «без карты» = `ReceiverCardChecks…modulesWithoutCard`.
- `ChainUnits.of(screen, ws[, numbering])`: `units(Mode.SIGNAL|POWER)`, `unitOf(id)`, `cabinetsOf(unit)`, `pixels(unit)`,
  `watts(unit)`, `anchor(unit)` (мм), `name(unit)`, `isModular()`; `Unit(id, kind, screen, name, cabinets, card, supply)`;
  статические `find(scene, ws, id)` и `pruneUnknown(scene)`. Немодульный экран = прежние видимые кабинеты (тест
  `ChainUnitsTest`). Модуль звеном не бывает.

## Правки (с отменой — через `AppModel`, текущий экран)
Все возвращают `EditReport` (или `void`): `noop()`, `replaced()`, `orphaned()`, `removedIds()`, `createdIds()`,
`message()` («Заменено карт: N, модулей без карты: M» — строка под холстом), `autoInfo()` («Подобрано: 4×2 мод. на блок,
блоков: 9»), `autoOk()`. Одна операция = одна запись отмены (`runBatch`); `noop` и ошибка записи не оставляют.
- Карты: `assignReceiverCardsManual(rect, typeId)`, `assignReceiverCardsAuto(rect, typeId, w, h)`, `removeReceiverCard(id)`,
  `setReceiverCardPlace(cardId, moduleId)`, `setReceiverCardManualOrder(cardId, ids|null)`,
  `appendReceiverCardManualOrder(cardId, moduleId)` (клик режима «Порядок вручную…»), `resetReceiverCardOrder(cardId)`,
  `setModuleNumbering(pattern)`.
- Блоки: `assignPowerSuppliesManual(rect, typeId)`, `assignPowerSuppliesAuto(rect, typeId)`, `removePowerSupply(id)`,
  `setPowerSupplyPlace(supplyId, moduleId)`.
- Те же правила без `AppModel` (тесты, превью): `ReceiverCardEditor`, `PowerSupplyEditor` (+`onTypeChanged`); они не трогают
  цепочки и историю.

## Целостность — кто что вызывает
Автоматически внутри `AppModel`: `toggleCabinetHidden` (модуль уходит из карты/блока), `setCabinetTypeOverride`,
`updateScreenGrid`, `updateCabinetType` (снят флаг модуля), синк библиотеки, `deleteScreen(s)`, `updatePowerSupplyType`
(смена `cardOutput` пересчитывает автообласти этого типа; фазы пересозданных блоков выпадают, D23), загрузка проекта
(`ChainUnits.pruneUnknown`). Звенья исчезнувших карт/блоков убираются из цепочек сцены, опустевшие цепочки удаляются.
Для UI: `AppModel.moduleFlagRemovalImpact(cabinetTypeId)` → `ModuleIntegrity.Impact(cardsRemoved, suppliesRemoved,
cardsShrunk, suppliesShrunk)` — показать «N карт, M блоков и их цепочки будут удалены» ДО `updateCabinetType` (если
`impact.any()`). Типы: `deleteReceiverCardType/PowerSupplyType` бросают `IllegalStateException`, пока на тип ссылается экран
(`isReceiverCardTypeReferenced`, `isPowerSupplyTypeReferenced`).

## Цвета состояний для видов (логика отдаёт признаки, цвета выбирает вид)
Без карты / без питания — оранжевый (`modulesWithoutCard`, `modulesWithoutSupply`); перегрузка выходов карты, перегруз блока,
длинный шлейф/кабель — красный (`overLimitModuleIds`, `longRibbonModuleIds`, `Supply.over()/longModuleIds()`);
«вручную» — `card.getManualPortOrder() != null`.

## Решения, не прописанные в плане
- «Модулей без карты: M» в отчёте — осиротевшие ИМЕННО этой правкой (не все модули экрана без карты).
- Повторная авто-протяжка, дающая те же карты/блоки (и ту же запись автообласти), — `noop`, id и фазы сохраняются; то же при
  пересчёте типа, если разбиение не изменилось.
- Автоподбор блока: перебор `w ≤ ширины области`, `h ≤ высоты области` (подпись «Подобрано» — реальный размер плитки); равные
  кандидаты (число блоков, площадь, квадратность) — первый по перебору (`w` по возрастанию), как в прототипе.
- «Снять блок» удаляет и запись об автообласти, которой блок принадлежал (иначе пересчёт типа его воскресил бы).
- Якорь карты в порядке К1…КN — её модуль, получивший бы №1 от стартового угла (для Г-образных карт; план: «модуль в
  стартовом углу»); блока — его место.
- Нагрузка карты в `ChainUnits.watts` — сумма модулей; блока — `ratedPowerW`, а если не указан — сумма модулей.
