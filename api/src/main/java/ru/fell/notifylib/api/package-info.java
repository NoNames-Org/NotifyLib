/**
 * Публичный API NotifyLib: уведомления в углу экрана (тосты) без клиентских модов.
 *
 * <p>Точка входа: {@link ru.fell.notifylib.api.NotifyLib#api()}. Подключайте {@code ru.fell:notifylib-api}
 * как {@code compileOnly}/{@code provided} (не шейдите) и добавьте {@code depend: [NotifyLib]} в
 * {@code plugin.yml}: в рантайме классы берутся из самого плагина NotifyLib.
 *
 * <p>Все типы null-marked: параметры и возвращаемые значения не null, если не помечены
 * {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package ru.fell.notifylib.api;

import org.jspecify.annotations.NullMarked;
