package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.junit.jupiter.api.Test;

/** Регрессия на реально обнаруженный баг: поле "Высота, px" в форме "Новый канвас"
 *  (VisualizationStagePanel) не вычисляло введённое выражение вроде "208*8" при
 *  потере фокуса/commitEdit — проверяем commitEdit() напрямую, как это делает
 *  JFormattedTextField при уходе фокуса/Enter, а не только парсинг MathExpr сам
 *  по себе (тот уже не под вопросом). */
class MathFieldsTest {

    @Test
    void commitEditEvaluatesExpressionOnIntegerSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 1));
        MathFields.enableExpressions(spinner);

        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setText("208*8");
        field.commitEdit();

        assertEquals(1664, spinner.getValue());
    }

    @Test
    void commitEditStillAcceptsPlainNumber() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 1));
        MathFields.enableExpressions(spinner);

        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setText("3328");
        field.commitEdit();

        assertEquals(3328, spinner.getValue());
    }

    /** Уже показанное отформатированное значение (с пробелом-разделителем разрядов,
     *  как рисует JSpinner.NumberEditor) MathExpr не понимает -- должен сработать
     *  откат на исходный NumberFormatter, а не молча оборвать ввод. */
    @Test
    void commitEditAcceptsAlreadyGroupedNumber() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1080, 1, 16384, 1));
        MathFields.enableExpressions(spinner);

        JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        field.setText("3 328");
        field.commitEdit();

        assertEquals(3328, spinner.getValue());
    }
}
