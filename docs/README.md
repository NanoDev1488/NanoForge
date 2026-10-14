<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=0:0f2027,100:2c5364&height=180&section=header&text=ɴᴀɴᴏꜰᴏʀɢᴇ&fontSize=60&fontColor=ffffff&animation=fadeIn"/>

<p align="center">
  <a href="https://git.io/typing-svg">
    <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=24&pause=1000&color=00FF9C&center=true&vCenter=true&width=650&lines=Official+Documentation;Minecraft+Server+Addon+Framework;Create+Addons+%26+Plugins+Without+Java;In-Game+GUI+Drag+%26+Drop+Editor;1.16+%E2%86%92+1.21.x+Full+Support" alt="typing animation" />
  </a>
</p>

<p align="center">✧･ﾟ: *✧･ﾟ:* 　　*:･ﾟ✧*:･ﾟ✧</p>

<p align="center">
  ➳ 📖 <b>Официальная документация NanoForge</b><br/>
  ➳ 📡 Телеграм-канал автора: <a href="https://t.me/nanodev_MC">t.me/nanodev_MC</a><br/>
  ➳ ⚡ Без компиляции, без перезагрузки сервера, прямо в YAML и через игру!<br/>
  ➳ 🛡️ Не мод Forge! Серверный плагин Spigot / Paper / Purpur.
</p>

<p align="center">
  <a href="https://nanodev1488.github.io/NanoForge/"><img src="https://img.shields.io/badge/Docs-GitHub%20Pages-00FF9C?style=for-the-badge&logo=gitbook&logoColor=black" alt="Documentation"/></a>
  <img src="https://img.shields.io/badge/Minecraft-1.16--1.21.x-2c5364?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft"/>
  <img src="https://img.shields.io/badge/Platform-Paper%20%7C%20Spigot%20%7C%20Purpur-2c5364?style=for-the-badge" alt="Platform"/>
  <img src="https://img.shields.io/badge/Java-8%20--%2021%2B-2c5364?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/License-MIT-2c5364?style=for-the-badge" alt="License"/>
  <a href="https://t.me/nanodev_MC"><img src="https://img.shields.io/badge/Telegram-@NanoDev__mc-2c5364?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Actions-21%20Types-2c5364?style=for-the-badge" alt="Actions"/>
  <img src="https://img.shields.io/badge/Wizard-GUI%20%2F%20No--Code-2c5364?style=for-the-badge" alt="Wizard"/>
  <img src="https://img.shields.io/badge/Integrations-Vault%20%7C%20WG%20%7C%20Citizens%20%7C%20LP-2c5364?style=for-the-badge" alt="Integrations"/>
  <img src="https://img.shields.io/badge/Status-Stable%20v1.6.1-00FF9C?style=for-the-badge&logoColor=black" alt="Status"/>
</p>

<p align="center">▄︻デ══━一 ▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄ 一━══デ︻▄</p>

<div align="center">

[Что это за плагин?](about.md) • [Журнал изменений](changelog.md) • [Быстрый старт](#быстрый-старт) • [Структура аддона](structure.md) • [Действия (Actions)](actions.md) • [Условия (Conditions)](conditions.md) • [GUI-меню](gui.md) • [Интеграции](integrations.md)

</div>

---

## 🌟 Что такое NanoForge?

**NanoForge** — это гибридный фреймворк-движок для серверов Minecraft, который стирает грань между разработкой плагинов на Java и конфигурацией через YAML.

Вместо того чтобы ради небольшого инвентарного интерфейса, цепочки команд или интеграции двух плагинов настраивать IDE, подключать библиотеки и собирать `.jar`, вы описываете логику в удобном формате `addon.yml` или настраиваете её прямо из игры через встроенный визуальный редактор.

> [!TIP]
> Если вы ещё не знакомы с плагином, обязательно прочитайте раздел: **[Что такое NanoForge простыми словами?](about.md)** — там подробно объяснено, почему это не мод Forge и как плагин заменяет собой связки Skript и DeluxeMenus.

### Ключевые возможности:

* ⚡ **Динамическая регистрация команд и событий:** Ваши команды и слушатели событий внедряются в движок Bukkit на лету и сразу видны игрокам (автоматическая синхронизация Brigadier-дерева через `syncCommands`).
* 🧩 **Два режима работы:** 
  * `type: addon` — надстройка над существующим плагином сервера с возможностью вызова его методов через рефлексию.
  * `type: new` — самостоятельный изолированный мини-плагин.
* 🔍 **Target Inspector:** Автоматический дамп классов, методов и полей целевого плагина в файл `target-api.txt` при создании аддона. Больше не нужно открывать декомпилятор!
* 🖥️ **Интерактивный GUI-редактор:** Настройка инвентарных меню перетаскиванием предметов из инвентаря прямо в игре (Drag-and-Drop) плюс настройка логики кликов через чат-DSL.
* 🎯 **19 встроенных типов действий (Actions):** Сообщения, консольные команды (с защитой от зацикливания через rate-limit), выдача предметов, звуки, частицы, таймеры (`delay`), рандомизация с весами (`random`), асинхронные вебхуки в Discord (`discord_webhook`) и многое другое.
* 🔒 **Продвинутая логика условий (`if`):** Поддержка составных условий `any_of`, `all_of`, `not`, проверка прав, миров, регионов WorldGuard, групп LuckPerms, баланса Vault, игрового времени, погоды, биомов, кулдаунов и двойных кликов подтверждения (`confirm`).
* 📦 **Полная изоляция и безопасность:** Ошибка в конфигурации одного аддона никогда не сломает другие аддоны и не уронит сервер. Автоматические бэкапы перед правками (`backups/`).
* 🌐 **Zero-Dependency интеграции:** Поддержка Vault, WorldGuard, LuckPerms, Citizens, PlaceholderAPI, PlugMan без необходимости принудительной установки этих плагинов.

---

## 🚀 Быстрый старт

### 1. Установка
1. Скачайте последнюю версию `NanoForge.jar`.
2. Поместите файл в папку `plugins/` вашего сервера Bukkit, Spigot или Paper (версии от 1.16.5 до 1.21.x).
3. Запустите или перезагрузите сервер.

> [!NOTE]
> NanoForge скомпилирован с обратной совместимостью Java 8 для запуска на классических версиях (1.16+), но при этом полностью оптимизирован под современные версии Java (17/21).

---

### 2. Создание первого аддона

Выдайте себе право `nano.admin` (или OP) и выполните в игре:

```shell
# Создать аддон к установленному плагину (с таб-комплитом по установленным плагинам)
/nano create addon EssentialsX MyEssentialsAddon

# Или создать независимый мини-плагин:
/nano create new QuickShopHelper
```

После выполнения команды в папке `plugins/NanoForge/addons/MyEssentialsAddon/` автоматически появятся:
* `addon.yml` — готовый рабочий шаблон с примерами команд, событий и меню.
* `target-api.txt` — полный дамп публичных методов и полей `EssentialsX` для быстрых вызовов.

---

### 3. Включение и тестирование

```shell
# Включаем аддон:
/nano enable MyEssentialsAddon

# Открываем созданное демонстрационное меню:
/nano menu MyEssentialsAddon main
```

Меню уже работает! В нём есть:
* Информационная кнопка с отправкой сообщения игроку.
* Кнопка с ограничением по праву (`if: permission`).
* Переход во вложенное меню с примером вызова метода плагина через `type: call`.
* Кнопка закрытия интерфейса.

---

## 🧭 Навигация по разделам

* ❓ [**Что это за плагин простыми словами?**](about.md) — разбор мифов, сравнение со Skript/Java, реальные сценарии.
* 📂 [**Структура и анатомия аддона**](structure.md) — файлы аддона, `storage.yml`, бэкапы, экспорт и импорт.
* ⌨️ [**Команды управления**](commands.md) — полный список команд `/nano`, аудит (`validate`), просмотр изменений (`diff`) и отладка (`debug`).
* 🎬 [**Действия (Actions Engine)**](actions.md) — все 19 действий, плейсхолдеры и аргументы.
* ⚖️ [**Условия (Conditions & If)**](conditions.md) — проверки прав, регионов, баланса, кулдаунов и логические блоки `any_of`/`all_of`/`not`.
* 🗃️ [**GUI Меню и редактор**](gui.md) — создание инвентарей, drag-and-drop редактор и чат-DSL.
* ⚡ [**События и NPC**](events.md) — подписка на Bukkit-события и интеграция с Citizens.
* 🔌 [**Интеграции и Рефлексия**](integrations.md) — вызов методов других плагинов, поддержка Vault, WorldGuard, LuckPerms.
* 💡 [**Готовые примеры**](examples.md) — готовые сценарии: аддон к экономике, панель администратора, магазин.

<p align="center">▄︻デ══━一 ▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄ 一━══デ︻▄</p>

<p align="center">˜”*°•.•°*”˜ ✦ ˜”*°•.•°*”˜</p>

<p align="center">
  <b>Разработано с ❤️ от <a href="https://t.me/NanoDev_mc">NanoDev</a></b><br/>
  Канал автора: <a href="https://t.me/NanoDev_mc">t.me/NanoDev_mc</a>
</p>

<img width="100%" src="https://capsule-render.vercel.app/api?type=waving&color=gradient&height=100&section=footer"/>
