package dev.unyielding.jail;

import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;

final class JailNbt {
    private JailNbt(){}

    static byte[] encode(CompoundTag tag) throws IOException { return encode(tag,4L*1024*1024); }

    static byte[] encode(CompoundTag tag, long budget) throws IOException {
        var bytes=new ByteArrayOutputStream();
        var out=new DataOutputStream(bytes);
        out.writeByte(Tag.TAG_COMPOUND);
        out.writeUTF("");
        payload(tag,out);
        byte[] encoded=bytes.toByteArray();
        var decoded=NbtIo.read(new DataInputStream(new ByteArrayInputStream(encoded)),NbtAccounter.create(budget));
        String difference=mismatch(tag,decoded,"root");
        if (difference!=null) throw new IOException("Custody NBT round-trip failed at "+difference);
        return encoded;
    }

    static CompoundTag decode(byte[] bytes)throws IOException{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(4L*1024*1024));}

    static void payload(Tag tag,DataOutput out)throws IOException {
        if(tag instanceof CompoundTag compound){
            for(String key:new TreeSet<>(compound.getAllKeys())){
                Tag value=compound.get(key);out.writeByte(value.getId());out.writeUTF(key);payload(value,out);
            }
            out.writeByte(Tag.TAG_END);
        } else if(tag instanceof ListTag list) {
            out.writeByte(list.getElementType());out.writeInt(list.size());
            for(Tag value:list)payload(value,out);
        } else if(tag instanceof DoubleTag d) {
            out.writeLong(Double.doubleToRawLongBits(d.getAsDouble()));
        } else if(tag instanceof FloatTag f) {
            out.writeInt(Float.floatToRawIntBits(f.getAsFloat()));
        } else tag.write(out);
    }

    // Logical NBT equality includes every key, list type and ordered element, array and
    // primitive. Compare the complete floating payload (including NaN sign and payload bits).
    // Never include the values or private player-provided field names in error reports.
    static String mismatch(Tag source, Tag decoded, String path) {
        if(source==null || decoded==null || source.getId()!=decoded.getId())return path+" (type)";
        if(source instanceof CompoundTag a && decoded instanceof CompoundTag b){
            if (!a.getAllKeys().equals(b.getAllKeys())) return path+" (keys)";
            int index=0;
            for(String key:new TreeSet<>(a.getAllKeys())){
                String field=switch(key){
                    case "attributes","abilities","persistent","effects","base","amount","modifiers","id","operation","health","PlayerPersisted","insanelib","nbt_tags" -> key;
                    default -> "field["+index+"]";
                };
                String child=mismatch(a.get(key),b.get(key),path+"."+field);
                if(child!=null)return child;
                index++;
            }
            return null;
        }
        if(source instanceof ListTag a && decoded instanceof ListTag b){
            if (a.getElementType()!=b.getElementType() || a.size()!=b.size())return path+" (list shape)";
            for(int i=0;i<a.size();i++){
                String child=mismatch(a.get(i),b.get(i),path+"["+i+"]");
                if(child!=null)return child;
            }
            return null;
        }
        if(source instanceof DoubleTag a && decoded instanceof DoubleTag b)
            return Double.doubleToRawLongBits(a.getAsDouble())==Double.doubleToRawLongBits(b.getAsDouble())?null:path+" (double bits)";
        if(source instanceof FloatTag a && decoded instanceof FloatTag b)
            return Float.floatToRawIntBits(a.getAsFloat())==Float.floatToRawIntBits(b.getAsFloat())?null:path+" (float bits)";
        return source.equals(decoded)?null:path+" ("+source.getType().getName()+")";
    }
}
