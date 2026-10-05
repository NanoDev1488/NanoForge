# Примеры готовых аддонов

Ниже приведены реальные конфигурации аддонов для типовых серверных задач. Вы можете скопировать любой из них в `plugins/NanoForge/addons/<Имя>/addon.yml` и адаптировать под свой сервер.

---

## 💎 Пример 1: Панель управления сторонним плагином (`CachesManagerHub`)

Аддон расширяет плагин `CachesManager`, предоставляя администратору удобный визуальный интерфейс для выборочной перезагрузки модулей через рефлексию:

```yaml
name: CachesManagerHub
type: addon
target: CachesManager
enabled: true

commands:
  cmhub:
    description: Панель управления CachesManager
    permission: nano.admin
    actions:
      - type: openmenu
        menu: main

menus:
  main:
    title: '&8&lCachesManager &7• Хаб'
    rows: 3
    items:
      '10':
        material: HOPPER
        name: '&e&lReload: база данных'
        lore:
          - '&7Вызывает CachesManager.reloadDatabaseOnly()'
          - '&7через рефлексию (call) и печатает {result}'
        actions:
          - type: call
            plugin: CachesManager
            method: reloadDatabaseOnly
            save_as: last_db_reload
          - type: message
            text: '&a✔ БД: &f{result}'

      '12':
        material: HOPPER_MINECART
        name: '&e&lReload: меню'
        lore:
          - '&7CachesManager.reloadMenusOnly()'
        actions:
          - type: call
            plugin: CachesManager
            method: reloadMenusOnly
          - type: message
            text: '&a✔ Меню: &f{result}'

      '14':
        material: BLAZE_POWDER
        name: '&e&lReload: анимации'
        lore:
          - '&7CachesManager.reloadAnimationsOnly()'
        actions:
          - type: call
            plugin: CachesManager
            method: reloadAnimationsOnly
          - type: message
            text: '&a✔ Анимации: &f{result}'

      '16':
        material: ENDER_CHEST
        name: '&b&lОткрыть меню кешей игроку'
        actions:
          - type: console
            command: execute as {player} run cachesmanager menu
          - type: closemenu

      '26':
        material: NETHER_STAR
        name: '&5&lСекретная VIP-плюшка'
        lore:
          - '&7Видна только с правом nano.vip'
        if:
          permission: nano.vip
        actions:
          - type: give_item
            material: DIAMOND
            amount: 1
            name: '&bПодарок хаба'
```

---

## 🎁 Пример 2: Ежедневный бонус с кулдауном и звуком (`DailyBonus`)

Автономный мини-плагин, выдающий награду раз в 24 часа с красивым оповещением на экране:

```yaml
name: DailyBonus
type: new
enabled: true

commands:
  bonus:
    description: "Получить ежедневную награду"
    actions:
      - type: openmenu
        menu: bonus_menu

menus:
  bonus_menu:
    title: "&8Ежедневный подарок"
    rows: 1
    items:
      "4":
        material: CHEST_MINECART
        name: "&6&lЗабрать награду"
        lore:
          - "&7Нажмите для получения:"
          - "&a+ 1000 монет"
          - "&b+ 5 изумрудов"
          - ""
          - "&eДоступно 1 раз в сутки."
        actions:
          - type: eco_give
            amount: 1000
            if:
              cooldown:
                seconds: 86400
                key: daily_claim
              deny_message: "&cВы уже забрали бонус! Приходите через {cooldown} сек."
          - type: give_item
            material: EMERALD
            amount: 5
          - type: play_sound
            sound: UI_TOAST_CHALLENGE_COMPLETE
            pitch: 1.2
          - type: title
            title: "&a&lНАГРАДА ПОЛУЧЕНА!"
            subtitle: "&e1000 монет и 5 изумрудов"
            actionbar: "&6Ждём вас завтра снова!"
          - type: closemenu
```

---

## 🛡️ Пример 3: Удаление клана с двойным подтверждением (`ClanDeleter`)

Демонстрирует безопасную кнопку с защитой от случайного клика (`confirm`):

```yaml
name: ClanSecurity
type: new
enabled: true

menus:
  manage:
    title: "&4Управление кланом"
    rows: 1
    items:
      "4":
        material: TNT
        name: "&c&lРАСПУСТИТЬ КЛАН"
        lore:
          - "&7Необратимое действие!"
          - "&eТребуется двойное нажатие."
        actions:
          - type: message
            text: "&4Клан успешно распущен!"
            if:
              confirm:
                seconds: 5
                key: disband_clan
              deny_message: "&e⚠ ВНИМАНИЕ! Нажмите кнопку ЕЩЁ РАЗ в течение 5 сек. для подтверждения!"
          - type: console
            command: "clan disband {player}"
          - type: play_sound
            sound: ENTITY_GENERIC_EXPLODE
          - type: closemenu
```

---

## 🧙 Пример 4: Квестовый NPC с проверкой погоды (`MysticNPC`)

Клик по NPC в Citizens вызывает проверку условий окружения:

```yaml
name: MysticQuest
type: new
enabled: true

npcs:
  shaman:
    npc_id: 42
    actions:
      - type: message
        text: "&9Шаман: &fДухи говорят со мной только во время грозы... Возвращайся в непогоду."
        if:
          not:
            weather_equals: "thunder"
      - type: message
        text: "&bШаман: &fНебо рыдает громом! Вот твой талисман молний, {player}!"
        if:
          weather_equals: "thunder"
      - type: give_item
        material: LIGHTNING_ROD
        name: "&eТалисман Бури"
        if:
          weather_equals: "thunder"
          cooldown:
            seconds: 3600
            key: shaman_talisman
          deny_message: "&cШаман: Ты уже брал талисман в этот час."
```
