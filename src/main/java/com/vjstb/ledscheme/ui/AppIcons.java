package com.vjstb.ledscheme.ui;

import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

/** Загрузка иконки приложения (окно/панель задач) из ресурсов classpath. */
final class AppIcons {

    private static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    private AppIcons() {
    }

    static List<Image> loadAppIconImages() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            java.net.URL url = AppIcons.class.getResource("/icons/app-" + size + ".png");
            if (url != null) {
                images.add(Toolkit.getDefaultToolkit().getImage(url));
            }
        }
        return images;
    }
}
