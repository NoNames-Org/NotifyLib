package ru.fell.notifylib.core.engine;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Тосты игрока, ждущие свободный слот. Порядок: срочные ({@code REPLACE_OLDEST}), потом приоритет, потом
 * порядок отправки. Очередь маленькая (конфиг ограничивает её сотней), поэтому список с линейным поиском
 * тут лучше кучи и ничего не аллоцирует за тик. Только главный поток.
 */
final class WaitingQueue {

    private final List<ActiveToast> toasts = new ArrayList<>();

    boolean isEmpty() {
        return toasts.isEmpty();
    }

    int size() {
        return toasts.size();
    }

    ActiveToast get(int index) {
        return toasts.get(index);
    }

    void add(ActiveToast toast) {
        toasts.add(toast);
    }

    boolean remove(ActiveToast toast) {
        return toasts.remove(toast);
    }

    /** Достаёт тост, который надо показать следующим. */
    @Nullable ActiveToast pollBest() {
        if (toasts.isEmpty()) {
            return null;
        }
        int best = 0;
        for (int i = 1; i < toasts.size(); i++) {
            if (compare(toasts.get(i), toasts.get(best)) < 0) {
                best = i;
            }
        }
        return toasts.remove(best);
    }

    /**
     * Если обычных (не срочных) тостов больше {@code limit}, выкидывает наименее важный из них.
     * Срочные в лимит не считаются.
     */
    @Nullable ActiveToast trim(int limit) {
        if (toasts.size() <= limit) {
            return null;
        }
        int regular = 0;
        int worst = -1;
        for (int i = 0; i < toasts.size(); i++) {
            ActiveToast candidate = toasts.get(i);
            if (!candidate.urgent) {
                regular++;
                if (worst < 0 || compare(candidate, toasts.get(worst)) > 0) {
                    worst = i;
                }
            }
        }
        return regular > limit ? toasts.remove(worst) : null;
    }

    /**
     * Удаляет подходящие тосты с конца списка. Проверка {@code i < size} нужна потому, что
     * {@code removed} может сам убрать что-то из очереди.
     */
    boolean removeIf(Predicate<ActiveToast> filter, Consumer<ActiveToast> removed) {
        boolean any = false;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            if (i < toasts.size() && filter.test(toasts.get(i))) {
                removed.accept(toasts.remove(i));
                any = true;
            }
        }
        return any;
    }

    void addAllTo(List<? super ActiveToast> out) {
        out.addAll(toasts);
    }

    /** Отрицательное, если {@code a} идёт раньше {@code b}. */
    static int compare(ActiveToast a, ActiveToast b) {
        if (a.urgent != b.urgent) {
            return a.urgent ? -1 : 1;
        }
        int priority = Integer.compare(b.toast().priority().ordinal(), a.toast().priority().ordinal());
        return priority != 0 ? priority : Long.compare(a.seq, b.seq);
    }
}
