package com.vjstb.ledscheme.service.graphimport;

import com.vjstb.ledscheme.model.CardPort;
import com.vjstb.ledscheme.model.EquipmentPreset;
import com.vjstb.ledscheme.model.PortDirection;
import com.vjstb.ledscheme.model.SchemaCard;
import com.vjstb.ledscheme.model.SchemaMode;
import com.vjstb.ledscheme.model.SchemaNodeType;

/** Пресеты библиотеки для тестов импорта схем (имена — из библиотеки пользователя). */
public final class GraphImportFixtures {

    private GraphImportFixtures() {
    }

    public static EquipmentPreset signal(String name, CardPort... ports) {
        EquipmentPreset p = new EquipmentPreset();
        p.setMode(SchemaMode.SIGNAL);
        p.setCategory(SchemaNodeType.CONTROLLER);
        p.setName(name);
        if (ports.length > 0) {
            SchemaCard c = new SchemaCard();
            c.setName("Basic Set");
            for (CardPort port : ports) {
                c.getPorts().add(port);
            }
            p.getCards().add(c);
        }
        return p;
    }

    public static EquipmentPreset power(String name, CardPort... ports) {
        EquipmentPreset p = new EquipmentPreset();
        p.setMode(SchemaMode.POWER);
        p.setCategory(SchemaNodeType.SOURCE);
        p.setName(name);
        for (CardPort port : ports) {
            p.getPowerConnectors().add(port);
        }
        return p;
    }

    public static CardPort port(String type, PortDirection dir, int count) {
        return new CardPort(type, dir, count);
    }
}
