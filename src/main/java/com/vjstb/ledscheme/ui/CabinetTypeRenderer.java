package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CabinetType;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

/** Рендерер выпадающего списка типов кабинетов: показывает название. */
public class CabinetTypeRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof CabinetType ct) {
            setText(ct.getName());
        } else if (value == null) {
            setText("— нет кабинетов —");
        }
        return this;
    }
}
