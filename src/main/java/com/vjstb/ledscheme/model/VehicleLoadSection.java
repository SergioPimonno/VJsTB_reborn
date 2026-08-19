package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;

/** Одна машина внутри {@link VehicleLoadPlan} — персистентная форма
 *  {@code ui.VehicleLoadVisualizerDialog.VehicleSection}. Ссылается на тип
 *  машины по id (см. {@code Workspace#vehicleTypeById}), как и {@link
 *  VehicleLoadPlacement} на тип кофра. */
public class VehicleLoadSection {

    private String vehicleTypeId;
    private List<VehicleLoadPlacement> placements = new ArrayList<>();

    public VehicleLoadSection() {
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(String vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public List<VehicleLoadPlacement> getPlacements() {
        return placements;
    }

    public void setPlacements(List<VehicleLoadPlacement> placements) {
        this.placements = placements;
    }
}
