package dev.unyielding.bundlesmoke;
import dev.unyielding.jail.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.server.level.ServerPlayer;
/** Passive counters and an isolated artifact-binding negative probe, never a custody implementation. */
public final class ProductionObserver {
 public static int automaticCalls,automaticCompletions,clientRespawnRequests;
 private ProductionObserver(){}
 public static boolean enabled(){return Boolean.getBoolean("uq.playerOnly");}
 public static void identity()throws Exception {
  Path jar=Path.of(System.getenv("UQ_JAIL_JAR")).toRealPath();
  if(!net.neoforged.fml.ModList.get().getModFileById("minecraftjail").getFile().getFilePath().toRealPath().equals(jar))throw new IllegalStateException("Wrong candidate loaded");
  try(var z=new ZipFile(jar.toFile())){
   for(Class<?> c:List.of(JailAutoRespawn.class,JailAdmissions.class,JailEscapeCustody.class,JailEnforcement.class)){
    String name=c.getName().replace('.','/')+".class";
    try(var in=c.getResourceAsStream("/"+name)){if(in==null||!Arrays.equals(in.readAllBytes(),z.getInputStream(z.getEntry(name)).readAllBytes()))throw new IllegalStateException("Production class shadowed: "+name);}
   }
  }
  PlayerOnlySmoke.flag("candidate_classes_loaded_from_jar_without_behavior_adapters");
 }
 private static String runtime(ServerPlayer p)throws Exception {
  var c=Class.forName("dev.unyielding.jail.JailHumanAudit");var m=c.getDeclaredMethod("runtime",ServerPlayer.class);m.setAccessible(true);return (String)m.invoke(null,p);
 }
 public static void selfBinding(ServerPlayer p)throws Exception {
  if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))throw new IllegalStateException("Disposable only");
  Path jar=Path.of(System.getenv("UQ_JAIL_JAR")).toRealPath(),root=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-harness/libs").toRealPath();
  if(!jar.getParent().equals(root))throw new IllegalStateException("Not isolated runtime JAR");
  byte[] original=Files.readAllBytes(jar);String before=runtime(p);
  try{
   Files.write(jar,new byte[]{10,42,10},StandardOpenOption.APPEND);
   if(before.equals(runtime(p)))throw new IllegalStateException("Changed Jail bytes reused old runtime identity");
   if(PlayerOnlySmoke.cmd("unjail Visitor")!=0)throw new IllegalStateException("Altered artifact authorized custody restoration");
  }finally{Files.write(jar,original,StandardOpenOption.TRUNCATE_EXISTING);}
  if(!Arrays.equals(original,Files.readAllBytes(jar))||!before.equals(runtime(p)))throw new IllegalStateException("Candidate byte restoration failed");
 }
}
