package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Баг-репорт: "вкладка recent должна быть общей для всех линий схемы, а не
 *  только для текущей выбранной" — {@link RecentColorsChooserPanel#remember}
 *  ведёт ОДИН общий (static) список для всего процесса приложения, а не
 *  отдельный на каждый вызов {@code JColorChooser.showDialog}. */
class RecentColorsChooserPanelTest {

    @BeforeEach
    void reset() {
        RecentColorsChooserPanel.clearForTests();
    }

    @Test
    void rememberAddsNewestFirst() {
        RecentColorsChooserPanel.remember(Color.RED);
        RecentColorsChooserPanel.remember(Color.GREEN);
        RecentColorsChooserPanel.remember(Color.BLUE);

        assertEquals(List.of(Color.BLUE, Color.GREEN, Color.RED),
                RecentColorsChooserPanel.recentSnapshotForTests());
    }

    @Test
    void rememberSameColorAgainMovesToFrontInsteadOfDuplicating() {
        RecentColorsChooserPanel.remember(Color.RED);
        RecentColorsChooserPanel.remember(Color.GREEN);
        RecentColorsChooserPanel.remember(Color.RED); // повторный выбор того же цвета

        assertEquals(List.of(Color.RED, Color.GREEN), RecentColorsChooserPanel.recentSnapshotForTests(),
                "повторный выбор должен переносить цвет в начало, а не дублировать запись");
    }

    @Test
    void rememberCapsListSizeAtMaximum() {
        for (int i = 0; i < 40; i++) {
            RecentColorsChooserPanel.remember(new Color(i % 256, 0, 0));
        }
        assertTrue(RecentColorsChooserPanel.recentSnapshotForTests().size() <= 24,
                "список недавних не должен расти неограниченно");
    }

    @Test
    void rememberIgnoresNull() {
        RecentColorsChooserPanel.remember(null);
        assertTrue(RecentColorsChooserPanel.recentSnapshotForTests().isEmpty());
    }
}
