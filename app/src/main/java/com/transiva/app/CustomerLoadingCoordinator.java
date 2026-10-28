package com.transiva.app;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Screen-owned, bounded loading work. Optional reads have their own single worker. */
public final class CustomerLoadingCoordinator {
    public enum State { QUEUED, LOADING, READY, ERROR, CANCELLED }
    public static final int CONTENT=0, SEARCH=1, OPTIONAL=2;
    private static final AtomicLong SEQUENCE=new AtomicLong(), REJECTED=new AtomicLong(), EXPIRED=new AtomicLong(), CANCELLED=new AtomicLong(), WAIT_MS=new AtomicLong(), STARTED=new AtomicLong();
    private static final Semaphore CAPACITY=new Semaphore(64);
    private static ThreadPoolExecutor pool(int count,String label){
        ThreadPoolExecutor pool=new ThreadPoolExecutor(count,count,30,TimeUnit.SECONDS,new PriorityBlockingQueue<Runnable>(),r->{Thread t=new Thread(r,label);t.setDaemon(true);return t;});pool.allowCoreThreadTimeOut(true);return pool;
    }
    private static final ThreadPoolExecutor FOREGROUND=pool(3,"transiva-content"), BACKGROUND=pool(1,"transiva-optional");
    private static final ThreadLocal<Job> CURRENT=new ThreadLocal<>();
    private final Map<String,Job> jobs=new HashMap<>();
    // Only named request generations live here. Unnamed completed tasks cannot evict a named callback.
    private final Map<String,Job> latest=new HashMap<>();
    private boolean destroyed;
    private final class Job extends FutureTask<Void> implements Comparable<Job> {
        final String key;final int priority;final boolean named;final long sequence=SEQUENCE.incrementAndGet(),queuedAt=System.nanoTime(),maxQueueNanos;
        final Runnable rejected;final ThreadPoolExecutor executor;
        volatile CustomerLoadingCoordinator.State state=CustomerLoadingCoordinator.State.QUEUED;
        volatile HttpURLConnection connection;
        Job(String key,int priority,boolean named,Runnable task,Runnable rejected,long maxQueueMs){super(task,null);this.key=key;this.priority=priority;this.named=named;this.rejected=rejected;this.maxQueueNanos=TimeUnit.MILLISECONDS.toNanos(maxQueueMs);executor=priority>=OPTIONAL?BACKGROUND:FOREGROUND;}
        boolean current(){synchronized(CustomerLoadingCoordinator.this){return !destroyed&&!isCancelled()&&(!named||latest.get(key)==this);}}
        public int compareTo(Job b){int p=Integer.compare(priority,b.priority);return p!=0?p:Long.compare(sequence,b.sequence);}
        @Override public void run(){
            if(isCancelled())return;
            long waited=System.nanoTime()-queuedAt;
            if(waited>maxQueueNanos){EXPIRED.incrementAndGet();cancel(false);if(rejected!=null)rejected.run();return;}
            WAIT_MS.addAndGet(TimeUnit.NANOSECONDS.toMillis(waited));STARTED.incrementAndGet();CURRENT.set(this);state=CustomerLoadingCoordinator.State.LOADING;
            try{super.run();}finally{CURRENT.remove();}
        }
        @Override public boolean cancel(boolean interrupt){boolean result=super.cancel(interrupt);if(result)CANCELLED.incrementAndGet();HttpURLConnection c=connection;if(c!=null)c.disconnect();executor.remove(this);return result;}
        @Override protected void done(){
            if(isCancelled())state=CustomerLoadingCoordinator.State.CANCELLED;
            else try{get();if(state!=CustomerLoadingCoordinator.State.ERROR)state=CustomerLoadingCoordinator.State.READY;}catch(Exception e){state=CustomerLoadingCoordinator.State.ERROR;}
            synchronized(CustomerLoadingCoordinator.this){if(jobs.get(key)==this)jobs.remove(key);}
            CAPACITY.release();
        }
    }
    public Future<?> execute(String key,int priority,Runnable task,Runnable rejected){return execute(key,priority,task,rejected,30000L);}
    synchronized Future<?> execute(String key,int priority,Runnable task,Runnable rejected,long maxQueueMs){
        if(destroyed||task==null)return null;
        boolean named=key!=null;if(!named)key="task-"+SEQUENCE.incrementAndGet();else cancel(key);
        if(!CAPACITY.tryAcquire()){REJECTED.incrementAndGet();if(rejected!=null)rejected.run();return null;}
        Job job=new Job(key,priority,named,task,rejected,Math.max(1,maxQueueMs));jobs.put(key,job);if(named)latest.put(key,job);job.executor.execute(job);return job;
    }
    public synchronized void cancel(String key){Job old=jobs.remove(key);latest.remove(key);if(old!=null)old.cancel(true);}
    public synchronized State state(String key){Job job=latest.get(key);return job==null?State.CANCELLED:job.state;}
    public synchronized void destroy(){destroyed=true;for(Job j:new ArrayList<>(jobs.values()))j.cancel(true);jobs.clear();latest.clear();}
    static Runnable guardCurrent(Runnable task){Job job=CURRENT.get();return job==null?task:()->{if(job.current())task.run();};}
    static void reportFailure(){Job job=CURRENT.get();if(job!=null&&!job.isCancelled())job.state=CustomerLoadingCoordinator.State.ERROR;}
    static void attach(HttpURLConnection c)throws InterruptedException{Job j=CURRENT.get();if(j!=null){j.connection=c;if(j.isCancelled()){c.disconnect();throw new InterruptedException("Cancelled");}}}
    static void detach(HttpURLConnection c){Job j=CURRENT.get();if(j!=null&&j.connection==c)j.connection=null;}
    public static Map<String,Long> metrics(){Map<String,Long> values=new LinkedHashMap<>();values.put("started",STARTED.get());values.put("wait_ms",WAIT_MS.get());values.put("rejected",REJECTED.get());values.put("expired",EXPIRED.get());values.put("cancelled",CANCELLED.get());values.put("queued",(long)(FOREGROUND.getQueue().size()+BACKGROUND.getQueue().size()));return Collections.unmodifiableMap(values);}
}
