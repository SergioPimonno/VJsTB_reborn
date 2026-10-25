package com.vjstb.ledscheme.ui.stage;

import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.room.ControlRoomExporter.View;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Что выгружать из раскладок Планера пультовой на этапе «Вывод»: какие виды рисовать (стойки спереди и сверху, план,
 * вид спереди) и нужна ли спецификация текстом. Выбор запоминается в профиле. Диалог показывается кнопкой строки
 * «Планер пультовой» таблицы «Состав вывода»; общий вывод берёт последний сохранённый выбор без вопросов
 * ({@link #fromProfile}) — так же, как таблица экранов.
 */
public record ControlRoomExportOptions(Set<View> views, boolean spec) {

    /** Без выбора в профиле: основные виды и спецификация. */
    private static final Set<View> DEFAULT_VIEWS = EnumSet.of(View.RACKS_FRONT, View.PLAN, View.ELEVATION);

    /** Последний сохранённый выбор. */
    public static ControlRoomExportOptions fromProfile(SettingsManager settings) {
        var p = settings.activeProfile();
        Set<View> views = EnumSet.noneOf(View.class);
        List<String> saved = p.getControlRoomExportViews();
        if (saved == null) {
            views.addAll(DEFAULT_VIEWS);
        } else {
            for (String name : saved) {
                for (View v : View.values()) {
                    if (v.name().equals(name)) {
                        views.add(v);
                    }
                }
            }
        }
        return new ControlRoomExportOptions(views, p.isControlRoomExportSpec());
    }

    /** Есть ли что выгружать. */
    public boolean isEmpty() {
        return views.isEmpty() && !spec;
    }

    /** Показывает диалог выбора, сохраняет его в профиль и возвращает; {@code null} — отмена. */
    public static ControlRoomExportOptions ask(Component parent, SettingsManager settings) {
        ControlRoomExportOptions current = fromProfile(settings);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.add(new JLabel("Какие виды раскладки нарисовать?"));
        List<JCheckBox> boxes = new ArrayList<>();
        for (View v : View.values()) {
            JCheckBox b = new JCheckBox(v.label(), current.views().contains(v));
            boxes.add(b);
            panel.add(b);
        }
        JCheckBox specBox = new JCheckBox("Спецификация текстом (.txt)", current.spec());
        panel.add(specBox);
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, panel, "Экспорт: Планер пультовой",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                return null;
            }
            Set<View> chosen = EnumSet.noneOf(View.class);
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    chosen.add(View.values()[i]);
                }
            }
            ControlRoomExportOptions result = new ControlRoomExportOptions(chosen, specBox.isSelected());
            if (result.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Отметьте хотя бы один вид или спецификацию.",
                        "Нечего экспортировать", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            List<String> names = new ArrayList<>();
            for (View v : View.values()) {
                if (chosen.contains(v)) {
                    names.add(v.name());
                }
            }
            settings.setControlRoomExportOptions(names, result.spec());
            return result;
        }
    }
}
