package ru.nanodev.nanoforge.database;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseManagerTest {

    @Test
    void sqliteExecutionAndQuery(@TempDir File tempDir) {
        String yamlStr = "type: sqlite\nfile: test.db\n";
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yamlStr));
        DatabaseManager db = new DatabaseManager(tempDir, config);

        assertThat(db.isValid()).isTrue();

        int create = db.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT, coins INT);", Collections.emptyList());
        assertThat(create).isGreaterThanOrEqualTo(0);

        int inserted = db.executeUpdate("INSERT INTO users (name, coins) VALUES (?, ?);", Arrays.asList("Alex", 150));
        assertThat(inserted).isEqualTo(1);

        List<Map<String, Object>> rows = db.executeQuery("SELECT coins FROM users WHERE name = ?;", Collections.singletonList("Alex"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("coins")).isEqualTo(150);
    }
}
