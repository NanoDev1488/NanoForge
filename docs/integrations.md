# Интеграции и Рефлексия (Reflection Bridge)

Главная суперсила NanoForge — возможность взаимодействовать с **любым установленным плагином сервера** без необходимости добавлять его в зависимости при сборке и без единой строчки Java-кода.

---

## 🔍 Target Inspector: Автодамп API

Когда вы создаёте аддон к существующему плагину:
```shell
/nano create addon EssentialsX MyAddon
```
NanoForge запускает встроенный анализатор **TargetInspector**. Он:
1. Находит `.jar`-файл целевого плагина на диске сервера.
2. Сканирует все скомпилированные классы плагина.
3. Отфильтровывает сторонние библиотеки (Google Guava, SnakeYAML, Apache Commons и т.д.), оставляя только собственный код целевого плагина.
4. Создаёт файл-шпаргалку: `plugins/NanoForge/addons/MyAddon/target-api.txt`.

### Пример содержимого `target-api.txt`:
```txt
=== Дамп API плагина: CachesManager ===
Версия: 1.2.0
Главный класс: ru.nanodev.caches.CachesPlugin
Jar: /plugins/CachesManager.jar

--- Класс: ru.nanodev.caches.CachesPlugin ---
Методы:
  public String reloadDatabaseOnly()
  public String reloadMenusOnly()
  public int getActiveCacheCount()
  public void purgeExpired(long maxAgeMillis)
```
Вам больше не нужно декомпилировать плагины, чтобы узнать названия их методов!

---

## ⚡ Вызов методов через `type: call`

Действие `type: call` обращается к методам главного класса целевого плагина через рефлексию:

```yaml
- type: call
  plugin: CachesManager         # Имя плагина из plugin.yml
  method: reloadDatabaseOnly    # Название вызываемого метода
  args: []                      # Аргументы метода (если требуются)
  save_as: db_status            # Сохранить возвращённое значение в переменную аддона
  scope: global                 # global или player (по умолчанию player)
- type: message
  text: "&aСтатус базы данных: &f{result}"
```

### Автоматическое приведение типов параметров:
Все аргументы в `args:` можно передавать в виде строк (включая плейсхолдеры `{player}`, `{arg1}`). NanoForge автоматически приведёт их к нужным типам Java:
* `String`
* `int` / `Integer`
* `long` / `Long`
* `double` / `Double`
* `boolean` / `Boolean`

---

## 🌉 Встроенные мосты (Bridges)

Все внешние интеграции в NanoForge работают по принципу **Soft-Dependency** (мягкая зависимость). Если плагина нет на сервере, NanoForge всё равно успешно запустится, а связанные с ним функции просто будут тихо ожидать установки.

| Интеграция | Возможности в NanoForge |
|---|---|
| **Vault** | `eco_give`, `eco_take`, условие `eco_at_least` |
| **WorldGuard 7.x** | Проверка нахождения игрока в регионе: `if: region: "название"` |
| **LuckPerms** | Проверка основной группы игрока: `if: luckperms_group: "vip"` |
| **Citizens** | Обработка кликов по жителям в секции `npcs:` |
| **PlaceholderAPI** | Поддержка любых `%плейсхолдеров%` во всех текстах + собственная экспансия |
| **PlugMan / PlugManX** | Кликабельные кнопки в чате для включения плагинов прямо из `/nano info` |

---

## 🧩 Собственная экспансия PlaceholderAPI

NanoForge регистрирует собственную экспансию в PlaceholderAPI, позволяя другим плагинам читать переменные ваших аддонов:

```
%nanoforge_<имя_аддона>_<ключ_переменной>%
```
* Если переменная есть у текущего игрока — возвращается её значение.
* Если per-player переменной нет — автоматически проверяется глобальная серверная переменная с таким именем.
* Если переменная отсутствует — возвращается пустая строка.

---

## 🩺 Startup Health-Check

При каждом старте сервера NanoForge выводит сводку по всем опциональным интеграциям в консоль:
```
[NanoForge] Проверка интеграций:
[NanoForge]   ✔ Vault: найден (1.7.3)
[NanoForge]   ✔ WorldGuard: найден (7.0.9)
[NanoForge]   ✔ PlaceholderAPI: найден (2.12.3)
[NanoForge]   - Citizens: не установлен. Скачать: https://ci.citizensnpcs.co/
[NanoForge]   - LuckPerms: не установлен. Скачать: https://luckperms.net/
```
Сводка подскажет, каких плагинов не хватает для полной функциональности, и сразу предоставит официальные ссылки для скачивания.
