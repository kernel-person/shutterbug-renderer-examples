package io.github.kernelperson.pov;

import java.util.*;

/** Main-thread state machine: bounded demand, one ticket, no frame queue or catch-up. */
final class FeedLoop<K> {
    record Ticket<K>(K target,long generation,long started) {}
    record Frame(byte[] pixels,long captured) {
        Frame {if(pixels.length!=16384)throw new IllegalArgumentException("Expected one map");pixels=pixels.clone();}
        @Override public byte[] pixels(){return pixels.clone();}
        boolean stale(long now){return now-captured>=3_000;}
    }
    private static final class Feed {long next;int failures;Frame frame;}
    private final LinkedHashMap<K,Feed> feeds=new LinkedHashMap<>();
    private int updatesPerSecond=4;
    private long sequence;
    private Ticket<K> pending;
    int updatesPerSecond(){return updatesPerSecond;}
    void updatesPerSecond(int rate) {
        if(rate<1||rate>20)throw new IllegalArgumentException("POV updates/second must be 1-20");
        updatesPerSecond=rate;
        for(Feed feed:feeds.values())if(feed.failures==0&&feed.frame!=null)
            feed.next=feed.frame.captured()+intervalMillis(); // failure cooldowns and active tickets are untouched
    }
    private long intervalMillis(){return (1_000L+updatesPerSecond-1)/updatesPerSecond;}
    void demand(Collection<K> requested) {
        Set<K> selected=new LinkedHashSet<>();for(K key:requested){selected.add(key);if(selected.size()==2)break;}
        for(K key:List.copyOf(feeds.keySet()))if(!selected.contains(key)){invalidate(key);feeds.remove(key);}
        for(K key:selected)feeds.computeIfAbsent(key,ignored->new Feed());
    }
    Ticket<K> begin(long now) {
        if(pending!=null)return null;
        for(K key:List.copyOf(feeds.keySet())) {
            Feed feed=feeds.get(key);if(now<feed.next)continue;
            feeds.remove(key);feeds.put(key,feed); // round-robin, not starvation by player iteration order
            return pending=new Ticket<>(key,++sequence,now);
        }
        return null;
    }
    boolean current(Ticket<K> ticket){return ticket!=null&&ticket.equals(pending);}
    boolean complete(Ticket<K> ticket,long now,byte[] pixels) {
        if(!current(ticket))return false;
        Feed feed=feeds.get(ticket.target());feed.frame=new Frame(pixels,ticket.started());
        feed.failures=0;feed.next=ticket.started()+intervalMillis();pending=null;return true;
    }
    void failed(Ticket<K> ticket,long now) {
        if(!current(ticket))return;
        Feed feed=feeds.get(ticket.target());feed.failures=Math.min(5,feed.failures+1);
        feed.next=now+Math.min(30_000,1_000L<<feed.failures);pending=null;
    }
    void invalidate(K key) {
        Feed feed=feeds.get(key);if(feed!=null){feed.frame=null;feed.next=0;feed.failures=0;}
        if(pending!=null&&pending.target().equals(key))pending=null;
    }
    Frame frame(K key){Feed feed=feeds.get(key);return feed==null?null:feed.frame;}
    int size(){return feeds.size();}
    void clear(){pending=null;feeds.clear();}
}
