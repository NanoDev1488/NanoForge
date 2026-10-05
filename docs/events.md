# События Bukkit и NPC Citizens

NanoForge позволяет перехватывать игровые события сервера и привязывать действия к жителям Citizens без написания Java-кода.

---

## ⚡ Динамические события Bukkit

В секции `events:` аддона вы можете подписаться на любое стандартное событие Bukkit или даже событие стороннего плагина:

```yaml
events:
  PlayerJoinEvent:
    actions:
      - type: message
        text: "&eДобро пожаловать на сервер, {player}!"
      - type: play_sound
        sound: ENTITY_EXPERIENCE_ORB_PICKUP

  PlayerQuitEvent:
    actions:
      - type: broadcast
        text: "&7Игрок &c{player} &7покинул сервер."

  PlayerBedEnterEvent:
    actions:
      - type: message
        text: "&aСпокойной ночи! Дождитесь утра."
```

### 🔍 Как NanoForge находит события:
1. **Короткие имена:** Вы можете указывать просто `PlayerJoinEvent` или `BlockBreakEvent`. Плагин автоматически ищет класс события в стандартных пакетах Bukkit:
   * `org.bukkit.event.player.*`
   * `org.bukkit.event.block.*`
   * `org.bukkit.event.entity.*`
   * `org.bukkit.event.inventory.*`
   * `org.bukkit.event.world.*`
   * `org.bukkit.event.server.*`
   * `org.bukkit.event.weather.*`
   * `org.bukkit.event.vehicle.*`
   * `org.bukkit.event.hanging.*`
2. **Полные пути (для сторонних плагинов):** Если вам нужно событие из другого плагина, укажите полный путь:
   ```yaml
   events:
     com.palmergames.bukkit.towny.event.NewTownEvent:
       actions:
         - type: broadcast
           text: "&6Основан новый город!"
   ```

### 🛡️ Безопасность и жизненный цикл:
* Слушатели регистрируются динамически через низкоуровневый `EventExecutor` Bukkit.
* При выключении или перезагрузке аддона (`/nano disable`, `/nano reload`) слушатели аккуратно снимаются со всех списков обработчиков (`HandlerList.unregisterAll()`), исключая утечки памяти и повторные срабатывания.
* Если во время обработки события возникает непредвиденная ошибка, плагин перехватывает её, предотвращая спам стектрейсов в консоли.

---

## 👥 Интеграция с Citizens (NPC-триггеры)

Если на сервере установлен популярный плагин **Citizens**, вы можете назначать действия на клик по любому NPC прямо в `addon.yml` без команд и Java-кода:

```yaml
npcs:
  quest_master:
    npc_id: 1                  # Числовой ID NPC в Citizens (/npc id)
    actions:
      - type: openmenu
        menu: quest_list
      - type: play_sound
        sound: ENTITY_VILLAGER_YES

  banker:
    npc_id: 5
    actions:
      - type: openmenu
        menu: bank_main
```

### Как это устроено:
* **Zero Dependency:** NanoForge не требует Citizens при компиляции. Он слушает обычное событие взаимодействия `PlayerInteractEntityEvent` и через рефлексию проверяет, принадлежит ли существо Citizens.
* Если Citizens нет на сервере, секция `npcs:` просто тихо игнорируется, не создавая ошибок.
* Валидатор `/nano validate` проверяет наличие обязательного поля `npc_id` и корректность действий внутри каждого NPC.
