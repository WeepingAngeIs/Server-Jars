package dev.unyielding.quests.settings;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class PokemonEditsTest {
    @TempDir Path directory;
    @Test void savesReloadsAndResetsExplicitOverrides() throws Exception {
        Path path=directory.resolve("pokemon/overrides.json");
        assertEquals(0,PokemonEdits.read(path).revision());
        JsonObject species=JsonParser.parseString("{\"name\":\"Example\",\"baseStats\":{\"hp\":80}}").getAsJsonObject();
        PokemonEdits.State state=new PokemonEdits.State(1,Map.of("cobblemon:example",species),Map.of());
        species.addProperty("name","Changed source");
        PokemonEdits.write(path,state);
        assertEquals("Example",PokemonEdits.read(path).species().get("cobblemon:example").get("name").getAsString());
        assertEquals(1,PokemonEdits.read(path).revision());
        PokemonEdits.write(path,new PokemonEdits.State(2,Map.of(),Map.of()));
        assertTrue(PokemonEdits.read(path).species().isEmpty());
        assertEquals(2,PokemonEdits.read(path).revision());
    }
    @Test void malformedConfigIsPreserved() throws Exception {
        Path path=directory.resolve("overrides.json");String broken="{\"format\":99}";
        Files.writeString(path,broken);
        assertThrows(java.io.IOException.class,()->PokemonEdits.read(path));
        assertEquals(broken,Files.readString(path));
    }
    @Test void validatesIdentifiersAndFiniteNumbers() {
        for(String bad:List.of("../example","cobblemon:../example","bad id:x","cobblemon:x//y","cobblemon:x/"))
            assertThrows(IllegalArgumentException.class,()->PokemonEdits.identifier(bad));
        assertEquals("unyieldingquests:editor/cobblemon/pikachu",PokemonEdits.identifier("unyieldingquests:editor/cobblemon/pikachu"));
        assertThrows(IllegalArgumentException.class,()->PokemonEdits.parse("{\"weight\":1e400}"));
        assertThrows(IllegalArgumentException.class,()->PokemonEdits.parse("x".repeat(PokemonEdits.MAX_DOCUMENT+1)));
    }
    @Test void rejectsExcessiveNestingBeforePersistence() {
        JsonObject root=new JsonObject(),cursor=root;
        for(int i=0;i<55;i++){JsonObject next=new JsonObject();cursor.add("next",next);cursor=next;}
        assertThrows(IllegalArgumentException.class,()->PokemonEdits.validate(root));
    }
    @Test void recognizesExplicitSpeciesAfterOtherProperties() {
        assertEquals("pikachu",PokemonEdits.speciesToken("level=10 species=pikachu shiny"));
        assertEquals("cobblemon:pikachu",PokemonEdits.speciesToken("cobblemon:pikachu level=10"));
    }
}
