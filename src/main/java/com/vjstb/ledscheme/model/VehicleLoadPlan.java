package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;

/** Раскладка загрузки машин(ы) кофрами для сцены (см. {@code
 *  ui.VehicleLoadVisualizerDialog}) — персистится как обычные данные сцены
 *  (см. {@link Scene#getVehicleLoadPlan()}), сохраняется/загружается вместе
 *  с остальным проектом (локально и при выгрузке в облако, см.
 *  VEHICLE_CALC_NOTES.md) — та же генерическая Jackson-сериализация, что и у
 *  {@link ContentCanvas}/{@link SchemaNode}, без отдельного кода
 *  (де)сериализации. Ровно ОДИН план на сцену (не список именованных
 *  вариантов) — осознанное упрощение v1, см. заметки. */
public class VehicleLoadPlan {

    private List<VehicleLoadSection> sections = new ArrayList<>();

    public VehicleLoadPlan() {
    }

    public List<VehicleLoadSection> getSections() {
        return sections;
    }

    public void setSections(List<VehicleLoadSection> sections) {
        this.sections = sections;
    }
}
