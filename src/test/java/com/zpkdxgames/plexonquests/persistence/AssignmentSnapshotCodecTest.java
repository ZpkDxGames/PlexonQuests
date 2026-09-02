package com.zpkdxgames.plexonquests.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AssignmentSnapshotCodecTest {
    private final AssignmentSnapshotCodec codec = new AssignmentSnapshotCodec();

    @Test
    void roundTripsFrozenQuestDefinition() throws IOException {
        QuestDefinition original = TestFixtures.quest("snapshot-test", CompletionMode.SEQUENCE, 3L, 7L);

        QuestDefinition decoded = codec.decode(codec.encode(original));

        assertEquals(original.id(), decoded.id());
        assertEquals(original.fingerprint(), decoded.fingerprint());
        assertEquals(original.completionMode(), decoded.completionMode());
        assertEquals(original.display(), decoded.display());
        assertEquals(original.objectives(), decoded.objectives());
        assertEquals(original.rewards(), decoded.rewards());
        assertEquals("database-snapshot", decoded.source());
    }

    @Test
    void rejectsMalformedAndTruncatedPayloads() {
        assertThrows(IOException.class, () -> codec.decode("not base64"));

        byte[] complete = Base64.getDecoder().decode(codec.encode(
                TestFixtures.quest("truncated", CompletionMode.ALL, 1L)));
        String truncated = Base64.getEncoder().encodeToString(Arrays.copyOf(complete, complete.length - 3));
        assertThrows(IOException.class, () -> codec.decode(truncated));
    }

    @Test
    void rejectsTrailingData() {
        byte[] complete = Base64.getDecoder().decode(codec.encode(
                TestFixtures.quest("trailing", CompletionMode.ALL, 1L)));
        byte[] extended = Arrays.copyOf(complete, complete.length + 1);
        assertThrows(IOException.class, () -> codec.decode(Base64.getEncoder().encodeToString(extended)));
    }
}
