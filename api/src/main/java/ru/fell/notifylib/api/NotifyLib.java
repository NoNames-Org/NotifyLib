package ru.fell.notifylib.api;

import org.bukkit.Bukkit;

/**
 * Статический доступ к API NotifyLib.
 *
 * <p>Тот же экземпляр зарегистрирован в {@link org.bukkit.plugin.ServicesManager} под {@link ToastApi}.
 * Доступен начиная с {@code onLoad} NotifyLib.
 */
public final class NotifyLib {

    private NotifyLib() {
    }

    /**
     * Возвращает экземпляр API.
     *
     * <p>Каждый вызов идёт через ServicesManager, так что при частом использовании сохраните результат в поле.
     *
     * @throws IllegalStateException если NotifyLib не загружен (забыли {@code depend: [NotifyLib]}?)
     *                               или уже выключен
     */
    public static ToastApi api() {
        ToastApi api = Bukkit.getServicesManager().load(ToastApi.class);
        if (api == null) {
            throw new IllegalStateException(
                    "NotifyLib не включён: установите его и добавьте 'depend: [NotifyLib]' в plugin.yml");
        }
        return api;
    }
}
