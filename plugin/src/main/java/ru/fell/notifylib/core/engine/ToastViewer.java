package ru.fell.notifylib.core.engine;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import ru.fell.notifylib.core.render.ToastFrame;

import java.util.UUID;

/** Онлайн-игрок с точки зрения движка. Вызывается только из главного потока. */
public interface ToastViewer {

    UUID id();

    /** Игровое время текущего мира игрока. По нему идёт анимация в шейдере. */
    long gameTime();

    /**
     * Показывает боссбар с этим кадром или обновляет имя уже показанного. Отрисовать и отправить
     * можно позже и в другом потоке, но порядок вызовов должен сохраниться.
     */
    void showBar(UUID bar, ToastFrame frame);

    void hideBar(UUID bar);

    void playSound(Sound sound);

    void sendActionBar(Component message);

    void sendMessage(Component message);
}
