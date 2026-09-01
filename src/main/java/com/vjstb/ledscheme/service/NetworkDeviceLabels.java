package com.vjstb.ledscheme.service;

import com.vjstb.ledscheme.model.NetworkDevicePlacement;
import com.vjstb.ledscheme.model.NetworkDeviceType;
import com.vjstb.ledscheme.model.SchemaNode;

/**
 * Резолв "живого" отображаемого имени {@link NetworkDevicePlacement} (запрос
 * пользователя/своя пометка > имя связанного узла общей схемы > имя
 * каталожного типа > заглушка) — общая логика для {@code
 * ui.NetworkCanvasPanel} (подпись блока на канвасе) и {@code
 * ui.NetworkAddressTableDialog} (столбец "Устройство" общей таблицы
 * адресов). Вынесено сюда ИЗ {@code NetworkCanvasPanel}, где было
 * приватным, — второй потребитель появился с адресной таблицей, дублировать
 * такую логику (три источника имени, приоритет между ними) в двух местах
 * рискованно: правка в одном легко разойдётся с другим.
 */
public final class NetworkDeviceLabels {

    private NetworkDeviceLabels() {
    }

    /** {@code null}-scene (ничего не выбрано) обрабатывается так же, как
     *  ненайденный узел — резолв просто переходит к следующему источнику. */
    public static String resolveLabel(NetworkDevicePlacement p, AppModel model) {
        if (p.getCustomLabel() != null && !p.getCustomLabel().isBlank()) {
            return p.getCustomLabel();
        }
        if (p.getLinkedSchemaNodeId() != null) {
            SchemaNode node = findSchemaNode(p.getLinkedSchemaNodeId(), model);
            if (node != null) {
                return node.getLabel();
            }
        }
        if (p.getDeviceTypeId() != null) {
            NetworkDeviceType type = model.getWorkspace().networkDeviceTypeById(p.getDeviceTypeId());
            if (type != null) {
                return type.getName();
            }
        }
        return "(устройство)";
    }

    private static SchemaNode findSchemaNode(String id, AppModel model) {
        if (model.getCurrentScene() == null) {
            return null;
        }
        for (SchemaNode n : model.getCurrentScene().getSchemaNodes()) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }
}
