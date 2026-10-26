package com.vjstb.ledscheme.ui.receiver;

import com.vjstb.ledscheme.service.AppModel;
import com.vjstb.ledscheme.settings.SettingsManager;

import java.awt.BorderLayout;
import javax.swing.JPanel;

/** Вид «Сигнал» → «Приёмные карты» (docs/receiver-cards/PLAN.md, 6.1). Контракт между треками: содержимое
 *  вида делает трек C, а этап (трек D) только создаёт его этим конструктором, кладёт в
 *  переключатель видов перед «Расключением экрана» и зовёт {@link #refresh()} при показе. */
public class ReceiverCardsView extends JPanel {

    public ReceiverCardsView(AppModel model, SettingsManager settings) {
        super(new BorderLayout());
    }

    /** Перечитать модель (вид показан, сменились сцена/экран, правка пришла извне). */
    public void refresh() {
    }
}
