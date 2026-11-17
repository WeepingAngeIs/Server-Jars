"""Keep test helpers out of the production module's exported Java package."""
import hashlib,json,os,shutil
from pathlib import Path
R=Path(__file__).resolve().parents[2]
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
S=R/'dist/packaged-harness/src/main/java';old=S/'dev/unyielding/jail';new=S/'dev/unyielding/playeronly'
assert sorted(p.name for p in old.iterdir())==['PlayerOnlyAdmissionMixin.java','PlayerOnlyCommandMixin.java','PlayerOnlyEnforcementMixin.java','PlayerOnlyJail.java']
new.mkdir(parents=True);(new/'mixin').mkdir()
def once(s,a,b):
 assert s.count(a)==1,(a,s.count(a));return s.replace(a,b,1)
for p in list(old.glob('*.java')):
 s=p.read_text()
 if p.name=='PlayerOnlyJail.java':
  s=once(s,'package dev.unyielding.jail;','package dev.unyielding.playeronly;\nimport dev.unyielding.jail.*;')
  for a,b in [('JailIntegrated.EscapeContext','PlayerOnlyAccess.Context'),('JailEscapeCustody.attempt(p)','PlayerOnlyAccess.escape(p)'),('JailIntegrated.escapeContext(p,false,true)','PlayerOnlyAccess.context(p)'),('JailCommands.claimAccess(p,j)','PlayerOnlyAccess.claim(p,j)'),('JailNbt.decode(e.snapshot())','PlayerOnlyAccess.decode(e.snapshot())'),('JailVanillaPort.readSavedPlayer(p)','PlayerOnlyAccess.saved(p)'),('JailNativeOriginPort.RECEIPT','PlayerOnlyAccess.originReceiptKey()'),('if(JailXp.retained(t))JailXp.verify(p,t);','PlayerOnlyAccess.verifyXp(p,t);'),('JailEscapeCustody.pending(next.server,c.transaction())','PlayerOnlyAccess.pending(next.server,c.transaction())')]:s=once(s,a,b)
  (new/p.name).write_text(s)
 else:
  s=once(s,'package dev.unyielding.jail;','package dev.unyielding.playeronly.mixin;\nimport dev.unyielding.jail.*;\nimport dev.unyielding.playeronly.*;')
  if p.name=='PlayerOnlyAdmissionMixin.java':s=once(s,'JailXp.live(p)','PlayerOnlyAccess.checkXp(p)')
  (new/'mixin'/p.name).write_text(s)
 p.unlink()
old.rmdir()
p=S/'dev/unyielding/bundlesmoke/PlayerOnlySmoke.java';s=p.read_text();s=once(s,'import dev.unyielding.jail.*;','import dev.unyielding.jail.*;\nimport dev.unyielding.playeronly.PlayerOnlyJail;');p.write_text(s)
(new/'PlayerOnlyAccess.java').write_text('''package dev.unyielding.playeronly;
import dev.unyielding.jail.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Exact test-only access to existing package-private checks, never a replacement audit. */
public final class PlayerOnlyAccess {
 private PlayerOnlyAccess() {}
 public record Context(UUID transaction,JailData.Assignment assignment,byte[] digest) {
  public Context {digest=digest.clone();}
  @Override public byte[] digest(){return digest.clone();}
 }
 private static Object call(Class<?> type,String name,Class<?>[] signature,Object... args)throws IOException {
  try{Method m=type.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(null,args);}
  catch(InvocationTargetException e){Throwable c=e.getCause();if(c instanceof IOException x)throw x;if(c instanceof RuntimeException x)throw x;if(c instanceof Error x)throw x;throw new IOException("Native check failed: "+name,c);}
  catch(ReflectiveOperationException|RuntimeException e){throw new IOException("Exact native check unavailable: "+type.getName()+"."+name,e);}
 }
 private static Object member(Object record,String name)throws IOException {
  try{Method m=record.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(record);}
  catch(ReflectiveOperationException e){throw new IOException("Native escape record unavailable",e);}
 }
 public static Context context(ServerPlayer p)throws IOException {
  Object c=call(JailIntegrated.class,"escapeContext",new Class<?>[]{ServerPlayer.class,boolean.class,boolean.class},p,false,true);
  if(c==null)return null;
  if(!c.getClass().getName().equals("dev.unyielding.jail.JailIntegrated$EscapeContext"))throw new IOException("Unknown native escape record");
  return new Context((UUID)member(c,"transaction"),(JailData.Assignment)member(c,"assignment"),(byte[])member(c,"digest"));
 }
 public static void escape(ServerPlayer p){
  try{call(JailEscapeCustody.class,"attempt",new Class<?>[]{ServerPlayer.class},p);}
  catch(IOException e){MinecraftJail.LOGGER.error("Player-only test adapter could not invoke verified escape: {}",e.toString());}
 }
 public static boolean pending(MinecraftServer s,UUID id)throws IOException{return (boolean)call(JailEscapeCustody.class,"pending",new Class<?>[]{MinecraftServer.class,UUID.class},s,id);}
 public static boolean claim(ServerPlayer p,Loc j)throws IOException{return (boolean)call(JailCommands.class,"claimAccess",new Class<?>[]{ServerPlayer.class,Loc.class},p,j);}
 public static CompoundTag saved(ServerPlayer p)throws IOException{return (CompoundTag)call(JailVanillaPort.class,"readSavedPlayer",new Class<?>[]{ServerPlayer.class},p);}
 public static CompoundTag decode(byte[] bytes)throws IOException {
  try{return (CompoundTag)call(Class.forName("dev.unyielding.jail.JailNbt"),"decode",new Class<?>[]{byte[].class},bytes);}
  catch(ClassNotFoundException e){throw new IOException(e);}
 }
 public static String originReceiptKey()throws IOException {
  try{Field f=JailNativeOriginPort.class.getDeclaredField("RECEIPT");f.setAccessible(true);return (String)f.get(null);}
  catch(ReflectiveOperationException e){throw new IOException(e);}
 }
 public static void checkXp(ServerPlayer p)throws IOException{call(JailXp.class,"live",new Class<?>[]{ServerPlayer.class},p);}
 public static void verifyXp(ServerPlayer p,CompoundTag t)throws IOException{call(JailXp.class,"verify",new Class<?>[]{ServerPlayer.class,CompoundTag.class},p,t);}
}
''')
p=R/'dist/packaged-harness/src/main/resources/player-only.mixins.json';config=json.loads(p.read_text());config['package']='dev.unyielding.playeronly.mixin';p.write_text(json.dumps(config,indent=2))
p=R/'dist/packaged-smoke/player-only-source.json';proof=json.loads(p.read_text());proof['generated_sha256']={str(p.relative_to(S)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(new.rglob('*.java'))};proof['isolated_module_package']=True;p.write_text(json.dumps(proof,indent=2)+'\n')
shutil.copytree(new,R/'dist/player-only-source/generated-adapter',dirs_exist_ok=True)
assert not old.exists() and len(proof['generated_sha256'])==5
print('Test adapter now has its own Java module package; original private checks are invoked exactly')
