package com.zpkdxgames.plexonquests.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AdminConfirmationGateTest {
    @Test
    void identicalSecondCommandConfirmsAndConsumesTheToken() {
        AtomicLong time = new AtomicLong(1_000L);
        AdminConfirmationGate gate = new AdminConfirmationGate(Duration.ofSeconds(30), time::get);
        String[] command = {"reset", "Tonim", "daily"};

        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("console", command));
        assertEquals(AdminConfirmationGate.Decision.CONFIRMED, gate.check("console", command));
        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("console", command));
    }

    @Test
    void actorBindingPreventsAnotherAdministratorFromConfirming() {
        AtomicLong time = new AtomicLong();
        AdminConfirmationGate gate = new AdminConfirmationGate(Duration.ofSeconds(30), time::get);
        String[] command = {"complete", "Tonim", "quest-a"};

        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("admin-a", command));
        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("admin-b", command));
        assertEquals(AdminConfirmationGate.Decision.CONFIRMED, gate.check("admin-a", command));
    }

    @Test
    void targetOrActionChangeRequiresRestaging() {
        AtomicLong time = new AtomicLong();
        AdminConfirmationGate gate = new AdminConfirmationGate(Duration.ofSeconds(30), time::get);

        assertEquals(AdminConfirmationGate.Decision.STAGED,
                gate.check("admin", new String[] {"reset", "Tonim", "daily"}));
        assertEquals(AdminConfirmationGate.Decision.STAGED,
                gate.check("admin", new String[] {"reset", "Alex", "daily"}));
        assertEquals(AdminConfirmationGate.Decision.STAGED,
                gate.check("admin", new String[] {"complete", "Alex", "daily"}));
    }

    @Test
    void expiredConfirmationCannotExecute() {
        AtomicLong time = new AtomicLong();
        AdminConfirmationGate gate = new AdminConfirmationGate(Duration.ofSeconds(30), time::get);
        String[] command = {"cancel", "Tonim", "assignment"};

        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("admin", command));
        time.set(Duration.ofSeconds(31).toNanos());
        assertEquals(AdminConfirmationGate.Decision.STAGED, gate.check("admin", command));
    }

    @Test
    void commandComparisonIsCaseInsensitiveButStillArgumentExact() {
        AtomicLong time = new AtomicLong();
        AdminConfirmationGate gate = new AdminConfirmationGate(Duration.ofSeconds(30), time::get);

        assertEquals(AdminConfirmationGate.Decision.STAGED,
                gate.check("admin", new String[] {"RESET", "Tonim", "DAILY"}));
        assertEquals(AdminConfirmationGate.Decision.CONFIRMED,
                gate.check("admin", new String[] {"reset", "tonim", "daily"}));
    }
}
