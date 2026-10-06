package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

public class DashboardReadExecutorTest {
    @Test public void fourWaitingCoordinatorsDoNotStarveLeafReads() throws Exception {
        ExecutorService coordinators=Executors.newFixedThreadPool(4);
        CountDownLatch allStarted=new CountDownLatch(4);
        List<Future<Integer>> results=new ArrayList<>();
        try {
            for(int i=0;i<4;i++) results.add(coordinators.submit(()->{
                allStarted.countDown();
                if(!allStarted.await(3,TimeUnit.SECONDS)) throw new TimeoutException();
                Future<Integer> child=DashboardReadExecutor.submit(()->42);
                return child.get(3,TimeUnit.SECONDS);
            }));
            for(Future<Integer> result:results) assertEquals(Integer.valueOf(42),result.get(5,TimeUnit.SECONDS));
        } finally { for(Future<?> f:results)f.cancel(true);coordinators.shutdownNow(); }
    }
}
