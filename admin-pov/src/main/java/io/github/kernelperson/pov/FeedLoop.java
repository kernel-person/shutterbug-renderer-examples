package io.github.kernelperson.pov;

import java.util.*;

/** Addon demand, last displayed palette and retry policy. Renderer owns frame scheduling. */
final class FeedLoop<K> {
    record Frame(byte[] pixels,long captured) {
        Frame {if(pixels.length!=16384)throw new IllegalArgumentException("Expected one map");pixels=pixels.clone();}
        @Override public byte[] pixels(){return pixels.clone();}
        boolean stale(long now){return now-captured>=3_000;}
    }
    private static final class Feed {long next;int failures;Frame frame;}
    private final LinkedHashMap<K,Feed> feeds=new LinkedHashMap<>();
    private int updatesPerSecond=4;
    int updatesPerSecond(){return updatesPerSecond;}
    void updatesPerSecond(int rate) {
        if(rate<1||rate>20)throw new IllegalArgumentException("POV updates/second must be 1-20");
        updatesPerSecond=rate;
    }
    void demand(Collection<K> requested) {
        Set<K> selected=new LinkedHashSet<>();for(K key:requested){selected.add(key);if(selected.size()==2)break;}
        feeds.keySet().retainAll(selected);
        for(K key:selected)feeds.computeIfAbsent(key,ignored->new Feed());
    }
    Set<K> targets(){return Set.copyOf(feeds.keySet());}
    boolean ready(K key,long now){Feed feed=feeds.get(key);return feed!=null&&now>=feed.next;}
    boolean complete(K key,long captured,byte[] pixels) {
        Feed feed=feeds.get(key);if(feed==null)return false;
        if(feed.frame!=null&&captured<feed.frame.captured())return false;
        feed.frame=new Frame(pixels,captured);feed.failures=0;feed.next=0;return true;
    }
    void failed(K key,long now) {
        Feed feed=feeds.get(key);if(feed==null)return;
        feed.failures=Math.min(5,feed.failures+1);feed.next=now+Math.min(30_000,1_000L<<feed.failures);
    }
    void invalidate(K key) {Feed feed=feeds.get(key);if(feed!=null){feed.frame=null;feed.next=0;feed.failures=0;}}
    Frame frame(K key){Feed feed=feeds.get(key);return feed==null?null:feed.frame;}
    int size(){return feeds.size();}
    void clear(){feeds.clear();}
}
