package com.vjstb.ledscheme.ui;

import com.vjstb.ledscheme.model.Screen;
import com.vjstb.ledscheme.service.AppModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Отдельное всплывающее окно «Форма экрана» (см. {@link ShapeEditorPanel}) —
 * запрос пользователя (по образцу {@link Structure3DDialog}): "давай добавим в
 * предпочтения опцию чтобы окно изменения формы экрана открывалось не областью
 * в сетапе а отдельным всплывающим окном. при этом содержимое окна должно
 * динамически зависеть от выбранного экрана в приложении" — см. javadoc
 * {@code UserProfile#shapeEditorFloating}.
 *
 * <p>В отличие от {@link Structure3DDialog} (та создаёт СВОЮ НОВУЮ {@code
 * Structure3DPanel} при каждом открытии — {@code AppModel} не поддерживает
 * {@code removeListener}, постоянная подписка внутри самой панели накопилась бы
 * при повторных открытиях) — это окно НЕ создаёт свой {@link ShapeEditorPanel}
 * вообще: {@code SetupStagePanel} передаёт сюда через {@link #attach} ОДИН И ТОТ
 * ЖЕ {@link JScrollPane}, обёртывающий её собственный (тоже единственный за всю
 * сессию) экземпляр {@link ShapeEditorPanel} — тот же компонент просто
 * переставляется между встроенной секцией и этим окном ({@code
 * Container.add} сам открепляет его от прежнего родителя). Так масштаб/состояние
 * {@link ShapeEditorPanel} не сбрасывается при переключении встроенный/
 * всплывающий режим — своей подписки на модель это окно тоже не заводит.
 *
 * <p>Содержимое «живое» само по себе — {@link ShapeEditorPanel} резолвит {@code
 * model.getCurrentScreen()} заново при КАЖДОЙ отрисовке, не хранит ссылку на
 * конкретный экран. Владелец ({@code SetupStagePanel}, уже подписанный на
 * {@code model.addListener}) просто зовёт {@link #refresh} на каждое изменение
 * модели, пока это окно открыто — обновляет заголовок под текущий экран и просит
 * содержимое перерисоваться.
 *
 * <p>Закрытие ЛЮБЫМ способом (крестик, кнопка «Закрыть») только ПРЯЧЕТ окно
 * ({@code HIDE_ON_CLOSE}), не уничтожает — тот же приём, что и у {@code
 * NetworkAddressTableDialog}: следующее открытие показывает то же окно снова, без
 * пересоздания.
 */
public class ShapeEditorDialog extends JDialog {

    private final AppModel model;
    private final JPanel centerHolder = new JPanel(new BorderLayout());

    public ShapeEditorDialog(Window owner, AppModel model) {
        super(owner, "Форма экрана", ModalityType.MODELESS);
        this.model = model;
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel hint = new JLabel(UiKit.wrapHtml("<html>Клик (или протяжка ЛКМ) по ячейке — исключить/включить"
                + " (так задаётся не прямоугольная форма экрана). ПКМ (зажать и повести к пункту) —"
                + " скрыть/восстановить, изменить форму или тип конкретной ячейки. Ctrl+колесо — масштаб.</html>"));
        hint.setForeground(Palette.MUTED);
        content.add(hint, BorderLayout.NORTH);
        content.add(centerHolder, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> setVisible(false));
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);
        setPreferredSize(new Dimension(520, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    /** Помещает {@code shapeScroll} (общий с встроенной секцией — см. class-javadoc)
     *  в это окно — no-op, если он уже тут (например, повторное открытие без
     *  промежуточного переключения обратно на встроенный режим). */
    public void attach(JScrollPane shapeScroll) {
        if (shapeScroll.getParent() != centerHolder) {
            centerHolder.add(shapeScroll, BorderLayout.CENTER);
            centerHolder.revalidate();
        }
    }

    /** Обновляет заголовок под текущий выбранный экран и перерисовывает содержимое —
     *  вызывается владельцем на каждое изменение модели, пока окно открыто (см.
     *  class-javadoc). */
    public void refresh() {
        Screen scr = model.getCurrentScreen();
        setTitle(scr != null ? "Форма экрана — " + scr.getName() : "Форма экрана");
        getContentPane().revalidate();
        getContentPane().repaint();
    }
}
