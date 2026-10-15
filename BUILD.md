# BUILD: NanoForge

## Требования
- JDK 17+ (для сборки и тестов), целевой байткод Java 8 (`maven.compiler.release=8`).
- Apache Maven 3.8+.

## Команда сборки
```cmd
mvn -q -o package -DskipTests > build.log 2>&1
```

## Где синхронизировать версию при релизах:
- `pom.xml` (`<version>1.7.0</version>`)
- `src/main/resources/plugin.yml` (`version: 1.7.0`)
- `README.md` (бейджи версий и статусов)
- `CHANGELOG.md` (секция новой версии)
- `docs/changelog.md` (секция новой версии в документации)
- `PROJECT_STATE.md`
