package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.NovaLctScreenOrder;
import com.vjstb.ledscheme.service.NovaLctScrWriter;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Дымовой тест новых шагов экспорта NovaLCT («Порядок экранов», «Как прописывать экраны»): окна
 *  строятся без исключений (раньше такие диалоги ломались только при показе пользователю — см.
 *  баг-репорты про невидимые радиокнопки в JOptionPane). Модальные окна не показываем — конструктор
 *  зовётся рефлексией. Пропускается в headless-среде. */
class NovaLctExportDialogsSmokeTest {

    @Test
    void screenOrderDialogBuilds() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        NovaLctScrWriter.MixedScreen ms = NovaLctScrWriter.MixedScreen.of(
                new NovaLctScrWriter.ScreenBlock(1, 1, 128, 128, 0, Map.of()));
        Constructor<NovaLctScreenOrderDialog> c =
                NovaLctScreenOrderDialog.class.getDeclaredConstructor(Window.class, List.class);
        c.setAccessible(true);
        NovaLctScreenOrderDialog dlg = c.newInstance(null, List.of(
                new NovaLctScreenOrder.Entry("A", ms), new NovaLctScreenOrder.Entry("B", ms)));
        assertNotNull(dlg);
        dlg.dispose();
    }

    @Test
    void screenKindDialogBuilds() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Screen s = new Screen();
        s.setName("R1");
        Constructor<NovaLctScreenKindDialog> c =
                NovaLctScreenKindDialog.class.getDeclaredConstructor(Window.class, List.class);
        c.setAccessible(true);
        NovaLctScreenKindDialog dlg = c.newInstance(null, List.of(
                new NovaLctScreenKindDialog.Row(s, true, List.of("зазор 100 мм", "на одном порту разные размеры"), true),
                new NovaLctScreenKindDialog.Row(new Screen(), false, List.of(), false)));
        assertNotNull(dlg);
        dlg.dispose();
    }
}
