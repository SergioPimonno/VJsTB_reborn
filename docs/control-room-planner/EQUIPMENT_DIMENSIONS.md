# Габариты оборудования общей библиотеки

Составлено 2026-10-23 по просьбе пользователя для Планнера пультовой (`PLAN.md`).

**Как собрано.** Прочитана общая библиотека сервера (публичный `GET /api/library/changes?since=0`, 99 записей): `CONTROLLER` — 4, `EQUIPMENT` — 32, `CASE` — 2, `CABINET` — 6 (с габаритами), `NETWORK_DEVICE` — нет, `VEHICLE` — 1. Для оборудования планнера (контроллеры, процессоры, серверы, конвертеры, мониторы, питание) искалась информация в сети. Записи в БД не менялись.

**Достоверность.**
- **A** — спецификация производителя (текст PDF прочитан).
- **B** — листинги продавцов и прокатных компаний, расходятся в мелочах.
- **C** — расчёт, не из источника.
- **нет данных / нужно уточнить модель** — в сети не найдено или в библиотеке слишком общее название.

**Ограничения.** Большинство названий в библиотеке короткие («H5», «Blackmagic», «AOC 24'»), поэтому у части позиций модель принята как допущение (указано в колонке «Модель»). Все цифры из этой таблицы нужно подтвердить на реальном изделии до того, как они попадут в общую библиотеку. Размеры даны в миллиметрах как Ширина (лицевая панель) × Высота × Глубина; в ячейках, где источник не подписывал оси, ось выведена по числу U, это отмечено в примечании.

## Таблица

| Позиции библиотеки (id) | Модель / допущение | Ш × В × Г, мм | U | Вес, кг | Мощность, Вт | Достоверность | Источники | Примечание |
|---|---|---|---|---|---|---|---|---|
| **Контроллеры и процессоры** | | | | | | | | |
| MCTRL4k (`0f96cb88`, EQUIPMENT); MCTRL4k old (`dff97bbf`, CONTROLLER) | Novastar MCTRL4K | 482,6 × 89 × 372 | 2 | 4,6 | 30 | A | [Novastar MCTRL4K Specifications Rev1.0.1 (PDF, текст проверен)](https://www.rentex.com/wp-content/uploads/2018/02/NovaStar-Controller-MCTRL4K-Specifications-Rev1.0.1.pdf) | Производитель пишет «Dimensions (L×W×H) 482.6×372×89 мм»: 482.6 — ширина лицевой панели, 372 — глубина, 89 — высота (2U). Некоторые продавцы дают высоту 96 мм (вероятно, с ножками/ушами). |
| VX1000 (`f49af2c8`, EQUIPMENT); VX1000 old (`d3059d16`, CONTROLLER) | Novastar VX1000 | 483,6 × 50,1 × 351,2 | 1 | 4 | 28 | B | [Норвичский театр, прокат: спецификация VX1000](https://hire.norwichtheatre.org/?p=3741)<br>[B&H: VX1000](https://www.bhphotovideo.com/c/product/1865481-REG/novastar_vx1000_all_in_one_controller.html)<br>[iDJnow: VX1000](https://idjnow.com/novastar-vx1000-controller.html) | Три продавца дают 483,6×351,2×50,1 мм и 4 кг. Мощность 28 Вт (прокатная карточка) или 35 Вт (B&H). Другой продавец даёт 440×321×50 мм: расхождение, оригинал datasheet не получен. VX1000 Pro — другая модель. |
| ROE HD102 (`6e084c05`, EQUIPMENT); HD102 old (`dc7b9a68`, CONTROLLER) | ROE HD102 | — × 44,45 × — | 1 | 5,1 | — | A (частично) | [ROE Visual: брошюра процессоров, март 2022 (PDF, текст проверен)](https://www.roevisual.com/uploads/files/Product%20File/roe-visual-processors-brochure-print-mar-30-2022.pdf) | В брошюре ROE: высота 1U, вес 5,1 кг. Ширина, глубина и мощность не приведены; ширина принимается 19″ (482,6 мм) по формату 1U, глубину надо измерить. |
| H2 (`68f58b07`, EQUIPMENT); H2 old (`ad68805d`, CONTROLLER) | Novastar H2 (шасси) | 482,6 × 88,1 × 455 | 2 | 15,6 | 210 | B | [audioeffetti: Novastar H2 master](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-m07030008-h2-main-frame-all-in-one-video-splicing-processor-hosts-max-2-output-cards-p71481) | Размеры у продавца без подписи осей: 482,6 — ширина, 88,1 — высота (2U), 455 — глубина. Вес нетто 15,6 кг (брутто 18). |
| H5 (`49c0132c`, EQUIPMENT) | Novastar H5 (шасси) | 482,6 × 228,2 × 532,8 | 5 | 25 | 400 | B | [audioeffetti: Novastar H5](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-h5-h5-main-frame-all-in-one-video-splicing-processor-hosts-max-3-output-cards-p71480) | Оси выведены по числу U (5U ≈ 222 мм): 228,2 — высота, 532,8 — глубина. Вес брутто 28 кг. |
| H9 (`a80dbd2b`, EQUIPMENT) | Novastar H9 (шасси) | 482,6 × 405,8 × 533 | 9 | 35 | 450 | B | [audioeffetti: Novastar H9 master](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-m07030004-h9-main-frame-all-in-one-video-splicing-processor-hosts-max-5-output-cards-p71479) | Оси выведены по числу U (9U ≈ 400 мм). Вес брутто 49 кг. |
| H9 Enhanced (`bf227562`, EQUIPMENT) | Novastar H9 Enhanced | — | 9 | — | — | нет данных | [audioeffetti: Novastar H9 master](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-m07030004-h9-main-frame-all-in-one-video-splicing-processor-hosts-max-5-output-cards-p71479) | Источника для H9 Enhanced не найдено. У H15 Enhanced шасси совпадает с H15, возможно, то же и у H9, но это предположение. Число U взято по названию серии (9U). |
| H15 (`202e6486`, EQUIPMENT) | Novastar H15 (шасси) | 482,6 × 683 × 533 | 15 | 61,8 | 900 | B | [audioeffetti: Novastar H15](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-h15-h15-main-frame-all-in-one-video-splicing-processor-hosts-max-10-output-cards-p71478) | Оси выведены по числу U (15U ≈ 667 мм, 683 мм с ножками). Вес брутто 75,5 кг. |
| H15 Enhanced (`7c1c707d`, EQUIPMENT) | Novastar H15 Enhanced | 482,6 × 683 × 533 | 15 | 61,8 | 900 | B | [audioeffetti: Novastar H15 Enhanced](https://audioeffetti.com/en/category/video/video-signal-processing/novastar-m04020108-all-in-one-video-splicing-processor-hosts-max-30-input-and-16-output-cards-p100088) | Страница даёт 48,26×68,3×53,3 см — те же размеры, что у H15. |
| PixelHue Q8 (`d301eebf`, EQUIPMENT) | PixelHue Q8 | 482,6 × 335,3 × 694,7 | 7 | 42,6 | 1400 | A | [PixelHue Q8 Seamless Switcher Specifications V1.9.0 (PDF, текст проверен)](https://www.rentex.com/wp-content/uploads/2026/04/Q8-Seamless-Switcher-Specifications-V1.9.0.pdf) | В datasheet: габариты 482,6×694,7×335,3 мм (оси не подписаны; высота выведена по «Space Requirement 7U»), вес без аксессуаров 42,6 кг. Для перевозки: флайткейс 1007,6×600,4×476,7 мм, брутто с кейсом 99,7 кг. |
| Barco E2 (`07ac3e9e`, EQUIPMENT) | Barco E2 (Gen 2), одно шасси | 432 × 178 × 569 | 4 | 31 | 1100 | A | [Barco E2 Gen 2 BTO spec sheet (PDF, текст проверен)](https://assets.barco.com/m/3b64ca7d2e0027e/original/E2-Gen-2-BTO-en-Spec-sheet.pdf)<br>[Audio General: E2 Jr (вес одного шасси)](https://www.audiogeneral.com/store/products/view/0602-2510) | Производитель: высота 17,8 см (4RU), ширина 43,2 см без ручек и 48,3 см с ручками, глубина 56,9 см (62,2 см с ручками). Вес зависит от конфигурации: 31–248 кг (31 кг — нижняя граница для 4RU). Мощность 1100 Вт на шасси. В библиотеке не указано поколение (Gen 2 принято). |
| **Медиасервера** | | | | | | | | |
| Disquise D3 (`645c69b8`, EQUIPMENT) | disguise vx 4 / vx 4+ / gx 3 (все серверы D3 — корпус 4U) | 445 × 178 × 593 | 4 | 21 | 900 | A | [vx 4 spec sheet 2019](https://www.disguise.one/media/3877/disguise_vx4_specsheet_q1_2019.pdf); [vx 4+](https://www.disguise.one/media/8645/spec_sheet_vx-4plus.pdf); [gx 3](https://disguise.one/sites/default/files/2025-04/Disguise%20GX%203%20spec%20sheet.pdf) | Все серверы D3 — 4U 19″. В новых листах 434–440 × 176–177 × 594–595 мм, с ручками 625 мм. Мощность: vx 4 — 900 ВА максимум, vx 4+ — 785 Вт пик (принято 900). Принято пользователем 2026-10-24. |
| SMODE (`589af434`, EQUIPMENT) | SMODE (габариты приняты как у D3) | 445 × 178 × 593 | 4 | 21 | 900 | принято по заданию | как D3 (ссылки выше) | Собственные листы SMODE габаритов не публикуют; по решению пользователя 2026-10-24 принято как у D3. Уточнить по изделию. |
| **Конвертеры** | | | | | | | | |
| CVT4k (`9c86d86e`, EQUIPMENT) | Novastar CVT4K-S (принято) | 500 × 96 × 386 | 2 | 4,6 | 10 | A (вес, мощность) / B (габариты) | [Novastar CVT4K-S Specifications (PDF, вес и мощность проверены)](https://www.procom-me.com/wp-content/uploads/2022/03/CVT4K-S-Fiber-Converter-Specifications.pdf)<br>[AVLGear: CVT4K-S (габариты по листингу)](https://www.avlgear.com/collections/novastar/products/novastar-cvt4k-s-fiber-converter) | Вес 4,6 кг и мощность 10 Вт — datasheet Novastar; габариты 500×386×96 мм — по листингам продавцов (оси не подписаны, 96 мм ≈ 2U с ножками). Модель в библиотеке названа «CVT4k», уточнить, что это именно CVT4K-S. |
| Lightware 110 (`bdcd7dc9`, EQUIPMENT) | Lightware «110» — модель не определена | — | — | — | — | нужно уточнить модель | [Lightware: DVI-OPT-TX110](https://www.lightware.com/en/products/Extenders/dvi-opt-tx110) | Если это DVI-OPT-TX110/RX110: 39×15,4×87,5 мм, 95 г (Lightware). Для HDMI-версий (HDMI-3D-OPTM-…110) данных не найдено. |
| Ligtware 220 (`ea298a3f`, EQUIPMENT) | Lightware «220» — модель не определена | — | — | — | — | нужно уточнить модель | [Lightware: HDMI-TPS-RX220AK](https://lightware.com/hdmi-tps-rx220ak) | Если это HDMI-TPS-RX220AK: 221 (ширина)×26 (высота)×100,4 (глубина) мм, 610 г; для TX220 данных не найдено. «HDMI20-OPTC-…220» — другие изделия. |
| Blackmagic (`c2e196d4`, EQUIPMENT) | Blackmagic «Mini Converter» — модель не определена (SDI→HDMI 6G как пример) | 114 × 23 × 92 | — | 0,257 | 5,2 | B (модель уточнить) | [Blackmagic: Mini Converter SDI to HDMI 6G (мощность)](https://www.blackmagicdesign.com/products/miniconverters/techspecs/W-CNV-31)<br>[Digital Camera Warehouse: габариты и вес](https://www.digitalcamerawarehouse.com.au/blackmagic-design-sdi-to-hdmi-6g-mini-converter) | Для SDI to HDMI 6G: 114×23×92 мм, 257 г, 5,2 Вт; у других Mini Converter размеры другие. Уточнить модель. |
| **Мониторы** | | | | | | | | |
| AOC 24' (`9439cfc0`, EQUIPMENT); Монитор AOC 24' (`2e092168`, EQUIPMENT) | AOC 24″ (пример — AOC 24G2) | 539 × 375 × 227 | — | 4,25 | — | B (модель уточнить) | [Evatech: AOC 24G2](https://evatech.com.au/product/7090/aoc-24g2-238inch-ips-144hz-freesync-gaming-monitor)<br>[DisplaySpecifications: AOC 24G2ZU](https://www.displayspecifications.com/en/model-weight/7fed1e76) | AOC 24G2 с подставкой: ширина 539 мм, высота 375–505 мм (регулировка), глубина 227 мм, 4,25 кг; только панель — 322×539×47 мм. В библиотеке модель не указана. Мощность не проверена. |
| TV 43' (`b8323446`, EQUIPMENT) | Телевизор 43″ (расчёт по диагонали) | 952 × 535 × — | — | — | — | C (расчёт) | — | Геометрия панели 16:9: ширина ≈ 0,872×диагональ, высота ≈ 0,490×диагональ. Глубину, стойку и вес надо взять из конкретной модели. |
| TV 75' (`a6b5276e`, EQUIPMENT) | Телевизор 75″ (расчёт по диагонали) | 1660 × 934 × — | — | — | — | C (расчёт) | — | То же, что для 43″. Глубину, стойку и вес надо взять из конкретной модели. |
| **Питание** | | | | | | | | |
| UPS FOH (`4dca1f2b`, EQUIPMENT) | UPS FOH | — | — | — | — | нет данных | — | Модель не указана — нужны габариты (форм-фактор: напольный или 2U в стойку). |
| AlpenBox 63A->4x32A (`d330fcbd`, EQUIPMENT); AlpenBox 63A->2x32A (`28f82c39`, EQUIPMENT); AlpenBox 125A->4x32A (`151f9282`, EQUIPMENT) | AlpenBox (63A→2×32A, 63A→4×32A, 125A→4×32A) | — | — | — | — | нет данных | — | Поиск по названию в сети ничего не дал (вероятно, фирменная или прокатная сборка). Ближайшие по классу аналоги другие изделия, поэтому цифры не переносились. Измерить. |
| Проходная (`646034b4`, EQUIPMENT); Крыса (`0bebfb6d`, EQUIPMENT); Пилот (`b23492e0`, EQUIPMENT); Штаны 63A->2x32A (`c8c266ae`, EQUIPMENT); Тройник 16A->2xSHUKO+16A (`35b57656`, EQUIPMENT); Тройник 16A->3x16A (`516aab23`, EQUIPMENT); Проходная Socapex (`b89e92b1`, EQUIPMENT) | Распределители и разветвители (Проходная, Крыса, Пилот, Штаны, Тройники, Проходная Socapex) | — | — | — | — | нет данных | — | Названия профессиональные, изделия кустарные/сборные, спецификаций в сети нет. Измерить. |

## Что уже есть в библиотеке с размерами (проверять не нужно)

| Позиция | Размеры | Вес |
|---|---|---|
| Кофр «MG7s Case» (`CASE`) | 1000 × 600 × 705 мм, штабелирование 3 | 120 кг |
| Кофр «Dicolor Case» (`CASE`) | 1080 × 660 × 705 мм, штабелирование 3 | 120 кг |
| Кабинеты `CABINET` (6 записей) | у всех указаны ширина, высота, вес; у «MG7s Cube XO Studio» не указана глубина | — |

В библиотеке **нет ни одного рэк-кофра** (ни числа юнитов, ни внутренней глубины): для планнера их нужно завести (см. `PLAN.md`, раздел 5). Не найдены на сервере и сетевые устройства (`NETWORK_DEVICE`).

## Итог

- Подтверждено производителем (A): MCTRL4K, PixelHue Q8, Barco E2 (по Gen 2), частично ROE HD102 и CVT4K-S.
- Из листингов продавцов (B): VX1000, H2, H5, H9, H15, H15 Enhanced, конвертеры и мониторы.
- Нет данных или нужно уточнить модель: H9 Enhanced, SMODE, Lightware 110/220, Blackmagic, UPS FOH, AlpenBox, распределители питания, конкретные телевизоры и мониторы.

Для ручной правки и загрузки — отдельные CSV по блокам в папке [`csv/`](csv/) (по одной строке на запись библиотеки, с полными id) и инструкция [`csv/README.md`](csv/README.md): кнопка «Импорт габаритов из CSV…» в админке читает эти файлы, показывает предпросмотр и пишет на сервер только после отметки строк и подтверждения.
