package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.settings.ExportSchemaStyle;
import com.vjstb.ledscheme.settings.UserProfile;

/**
 * Единая точка «в каком оформлении рисуются схемы при экспорте» (запрос пользователя
 * 2026-10-24: выбор способа отрисовки в «Параметрах экспорта» и печатный стиль у ВСЕХ схем
 * в этом режиме). Общая схема ({@code SchemaCanvasPanel}) берёт цвета из {@link SchemaStyle}
 * и выбирает пресет сама (см. {@code UserProfile#effectiveExportSchemaStylePreset}), а
 * остальные картинки — схемы расключения экранов, обзор сцены, таблицы, риггинг, транспорт,
 * сеть — рисуют живыми цветами {@link Palette} (тема интерфейса). Поэтому для режима
 * {@link ExportSchemaStyle#PRINT} каждый вызов рендера оборачивается в {@link #run}:
 * на время вызова {@link Palette} переключается на светлый набор, а затем возвращается —
 * отдельного «печатного» рендера у каждой картинки заводить не нужно, и новая картинка,
 * рисующая через {@code Palette}, получает печатный вид автоматически.
 */
public final class ExportRendering {

    private ExportRendering() {
    }

    /** Выполняет {@code body} (рендер одной схемы для экспорта) в оформлении, выбранном в
     *  «Параметрах экспорта»; вызывать на потоке экспорта (как и сам рендер). */
    public static <T, E extends Exception> T run(UserProfile profile, Palette.Scoped<T, E> body) throws E {
        if (profile.getExportSchemaStyle() == ExportSchemaStyle.PRINT) {
            return Palette.withLightTheme(body);
        }
        return body.get();
    }
}
