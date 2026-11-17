package dev.unyielding.jail.party.live;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/** Test-owned files only. Exercises exact spelling, traversal, missing files and symlink refusal. */
public final class NativePathPolicyTest {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("ok "+checks+" "+label);}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("jail-native-path-").toRealPath();
        try {
            Path current=root.resolve("owner.dat"),temp=Path.of(current+".temp"),backup=Path.of(current+".old");
            Files.writeString(current,"original");Files.writeString(temp,"pending");Files.writeString(backup,"backup");
            Path dotted=root.resolve(".").resolve("owner.dat");
            check(NativePathPolicy.checkedFile(current).equals(current),"canonical current");
            check(NativePathPolicy.checkedFile(dotted).equals(current),"bound dot spelling resolves same file");
            check(NativePathPolicy.temporary(current,current,temp),"exact canonical native temporary");
            check(NativePathPolicy.temporary(current,dotted,Path.of(dotted+".temp")),"exact dotted native temporary");
            check(!NativePathPolicy.temporary(current,dotted,temp),"unissued canonical alias denied");
            check(!NativePathPolicy.temporary(current,current,Path.of(current+".tmp")),"wrong suffix denied");
            check(!NativePathPolicy.temporary(current,current,backup),"backup denied");
            check(!NativePathPolicy.temporary(root.resolve("other.dat"),current,temp),"foreign authority denied");
            Path other=root.resolve("other.dat");Files.writeString(other,"other");Files.writeString(Path.of(other+".temp"),"other-pending");
            check(!NativePathPolicy.temporary(current,other,Path.of(other+".temp")),"foreign emitted owner denied");
            boolean denied=false;try{NativePathPolicy.checkedFile(root.resolve("unused/../owner.dat"));}catch(IOException expected){denied=true;}
            check(denied,"native parent traversal refused before normalization");
            check(!NativePathPolicy.temporary(current,current,root.resolve("unused/../owner.dat.temp")),"requested traversal denied");
            Files.delete(temp);check(!NativePathPolicy.temporary(current,current,temp),"missing native temporary denied");
            Files.createSymbolicLink(temp,current);check(!NativePathPolicy.temporary(current,current,temp),"symbolic temporary denied");Files.delete(temp);Files.writeString(temp,"pending");
            Path alias=root.resolve("alias.dat");Files.createSymbolicLink(alias,current);
            denied=false;try{NativePathPolicy.checkedFile(alias);}catch(IOException expected){denied=true;}check(denied,"symbolic current denied");
            Path folder=root.resolve("real");Files.createDirectory(folder);Files.writeString(folder.resolve("owner.dat"),"original");
            Path link=root.resolve("linked");Files.createSymbolicLink(link,folder);denied=false;try{NativePathPolicy.checkedFile(link.resolve("owner.dat"));}catch(IOException expected){denied=true;}
            check(denied,"symbolic ancestor denied");
            check(!NativePathPolicy.temporary(current,root.resolve("missing.dat"),temp),"missing native current denied");
            if(!Files.readString(current).equals("original")||!Files.readString(backup).equals("backup"))throw new AssertionError("Policy changed files");
            if(checks!=16)throw new AssertionError("Incomplete test set");
            System.out.println("passed: 16 exact native path checks");
        }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
