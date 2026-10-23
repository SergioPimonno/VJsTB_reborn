package com.vjstb.ledscheme.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Слой башни: рэк-кофр со слотами либо «стопка без кофра» (оборудование лежит друг на друге).
 * Слои башни хранятся СНИЗУ ВВЕРХ; элементы стопки — тоже снизу вверх.
 */
public class RoomSegment {

    private String id = UUID.randomUUID().toString();
    /** true — стопка без кофра (тогда caseRef не используется). */
    private boolean stack = false;
    private RoomLibRef caseRef;
    private List<RoomRackItem> items = new ArrayList<>();

    public RoomSegment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isStack() {
        return stack;
    }

    public void setStack(boolean stack) {
        this.stack = stack;
    }

    public RoomLibRef getCaseRef() {
        return caseRef;
    }

    public void setCaseRef(RoomLibRef caseRef) {
        this.caseRef = caseRef;
    }

    public List<RoomRackItem> getItems() {
        return items;
    }

    public void setItems(List<RoomRackItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

}
