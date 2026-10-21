package io.github.kernelperson.pov;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FeedLoopTest {
    @Test void twoDemandedTargetsAndSharedSubscribers(){var loop=new FeedLoop<String>();loop.demand(List.of("a","a","b","c"));assertEquals(Set.of("a","b"),loop.targets());}
    @Test void retryStartsAtTwoSecondsAndCapsAtThirty(){var loop=new FeedLoop<String>();loop.demand(List.of("a"));long now=0;for(long delay:new long[]{2000,4000,8000,16000,30000,30000}){loop.failed("a",now);assertFalse(loop.ready("a",now+delay-1));assertTrue(loop.ready("a",now+delay));now+=delay;}}
    @Test void rateChangesPreserveRetryAndFrame(){var loop=new FeedLoop<String>();loop.demand(List.of("a"));loop.complete("a",100,new byte[16384]);var frame=loop.frame("a");loop.failed("a",200);loop.updatesPerSecond(20);assertSame(frame,loop.frame("a"));assertFalse(loop.ready("a",2199));assertTrue(loop.ready("a",2200));}
    @Test void demandLossAndClearRejectUndemandedFrames(){var loop=new FeedLoop<String>();loop.demand(List.of("a"));loop.complete("a",100,new byte[16384]);loop.demand(List.of("b"));assertFalse(loop.complete("a",200,new byte[16384]));assertNull(loop.frame("a"));loop.clear();assertFalse(loop.complete("b",300,new byte[16384]));}
    @Test void paletteIsDefensiveAndOlderEvidenceCannotRewind(){var loop=new FeedLoop<String>();loop.demand(List.of("a"));byte[] pixels=new byte[16384];pixels[0]=42;assertTrue(loop.complete("a",100,pixels));pixels[0]=0;assertEquals(42,loop.frame("a").pixels()[0]);assertFalse(loop.complete("a",99,new byte[16384]));assertFalse(loop.frame("a").stale(3099));assertTrue(loop.frame("a").stale(3100));}
    @Test void defaultAndDemoRateBounds(){var loop=new FeedLoop<String>();assertEquals(4,loop.updatesPerSecond());for(int rate:new int[]{1,20}){loop.updatesPerSecond(rate);assertEquals(rate,loop.updatesPerSecond());}for(int rate:new int[]{0,21})assertThrows(IllegalArgumentException.class,()->loop.updatesPerSecond(rate));}
}
