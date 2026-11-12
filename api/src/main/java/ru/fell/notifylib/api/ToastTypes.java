package ru.fell.notifylib.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/** Встроенные типы тостов, доступны всегда. */
public final class ToastTypes {

    public static final String NAMESPACE = "notifylib";

    /** Синяя «i»: нейтральная информация. */
    public static final ToastType INFO = builtin("info", 0x4DA6FF,
            sound("block.note_block.bell", 0.6F, 1.6F));
    /** Зелёная галочка: всё прошло успешно. */
    public static final ToastType SUCCESS = builtin("success", 0x58D66D,
            sound("entity.experience_orb.pickup", 0.6F, 1.2F));
    /** Жёлтый «!»: внимание. */
    public static final ToastType WARNING = builtin("warning", 0xFFC43D,
            sound("block.note_block.bass", 0.8F, 1.0F));
    /** Красный крестик: ошибка. */
    public static final ToastType ERROR = builtin("error", 0xFF5555,
            sound("entity.villager.no", 0.6F, 1.0F));

    public static final List<ToastType> ALL = List.of(INFO, SUCCESS, WARNING, ERROR);

    private ToastTypes() {
    }

    private static ToastType builtin(String name, int color, Sound sound) {
        return new ToastType(Key.key(NAMESPACE, name), TextColor.color(color), sound);
    }

    private static Sound sound(String key, float volume, float pitch) {
        return Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
    }
}
