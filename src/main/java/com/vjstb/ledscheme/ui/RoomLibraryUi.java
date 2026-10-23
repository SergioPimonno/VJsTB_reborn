package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.CaseType;
import com.vjstb.ledscheme.model.PhysicalSpec;
import com.vjstb.ledscheme.model.RoomEquipmentType;
import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;
import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.service.room.RoomLibraryEdit;
import com.vjstb.ledscheme.settings.SettingsManager;
import com.vjstb.ledscheme.ui.room.RoomLibraryActions;
import java.awt.Window;
import javax.swing.JOptionPane;

/**
 * Действия с библиотекой Планнера для окна: диалоги габаритов и создания своих позиций, удаление с предупреждением и
 * «Предложить на сервер…». Одна реализация на редактор и на вкладку «Библиотека (габариты)», чтобы поведение было
 * одинаковым там и там. «Свои» — личные позиции пультовой, мебели и рэк-кофры (общие правит администратор; для них
 * доступны только личные габариты).
 */
public final class RoomLibraryUi implements RoomLibraryActions {

    private final Window owner;
    private final AppModel model;
    private final SettingsManager settings;

    public RoomLibraryUi(Window owner, AppModel model, SettingsManager settings) {
        this.owner = owner;
        this.model = model;
        this.settings = settings;
    }

    private static boolean isOwn(RoomLibraryEdit.Found f) {
        if (f == null || f.shared()) {
            return false;
        }
        RoomLibRef.Kind k = f.ref().getKind();
        return k == RoomLibRef.Kind.ROOM_EQUIPMENT || k == RoomLibRef.Kind.ROOM_FURNITURE || k == RoomLibRef.Kind.CASE;
    }

    @Override
    public void specifyDimensions(RoomLibRef ref) {
        if (ref != null) {
            RoomDimensionsDialog.show(owner, model, settings, ref);
        }
    }

    @Override
    public void createEquipment() {
        RoomItemDialog.createEquipment(owner, model);
    }

    @Override
    public void createCase() {
        RoomItemDialog.createCase(owner, model);
    }

    @Override
    public void createFurniture(RoomFurnitureType.Kind kind) {
        RoomItemDialog.createFurniture(owner, model, kind);
    }

    @Override
    public boolean canEdit(RoomLibRef ref) {
        return isOwn(RoomLibraryEdit.find(model, ref));
    }

    @Override
    public void edit(RoomLibRef ref) {
        RoomLibraryEdit.Found f = RoomLibraryEdit.find(model, ref);
        if (!isOwn(f)) {
            return;
        }
        if (f.item() instanceof RoomEquipmentType e) {
            RoomItemDialog.editEquipment(owner, model, e);
        } else if (f.item() instanceof RoomFurnitureType t) {
            RoomItemDialog.editFurniture(owner, model, t);
        } else if (f.item() instanceof CaseType c) {
            RoomItemDialog.editCase(owner, model, c);
        }
    }

    @Override
    public void delete(RoomLibRef ref) {
        RoomLibraryEdit.Found f = RoomLibraryEdit.find(model, ref);
        if (!isOwn(f)) {
            return;
        }
        int used = model.countRoomLayoutReferences(f.ref());
        String text = "Удалить «" + f.name() + "» из личной библиотеки?"
                + (used > 0 ? "\nПозиция стоит в раскладках (" + used + " шт.) — там она станет «неизвестной»." : "");
        if (JOptionPane.showConfirmDialog(owner, text, "Удалить позицию", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        switch (f.ref().getKind()) {
            case ROOM_EQUIPMENT -> model.deleteRoomEquipmentType(f.id());
            case ROOM_FURNITURE -> model.deleteRoomFurnitureType(f.id());
            case CASE -> model.deleteCaseType(f.id());
            default -> {
            }
        }
    }

    /** Габариты, которые сейчас действуют у позиции: личные, иначе записанные в самой позиции. */
    private PhysicalSpec effectiveSpec(RoomLibraryEdit.Found f) {
        PhysicalSpec own = model.getPhysicalOverrides().get(f.ref().key());
        return own != null ? own : RoomLibraryEdit.physicalOf(f.item());
    }

    @Override
    public boolean canPropose(RoomLibRef ref) {
        RoomLibraryEdit.Found f = RoomLibraryEdit.find(model, ref);
        if (f == null || settings == null) {
            return false;
        }
        boolean personalData = isOwn(f) || model.getPhysicalOverrides().containsKey(f.ref().key());
        return personalData && RoomLibraryEdit.isComplete(f.item(), effectiveSpec(f));
    }

    @Override
    public void propose(RoomLibRef ref) {
        RoomLibraryEdit.Found f = RoomLibraryEdit.find(model, ref);
        if (f == null || settings == null) {
            return;
        }
        PhysicalSpec spec = effectiveSpec(f);
        if (!RoomLibraryEdit.isComplete(f.item(), spec)) {
            JOptionPane.showMessageDialog(owner, "Сначала заполните габариты полностью.", "Предложить на сервер",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProposeDialog.show(owner, settings, f.serverKind(), f.name(), RoomLibraryEdit.draftWith(f.item(), spec),
                f.shared() ? f.id() : null);
    }
}
