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
    static final Path ROOT=Path.of("/tmp/uq-compact-controls");
    static final List<String> actions=new ArrayList<>(),messages=new ArrayList<>(),captures=new ArrayList<>();
    static final Deque<Step> steps=new ArrayDeque<>();
    static String name="",seen="";
    static String[] pending;
    static int tick,sentAt,generation,delay;
    static boolean barrierSent,uiStarted;
    static int toastQuiet;
    static final List<String> defects=new ArrayList<>();
    static final ListTag geometry=new ListTag();
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
        var s=screen(TeamScreen.class);require(pageSize()==3,"Expected actual compact GUI with three roster rows");require(number(s,"memberPage")==page,"Roster page differs");
        require(visibleMembers().equals(names),"Visible roster differs: "+visibleMembers());require(button("<").active==previous&&button(">").active==next,"Roster boundary button state differs");
        actions.add("observed roster page="+(page+1)+" members="+names+" previous="+previous+" next="+next);
    }
    static void snapshot(String file) {
        queue("capture actual framebuffer "+file,4,()->{
            require(screen().width==320&&screen().height==240,"Actual compact GUI differs: "+screen().width+"x"+screen().height);
            require(toastQuiet>=20,"Capture attempted before native notifications cleared naturally");
            Screenshot.grab(Path.of(System.getenv("UQ_COMPACT_EVIDENCE")).toFile(),file+".png",mc().getMainRenderTarget(),c->{});captures.add(file+".png");
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
    record Rect(String label,int x,int y,int w,int h) {
        boolean intersects(Rect b){return x<b.x+b.w&&x+w>b.x&&y<b.y+b.h&&y+h>b.y;}
    }
    static void fault(String message){if(!defects.contains(message))defects.add(message);}
    static void recordRects(String stage,List<Rect> rects,boolean disjoint)throws Exception {
        var entry=new CompoundTag();entry.putString("stage",stage);var rows=new ListTag();
        for(var r:rects) {
            var row=new CompoundTag();row.putString("label",r.label());row.putIntArray("bounds",new int[]{r.x(),r.y(),r.w(),r.h()});rows.add(row);
            if(r.w()<=0||r.h()<=0||r.x()<0||r.y()<0||r.x()+r.w()>screen().width||r.y()+r.h()>screen().height)fault(stage+": out-of-bounds "+r);
        }
        if(disjoint)for(int i=0;i<rects.size();i++)for(int j=i+1;j<rects.size();j++)if(rects.get(i).intersects(rects.get(j)))fault(stage+": overlapping native controls "+rects.get(i)+" / "+rects.get(j));
        entry.put("rectangles",rows);geometry.add(entry);
    }
    static void auditOwnerWidgets(String stage)throws Exception {
        List<Rect> rects=new ArrayList<>();
        for(var child:screen().children())if(child instanceof AbstractWidget widget&&widget.visible) {
            String label=widget instanceof EditBox?"Text field":widget.getMessage().getString();
            rects.add(new Rect(label,widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight()));
            boolean iconOnly=widget instanceof dev.unyielding.quests.client.ui.SpriteButton&&(boolean)field(widget,"iconOnly");
            if(widget instanceof Button&&!iconOnly&&mc().font.width(label)>widget.getWidth()-4)fault(stage+": button label does not fit "+label+" in "+widget.getWidth());
        }
        recordRects(stage,rects,true);
    }
    static void auditQuestControls(String stage,boolean detail)throws Exception {
        var q=screen(QuestScreen.class);List<Rect> rects=new ArrayList<>();
        if(detail) {
            int pw=Math.min(340,q.width-24),px=(q.width-pw)/2;
            rects.add(new Rect("Pin/Unpin",px+pw-75,21,47,16));rects.add(new Rect("Close",px+pw-24,21,16,16));
            var quest=selected();int titleWidth=pw-44-30-48;
            if(mc().font.width(quest.displayTitle())>titleWidth)actions.add("native detail title deliberately elides to "+titleWidth+" pixels");
        } else {
            int side=number(q,"side"),bottom=sidebarBottom();rects.add(new Rect("Search",6,bottom,(side-16)/2,18));rects.add(new Rect("Next",10+(side-16)/2,bottom,(side-16)/2,18));rects.add(new Rect("Status: STARTED",6,bottom+22,side-12,18));
            if(mc().font.width("Status: STARTED")>side-16)fault(stage+": full STARTED filter label does not fit");
        }
        recordRects(stage,rects,true);
        for(Rect r:rects){boolean present=false;for(Object hit:(List<?>)field(q,"hits"))if(number(hit,"x0")==r.x()&&number(hit,"y0")==r.y()&&number(hit,"x1")==r.x()+r.w()&&number(hit,"y1")==r.y()+r.h())present=true;if(!present)fault(stage+": expected actual rendered clickable control missing "+r.label());}
    }
    static int[] toastCounts()throws Exception {
        Object component=mc().getToasts();return new int[]{((Collection<?>)field(component,"visible")).size(),((Collection<?>)field(component,"queued")).size()};
    }
    static boolean nativePartyReady() {
        var party=com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty();return party!=null&&party.getSlots().stream().filter(Objects::nonNull).count()==1;
    }
    static void begin(String[] a)throws Exception {
        actions.clear();messages.clear();captures.clear();defects.clear();geometry.clear();steps.clear();barrierSent=false;uiStarted=false;toastQuiet=0;mc().setScreen(null);
        require(ClientData.file!=null&&ClientData.file.quests.containsKey(108L),"Synthetic native quest book has not arrived");
        require(ClientData.myTeam()!=null&&ClientData.myTeam().members.size()==14,"Synthetic native party has not arrived");
        require(!ClientData.shareQuestProgress&&ClientData.progressTeam.equals(mc().player.getUUID()),"Fixture requires separate personal quest progress");
        require(mc().getWindow().getGuiScaledWidth()==320&&mc().getWindow().getGuiScaledHeight()==240,"Harness viewport must be640x480 GUI2");
        require(!mc().options.hideGui,"Native HUD must remain normally visible");
        switch(a[1]) {
            case "compact_roster" -> {
                queue("open native owner Teams",ClientChecks::openTeams);
                queue("observe first compact page",()->roster(0,List.of("Dev","Visitor","Offline01"),false,true));snapshot("01-owner-roster-first");
                queue("record native first-page bounds and intersections after capture",()->auditOwnerWidgets("first page"));
                for(int i=0;i<4;i++)queue("native next toward last compact page",()->click(">"));
                queue("observe last compact page",()->roster(4,List.of("Offline11","Offline12"),true,false));snapshot("01-owner-roster-last");
                queue("record native last-page bounds and intersections after capture",()->auditOwnerWidgets("last page"));
                queue("type actual compact member search",()->input("Offline12"));queue("click native Find",()->click("Find"));
                queue("observe compact search result",()->roster(0,List.of("Offline12"),false,false));snapshot("01-owner-roster-search");
                queue("record native search controls without pressing destructive actions",()->auditOwnerWidgets("search result"));
            }
            case "compact_filter" -> {
                queue("open member native Quests",ClientChecks::openQuests);queue("click native compact Search",ClientChecks::searchButton);
                queue("type actual compact Prompt field",()->{screen(PromptScreen.class);input("Navigation Started");});snapshot("02-compact-search-prompt");queue("click native compact Save",()->click("Save"));queue("verify searched native STARTED detail",()->expectSelected(103,"STARTED"));
                queue("close native searched detail",ClientChecks::closeDetail);
                for(int i=0;i<3;i++)queue("cycle native Status toward STARTED",ClientChecks::statusButton);
                queue("use native Next with one selected filter",()->{require(number(screen(),"searchStatus")==3,"STARTED filter not selected");nextButton();});queue("verify one compact filtered result",()->expectSelected(103,"STARTED"));queue("close detail for readable whole filter",ClientChecks::closeDetail);snapshot("02-compact-started-filter");queue("audit native Search Next and full STARTED label",()->auditQuestControls("STARTED overview",false));
            }
            case "compact_pin" -> {
                queue("open owner native Quests",ClientChecks::openQuests);searchAndExpect("Navigation Started",103,"STARTED");
                queue("verify owner personal task counter",()->require(ClientData.progress.progress(1103L)==4,"Owner's counter changed"));snapshot("03-owner-pin-detail");queue("audit compact Pin and close before input",()->auditQuestControls("owner detail",true));queue("click native Pin",ClientChecks::pinButton);queue("close native owner detail",ClientChecks::closeDetail);
            }
            case "compact_goal" -> {
                queue("open member native Quests",ClientChecks::openQuests);queue("verify synchronized goal and click actual Party goal",()->{require(ClientData.myTeam().pinnedQuest==103,"Pinned metadata not received");goalButton();});queue("verify member personally distinct detail",()->{expectSelected(103,"STARTED");require(ClientData.progress.progress(1103L)==2,"Pinned detail changed/merged member counter");});snapshot("03-member-goal-detail");queue("audit native Unpin and close",()->auditQuestControls("member goal detail",true));queue("click actual native Unpin",ClientChecks::pinButton);queue("close actual native member detail",ClientChecks::closeDetail);
            }
            case "compact_no_goal" -> {
                queue("open owner after member Unpin",ClientChecks::openQuests);queue("observe removed compact shortcut",()->require(ClientData.myTeam().pinnedQuest==0&&selected()==null,"Unpin retained a goal/detail"));
            }
            default -> throw new IllegalArgumentException("Unknown compact UI action "+a[1]);
        }
    }
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event){if(!event.isOverlay())messages.add(event.getMessage().getString());}
    static boolean screenshotsReady()throws Exception {
        Path folder=Path.of(System.getenv("UQ_COMPACT_EVIDENCE")).resolve("screenshots");for(String capture:captures){Path f=folder.resolve(capture);if(!Files.isRegularFile(f)||Files.size(f)<64)return false;}return true;
    }
    static void completed()throws Exception {
        var o=new CompoundTag();o.putString("screen",screen().getClass().getName());o.putInt("guiWidth",screen().width);o.putInt("guiHeight",screen().height);o.putBoolean("shareQuestProgress",ClientData.shareQuestProgress);o.putUUID("progressTeam",ClientData.progressTeam);o.putLong("pinnedQuest",ClientData.myTeam().pinnedQuest);o.putString("role",ClientData.myTeam().rankOf(mc().player.getUUID()).name());o.putIntArray("toastVisibleQueued",toastCounts());o.putInt("toastQuietTicks",toastQuiet);o.put("progress",ClientData.progress.toTag(mc().player.getUUID()));o.put("geometry",geometry.copy());
        if(screen() instanceof TeamScreen){o.putInt("memberPage",number(screen(),"memberPage"));o.putString("visibleMembers",visibleMembers().toString());}
        if(screen() instanceof QuestScreen){var q=selected();o.putLong("selectedQuest",q==null?0:q.id);o.putInt("statusFilter",number(screen(),"searchStatus"));}
        var text=new ListTag();for(String action:actions)text.add(StringTag.valueOf(action));o.put("nativeActions",text);var shots=new ListTag();for(String capture:captures)shots.add(StringTag.valueOf(capture));o.put("screenshots",shots);var errors=new ListTag();for(String defect:defects)errors.add(StringTag.valueOf(defect));o.put("defects",errors);
        write(ROOT.resolve(name+".observation."+pending[0]+".snbt"),o.toString());log(pending[0]+"\tordered receipt and completed original screenshots\t"+o);write(ROOT.resolve(name+".ack"),pending[0]+"\t"+(defects.isEmpty()?"passed":"failed: "+String.join("; ",defects)));pending=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;var mc=mc();tick++;
        try {
            if(mc.player==null||mc.level==null)return;name=mc.player.getGameProfile().getName();if(!name.equals("Dev")&&!name.equals("Visitor"))return;
            if(oldPlayer!=mc.player){oldPlayer=mc.player;generation++;}if(mc.player.tickCount<100)return;
            Files.createDirectories(ROOT);write(ROOT.resolve(name+".ready"),mc.player.getUUID()+"\t"+generation);
            int[] counts=toastCounts();toastQuiet=counts[0]==0&&counts[1]==0?toastQuiet+1:0;
            write(ROOT.resolve(name+".notifications"),"visible="+counts[0]+" queued="+counts[1]+" quiet="+toastQuiet+" nativePartyReady="+nativePartyReady());
            if(pending!=null) {
                require(tick-sentAt<1100,"Compact native action timed out while "+(steps.isEmpty()?"waiting for receipt/screenshots":steps.peek().description()));
                if(!uiStarted){if(!nativePartyReady()||toastQuiet<20)return;uiStarted=true;actions.add("normal world rendered with one owned synthetic Pokemon; native visible/queued toasts empty for20ticks");}
                if(delay>0){delay--;return;}
                if(!steps.isEmpty()) {
                    if(steps.peek().description().startsWith("capture actual framebuffer")&&toastQuiet<20)return;
                    var s=steps.remove();actions.add(s.description());log(pending[0]+"\t"+s.description());s.body().run();delay=s.after();return;
                }
                if(!barrierSent){Net.sendToServer(new BundleSmoke.Barrier(pending[0]));barrierSent=true;return;}
                if(BundleSmoke.RECEIPTS.contains(pending[0])&&screenshotsReady()){BundleSmoke.RECEIPTS.remove(pending[0]);completed();}return;
            }
            Path f=ROOT.resolve(name+".txt");if(!Files.exists(f))return;String value=Files.readString(f);if(value.equals(seen))return;String[] a=value.split("\t",-1);require(a.length==2,"Malformed compact request");seen=value;pending=a;sentAt=tick;begin(a);
        }catch(Throwable bad) {
            bad.printStackTrace();if(pending==null)return;
            try{fault("Native input/observation failure: "+bad);actions.add("failure retained; completing screenshot/receipt protocol");log(pending[0]+"\tfailed\t"+bad);steps.clear();delay=0;uiStarted=true;if(!barrierSent){Net.sendToServer(new BundleSmoke.Barrier(pending[0]));barrierSent=true;}if(BundleSmoke.RECEIPTS.contains(pending[0])&&screenshotsReady()){BundleSmoke.RECEIPTS.remove(pending[0]);completed();}}
            catch(Exception reporting){try{write(ROOT.resolve(name+".ack"),pending[0]+"\tfailed: report "+reporting);}catch(Exception ignored){}pending=null;}
        }
    }
}
