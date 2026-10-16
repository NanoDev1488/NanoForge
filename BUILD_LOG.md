# BUILD_LOG

## [2026-10-16] Версия 1.8.0 — NanoScript (Zero-Allocation Expression Engine & Micro-VM)
- **Коммит:** `72bee78`
- **Что сделано:**
  - Реализован легковесный лексер и Pratt-парсер с поддержкой процентов (`25%` -> `0.25`), комментариев, логических и арифметических выражений.
  - Constant Folding на этапе парсинга (`5 + 5 * 2 -> 15.0`).
  - Стековая микро-VM (`NanoVM`) с плоским массивом кодов инструкций (`int[]`) и пулом фреймов стека через `ThreadLocal<NanoStackFrame>` без создания объектов в цикле выполнения в тике.
  - Fast-Path Bukkit Binding: доступ к свойствам (`player.health`, `player.food`, `attacker.*`, `victim.*`, `event.damage`), методам (`has_perm`, `distance_to`, `in_group`) и глобальным функциям (`chance(25%)`, `min`, `max`, `abs`, `round`, `clamp`).
  - Полная интеграция в YAML: строковые условия `condition: "..."`, блоки `if: { script: "..." }`, действия `type: script`, плейсхолдеры `{event.damage * 0.35}` и компактный синтаксис строковых actions (`heal: attacker, amount = event.damage * 0.35`).
  - Команда `/nano script <test|eval|disasm> <выражение>`.
- **Результат CI (GitHub Actions):**
  - Workflow CI: **SUCCESS** (все тесты `mvn -B clean verify`, живой Paper-сервер и автономное тестирование скомпилированных аддонов пройдены).
  - Workflow Release: **SUCCESS** (сборка `NanoForge.jar` успешно сформирована).
