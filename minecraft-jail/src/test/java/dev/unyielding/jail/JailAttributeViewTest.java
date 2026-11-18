package dev.unyielding.jail;

import java.io.IOException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailAttributeViewTest {
    private static Map<String,CompoundTag> view(CompoundTag baseline,boolean equipment,CompoundTag... rows) throws IOException {
        var list=new ListTag();
        for(CompoundTag row:rows) list.add(row);
        var result=JailAttributeView.index(list);
        JailAttributeView.addDefault(result,baseline,equipment);
        return result;
    }

    @Test void implicitSupplierDefaultAndMaterializedDefaultHaveIdenticalProjections() throws Exception {
        var baseline=TagParser.parseTag("{id:'example:reach',base:2.0d}");
        assertEquals(view(baseline,false),view(baseline,false,baseline));
        var live=baseline.copy();live.put("modifiers",new ListTag());
        assertEquals(view(baseline,true),view(baseline,true,live));
        assertFalse(baseline.contains("modifiers"));
    }

    @Test void explicitBaseModifierUnknownFieldAndForeignAttributeRemainDistinct() throws Exception {
        var baseline=TagParser.parseTag("{id:'example:reach',base:2.0d}");
        var implicit=view(baseline,false);
        for(String changed:new String[]{"{id:'example:reach',base:2.01d}",
                "{id:'example:reach',base:2.0d,modifiers:[{id:'example:bonus',amount:0.0d,operation:'add_value',extra:1b}]}",
                "{id:'example:reach',base:2.0d,custom:1}", "{id:'unknown:other',base:2.0d}"}) {
            var row=TagParser.parseTag(changed);
            var observed=view(baseline,false,row);
            assertNotEquals(implicit,observed);
            assertEquals(row,observed.get(row.getString("id")));
        }
        assertThrows(IOException.class,()->view(baseline,false,baseline,baseline.copy()));
    }
}
