package com.vjstb.ledscheme.ui.room;

import com.vjstb.ledscheme.model.RoomFurnitureType;
import com.vjstb.ledscheme.model.RoomLibRef;

/**
 * Что можно делать с позициями библиотеки прямо из редактора Планнера: указать габариты, создать свою позицию
 * (блок в стойку или мелочь, рэк-кофр, мебель/станок/монитор), изменить или удалить свою, предложить на сервер.
 * Реализация — {@code ui.RoomLibraryUi} (диалоги, модель, аккаунт); редактор знает только этот интерфейс, поэтому
 * панели можно проверять без окон.
 */
public interface RoomLibraryActions {

    /** Указать (поправить) габариты позиции — личные, пока в общей библиотеке нет данных. */
    void specifyDimensions(RoomLibRef ref);

    /** Новый «блок»: оборудование пультовой (в стойку или россыпью). */
    void createEquipment();

    /** Новый рэк-кофр. */
    void createCase();

    /** Новый станок, стол/мебель или монитор — вид выбран заранее, размеры подставлены типовые. */
    void createFurniture(RoomFurnitureType.Kind kind);

    /** Изменить свою позицию. */
    void edit(RoomLibRef ref);

    /** Удалить свою позицию (с предупреждением, если она стоит в раскладках). */
    void delete(RoomLibRef ref);

    /** Предложить позицию с её габаритами на сервер. */
    void propose(RoomLibRef ref);

    /** Позиция своя и её можно менять и удалять. */
    boolean canEdit(RoomLibRef ref);

    /** Позиции есть что предлагать (свои или личные габариты) и она описана полностью, а вход в аккаунт возможен. */
    boolean canPropose(RoomLibRef ref);

    /** Пустая реализация — для тестов и окон без библиотеки. */
    RoomLibraryActions NONE = new RoomLibraryActions() {
        @Override
        public void specifyDimensions(RoomLibRef ref) {
        }

        @Override
        public void createEquipment() {
        }

        @Override
        public void createCase() {
        }

        @Override
        public void createFurniture(RoomFurnitureType.Kind kind) {
        }

        @Override
        public void edit(RoomLibRef ref) {
        }

        @Override
        public void delete(RoomLibRef ref) {
        }

        @Override
        public void propose(RoomLibRef ref) {
        }

        @Override
        public boolean canEdit(RoomLibRef ref) {
            return false;
        }

        @Override
        public boolean canPropose(RoomLibRef ref) {
            return false;
        }
    };
}
