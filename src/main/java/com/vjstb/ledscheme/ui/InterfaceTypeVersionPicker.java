package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.InterfaceType;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Двухуровневый выбор типа разъёма вместо плоского списка вида "HDMI 2.1"/
 * "HDMI 2.0" отдельными строками: сначала вид интерфейса (HDMI/DisplayPort/
 * SDI/Ethernet/...), затем версия — из СОБСТВЕННОГО списка версий этого вида
 * (см. {@link InterfaceType}, справочник — этап «Библиотеки»/админский режим).
 * Виды без версий (например XLR/BNC) используются одним названием без версии.
 * "Другое (свой текст)…" — сентинел для всего, что вне справочника, включая
 * уже сохранённые ранее произвольные строки — без него старые данные, не
 * совпавшие ни с одним видом/версией, были бы не показать/не отредактировать.
 */
public class InterfaceTypeVersionPicker extends JPanel {

    private static final String CUSTOM = "Другое (свой текст)…";
    private static final String CARD_VERSION = "version";
    private static final String CARD_CUSTOM = "custom";
    private static final String CARD_NONE = "none";

    private final AppModel model;
    private final JComboBox<String> familyCombo = new JComboBox<>();
    private final JComboBox<String> versionCombo = new JComboBox<>();
    private final JTextField customField = new JTextField();
    private final CardLayout versionCards = new CardLayout();
    private final JPanel versionHost = new JPanel(versionCards);
    private Runnable onChange = () -> { };

    public InterfaceTypeVersionPicker(AppModel model) {
        super(new GridLayout(1, 2, 4, 0));
        this.model = model;
        for (InterfaceType t : model.getInterfaceTypes()) {
            familyCombo.addItem(t.getName());
        }
        familyCombo.addItem(CUSTOM);
        familyCombo.addActionListener(e -> {
            onFamilyChanged();
            onChange.run();
        });
        versionCombo.addActionListener(e -> onChange.run());
        customField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        });

        versionHost.add(versionCombo, CARD_VERSION);
        versionHost.add(customField, CARD_CUSTOM);
        versionHost.add(new JLabel(""), CARD_NONE);

        add(familyCombo);
        add(versionHost);

        if (familyCombo.getItemCount() > 0) {
            familyCombo.setSelectedIndex(0);
        }
        onFamilyChanged();
    }

    /** Вызывается при любом изменении выбора (вид/версия/свой текст) — например,
     *  чтобы обновить живой предпросмотр подписи кабеля (см. CableTypeDialog). */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange != null ? onChange : () -> { };
    }

    private void onFamilyChanged() {
        Object sel = familyCombo.getSelectedItem();
        if (CUSTOM.equals(sel)) {
            versionCards.show(versionHost, CARD_CUSTOM);
            return;
        }
        InterfaceType t = model.interfaceTypeByName((String) sel);
        versionCombo.removeAllItems();
        if (t == null || t.getVersions().isEmpty()) {
            versionCards.show(versionHost, CARD_NONE);
            return;
        }
        for (String v : t.getVersions()) {
            versionCombo.addItem(v);
        }
        versionCombo.setSelectedIndex(0);
        versionCards.show(versionHost, CARD_VERSION);
    }

    /** Составленная строка "Вид Версия" (или просто текст из "Другое"), в
     *  которой тип разъёма хранится и раньше (CardPort.connectorType и т.п.). */
    public String getValue() {
        Object sel = familyCombo.getSelectedItem();
        if (CUSTOM.equals(sel) || sel == null) {
            return customField.getText().trim();
        }
        InterfaceType t = model.interfaceTypeByName((String) sel);
        String version = versionCombo.getSelectedItem() instanceof String s ? s : null;
        return t != null ? t.composedLabel(version) : String.valueOf(sel);
    }

    /** Пытается разобрать уже сохранённую строку обратно на (вид, версия) —
     *  если она в точности совпадает с "Вид"+"Версия" известного вида (или
     *  просто с названием вида без версий) — выставляет оба комбобокса;
     *  иначе считает всю строку произвольным текстом ("Другое"), не теряя
     *  старые/нестандартные записи. */
    public void setValue(String label) {
        String value = label == null ? "" : label;
        for (int i = 0; i < familyCombo.getItemCount(); i++) {
            String name = familyCombo.getItemAt(i);
            if (CUSTOM.equals(name)) {
                continue;
            }
            InterfaceType t = model.interfaceTypeByName(name);
            if (t == null) {
                continue;
            }
            if (t.getVersions().isEmpty()) {
                if (value.equalsIgnoreCase(name)) {
                    familyCombo.setSelectedItem(name);
                    onFamilyChanged();
                    return;
                }
                continue;
            }
            for (String v : t.getVersions()) {
                if (value.equalsIgnoreCase(t.composedLabel(v))) {
                    familyCombo.setSelectedItem(name);
                    onFamilyChanged();
                    versionCombo.setSelectedItem(v);
                    return;
                }
            }
        }
        familyCombo.setSelectedItem(CUSTOM);
        onFamilyChanged();
        customField.setText(value);
    }
}
