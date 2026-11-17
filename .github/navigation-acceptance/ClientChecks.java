package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.*;
import dev.unyielding.quests.client.edit.PromptScreen;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.team.Team;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Actual rendered screens and native input; reflection observes state/hit bounds only. */
@EventBusSubscriber(modid="uq_bundle_smoke",value=Dist.CLIENT)
public final class ClientChecks {
    static final Path ROOT=Path.of("/tmp/uq-navigation-controls");
    static final List<String> actions=new ArrayList<>(),messages=new ArrayList<>(),captures=new ArrayList<>();
    static final Deque<Step> steps=new ArrayDeque<>();
    static String name="",seen="";
    static String[] pending;
    static int tick,sentAt,generation,delay;
    static boolean barrierSent;
    static Object oldPlayer;
    interface Body{void run()throws Exception;}
    record Step(String description,int after,Body body){}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static void log(String text)throws Exception{Files.writeString(ROOT.resolve(name+".events.log"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    static void require(boolean ok,String message){BundleSmoke.require(ok,message);}
    static Object field(Object target,String name)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static int number(Object target,String name)throws Exception{return((Number)field(target,name)).intValue();}
    static void queue(String text,Body body){steps.add(new Step(text,12,body));}
    static void queue(String text,int after,Body body){steps.add(new Step(text,after,body));}
    static Minecraft mc(){return Minecraft.getInstance();}
    static Screen screen(){return Objects.requireNonNull(mc().screen,"Native screen is closed");}
    static <T extends Screen>T screen(Class<T> type){require(type.isInstance(screen()),"Expected "+type.getSimpleName()+", got "+screen().getClass().getName());return type.cast(screen());}
    static Button button(String label) {
        return screen().children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(label)).map(x->(Button)x).findFirst().orElseThrow(()->new IllegalStateException("Missing native button "+label));
    }
    static void clickWidget(AbstractWidget widget)throws Exception {
        require(widget.visible&&widget.active,"Native widget unavailable: "+widget.getMessage().getString());
        int x=widget.getX()+widget.getWidth()/2,y=widget.getY()+widget.getHeight()/2;
        require(x>=0&&y>=0&&x<screen().width&&y<screen().height,"Widget is outside actual viewport");
        require(screen().mouseClicked(x,y,0),"Native mouse click was not consumed");screen().mouseReleased(x,y,0);
    }
    static void click(String label)throws Exception{clickWidget(button(label));}
    static void input(String text)throws Exception {
        EditBox box=screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();clickWidget(box);
        // Delete using the real key path, then type into the actual focused field.
        box.moveCursorToEnd(false);int length=box.getValue().length();for(int i=0;i<length;i++)screen().keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);
        require(box.getValue().isEmpty(),"Backspace did not clear the native field");
        for(char c:text.toCharArray())require(screen().charTyped(c,0),"Native text input was not accepted");
        require(box.getValue().equals(text),"Typed native field value differs");
    }
    static void openTeams(){ClientHandlers.onOpen(new Net.OpenScreen(1));}
    static void openQuests(){QuestScreen.editMode=false;ClientHandlers.onOpen(new Net.OpenScreen(0));}
    static void renderedQuestClick(int x,int y,int w,int h)throws Exception {
        var q=screen(QuestScreen.class);List<?> hits=(List<?>)field(q,"hits");Object match=null;
        for(Object hit:hits)if(number(hit,"x0")==x&&number(hit,"y0")==y&&number(hit,"x1")==x+w&&number(hit,"y1")==y+h)match=hit;
        require(match!=null,"Expected enabled native rendered hit at "+x+","+y+" "+w+"x"+h);
        require(q.mouseClicked(x+w/2.0,y+h/2.0,0),"Native immediate-mode click was not consumed");q.mouseReleased(x+w/2.0,y+h/2.0,0);
    }
    static int sidebarBottom()throws Exception {
        var q=screen(QuestScreen.class);int bottom=q.height-4;if(mc().player.hasPermissions(2))bottom-=24;
        if(ClientData.myTeam()!=null&&ClientData.myTeam().type==Team.Type.PARTY&&ClientData.myTeam().pinnedQuest!=0)bottom-=22;return bottom-44;
    }
    static void searchButton()throws Exception{var q=screen(QuestScreen.class);int side=number(q,"side");renderedQuestClick(6,sidebarBottom(),(side-16)/2,18);}
    static void nextButton()throws Exception{var q=screen(QuestScreen.class);int side=number(q,"side");renderedQuestClick(10+(side-16)/2,sidebarBottom(),(side-16)/2,18);}
    static void statusButton()throws Exception{var q=screen(QuestScreen.class);renderedQuestClick(6,sidebarBottom()+22,number(q,"side")-12,18);}
    static void goalButton()throws Exception{var q=screen(QuestScreen.class);require(ClientData.myTeam().pinnedQuest!=0,"Party goal shortcut missing");renderedQuestClick(6,sidebarBottom()+44,number(q,"side")-12,18);}
    static void closeDetail()throws Exception {
        var q=screen(QuestScreen.class);require(field(q,"selected")!=null,"No native quest detail to close");int pw=Math.min(340,q.width-24),px=(q.width-pw)/2;
        renderedQuestClick(px+pw-24,21,16,16);
    }
    static void pinButton()throws Exception {
        var q=screen(QuestScreen.class);int pw=Math.min(340,q.width-24),px=(q.width-pw)/2;
        renderedQuestClick(px+pw-75,21,47,16);
    }
    static QuestFile.Quest selected()throws Exception{return(QuestFile.Quest)field(screen(QuestScreen.class),"selected");}
    static void expectSelected(long id,String status)throws Exception {
        var q=selected();require(q!=null&&q.id==id,"Selected native quest expected "+id+", got "+(q==null?"none":q.id));
        require(field(screen(),"chapter")==ClientData.file.chapterMap.get(q.chapter),"Native search selected wrong chapter");
        var p=ClientData.progress;String observed=p.questDone(q.id)?(q.rewards.stream().anyMatch(r->!p.isClaimed(mc().player.getUUID(),r.id))||ClientData.spinAvailable(q,mc().player.getUUID())?"CLAIM":"DONE"):!dev.unyielding.quests.quest.QuestManager.available(ClientData.file,q,p)?"LOCKED":q.tasks.stream().anyMatch(t->p.taskDone(t.id)||p.progress(t.id)>0)?"STARTED":"AVAILABLE";
        require(observed.equals(status),"Native selected quest status expected "+status+", got "+observed);actions.add("observed detail "+id+" "+status+" chapter="+q.chapter);
        require(((Number)field(screen(),"detailAnim")).floatValue()>.85f,"Detail was not rendered interactively");
    }
    static List<String> visibleMembers()throws Exception {
        var s=screen(TeamScreen.class);String query=((String)field(s,"memberSearch")).trim().toLowerCase(Locale.ROOT);int size=pageSize(),page=number(s,"memberPage");
        return ClientData.myTeam().members.keySet().stream().map(ClientData.myTeam()::nameOf).filter(n->n.toLowerCase(Locale.ROOT).contains(query)).skip((long)page*size).limit(size).toList();
    }
    static int pageSize()throws Exception {var s=screen(TeamScreen.class);return Math.max(1,Math.min(5,(number(s,"py")+number(s,"ph")-44-(number(s,"top")+52))/20));}
    static void roster(int page,List<String> names,boolean previous,boolean next)throws Exception {
        var s=screen(TeamScreen.class);require(pageSize()==5,"Expected actual 512x384 GUI with five roster rows");require(number(s,"memberPage")==page,"Roster page differs");
        require(visibleMembers().equals(names),"Visible roster differs: "+visibleMembers());require(button("<").active==previous&&button(">").active==next,"Roster boundary button state differs");
        actions.add("observed roster page="+(page+1)+" members="+names+" previous="+previous+" next="+next);
    }
    static void snapshot(String file) {
        queue("capture actual framebuffer "+file,4,()->{
            require(screen().width==512&&screen().height==384,"Actual GUI viewport differs: "+screen().width+"x"+screen().height);
            Screenshot.grab(Path.of(System.getenv("UQ_NAVIGATION_EVIDENCE")).toFile(),file+".png",mc().getMainRenderTarget(),c->{});captures.add(file+".png");
        });
    }
    static void search(String query) {
        queue("click native Search",ClientChecks::searchButton);
        queue("type actual quest search prompt: "+query,()->{screen(PromptScreen.class);input(query);});
        queue("click prompt Save",()->click("Save"));
    }
    static void searchAndExpect(String query,long id,String status) {
        search(query);queue("verify rendered matching quest "+id,()->expectSelected(id,status));
    }
    static void begin(String[] a)throws Exception {
        actions.clear();messages.clear();captures.clear();steps.clear();barrierSent=false;ClientData.notice="";
        require(ClientData.file!=null&&ClientData.file.quests.containsKey(108L),"Synthetic native quest book has not arrived");
        require(ClientData.myTeam()!=null&&ClientData.myTeam().members.size()==14,"Synthetic native party has not arrived");
        require(!ClientData.shareQuestProgress&&ClientData.progressTeam.equals(mc().player.getUUID()),"Fixture requires separate personal quest progress");
        require(mc().getWindow().getGuiScaledWidth()==512&&mc().getWindow().getGuiScaledHeight()==384,"Harness viewport must be 1024x768 GUI2");
        switch(a[1]) {
            case "roster_pages" -> {
                queue("open native Teams screen",ClientChecks::openTeams);
                queue("verify first page and disabled previous",()->roster(0,List.of("Dev","Visitor","Offline01","Offline02","Offline03"),false,true));snapshot("01-roster-first");
                queue("click actual next page",()->click(">"));queue("verify middle page",()->roster(1,List.of("Offline04","Offline05","Offline06","Offline07","Offline08"),true,true));
                queue("click actual final page",()->click(">"));queue("verify last page and disabled next",()->roster(2,List.of("Offline09","Offline10","Offline11","Offline12"),true,false));snapshot("01-roster-last");
                queue("disabled last-page arrow consumes no navigation",()->{var b=button(">");screen().mouseClicked(b.getX()+10,b.getY()+10,0);require(number(screen(),"memberPage")==2,"Disabled next moved page");});
            }
            case "roster_search" -> {
                queue("open native Teams",ClientChecks::openTeams);queue("move to middle page",()->click(">"));queue("move to last page",()->click(">"));
                queue("type trimmed mixed-case member query",()->input(" oFfLiNe1 "));queue("click native Find",()->click("Find"));
                queue("verify search reset and matches",()->roster(0,List.of("Offline10","Offline11","Offline12"),false,false));snapshot("02-roster-search");
                queue("clear real member text field",()->input(""));queue("click Find to restore full roster",()->click("Find"));queue("verify original roster restored",()->roster(0,List.of("Dev","Visitor","Offline01","Offline02","Offline03"),false,true));
            }
            case "roster_empty" -> {
                queue("open ordinary member native Teams",ClientChecks::openTeams);
                queue("verify native role and absence of destructive management buttons",()->{require(ClientData.myTeam().rankOf(mc().player.getUUID())==Team.Rank.MEMBER,"Expected ordinary member");for(String forbidden:List.of("Kick","Owner","Promote","Demote","Disband"))require(screen().children().stream().noneMatch(x->x instanceof Button b&&b.getMessage().getString().equals(forbidden)),"Member sees management control "+forbidden);});
                queue("type no-match roster query",()->input("NobodyMatches"));queue("click actual Find",()->click("Find"));queue("verify empty result and disabled pagination",()->roster(0,List.of(),false,false));snapshot("03-roster-no-match");
            }
            case "quest_search" -> {
                queue("open ordinary member native Quests",ClientChecks::openQuests);searchAndExpect("  nAvIgAtIoN  ",101,"LOCKED");
                long[] ids={102,103,104,105,106,101};String[] status={"AVAILABLE","STARTED","DONE","CLAIM","AVAILABLE","LOCKED"};
                for(int i=0;i<ids.length;i++){long id=ids[i];String st=status[i];queue("close native detail before Next",ClientChecks::closeDetail);queue("click native Next",ClientChecks::nextButton);queue("verify visible result "+id,()->expectSelected(id,st));if(id==106)snapshot("04-quest-cross-chapter");}
                queue("hidden personal quest was excluded",()->require(!actions.stream().anyMatch(s->s.startsWith("observed detail 107 ")),"Hidden quest leaked into search"));
            }
            case "quest_status" -> {
                queue("open fresh native Quests",ClientChecks::openQuests);searchAndExpect("Navigation",101,"LOCKED");
                long[] ids={101,102,103,104,105,101};String[] status={"LOCKED","AVAILABLE","STARTED","DONE","CLAIM","LOCKED"};
                for(int i=0;i<ids.length;i++){int filter=(i+1)%6;long id=ids[i];String st=status[i];queue("close native detail",ClientChecks::closeDetail);queue("cycle actual Status button to "+filter,ClientChecks::statusButton);queue("verify filter state and click Next",()->{require(number(screen(),"searchStatus")==filter,"Status cycle differs");nextButton();});queue("verify filtered native detail "+st,()->expectSelected(id,st));
                    if(filter==2){queue("close AVAILABLE result",ClientChecks::closeDetail);queue("Next within AVAILABLE only",ClientChecks::nextButton);queue("AVAILABLE crosses chapter",()->expectSelected(106,"AVAILABLE"));queue("close second AVAILABLE result",ClientChecks::closeDetail);queue("Next wraps within AVAILABLE",ClientChecks::nextButton);queue("AVAILABLE wrap excludes other statuses",()->expectSelected(102,"AVAILABLE"));}
                    if(filter==3){queue("close detail to show full status selector",ClientChecks::closeDetail);snapshot("05-status-started");queue("reopen only STARTED match with Next",ClientChecks::nextButton);queue("verify STARTED still isolated",()->expectSelected(103,"STARTED"));}
                    if(filter==5)snapshot("05-status-claim");}
            }
            case "pin_available" -> {
                queue("open owner native Quests",ClientChecks::openQuests);searchAndExpect("Navigation Available",102,"AVAILABLE");queue("click actual Pin",ClientChecks::pinButton);
            }
            case "open_available_goal" -> {
                queue("open member native Quests",ClientChecks::openQuests);queue("verify synchronized pinned metadata",()->require(ClientData.myTeam().pinnedQuest==102,"Member did not receive pinned goal"));queue("click native Party goal",ClientChecks::goalButton);queue("verify informational available detail",()->expectSelected(102,"AVAILABLE"));snapshot("06-member-party-goal");
            }
            case "member_repin" -> {
                queue("open normal member Quests",ClientChecks::openQuests);searchAndExpect("Navigation Started",103,"STARTED");queue("normal MEMBER clicks actual Pin",()->{require(ClientData.myTeam().rankOf(mc().player.getUUID())==Team.Rank.MEMBER,"Expected ordinary member");pinButton();});
            }
            case "observe_member_goal" -> {
                queue("open owner Quests",ClientChecks::openQuests);queue("verify member pin synchronized to owner",()->require(ClientData.myTeam().pinnedQuest==103,"Owner did not receive normal member pin"));queue("click owner Party goal",ClientChecks::goalButton);queue("verify owner's distinct personal counter still used",()->{expectSelected(103,"STARTED");require(ClientData.progress.progress(1103)==4,"Goal showed another member's progress");});snapshot("07-owner-member-pinned-goal");
            }
            case "member_unpin" -> {
                queue("open member Quests",ClientChecks::openQuests);queue("open existing Party goal",ClientChecks::goalButton);queue("verify member's own counter",()->{expectSelected(103,"STARTED");require(ClientData.progress.progress(1103)==2,"Goal merged personal task counts");});queue("normal MEMBER clicks actual Unpin",ClientChecks::pinButton);
            }
            case "observe_no_goal" -> {
                queue("open native Quests after unpin",ClientChecks::openQuests);queue("verify shortcut is gone",()->{require(ClientData.myTeam().pinnedQuest==0,"Owner retained stale goal metadata");var q=screen(QuestScreen.class);int side=number(q,"side"),y=sidebarBottom()+44;for(Object hit:(List<?>)field(q,"hits"))require(!(number(hit,"x0")==6&&number(hit,"y0")==y&&number(hit,"x1")==side-6),"Unpinned goal retained clickable shortcut");});
            }
            case "pin_hidden" -> {
                queue("open owner Quests with completed personal prerequisite",ClientChecks::openQuests);searchAndExpect("Navigation Hidden",107,"AVAILABLE");queue("owner pins personally visible hidden quest",ClientChecks::pinButton);
            }
            case "hidden_goal" -> {
                queue("open viewer without personal prerequisite",ClientChecks::openQuests);queue("click synchronized Party goal",ClientChecks::goalButton);
                queue("verify personal visibility refusal",()->{require(selected()==null,"Hidden goal opened detail");require(ClientData.notice.equals("Party goal is unavailable to you."),"Hidden goal refusal missing");});snapshot("08-hidden-goal-unavailable");
                search("Navigation Hidden");queue("verify hidden quest is absent from title search",()->{require(selected()==null&&ClientData.notice.equals("No matching visible quests."),"Hidden quest leaked into title search");});snapshot("08-hidden-search-empty");
                queue("declared forged hidden-for-actor pin packet boundary",()->Net.sendToServer(new Net.TeamAction("pin_quest","107","")));
            }
            case "unpin_hidden" -> {
                queue("open owner Quests",ClientChecks::openQuests);queue("click personally visible pinned goal",ClientChecks::goalButton);queue("verify owner can open same hidden goal",()->expectSelected(107,"AVAILABLE"));queue("owner clicks native Unpin",ClientChecks::pinButton);
            }
            default -> throw new IllegalArgumentException("Unknown navigation action "+a[1]);
        }
    }
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event){if(!event.isOverlay())messages.add(event.getMessage().getString());}
    static boolean screenshotsReady()throws Exception {
        Path folder=Path.of(System.getenv("UQ_NAVIGATION_EVIDENCE")).resolve("screenshots");
        for(String capture:captures){Path f=folder.resolve(capture);if(!Files.isRegularFile(f)||Files.size(f)<64)return false;}return true;
    }
    static void completed()throws Exception {
        if(pending[1].equals("hidden_goal"))require(ClientData.notice.equals("That quest is hidden.")&&messages.stream().anyMatch(s->s.equals("That quest is hidden.")),"Server did not explicitly reject the hidden-for-actor pin packet");
        var o=new CompoundTag();o.putString("screen",screen().getClass().getName());o.putInt("guiWidth",screen().width);o.putInt("guiHeight",screen().height);o.putBoolean("shareQuestProgress",ClientData.shareQuestProgress);o.putUUID("progressTeam",ClientData.progressTeam);o.putLong("pinnedQuest",ClientData.myTeam().pinnedQuest);o.putString("notice",ClientData.notice);o.putString("role",ClientData.myTeam().rankOf(mc().player.getUUID()).name());
        o.put("progress",ClientData.progress.toTag(mc().player.getUUID()));
        if(screen() instanceof TeamScreen){o.putInt("memberPage",number(screen(),"memberPage"));o.putString("memberSearch",(String)field(screen(),"memberSearch"));o.putString("visibleMembers",visibleMembers().toString());}
        if(screen() instanceof QuestScreen){var q=selected();o.putLong("selectedQuest",q==null?0:q.id);o.putInt("statusFilter",number(screen(),"searchStatus"));}
        var text=new ListTag();for(String action:actions)text.add(StringTag.valueOf(action));o.put("nativeActions",text);var shots=new ListTag();for(String capture:captures)shots.add(StringTag.valueOf(capture));o.put("screenshots",shots);
        var chat=new ListTag();for(String message:messages)chat.add(StringTag.valueOf(message));o.put("messages",chat);
        write(ROOT.resolve(name+".observation."+pending[0]+".snbt"),o.toString());log(pending[0]+"\tordered server receipt\t"+o);write(ROOT.resolve(name+".ack"),pending[0]+"\tpassed");pending=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        var mc=mc();tick++;
        try {
            if(mc.player==null||mc.level==null)return;name=mc.player.getGameProfile().getName();if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            if(oldPlayer!=mc.player){oldPlayer=mc.player;generation++;}if(mc.player.tickCount<100)return;
            Files.createDirectories(ROOT);write(ROOT.resolve(name+".ready"),mc.player.getUUID()+"\t"+generation);
            if(pending!=null) {
                require(tick-sentAt<1100,"Native navigation action timed out");
                if(delay>0){delay--;return;}
                if(!steps.isEmpty()){var s=steps.remove();actions.add(s.description());log(pending[0]+"\t"+s.description());s.body().run();delay=s.after();return;}
                if(!barrierSent){Net.sendToServer(new BundleSmoke.Barrier(pending[0]));barrierSent=true;return;}
                if(BundleSmoke.RECEIPTS.contains(pending[0])&&screenshotsReady()){BundleSmoke.RECEIPTS.remove(pending[0]);completed();}return;
            }
            Path f=ROOT.resolve(name+".txt");if(!Files.exists(f))return;String value=Files.readString(f);if(value.equals(seen))return;
            String[] a=value.split("\t",-1);require(a.length==2,"Malformed navigation request");seen=value;pending=a;sentAt=tick;begin(a);
        }catch(Throwable bad){bad.printStackTrace();try{String id=pending==null?"0":pending[0];log(id+"\tfailed\t"+bad);write(ROOT.resolve(name+".ack"),id+"\tfailed: "+bad);}catch(Exception ignored){}finally{pending=null;steps.clear();delay=0;}}
    }
}
