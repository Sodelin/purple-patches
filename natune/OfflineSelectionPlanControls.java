import java.util.*;

/** Controlled snapshots only, not real Android/cache/authentication acceptance. */
public final class OfflineSelectionPlanControls {
    static int passed;
    static void check(boolean b) {if(!b)throw new AssertionError();passed++;}
    static void refuse(Runnable r) {try{r.run();}catch(IllegalStateException|IllegalArgumentException e){passed++;return;}throw new AssertionError("Expected refusal");}
    static OfflineSelectionPlan.Key key(String s) {return new OfflineSelectionPlan.Key(s,"flac-r1");}
    static OfflineSelectionPlan.Candidate candidate(String s,long n) {return new OfflineSelectionPlan.Candidate(key(s),n);}
    static OfflineSelectionPlan.Held held(String s,long n,boolean complete,boolean used,String...owners) {return new OfflineSelectionPlan.Held(key(s),n,Set.of(owners),complete,used);}
    public static void main(String[] args) {
        String owner="smart:account-a",revision="recommendations-1";
        var old=List.of(held("old-smart",50,true,false,owner),held("manual",60,true,false,owner,"manual"),
            held("shared",70,true,false,owner,"playlist:other"),held("playing",80,true,true,owner));
        var p=OfflineSelectionPlan.select(owner,revision,true,List.of(candidate("fresh",70),candidate("next",40),candidate("small",20),candidate("unknown",0),candidate("fresh",70)),old,100);
        check(p.selected().equals(List.of(key("fresh"),key("small"))));check(p.selectedBytes()==90);
        check(p.needDownload().size()==2);check(p.unknownSize().equals(List.of(key("unknown"))));check(p.overBudget().equals(List.of(key("next"))));
        refuse(()->OfflineSelectionPlan.readyDelta(p,revision,old));
        var ready=new ArrayList<OfflineSelectionPlan.Held>(old);ready.add(held("fresh",70,true,false));ready.add(held("small",20,true,false));
        var d=OfflineSelectionPlan.readyDelta(p,revision,ready);
        check(d.claim().equals(p.selected()));check(d.release().size()==4);
        check(d.retireTemporaryAfterCommit().equals(List.of(key("old-smart"))));
        refuse(()->OfflineSelectionPlan.readyDelta(p,"recommendations-2",ready));
        var oversized=new ArrayList<>(old);oversized.add(held("fresh",71,true,false));oversized.add(held("small",20,true,false));
        refuse(()->OfflineSelectionPlan.readyDelta(p,revision,oversized));
        var partial=new ArrayList<>(old);partial.add(held("fresh",70,false,false));partial.add(held("small",20,true,false));
        refuse(()->OfflineSelectionPlan.readyDelta(p,revision,partial));
        refuse(()->OfflineSelectionPlan.select(owner,revision,false,List.of(),old,100));
        var empty=OfflineSelectionPlan.select(owner,revision,true,List.of(),old,100);
        check(OfflineSelectionPlan.readyDelta(empty,revision,old).retireTemporaryAfterCommit().equals(List.of(key("old-smart"))));
        var playlist=OfflineSelectionPlan.select("playlist:one","playlist-complete-2",true,List.of(candidate("manual",1),candidate("manual",1)),old,60);
        check(playlist.selectedBytes()==60);check(playlist.reused().equals(List.of(key("manual"))));check(playlist.needDownload().isEmpty());
        check(OfflineSelectionPlan.readyDelta(playlist,"playlist-complete-2",old).claim().equals(List.of(key("manual"))));
        var giant=OfflineSelectionPlan.select(owner,revision,true,List.of(candidate("a",Long.MAX_VALUE),candidate("b",1)),List.of(),Long.MAX_VALUE);
        check(giant.selectedBytes()==Long.MAX_VALUE);check(giant.overBudget().equals(List.of(key("b"))));
        var noBudget=OfflineSelectionPlan.select(owner,revision,true,List.of(candidate("fresh",70)),old,0);check(noBudget.selected().isEmpty());
        refuse(()->OfflineSelectionPlan.select(owner,revision,true,List.of(),List.of(old.get(0),old.get(0)),100));
        refuse(()->OfflineSelectionPlan.select(owner,revision,true,List.of(),old,-1));
        // A different rendition must not reuse the old file under the same song id.
        var r2=new OfflineSelectionPlan.Key("manual","flac-r2");
        check(OfflineSelectionPlan.select(owner,revision,true,List.of(new OfflineSelectionPlan.Candidate(r2,55)),old,60).needDownload().equals(List.of(r2)));
        try{p.selected().clear();throw new AssertionError();}catch(UnsupportedOperationException expected){passed++;}
        refuse(()->new OfflineSelectionPlan.Selection(owner,revision,List.of(key("x")),Map.of(),List.of(key("x")),List.of(),List.of(),List.of(),1,100));
        refuse(()->new OfflineSelectionPlan.Selection(owner,revision,List.of(key("x")),Map.of(key("x"),1L),List.of(key("x")),List.of(key("x")),List.of(),List.of(),1,100));
        var a=key("trip-a");var b=key("trip-b");var c=key("trip-c");
        var lookback=RoadTripLookback.empty().advance(a);check(lookback.protects(a));
        lookback=lookback.advance(b);check(lookback.protects(a) && lookback.protects(b));
        check(lookback.advance(b).equals(lookback));
        lookback=lookback.advance(c);check(!lookback.protects(a) && lookback.protects(b) && lookback.protects(c));
        var trip=List.of(held("trip-a",10,true,false,"road-trip"),held("trip-b",10,true,false,"road-trip"),held("trip-c",10,true,false,"road-trip"),held("manual-trip",10,true,false,"road-trip","manual"));
        var tripEmpty=OfflineSelectionPlan.select("road-trip","queue-2",true,List.of(),trip,100);
        check(OfflineSelectionPlan.readyDelta(tripEmpty,"queue-2",lookback.protect(trip)).retireTemporaryAfterCommit().equals(List.of(a)));
        var back=lookback.advance(a);check(back.protects(a) && back.protects(c) && !back.protects(b));
        check(OfflineSelectionPlan.readyDelta(tripEmpty,"queue-2",back.protect(trip)).retireTemporaryAfterCommit().equals(List.of(b)));
        var externallyHeld=List.of(held("trip-a",10,true,true,"road-trip"));
        check(back.protect(externallyHeld).get(0).protectedUse());
        System.out.println("OFFLINE_SELECTION_CONTROLS passed="+passed+" android_owner_binding=false source_authentication=false deletion_authority=false");
    }
}
