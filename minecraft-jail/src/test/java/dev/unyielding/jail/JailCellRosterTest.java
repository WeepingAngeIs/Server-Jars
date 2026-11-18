package dev.unyielding.jail;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailCellRosterTest {
    @Test void planAloneReservesTheCellBeforeInventoryRemoval() throws Exception {
        var roster=new JailCellRoster(6);
        var owner=UUID.randomUUID();
        roster.record(1,owner,false);
        assertEquals(2,roster.choose(0));
        assertEquals(6,roster.choose(6));
        assertFalse(roster.occupant(1).assigned());
        assertThrows(IOException.class,()->roster.choose(1));
        roster.record(1,owner,true);
        roster.record(1,owner,false);
        assertEquals(new JailCellRoster.Occupant(owner,true),roster.occupant(1));
    }

    @Test void contradictoryCoordinatesOrOwnersRefuseFurtherAdmission() throws Exception {
        var roster=new JailCellRoster(6);
        var owner=UUID.randomUUID();
        roster.record(2,owner,true);
        assertThrows(IOException.class,()->roster.record(2,UUID.randomUUID(),false));
        assertThrows(IOException.class,()->roster.record(3,owner,false));
        assertThrows(IOException.class,()->roster.record(0,owner,false));
        assertThrows(IOException.class,()->roster.choose(7));
    }

    @Test void fullFacilityDoesNotShareACell() throws Exception {
        var roster=new JailCellRoster(6);
        for(int i=1;i<=6;i++)roster.record(i,UUID.randomUUID(),true);
        assertThrows(IOException.class,()->roster.choose(0));
        assertThrows(IOException.class,()->roster.choose(6));
    }
}
