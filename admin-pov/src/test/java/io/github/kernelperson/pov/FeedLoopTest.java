package io.github.kernelperson.pov;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedLoopTest {
    @Test void onePipelineTwoTargetsAndNoCatchUpQueue() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice","bob","charlie"));
        var first=loop.begin(0);assertEquals("alice",first.target());
        assertNull(loop.begin(5_000),"slow render owns sole pipeline");
        assertTrue(loop.complete(first,5_000,new byte[16384]));
        var second=loop.begin(5_000);assertEquals("bob",second.target());
        loop.complete(second,5_010,new byte[16384]);
        var third=loop.begin(5_010);assertNotNull(third,"slow work adds no idle interval");assertEquals("alice",third.target());
        loop.complete(third,5_020,new byte[16384]);
        assertNull(loop.begin(5_020),"one new start resets cadence; missed frames are not queued");
        assertEquals("bob",loop.begin(5_250).target());
        assertEquals(2,loop.size());
    }
    @Test void defaultCadenceIsFourStartsPerSecondRatherThanAPauseAfterCompletion() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice"));
        var first=loop.begin(0);loop.complete(first,100,new byte[16384]);
        assertNull(loop.begin(249));
        var second=loop.begin(250);assertNotNull(second,"second start is 250ms after first, not after completion");
        loop.complete(second,300,new byte[16384]);
        assertNull(loop.begin(499));assertNotNull(loop.begin(500));
    }
    @Test void delayedPollingSkipsMissedFramesInsteadOfStartingACatchUpBurst() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice"));
        var first=loop.begin(0);loop.complete(first,20,new byte[16384]);
        var delayed=loop.begin(5_000);assertNotNull(delayed);
        loop.complete(delayed,5_020,new byte[16384]);
        assertNull(loop.begin(5_020));assertNull(loop.begin(5_249));assertNotNull(loop.begin(5_250));
    }
    @Test void slowWorkCanStartAgainAtCompletionWithoutOverlappingOrCatchingUp() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice"));
        var first=loop.begin(0);assertNull(loop.begin(2_000));
        loop.complete(first,2_000,new byte[16384]);
        var next=loop.begin(2_000);assertNotNull(next);
        assertNull(loop.begin(2_000));loop.complete(next,2_020,new byte[16384]);
        assertNull(loop.begin(2_249));assertNotNull(loop.begin(2_250));
    }
    @Test void duplicateSubscribersShareOneFrameAndBackoffIsBounded() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice","alice"));assertEquals(1,loop.size());
        var ticket=loop.begin(0);loop.failed(ticket,10);
        assertNull(loop.begin(1_000));ticket=loop.begin(2_010);assertNotNull(ticket);
        loop.failed(ticket,2_020);assertNull(loop.begin(6_019));
        ticket=loop.begin(6_020);assertNotNull(ticket);
        byte[] palette=new byte[16384];palette[0]=42;
        assertTrue(loop.complete(ticket,6_050,palette));palette[0]=0;
        assertEquals(42,loop.frame("alice").pixels()[0]);
    }
    @Test void demandLossTeleportAndOldCallbacksCannotResurrectFrames() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice"));
        var old=loop.begin(0);loop.invalidate("alice");
        assertFalse(loop.complete(old,100,new byte[16384]));
        var fresh=loop.begin(200);assertNotNull(fresh);
        loop.demand(List.of("bob"));assertFalse(loop.complete(fresh,300,new byte[16384]));
        assertNull(loop.frame("alice"));assertEquals("bob",loop.begin(300).target());
    }
    @Test void stopAndReloadClearFramesAndStaleTickets() {
        var loop=new FeedLoop<String>();loop.demand(List.of("alice"));
        var ticket=loop.begin(0);loop.complete(ticket,100,new byte[16384]);
        assertFalse(loop.frame("alice").stale(2_999));assertTrue(loop.frame("alice").stale(3_100));
        ticket=loop.begin(1_100);loop.clear();
        assertFalse(loop.complete(ticket,1_200,new byte[16384]));assertNull(loop.frame("alice"));
    }
}
