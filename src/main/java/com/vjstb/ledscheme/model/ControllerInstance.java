package com.vjstb.ledscheme.model;

import java.util.UUID;

/**
 * Экземпляр контроллера, назначенный экрану. Экран может обслуживаться несколькими
 * контроллерами — их суммарное число портов даёт общее число портов расключения сигнала.
 */
public class ControllerInstance {

    private String id = UUID.randomUUID().toString();
    private String controllerTypeId;
    private String label = "";
    /** id ДРУГОГО контроллера этого же экрана, который целиком подхватывает сигнал,
     *  если этот (основной) откажет — резерв на уровне контроллера, а не отдельного
     *  порта: второй контроллер полностью дублирует все порты первого. null — резерв
     *  не назначен. Задаётся на ОСНОВНОМ контроллере (см. {@link #getBackupControllerId()}),
     *  как и {@code SignalChain.backupPortNumber} для отдельного порта. */
    private String backupControllerId;
    /** Резерв на уровне ОТДЕЛЬНОЙ КАРТЫ (пула Ethernet-портов) этого контроллера —
     *  в отличие от {@link #backupControllerId} (весь контроллер целиком дублирует
     *  другой), здесь конкретная карта резервируется конкретной картой (возможно —
     *  другого) контроллера сцены: контроллеры с несколькими выходными картами
     *  (например, Novastar H2 — 2 карты) резервируют их зачастую по отдельности, а
     *  не весь контроллер разом (запрос: "сделать так же для контроллеров с картами,
     *  клик по заголовкам карт в окне портов"). Ключ — 0-based индекс ПУЛА ЭТОГО
     *  контроллера (см. {@code ControllerType.ethernetPoolCount}); null/отсутствие
     *  записи — у карты резерва нет. */
    private java.util.Map<Integer, CardBackupLink> cardBackupLinks = new java.util.LinkedHashMap<>();

    /** Куда резервируется одна карта {@link #cardBackupLinks} — id контроллера сцены
     *  (может быть этот же или другой) и 0-based индекс ЕГО пула. */
    public static class CardBackupLink {
        private String controllerId;
        private int poolIndex;

        public CardBackupLink() {
        }

        public CardBackupLink(String controllerId, int poolIndex) {
            this.controllerId = controllerId;
            this.poolIndex = poolIndex;
        }

        public String getControllerId() {
            return controllerId;
        }

        public void setControllerId(String controllerId) {
            this.controllerId = controllerId;
        }

        public int getPoolIndex() {
            return poolIndex;
        }

        public void setPoolIndex(int poolIndex) {
            this.poolIndex = poolIndex;
        }

        public CardBackupLink copy() {
            return new CardBackupLink(controllerId, poolIndex);
        }
    }

    public ControllerInstance() {
    }

    public ControllerInstance(String controllerTypeId, String label) {
        this.controllerTypeId = controllerTypeId;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getControllerTypeId() {
        return controllerTypeId;
    }

    public void setControllerTypeId(String controllerTypeId) {
        this.controllerTypeId = controllerTypeId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBackupControllerId() {
        return backupControllerId;
    }

    public void setBackupControllerId(String backupControllerId) {
        this.backupControllerId = backupControllerId;
    }

    public java.util.Map<Integer, CardBackupLink> getCardBackupLinks() {
        return cardBackupLinks;
    }

    public void setCardBackupLinks(java.util.Map<Integer, CardBackupLink> cardBackupLinks) {
        this.cardBackupLinks = cardBackupLinks != null ? cardBackupLinks : new java.util.LinkedHashMap<>();
    }

    public ControllerInstance copy() {
        ControllerInstance c = new ControllerInstance();
        c.id = id;
        c.controllerTypeId = controllerTypeId;
        c.label = label;
        c.backupControllerId = backupControllerId;
        for (java.util.Map.Entry<Integer, CardBackupLink> e : cardBackupLinks.entrySet()) {
            c.cardBackupLinks.put(e.getKey(), e.getValue().copy());
        }
        return c;
    }
}
