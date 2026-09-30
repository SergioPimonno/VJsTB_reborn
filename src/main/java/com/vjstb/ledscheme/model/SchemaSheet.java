package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Одна блок-схема (лист) общей схемы площадки — запрос пользователя 2026-09-30
 * (docs/masks-and-schema-sheets/PLAN.md, пункт 8): на одной сцене может быть
 * НЕСКОЛЬКО схем питания и несколько схем сигнала (например, отдельная схема для
 * основного и для резервного тракта, или по зонам площадки), а не ровно одна на
 * режим, как было раньше.
 *
 * <p>Сам лист — только заголовок: имя, режим, порядок и умолчания листа. Узлы и
 * связи НЕ вложены сюда, а остаются ПЛОСКИМИ списками сцены ({@link
 * Scene#getSchemaNodes()}/{@link Scene#getSchemaEdges()}) и ссылаются на лист по
 * {@link SchemaNode#getSheetId()}/{@link SchemaEdge#getSheetId()} — так файл
 * проекта остаётся читаемым для админ-консоли ({@code ProjectSummary} читает
 * {@code scene.schemaNodes} напрямую) и для старых клиентов (решение D5, см.
 * {@link #storageOffsetX}).
 *
 * <p>Инвариант (см. {@code service.SchemaSheetMigration}): у каждой сцены на
 * КАЖДЫЙ режим есть хотя бы один лист; узел/связь без {@code sheetId} или со
 * ссылкой на несуществующий лист принадлежит ПЕРВОМУ (по {@link #orderIndex})
 * листу своего режима — так старые проекты открываются как «одна схема на режим»,
 * без изменений поведения.
 */
public class SchemaSheet {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private SchemaMode mode = SchemaMode.POWER;
    /** Порядок листа среди листов ТОГО ЖЕ режима сцены (0 — первый). Первый лист
     *  режима особый: только в него пишет автозаполнение экранами/контроллерами
     *  (решение пользователя D2). */
    private int orderIndex;
    /** Размер шрифта блоков этого листа по умолчанию (пункты) — для блоков БЕЗ
     *  собственного {@link SchemaNode#getFontSize()}. {@code null} — стандартный
     *  размер. Заведено заранее под трек C4 (кнопка «Шрифт схемы…», пункт 1 запроса
     *  2026-09-30); в этом треке только хранится и копируется. */
    private Integer defaultFontSize;
    /** То же, что {@link #defaultFontSize}, но для подписей линий листа (для связей
     *  без собственного {@link SchemaEdge#getFontSize()}). */
    private Integer defaultEdgeFontSize;
    /** СЛУЖЕБНОЕ поле формата файла (решение D5), в памяти всегда 0. Старый клиент
     *  не знает про листы и рисует ВСЕ узлы режима на одном холсте — если бы
     *  координаты листов хранились как есть (у каждого листа свои, от нуля), схемы
     *  легли бы у него друг на друга стопкой. Поэтому при записи в JSON листы одного
     *  режима раскладываются по X рядом (см. {@code Scene} — там расчёт и сдвиг), а
     *  здесь записывается, на сколько сдвинут этот лист; при чтении новый клиент
     *  вычитает это значение и обнуляет поле. */
    private double storageOffsetX;

    public SchemaSheet() {
    }

    public SchemaSheet(SchemaMode mode, String name, int orderIndex) {
        this.mode = mode;
        this.name = name;
        this.orderIndex = orderIndex;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SchemaMode getMode() {
        return mode;
    }

    public void setMode(SchemaMode mode) {
        this.mode = mode;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Integer getDefaultFontSize() {
        return defaultFontSize;
    }

    public void setDefaultFontSize(Integer defaultFontSize) {
        this.defaultFontSize = defaultFontSize;
    }

    public Integer getDefaultEdgeFontSize() {
        return defaultEdgeFontSize;
    }

    public void setDefaultEdgeFontSize(Integer defaultEdgeFontSize) {
        this.defaultEdgeFontSize = defaultEdgeFontSize;
    }

    public double getStorageOffsetX() {
        return storageOffsetX;
    }

    public void setStorageOffsetX(double storageOffsetX) {
        this.storageOffsetX = storageOffsetX;
    }

    public SchemaSheet copy() {
        SchemaSheet s = new SchemaSheet();
        s.id = id;
        s.name = name;
        s.mode = mode;
        s.orderIndex = orderIndex;
        s.defaultFontSize = defaultFontSize;
        s.defaultEdgeFontSize = defaultEdgeFontSize;
        s.storageOffsetX = storageOffsetX;
        return s;
    }

    @Override
    public String toString() {
        return name;
    }
}
