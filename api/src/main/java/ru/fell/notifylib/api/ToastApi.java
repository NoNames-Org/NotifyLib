package ru.fell.notifylib.api;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Основной интерфейс NotifyLib.
 *
 * <p>Любой метод можно вызывать из любого потока. Сама работа выполняется в главном потоке на следующем
 * тике, а {@code show} и {@code broadcast} возвращают хэндлы сразу. Неверные аргументы сразу бросают
 * {@link IllegalArgumentException} в вызывающем потоке.
 *
 * <p>Когда плагин выключается, его тосты закрываются с {@link CloseReason#PLUGIN_DISABLED}.
 */
public interface ToastApi {

    /**
     * Показывает тост с текстом MiniMessage и настройками по умолчанию.
     *
     * @param type тип тоста, например {@link ToastTypes#ERROR}
     * @throws IllegalArgumentException если тип неизвестен (не встроенный и не объявлен в конфиге NotifyLib)
     */
    ToastHandle show(Player player, ToastType type, String miniMessage);

    /**
     * Показывает тост с готовым компонентом и настройками по умолчанию.
     *
     * @throws IllegalArgumentException если тип неизвестен (не встроенный и не объявлен в конфиге NotifyLib)
     */
    ToastHandle show(Player player, ToastType type, Component text);

    /**
     * @throws IllegalArgumentException если тип тоста неизвестен (не встроенный и не объявлен в конфиге NotifyLib)
     */
    ToastHandle show(Player player, Toast toast);

    /**
     * Показывает тост всем игрокам онлайн.
     *
     * @return по одному хэндлу на получателя
     */
    List<ToastHandle> broadcast(Toast toast);

    /** @return по одному хэндлу на получателя */
    List<ToastHandle> broadcast(Collection<? extends Player> players, Toast toast);

    /**
     * Показывает тост всем игрокам онлайн, прошедшим фильтр. Фильтр выполняется в вызывающем потоке, поэтому
     * если он читает состояние игрока (права, позицию и т.п.), вызывайте из главного потока.
     *
     * @return по одному хэндлу на получателя
     */
    List<ToastHandle> broadcast(Predicate<? super Player> filter, Toast toast);

    /**
     * Показывает тост всем игрокам онлайн с этим правом. Права проверяются в вызывающем потоке,
     * лучше вызывать из главного.
     *
     * @return по одному хэндлу на получателя
     */
    List<ToastHandle> broadcastPermission(String permission, Toast toast);

    /**
     * Показывает тост всем игрокам в мире. Мир игрока читается в вызывающем потоке, лучше вызывать из главного.
     *
     * @return по одному хэндлу на получателя
     */
    List<ToastHandle> broadcast(World world, Toast toast);

    ToastTypeRegistry types();

    /**
     * Личные настройки игрока онлайн.
     *
     * @return живое представление, изменения сразу применяются
     */
    PlayerToastSettings settings(Player player);

    /**
     * Видимые тосты игрока и тосты в очереди.
     *
     * @return неизменяемый снимок: сначала ближайший к углу, тосты из очереди в конце
     */
    List<ToastHandle> active(Player player);

    /** Закрывает все видимые тосты игрока и тосты в очереди с {@link CloseReason#DISMISSED}. */
    void dismissAll(Player player);
}
