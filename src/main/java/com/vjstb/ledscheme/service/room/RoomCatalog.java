package com.vjstb.ledscheme.service.room;

import com.vjstb.ledscheme.model.RoomLibRef;
import java.util.List;

/**
 * Источник позиций для расчётов раскладки: по ссылке {@link RoomLibRef} отдаёт позицию,
 * приведённую к {@link RoomItemSpec}. В приложении это {@code RoomLibraryAdapter} над общей и
 * локальной библиотеками; в тестах — {@link MapRoomCatalog}. Расчётные классы
 * ({@link RoomGeometry}, {@link RoomRackLayout}, {@link RoomPlanCalc}) знают только этот
 * интерфейс — поэтому их можно проверять без библиотеки и без интерфейса.
 */
public interface RoomCatalog {

    /** Позиция по ссылке; для ненайденной — {@link RoomItemSpec#unknown(RoomLibRef)}, не {@code null}. */
    RoomItemSpec resolve(RoomLibRef ref);

    /** Все позиции для списка выбора (порядок — как в библиотеке). */
    List<RoomItemSpec> all();
}
