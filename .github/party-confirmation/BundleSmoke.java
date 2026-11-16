package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.spin.DailyLogin;
import dev.unyielding.quests.team.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Independent destructive hosted-only fixture; all production comes from the assembled JAR. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static MinecraftServer server;
    static final Path CONTROL = Path.of("/tmp/uq-party-controls");
    static final Path RESULT = Path.of("/tmp/uq-party-confirmation-result");
    static UUID partyId, ownerId, visitorId, unrelated;
    static CompoundTag unrelatedBefore;
    static final long QUEST = 991001, TASK = 991002, REWARD = 991003;
    static int ticks, stepStart, sequence;
    static boolean initialized, done;
    record Step(String name, Runnable start, BooleanSupplier ready, Runnable check) {}
    static final Deque<Step> steps = new ArrayDeque<>();
    public BundleSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static void ok(String error) { require(error == null, "action rejected: " + error); }
    static void denied(String error) { require(error != null, "unsafe action succeeded"); }
    static ServerPlayer p() { return server.getPlayerList().getPlayerByName("Dev"); }
    static ServerPlayer v() { return server.getPlayerList().getPlayerByName("Visitor"); }
    static Team party() { return TeamManager.data().teams.get(partyId); }
    static QuestData quests() { return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME); }
    static Map<UUID, Object> pending() {
        try { var field = TeamManager.class.getDeclaredField("pending"); field.setAccessible(true); return (Map<UUID,Object>)field.get(null); }
        catch (Exception bad) { throw new IllegalStateException(bad); }
    }
    static void unchanged() {
        require(party() != null && ownerId.equals(party().owner), "party destroyed or owner seized");
        require(unrelatedBefore.equals(TeamManager.data().teams.get(unrelated).toTag(true)), "unrelated party changed");
        require(quests().isClaimed(ownerId, REWARD) && !quests().isClaimed(visitorId, REWARD), "personal claims changed");
        require(p().experienceLevel == 3 && v().experienceLevel == 0, "earned XP changed or leaked");
        var balances = server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(), DailyLogin.Data.NAME);
        require(balances.players.get(ownerId)[3] == 11 && balances.players.get(visitorId)[3] == 4, "earned spin balances changed");
    }
    static void direct(String name, Runnable action) { steps.add(new Step(name, action, () -> true, BundleSmoke::unchanged)); }
    static void join() { ok(TeamManager.invite(p(), "Visitor")); ok(TeamManager.join(v(), partyId.toString())); }
    static void transferRequest() { ok(TeamManager.setRank(p(), "Visitor", Team.Rank.OWNER)); }
    static void confirm() { ok(TeamManager.confirm(p(), partyId.toString(), visitorId.toString())); }
    static void send(String player, String type, String a, String b) {
        try { sequence++; Files.writeString(CONTROL.resolve(player + ".txt"), sequence + "\t" + type + "\t" + a + "\t" + b); }
        catch (Exception bad) { throw new IllegalStateException(bad); }
    }
    static boolean ack(String player) {
        try { return Files.exists(CONTROL.resolve(player + ".ack")) && Files.readString(CONTROL.resolve(player + ".ack")).equals("" + sequence); }
        catch (Exception bad) { throw new IllegalStateException(bad); }
    }
    static void client(String name, String player, String type, String a, String b, Runnable check) {
        steps.add(new Step(name, () -> send(player, type, a, b), () -> ack(player), check));
    }
    static void expire() {
        try {
            Object old = pending().get(ownerId); var type = old.getClass();
            var components = type.getRecordComponents(); Object[] args = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
            for (int i=0;i<components.length;i++) { var accessor=components[i].getAccessor(); accessor.setAccessible(true); args[i]=accessor.invoke(old); types[i]=components[i].getType(); }
            args[args.length-1] = System.nanoTime()-1;
            var ctor=type.getDeclaredConstructor(types);ctor.setAccessible(true);pending().put(ownerId,ctor.newInstance(args));
        } catch (Exception bad) { throw new IllegalStateException(bad); }
    }
    static void configureBook() {
        var book = new QuestFile(); var q = new QuestFile.Quest(); q.id=QUEST;
        var task=new QuestFile.Task();task.id=TASK;task.quest=QUEST;task.type=QuestFile.TaskType.CHECKMARK;
        var reward=new QuestFile.Reward();reward.id=REWARD;reward.quest=QUEST;reward.type=QuestFile.RewardType.XP_LEVELS;reward.amount=3;
        q.tasks.add(task);q.rewards.add(reward);book.quests.put(QUEST,q);book.tasks.put(TASK,task);book.rewards.put(REWARD,reward);QuestManager.file=book;
    }
    static void phaseOne() {
        ok(TeamManager.createParty(p(), "Confirmation Test")); partyId=TeamManager.teamOf(ownerId).id;
        QuestManager.onTaskAction(p(), new Net.TaskAction(TASK,0)); QuestManager.onClaim(p(),new Net.Claim(REWARD,0));
        join(); require(!QuestManager.progressOf(v()).questDone(QUEST), "off-mode join inherited completion");
        QuestManager.onClaim(v(),new Net.Claim(REWARD,0));
        var balances=server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);
        balances.players.computeIfAbsent(ownerId,k->new long[5])[3]=11;balances.players.computeIfAbsent(visitorId,k->new long[5])[3]=4;balances.setDirty();
        var other=new Team();other.id=UUID.randomUUID();other.type=Team.Type.PARTY;other.name="Unrelated fixture";other.owner=UUID.randomUUID();other.members.put(other.owner,Team.Rank.OWNER);
        unrelated=other.id;TeamManager.data().teams.put(unrelated,other);unrelatedBefore=other.toTag(true);
        direct("unauthorized member",()->{denied(TeamManager.disband(v()));denied(TeamManager.setRank(v(),"Dev",Team.Rank.OWNER));denied(TeamManager.confirm(v(),"",""));});
        direct("cancelled and repeated confirm",()->{ok(TeamManager.disband(p()));ok(TeamManager.cancelConfirmation(p()));denied(TeamManager.confirm(p(),"",""));denied(TeamManager.confirm(p(),"",""));});
        direct("expired deadline",()->{transferRequest();expire();denied(TeamManager.confirm(p(),"",""));});
        direct("membership changed",()->{transferRequest();ok(TeamManager.leave(v()));denied(TeamManager.confirm(p(),"",""));join();});
        direct("ownership changed",()->{transferRequest();party().members.put(ownerId,Team.Rank.MEMBER);party().members.put(visitorId,Team.Rank.OWNER);party().owner=visitorId;denied(TeamManager.confirm(p(),"",""));party().members.put(ownerId,Team.Rank.OWNER);party().members.put(visitorId,Team.Rank.MEMBER);party().owner=ownerId;});
        direct("admin permission revoked",()->{server.getPlayerList().op(p().getGameProfile());ok(TeamManager.adminDelete(p(),party().name));server.getPlayerList().deop(p().getGameProfile());denied(TeamManager.confirm(p(),"",""));});
        direct("competing requests mismatch",()->{transferRequest();ok(TeamManager.disband(p()));denied(TeamManager.confirm(p(),partyId.toString(),visitorId.toString()));denied(TeamManager.confirm(p(),"",""));});
        client("real UI request", "Dev", "ui_transfer", visitorId.toString(), "", ()->require(pending().containsKey(ownerId),"UI request never reached server"));
        client("real UI cancellation", "Dev", "ui_no", "", "", ()->{require(!pending().containsKey(ownerId),"UI cancellation left request live");unchanged();});
        client("network disband request", "Dev", "disband", "", "", ()->require(pending().containsKey(ownerId),"network request missing"));
        client("network replacement transfer", "Dev", "transfer", "Visitor", "", ()->require(pending().containsKey(ownerId),"replacement request missing"));
        client("stale disband confirmation", "Dev", "confirm", partyId.toString(), "", BundleSmoke::unchanged);
        client("new UI transfer", "Dev", "ui_transfer", visitorId.toString(), "", ()->require(pending().containsKey(ownerId),"UI transfer request missing"));
        client("competitor cannot confirm", "Visitor", "confirm", partyId.toString(), visitorId.toString(), BundleSmoke::unchanged);
        client("real UI successful transfer", "Dev", "ui_yes", "", "", ()->{require(visitorId.equals(party().owner),"UI ownership transfer failed");require(party().rankOf(ownerId)==Team.Rank.OFFICER,"former owner rank changed incorrectly");});
        client("replayed transfer confirmation", "Dev", "confirm", partyId.toString(), visitorId.toString(), ()->require(visitorId.equals(party().owner),"replay changed owner"));
        direct("restore owner through legitimate confirmation",()->{ok(TeamManager.setRank(v(),"Dev",Team.Rank.OWNER));ok(TeamManager.confirm(v(),partyId.toString(),ownerId.toString()));});
        client("request before disconnect", "Dev", "transfer", "Visitor", "", ()->require(pending().containsKey(ownerId),"disconnect test lacks pending request"));
        steps.add(new Step("actual disconnect/reconnect",()->send("Dev","reconnect","",""),()->ack("Dev")&&p()!=null,()->require(!pending().containsKey(ownerId),"confirmation survived actual disconnect/reconnect")));
        client("stale confirmation after reconnect", "Dev", "confirm", partyId.toString(), visitorId.toString(), BundleSmoke::unchanged);
        direct("leave pending across saved restart",()->{ok(TeamManager.disband(p()));require(pending().containsKey(ownerId),"restart request missing");server.getPlayerList().saveAll();server.saveEverything(true,true,true);});
    }
    static void phaseTwo() {
        var party=TeamManager.teamOf(ownerId);partyId=party.id;
        unrelated=TeamManager.data().teams.values().stream().filter(t->t.name.equals("Unrelated fixture")).findFirst().orElseThrow().id;
        unrelatedBefore=TeamManager.data().teams.get(unrelated).toTag(true);
        direct("restart clears pending",()->{require(pending().isEmpty(),"confirmation survived restart");denied(TeamManager.confirm(p(),"",""));});
        direct("off sharing persisted",()->{require(QuestManager.progressOf(p()).questDone(QUEST),"owner progress lost");require(!QuestManager.progressOf(v()).questDone(QUEST),"visitor inherited progress after restart");QuestManager.onClaim(p(),new Net.Claim(REWARD,0));QuestManager.onClaim(v(),new Net.Claim(REWARD,0));});
        // Exercise sharing-enabled disband separately; claims remain personal.
        direct("enable configured sharing",()->{Config.SHARE_QUEST_PROGRESS.set(true);QuestManager.onSharingChanged(true);});
        client("disband request", "Dev", "ui_disband", "", "", ()->require(pending().containsKey(ownerId),"UI disband missing"));
        client("successful disband", "Dev", "ui_yes", "", "", ()->{
            require(party()==null,"party not deleted");require(TeamManager.teamIdOf(ownerId).equals(ownerId)&&TeamManager.teamIdOf(visitorId).equals(visitorId),"personal teams not restored");
            require(QuestManager.progressOf(p()).questDone(QUEST)&&QuestManager.progressOf(v()).questDone(QUEST),"enabled sharing failed to preserve progress");
            require(quests().isClaimed(ownerId,REWARD)&&!quests().isClaimed(visitorId,REWARD),"disband changed personal claims");
            require(p().experienceLevel==3&&v().experienceLevel==0,"disband gave rewards automatically");
        });
        client("replayed disband", "Dev", "confirm", partyId.toString(), "", ()->{require(party()==null,"deleted party reappeared");require(unrelatedBefore.equals(TeamManager.data().teams.get(unrelated).toTag(true)),"unrelated party changed");server.getPlayerList().saveAll();server.saveEverything(true,true,true);});
    }
    void tick(ServerTickEvent.Post e) {
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        server=e.getServer(); ticks++;
        try {
            if(!initialized){if(p()==null||v()==null||ticks<100)return;initialized=true;
                require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"wrong production source");
                require(server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"integrated jail returned");
                Config.SPIN_ENABLED.set(false);Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);configureBook();
                ownerId=p().getUUID();visitorId=v().getUUID();Files.createDirectories(CONTROL);
                if("1".equals(System.getenv("UQ_PARTY_PHASE")))phaseOne();else phaseTwo();stepStart=0;
            }
            if(steps.isEmpty()){done=true;Files.writeString(RESULT,"passed: party confirmation phase "+System.getenv("UQ_PARTY_PHASE")+"\n");return;}
            var step=steps.peek();
            if(stepStart==0){System.out.println("PARTY CASE START: "+step.name());step.start().run();stepStart=ticks;}
            if(ticks-stepStart>900)throw new IllegalStateException("timeout: "+step.name());
            if(ticks-stepStart<30||!step.ready().getAsBoolean())return;
            step.check().run();System.out.println("PARTY CASE PASS: "+step.name());steps.remove();stepStart=0;
        } catch(Throwable bad){done=true;bad.printStackTrace();try{Files.writeString(RESULT,"failed: "+(steps.isEmpty()?"startup":steps.peek().name())+": "+bad+"\n");}catch(Exception ignored){}}
    }
}
