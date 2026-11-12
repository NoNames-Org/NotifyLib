# NotifyLib

Plugin for **Paper** & **Leaf** that adds corner notifications to the game: icon, text, timer bar and animation. **No client mods needed**: works through a boss bar and a resource pack shader.

---

# 🇷🇺 Русский

## 🔔 Что это

Уведомления в углу экрана в стиле тостов: иконка типа, текст, полоса таймера, плавный выезд и сдвиг стопки. Игроку ничего не нужно ставить, достаточно ресурспака сервера.

- 4 угла экрана: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`
- Однострочные и двухстрочные уведомления (заголовок + текст)
- До 4 уведомлений на экране, остальные ждут в очереди
- При переполнении: `QUEUE`, `REPLACE_OLDEST` или `DROP`
- Встроенные типы: `info`, `success`, `warning`, `error`, плюс свои типы с иконкой, цветом и звуком
- Текст в MiniMessage, длинный текст аккуратно обрезается по ширине
- Игроки без ресурспака получают уведомление в action bar или чат (`fallback`)

## 💬 Команды

| Команда | Право | Описание |
|---|---|---|
| `/notify send <цель> <тип> <сек> <текст>` | `notifylib.send` | Отправить уведомление. Цель: ник, `*` или селектор `@a[...]`. `\n` делает уведомление двухстрочным |
| `/notify types` | `notifylib.admin` | Список типов |
| `/notify reload` | `notifylib.admin` | Перезагрузить `config.yml` и `messages.yml` без рестарта |

## 👤 Личные настройки игроков

Каждый игрок может выбрать свой угол, выключить уведомления или звук и скрыть отдельные типы. Настройки меняются через API, например из меню вашего плагина.

- Без MySQL: хранятся в данных игрока на сервере
- С MySQL: общие для всех серверов за прокси. Читаются ещё до входа игрока, пишутся в отдельном потоке

## ⚙️ Конфигурация

- Угол, длительность, лимит стопки и очереди, поведение при переполнении, звуки
- Новое уведомление в конец стопки или к самому углу (`new-position`)
- Свои типы уведомлений в `config.yml` (иконка и полоса добавляются в ресурспак)
- Все тексты команд в `messages.yml` (MiniMessage)
- Неверное значение в конфиге не ломает плагин: в лог пишется предупреждение и берётся значение по умолчанию

## 🧩 API для разработчиков

```java
ToastApi toasts = NotifyLib.api();

toasts.show(player, ToastTypes.ERROR, "Вас убил <yellow>Steve</yellow>");

toasts.show(player, Toast.builder()
        .type(ToastTypes.SUCCESS)
        .title("<b>Новый уровень")
        .text("Открыты новые киты")
        .duration(Duration.ofSeconds(4))
        .replaceKey("level")      // тот же ключ обновляет уведомление на экране
        .priority(Priority.HIGH)
        .build());
```

- Вызывать можно из любого потока
- Бессрочные уведомления с ручным закрытием, колбэк на закрытие
- Рассылка всем, по праву, по миру или по своему условию
- События `ToastShowEvent` (отменяемое, уведомление можно подменить) и `ToastCloseEvent`
- Подключите `NotifyLib-API` как `compileOnly` и добавьте `depend: [NotifyLib]` в `plugin.yml`

## 🚀 Производительность

- Когда уведомлений нет, плагин вообще не работает на главном потоке: тик останавливается
- Текст боссбаров собирается и отправляется через PacketEvents в фоновом потоке
- Разметка и ширины текста кэшируются: рассылка сотням игроков парсит текст один раз

---

# 🇬🇧 English

## 🔔 What it is

Toast-style notifications in the corner of the screen: type icon, text, timer bar, smooth slide-in and stack shifting. Players don't need to install anything, the server resource pack is enough.

- 4 screen corners: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`
- Single-line and two-line notifications (title + text)
- Up to 4 notifications on screen, the rest wait in a queue
- When the stack is full: `QUEUE`, `REPLACE_OLDEST` or `DROP`
- Built-in types: `info`, `success`, `warning`, `error`, plus custom types with your own icon, color and sound
- MiniMessage text, long text is neatly trimmed to fit
- Players without the resource pack get the notification in the action bar or chat (`fallback`)

## 💬 Commands

| Command | Permission | Description |
|---|---|---|
| `/notify send <target> <type> <sec> <text>` | `notifylib.send` | Send a notification. Target: name, `*` or selector `@a[...]`. `\n` makes it two-line |
| `/notify types` | `notifylib.admin` | List types |
| `/notify reload` | `notifylib.admin` | Reload `config.yml` and `messages.yml` with no server restart |

## 👤 Per-player settings

Each player can pick their own corner, turn notifications or sound off and hide specific types. Settings are changed through the API, e.g. from your plugin's menu.

- Without MySQL: stored in player data on the server
- With MySQL: shared across all servers behind a proxy. Loaded before the player joins, saved on a separate thread

## ⚙️ Configuration

- Corner, duration, stack and queue limits, overflow behavior, sounds
- New notifications go to the end of the stack or right into the corner (`new-position`)
- Custom notification types in `config.yml` (icon and bar go into the resource pack)
- All command texts in `messages.yml` (MiniMessage)
- An invalid config value doesn't break the plugin: a warning is logged and the default is used

## 🧩 Developer API

```java
ToastApi toasts = NotifyLib.api();

toasts.show(player, ToastTypes.ERROR, "You were killed by <yellow>Steve</yellow>");

toasts.show(player, Toast.builder()
        .type(ToastTypes.SUCCESS)
        .title("<b>Level up")
        .text("New kits unlocked")
        .duration(Duration.ofSeconds(4))
        .replaceKey("level")      // same key updates the notification on screen
        .priority(Priority.HIGH)
        .build());
```

- Safe to call from any thread
- Persistent notifications with manual dismiss, close callbacks
- Broadcast to everyone, by permission, by world or by your own filter
- `ToastShowEvent` (cancellable, the toast can be replaced) and `ToastCloseEvent`
- Add `NotifyLib-API` as `compileOnly` and `depend: [NotifyLib]` to your `plugin.yml`

## 🚀 Performance

- With no notifications on screen the plugin does no main-thread work at all: its tick stops
- Boss bar text is built and sent through PacketEvents on a background thread
- Markup and text widths are cached: a broadcast to hundreds of players parses the text once
