package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.content.ContentRepository;
import com.shieldudaram.fightcaves.content.LoadedContent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContentRepositoryTest {

    @Test
    void seedsAndLoadsContentCatalog() throws Exception {
        Path dir = Files.createTempDirectory("fc-content-test");
        ContentRepository repo = new ContentRepository(dir, Logger.getLogger("test"));

        LoadedContent content = repo.get();
        assertEquals(63, content.totalWaves());
        assertEquals(63, content.waves().size());
        assertFalse(content.enemyById().isEmpty());
    }
}
