package com.judgeTool.controller;

import com.judgeTool.model.Problem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.Callback;

final class ProblemCells {

    private ProblemCells() {
    }

    /**
     * Cài đặt cách hiển thị Problem trong cả dropdown và button của ComboBox.
     */
    static void install(ComboBox<Problem> combo) {
        combo.setCellFactory(factory());
        combo.setButtonCell(newCell());
    }

    private static Callback<javafx.scene.control.ListView<Problem>, ListCell<Problem>> factory() {
        return lv -> newCell();
    }

    private static ListCell<Problem> newCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Problem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#" + item.getId() + " — " + item.getTitle());
            }
        };
    }
}
