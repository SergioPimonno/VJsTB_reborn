package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomLibraryEdit;
import com.vjstb.ledscheme.settings.SettingsManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * «Указать габариты…» позиции библиотеки в Планере пультовой (запрос 2026-10-23: позиции без
 * габаритов помечаются цветом, пользователь указывает их сам и может предложить на сервер).
 *
 * <p>Что происходит при сохранении — см. {@link RoomLibraryEdit#saveDimensions}: личная позиция
 * пультовой правится сама, у остальных габариты хранятся как личные и действуют, пока общих данных
 * не хватает. «Сохранить и предложить…» после этого открывает обычное окно предложения
 * ({@link ProposeDialog}): общая позиция предлагается как правка существующей, личная — как новая.
 * Предложить можно только заполненный блок — на сервер не уходит мусор, который администратору пришлось бы
 * дописывать.
 */
final class RoomDimensionsDialog extends JDialog {

    private final AppModel model;
    private final SettingsManager settings;
    private final RoomLibraryEdit.Found found;
    private final RoomDimensionsPanel panel;

    /** Открыть окно для позиции {@code ref}; ничего не делает, если позиции нет в библиотеке. */
    static void show(Window owner, AppModel model, SettingsManager settings, RoomLibRef ref) {
        RoomLibraryEdit.Found found = RoomLibraryEdit.find(model, ref);
        if (found == null) {
            JOptionPane.showMessageDialog(owner, "Позиции нет в библиотеке (удалена или ещё не синхронизирована).",
                    "Габариты", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new RoomDimensionsDialog(owner, model, settings, found).setVisible(true);
    }

    private RoomDimensionsDialog(Window owner, AppModel model, SettingsManager settings, RoomLibraryEdit.Found found) {
        super(owner, "Габариты: " + found.name(), ModalityType.APPLICATION_MODAL);
        this.model = model;
        this.settings = settings;
        this.found = found;

        RoomDimensionsPanel.Mode mode = switch (found.ref().getKind()) {
            case CASE -> RoomDimensionsPanel.Mode.CASE;
            case ROOM_FURNITURE -> RoomDimensionsPanel.Mode.FURNITURE;
            default -> RoomDimensionsPanel.Mode.DEVICE;
        };
        panel = new RoomDimensionsPanel(mode);
        PhysicalSpec own = model.getPhysicalOverrides().get(found.ref().key());
        panel.load(own != null ? own : RoomLibraryEdit.physicalOf(found.item()));

        String note;
        if (mode == RoomDimensionsPanel.Mode.CASE) {
            note = "Кофр считается рэк-кофром, когда известна высота в юнитах и внутренняя глубина. "
                    + "Наружные размеры и вес кофра правятся в его собственной карточке библиотеки.";
        } else if (found.shared() || own != null || !isPersonalRoomItem()) {
            note = "Эти габариты сохранятся у вас и будут действовать, пока администратор не заполнит позицию в общей "
                    + "библиотеке. Хотите, чтобы они появились у всех — нажмите «Сохранить и предложить…».";
        } else {
            note = "Позиция ваша личная: габариты сохранятся прямо в ней.";
        }
        JLabel noteLabel = UiKit.muted(UiKit.wrapHtml(note));

        JButton save = new JButton("Сохранить");
        JButton propose = new JButton("Сохранить и предложить…");
        JButton clear = new JButton("Убрать мои габариты");
        JButton cancel = new JButton("Отмена");
        save.addActionListener(e -> save(false));
        propose.addActionListener(e -> save(true));
        clear.addActionListener(e -> {
            RoomLibraryEdit.clearPersonalDimensions(model, found.ref());
            dispose();
        });
        cancel.addActionListener(e -> dispose());
        clear.setEnabled(own != null);
        propose.setEnabled(settings != null);
        propose.setToolTipText(settings == null ? "Нужны настройки приложения" : "Отправить эти габариты администратору на проверку");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(clear);
        buttons.add(propose);
        buttons.add(save);
        buttons.add(cancel);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(noteLabel, BorderLayout.NORTH);
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new java.awt.Dimension(460, getHeight()));
        setLocationRelativeTo(owner);
    }

    private boolean isPersonalRoomItem() {
        RoomLibRef.Kind k = found.ref().getKind();
        return !found.shared() && (k == RoomLibRef.Kind.ROOM_EQUIPMENT || k == RoomLibRef.Kind.ROOM_FURNITURE);
    }

    private void save(boolean thenPropose) {
        PhysicalSpec spec;
        try {
            spec = panel.read();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Габариты", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean complete = RoomLibraryEdit.isComplete(found.item(), spec);
        if (thenPropose && !complete) {
            JOptionPane.showMessageDialog(this,
                    "Заполните все нужные поля — неполные габариты на сервер не предлагаются.",
                    "Предложить на сервер", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object draft;
        try {
            draft = RoomLibraryEdit.saveDimensions(model, found, spec);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Не удалось сохранить", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
        if (thenPropose) {
            ProposeDialog.show(getOwner(), settings, found.serverKind(), found.name(), draft,
                    found.shared() ? found.id() : null);
        }
    }
}
