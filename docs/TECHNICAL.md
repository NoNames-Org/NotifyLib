# NotifyLib

Плагин для Paper/Leaf 1.21.11: уведомления в углу экрана с иконкой, текстом, полосой таймера и
анимацией. Модов на клиенте не нужно: уведомление — это имя белого боссбара, а шейдер ресурспака сам ставит
его в угол и анимирует.

## Что здесь

| Путь | Что это |
|---|---|
| `api/` | Модуль **notifylib-api**: только публичный API (`ru.fell.notifylib.api`) и события. Его отдаёшь разработчикам |
| `plugin/` | Модуль **notifylib**: сам плагин для сервера — движок, рендер, хранилище настроек, команды, конфиг. API вшит внутрь |
| `resourcepack/` | Готовый ресурспак. Плагин его **не собирает и не раздаёт**: подключи папку к своему плагину-раздатчику (ItemsAdder, Nexo и т. п.) или заархивируй и раздай как обычно |

Пакеты модуля `plugin` (`plugin/src/main/java/ru/fell/notifylib/`):

- `core/` — движок (`engine`), кодирование строки для шейдера (`protocol`), рендер (`render`), текст и
  ширины глифов (`text`), типы (`type`), настройки игрока (`settings`), фасад API (`service`);
- `plugin/` — запуск плагина, конфиг, команды, слушатели событий Paper, хранилище MySQL.

## Сборка

```bash
mvn install
# plugin/target/NotifyLib-1.0.0.jar          — на сервер
# api/target/NotifyLib-API-1.0.0.jar         — разработчикам (+ -sources.jar и -javadoc.jar для подсказок в IDE)
```

`mvn install` кладёт `ru.fell:notifylib-api:1.0.0` в локальный Maven (`~/.m2`), так что твои плагины на этой
же машине подключают его как обычную зависимость. Чтобы разработчики подключали API из своего Maven или Gradle,
выложи его в свой репозиторий (Reposilite, Nexus, GitHub Packages): раскомментируй `distributionManagement` в
корневом `pom.xml` и выполни `mvn deploy`.

## Установка

1. `plugin/target/NotifyLib-1.0.0.jar` и [PacketEvents](https://modrinth.com/plugin/packetevents) 2.14.0+
   (`packetevents-spigot-*.jar`) положи в `plugins/`.
2. Содержимое `resourcepack/` добавь в пак своего сервера: через плагин-раздатчик или слиянием в общий
   zip. В корне пака должен оказаться `pack.mcmeta`, а секцию `overlays` нужно сохранить (без неё не
   будут работать клиенты 26.1 и 26.2).
3. Проверь в игре: `/notify send <ник> info 5 тест`. Уведомление должно выехать в углу. Если вместо
   него посреди экрана висит боссбар с символами, `rendertype_text` перекрыт другим паком (см. «Слияние
   шейдера»).
4. Чтобы настройки игроков были общими для всех серверов, заполни `storage.mysql` в `config.yml` на
   каждом сервере одинаково (см. «Настройки игроков»).

Игроки, отказавшиеся от пака или не скачавшие его, получают уведомления через `fallback` (по умолчанию в
action bar). NotifyLib узнаёт об этом из событий статуса пака, какой бы плагин его ни раздавал.

## Использование из другого плагина

Разработчику нужен только `notifylib-api`, весь плагин ему не нужен. Подключи его как `provided`/`compileOnly`
(в свой jar не вшивать: классы берутся из NotifyLib на сервере) и укажи `depend: [NotifyLib]` в `plugin.yml`.

Maven:

```xml
<dependency>
  <groupId>ru.fell</groupId>
  <artifactId>notifylib-api</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

Gradle:

```kotlin
compileOnly("ru.fell:notifylib-api:1.0.0")
```

Без репозитория можно просто отдать файл `NotifyLib-API-1.0.0.jar`: `compileOnly(files("libs/NotifyLib-API-1.0.0.jar"))`.


```java
ToastApi toasts = NotifyLib.api();

toasts.show(player, ToastTypes.ERROR, "Вас убил <yellow>qwened</yellow> с 15 ХП");

ToastHandle h = toasts.show(player, Toast.builder()
        .type(ToastTypes.SUCCESS)
        .text(Component.text("Убийство x3"))
        .duration(Duration.ofSeconds(4))
        .replaceKey("killstreak")          // тот же ключ обновляет видимое уведомление без нового выезда
        .priority(Priority.HIGH)
        .overflow(Overflow.REPLACE_OLDEST)
        .build());
h.onClose(reason -> getLogger().info("уведомление закрыто: " + reason));

// двухстрочный и бессрочный
toasts.show(player, Toast.builder().title("<b>Новый уровень").text("Открыты киты").build());
ToastHandle login = toasts.show(player, Toast.builder().text("Введите пароль").persistent().build());
login.dismiss();

// свой тип из config.yml
ToastType donate = toasts.types().get(Key.key("mistik", "donate")).orElseThrow();
toasts.broadcast(p -> p.hasPermission("vip"), Toast.builder().type(donate).text("...").build());
```

- Методы можно вызывать из любого потока: работа выполняется на главном потоке на следующем тике.
- На главном потоке остаются только очередь, состояния уведомлений, события и звуки. Имена боссбаров
  собираются, а пакеты боссбаров отправляются через PacketEvents в фоновом потоке `NotifyLib-bars`.
  Когда уведомлений нет, NotifyLib на главном потоке не работает вовсе: его тик останавливается.
- Неверные аргументы дают `IllegalArgumentException`.
- При выключении плагина его уведомления закрываются с `PLUGIN_DISABLED`.
- События: `ToastShowEvent` (отменяемое, уведомление можно подменить) и `ToastCloseEvent`.

## Команды

| Команда | Право | Что делает |
|---|---|---|
| `/notify send <цель> <тип> <сек> <текст>` | `notifylib.send` | цель: ник, `*` или селектор `@a[...]`; `\n` в тексте делает уведомление двухстрочным: `<b>Заголовок\nвторая строка` |
| `/notify types` | `notifylib.admin` | список типов и их символов |
| `/notify reload` | `notifylib.admin` | перечитать `config.yml` (включая типы) и `messages.yml` |

Все тексты команд лежат в `plugins/NotifyLib/messages.yml` (MiniMessage). Если ключ удалить,
берётся значение по умолчанию из jar.

Команд для игроков нет: личные настройки меняются только через API (например, из меню твоего плагина).

## Настройки игроков

Каждый игрок может выбрать угол, выключить уведомления или звук и скрыть отдельные типы:

```java
PlayerToastSettings settings = NotifyLib.api().settings(player);
settings.corner(Corner.TOP_RIGHT);    // settings.resetCorner() — угол сервера по умолчанию
settings.enabled(false);
settings.sound(false);
settings.hidden(ToastTypes.INFO.key(), true);
```

Где они хранятся, задаёт `storage.mysql` в `config.yml`:

- **`host` пустой** — локально, в данных игрока (PDC) на этом сервере. На каждом сервере свои настройки.
- **`host` указан** — в MySQL, таблица `notifylib_settings` (создаётся сама). Укажи одну и ту же базу на
  всех серверах за прокси, и настройки станут общими. Настройки читаются при входе на сервер (ещё до
  появления игрока) и пишутся при изменении в отдельном потоке, главный поток базу не ждёт. Драйвер MySQL
  и HikariCP Paper скачивает сам при первом запуске (`libraries` в `plugin.yml`).
- Если MySQL недоступен при старте, плагин пишет ошибку в лог и до рестарта хранит настройки локально.
- `storage` применяется только после рестарта, `/notify reload` его не трогает.

## Свой тип

Тип описывается в двух местах: в `config.yml` (для плагина) и в ресурспаке (иконка и полоса).

**1. config.yml**

```yaml
types:
  - key: mistik:donate
    glyph: E210            # следующие свободные блоки: E214, E218, E21C, ...
    bar-color: "#FFB000"
    icon-width: 9
    sound: entity.player.levelup
```

Каждый тип занимает 4 символа подряд, начиная с `glyph`: иконка, иконка второй строки, полоса,
полоса второй строки. Блоки `E200`–`E20F` заняты встроенными типами.

**2. Ресурспак.** Положи иконку 9×9 в `assets/notifylib/textures/font/type/donate_icon.png` и полосу
152×2 нужного цвета в `type/donate_bar.png`. Затем добавь в `assets/notifylib/font/toast.json`, в массив
`providers`, четыре записи. Ascent у всех типов одинаковые; меняются только файлы и символы
(`E210`…`E213`):

```json
{ "type": "bitmap", "file": "notifylib:font/type/donate_icon.png", "ascent": 8,      "height": 9, "chars": [""] },
{ "type": "bitmap", "file": "notifylib:font/type/donate_icon.png", "ascent": -8184,  "height": 9, "chars": [""] },
{ "type": "bitmap", "file": "notifylib:font/type/donate_bar.png",  "ascent": -4100,  "height": 2, "chars": [""] },
{ "type": "bitmap", "file": "notifylib:font/type/donate_bar.png",  "ascent": -12292, "height": 2, "chars": [""] }
```

После этого: `/notify reload` на сервере, а игрокам — обновлённый пак.

## Слияние шейдера

Клиент использует только один `rendertype_text`. Пак выше по приоритету (ItemsAdder, Nexo, HUD-паки)
перекрывает наш. Вся логика уведомления лежит в `include/toast.glsl`; в чужой шейдер нужно добавить две вставки.
Готовые шейдеры для 1.21.11, 26.1 и 26.2 лежат в `resourcepack/` и служат образцом.

`rendertype_text.vsh`:

```glsl
#moj_import <minecraft:toast.glsl>

flat out int toastMode;
flat out vec2 toastTiming;
out float toastU;

float glyphRight() {
    return step(0.5, fract(UV0.x * float(textureSize(Sampler0, 0).x)));
}

void main() {
    vec3 pos = Position;
    // ... их код, который считает vertexColor/texCoord0 ...
    toastMode = T_MODE_NONE;
    toastTiming = vec2(0.0);
    toastU = 0.0;
    if (ProjMat[3][3] == 1.0) {
        vec2 screen = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
        float alpha; int mode; vec2 timing;
        if (toastVertex(pos, screen, Color.rgb, GameTime * T_DAY, alpha, mode, timing)) {
            toastMode = mode;
            toastTiming = timing;
            if (mode == T_MODE_TIMER) {
                vertexColor = vec4(1.0, 1.0, 1.0, Color.a);
                toastU = glyphRight();
            }
            vertexColor.a *= alpha;
        } else if (toastIsDebugMarker(Color.rgb)) {
            vertexColor = vec4(0.3, 1.0, 0.3, Color.a);
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);   // pos, а не Position
}
```

`rendertype_text.fsh`:

```glsl
#moj_import <minecraft:toast.glsl>

flat in int toastMode;
flat in vec2 toastTiming;
in float toastU;

// после вычисления color, до discard:
if (toastMode == T_MODE_TIMER && toastU > toastTimerLeft(toastTiming, GameTime * T_DAY)) {
    color.rgb *= 0.3;
    color.a *= 0.5;
}
```

## Важно при правках

- Блок констант в `toast.glsl` («Generated from Protocol») должен совпадать с
  `plugin/src/.../core/protocol/Protocol.java`. Меняешь одно — меняй и второе, во всех трёх копиях `toast.glsl`
  (основной пак и оверлеи `overlay_26_1`, `overlay_26_2`). Иначе уведомления встанут не туда.
- Если меняешь `panel.png`, оставь размер 160×18. Иконки — 9×9 (или укажи `icon-width`).

## Ограничения

- Белый боссбар зарезервирован: пак прячет полоску у `BossBar.Color.WHITE`.
- Клиент рисует только боссбары из верхней трети экрана. Если у игрока много чужих боссбаров, уведомления
  могут не поместиться.
- Ширины текста точны для ванильных bitmap-шрифтов (латиница, кириллица, греческий, знаки). Для CJK и
  прочих символов Unifont ширина оценочная, а в двухстрочном уведомлении такие символы заменяются на `?`.
  Там же снимаются курсив, подчёркивание и зачёркивание.
- `Component.translatable` и `<lang:…>` показывают ключ перевода. Передавайте готовый текст.
- Каждый шаг сдвига стопки — 2 пакета (сдвиг и «стоит»): метка движения хранится по модулю 16 тиков.
- Тосты живут на бэкенде. При переходе через прокси на другой сервер прокси снимает боссбары.
