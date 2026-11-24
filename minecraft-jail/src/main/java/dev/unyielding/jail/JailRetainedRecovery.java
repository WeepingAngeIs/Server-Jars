package dev.unyielding.jail;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/** Recovery for a hash-only confinement journal. It has no inventory mutation API. */
final class JailRetainedRecovery {
    private JailRetainedRecovery(){}
    @FunctionalInterface interface Proof { void verify()throws IOException; }
    private static void require(boolean ok,String message)throws IOException{if(!ok)throw new IOException(message);}

    /** An already saved switch may be confirmed; an ambiguous native callback is never repeated. */
    static void original(JailEscrowJournal origins,JailEscrowJournal confinement,UUID id,
                         JailOriginTransaction.Port port,JailOriginTransaction.Checkpoint authority)throws IOException{
        var parent=confinement.read(id);var entry=origins.read(id);
        require(parent!=null&&entry!=null&&parent.owner().equals(port.owner())&&entry.owner().equals(port.owner()),"Missing retained origin binding");
        if(parent.state()==JailEscrowJournal.State.HELD){
            var status=JailOriginTransaction.inspect(origins,confinement,id,port);
            if(status==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW
                    ||status==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW){
                authority.at(JailOriginTransaction.Boundary.BEFORE_COMPLETE);
                JailOriginTransaction.confirmSavedForDisposableTest(origins,confinement,id,port);
            }
            if(origins.read(id).state()==JailEscrowJournal.State.HELD)
                JailOriginTransaction.originalForDisposableTest(origins,confinement,id,port,authority);
        }else require(parent.state()==JailEscrowJournal.State.RESTORE_STARTED,"Retained confinement cannot authorize an origin return");
        require(origins.read(id).state()==JailEscrowJournal.State.COMPLETE,"Retained origin return is uncertain; no callback replay");
        byte[] intent=JailOriginTransaction.intentForBoundReview(origins,confinement,id,port.owner());
        var live=port.current();var disk=port.durable();
        require(Arrays.equals(live.state(),disk.state())&&port.isOriginal(intent,id,live)&&port.isOriginal(intent,id,disk),"Retained original origin/claims do not verify");
    }

    /**
     * Cancel a reservation only when both native copies still equal the admission
     * source. A REMOVAL_STARTED origin is deliberately ineligible, even if a
     * projection resembles the source: its callback outcome is uncertain.
     */
    static void unchanged(JailEscrowJournal confinement,JailEscrowJournal origins,UUID id,
                          JailOriginTransaction.Port port,byte[] before,Proof independent)throws IOException{
        var entry=confinement.read(id);
        require(entry!=null&&entry.owner().equals(port.owner())&&!JailEscrowJournal.terminal(entry.state()),"Retained reservation is absent or terminal");
        Proof proof=()->{
            independent.verify();var live=port.current();var disk=port.durable();
            require(before.length==32&&Arrays.equals(before,JailOriginTransaction.hash(live.state()))
                    &&Arrays.equals(live.state(),disk.state())&&live.transaction()==null&&disk.transaction()==null
                    &&live.receipt()==JailOriginTransaction.Receipt.NONE&&disk.receipt()==JailOriginTransaction.Receipt.NONE,
                    "Retained admission source is not exactly unchanged");
        };
        proof.verify();
        if(origins!=null){
            var origin=origins.read(id);
            require(origins.list().size()==(origin==null?0:1),"Foreign retained origin history");
            if(origin!=null){
                byte[] intent=JailOriginTransaction.intentForBoundReview(origins,confinement,id,port.owner());
                require((origin.state()==JailEscrowJournal.State.PREPARED||origin.state()==JailEscrowJournal.State.CANCELLED)
                        &&port.isPrepared(intent,port.current())&&port.isPrepared(intent,port.durable()),"Native origin mutation started or is uncertain; no cancellation");
                if(origin.state()==JailEscrowJournal.State.PREPARED){proof.verify();origins.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);}
            }
        }
        proof.verify();
        if(entry.state()==JailEscrowJournal.State.PREPARED){confinement.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);return;}
        // Later parent phases represent reservation only, not item custody. Close
        // them after source proof instead of inventing an item/origin receipt.
        if(entry.state()==JailEscrowJournal.State.REMOVAL_STARTED){proof.verify();entry=confinement.transition(id,entry.state(),JailEscrowJournal.State.HELD);}
        if(entry.state()==JailEscrowJournal.State.HELD){proof.verify();entry=confinement.transition(id,entry.state(),JailEscrowJournal.State.RESTORE_STARTED);}
        require(entry.state()==JailEscrowJournal.State.RESTORE_STARTED,"Unknown retained reservation phase");
        proof.verify();confinement.transition(id,entry.state(),JailEscrowJournal.State.COMPLETE);
    }
}
