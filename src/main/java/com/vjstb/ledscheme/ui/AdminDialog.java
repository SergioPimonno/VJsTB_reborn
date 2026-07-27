package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;

/**
 * Заготовка админ-режима — редактирование общих справочников, которые пока
 * зашиты только как встроенные значения по умолчанию (см. AppModel
 * seedDefaultInterfaceTypesIfEmpty): виды интерфейса и их версии, подкатегории
 * оборудования (под общим "Прочее оборудование"), тексты Руководства/
 * Приветствия. Пригодится в первую очередь при будущей серверной интеграции
 * (общий для рабочих мест справочник вместо локального), но уже сейчас
 * позволяет не трогать код приложения ради правки списка версий/категорий.
 */
public class AdminDialog extends JDialog {

    public AdminDialog(Window owner, AppModel model, SettingsManager settings) {
        super(owner, "Админ-режим", ModalityType.MODELESS);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Виды интерфейса", buildInterfacesTab(model));
        tabs.addTab("Категории оборудования", buildCategoriesTab(model));
        tabs.addTab("Тексты", buildTextsTab(settings));

        JPanel content = new JPanel(new BorderLayout());
        content.add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(560, 460);
        setLocationRelativeTo(owner);
    }

    // ---- виды интерфейса и версии ----

    private JPanel buildInterfacesTab(AppModel model) {
        DefaultListModel<InterfaceType> listModel = new DefaultListModel<>();
        for (InterfaceType t : model.getInterfaceTypes()) {
            listModel.addElement(t);
        }
        JList<InterfaceType> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new NamedRenderer<InterfaceType>(InterfaceType::getName,
                t -> t.getVersions().isEmpty() ? "без версий" : String.join(", ", t.getVersions())));
        Runnable reload = () -> {
            InterfaceType sel = list.getSelectedValue();
            listModel.clear();
            for (InterfaceType t : model.getInterfaceTypes()) {
                listModel.addElement(t);
            }
            if (sel != null) {
                list.setSelectedValue(sel, false);
            }
        };

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(200, 200));

        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            InterfaceType r = new InterfaceTypeEditDialog(this, null).showDialog();
            if (r != null) {
                tryRun(() -> model.addInterfaceType(r.getName(), r.getVersions()));
                reload.run();
            }
        });
        JButton edit = new JButton("Изменить");
        edit.addActionListener(e -> {
            InterfaceType sel = list.getSelectedValue();
            if (sel == null) return;
            InterfaceType r = new InterfaceTypeEditDialog(this, sel).showDialog();
            if (r != null) {
                tryRun(() -> model.updateInterfaceType(sel, r.getName(), r.getVersions()));
                reload.run();
            }
        });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            InterfaceType sel = list.getSelectedValue();
            if (sel != null && confirm("Удалить вид интерфейса «" + sel.getName() + "»?")) {
                tryRun(() -> model.deleteInterfaceType(sel));
                reload.run();
            }
        });
        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        crud.add(add);
        crud.add(edit);
        crud.add(del);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(UiKit.muted("<html>Используются при выборе типа разъёма для карт оборудования и кабелей"
                + " (сначала вид, затем версия из его списка) — см. этап «Библиотеки».</html>"), BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(crud, BorderLayout.SOUTH);
        return body;
    }

    // ---- подкатегории оборудования ----

    private JPanel buildCategoriesTab(AppModel model) {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String c : model.getCustomEquipmentCategories()) {
            listModel.addElement(c);
        }
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(200, 200));

        JButton add = new JButton("Добавить");
        add.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Название подкатегории:");
            if (name != null && !name.trim().isEmpty()) {
                tryRun(() -> model.addCustomEquipmentCategory(name));
                listModel.clear();
                for (String c : model.getCustomEquipmentCategories()) {
                    listModel.addElement(c);
                }
            }
        });
        JButton rename = new JButton("Переименовать");
        rename.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) return;
            String name = JOptionPane.showInputDialog(this, "Новое название:", sel);
            if (name != null && !name.trim().isEmpty()) {
                tryRun(() -> model.renameCustomEquipmentCategory(sel, name));
                listModel.clear();
                for (String c : model.getCustomEquipmentCategories()) {
                    listModel.addElement(c);
                }
            }
        });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel != null && confirm("Удалить подкатегорию «" + sel + "»?")) {
                model.deleteCustomEquipmentCategory(sel);
                listModel.removeElement(sel);
            }
        });
        JPanel crud = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        crud.add(add);
        crud.add(rename);
        crud.add(del);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(UiKit.muted("<html>Дополнительные подкатегории под общим «Прочее оборудование» — заготовка:"
                + " список пока не подставляется автоматически в выбор категории пресета, доступен здесь для"
                + " подготовки к будущей интеграции.</html>"), BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(crud, BorderLayout.SOUTH);
        return body;
    }

    // ---- тексты руководства/приветствия ----

    private JPanel buildTextsTab(SettingsManager settings) {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        body.add(UiKit.muted("<html>Открывает существующие окна «Руководство»/«Приветствие» — редактирование"
                + " текста там же, кнопкой «✎ Редактировать» (см. верхнюю панель приложения).</html>"),
                BorderLayout.NORTH);

        JPanel buttons = new JPanel();
        buttons.setLayout(new javax.swing.BoxLayout(buttons, javax.swing.BoxLayout.Y_AXIS));
        JButton openGuide = new JButton("Открыть «Руководство»…");
        openGuide.addActionListener(e -> new GuideDialog(this, settings).setVisible(true));
        JButton openOnboarding = new JButton("Открыть «Приветствие»…");
        openOnboarding.addActionListener(e -> new OnboardingDialog(this, settings).setVisible(true));
        openGuide.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        openOnboarding.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        buttons.add(openGuide);
        buttons.add(javax.swing.Box.createVerticalStrut(6));
        buttons.add(openOnboarding);
        body.add(buttons, BorderLayout.CENTER);
        return body;
    }

    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Подтверждение", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    private void tryRun(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
