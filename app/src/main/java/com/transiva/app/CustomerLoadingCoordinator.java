package com.transiva.app;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** One owner per screen, keyed replacement, bounded priority queue and socket cancellation.
 * Main content precedes optional work in the waiting queue. Mutations use unkeyed execute.
 */
public final class CustomerLoadingCoordinator {
    public enum State { QUEUED, LOADING, READY, ERROR, CANCELLED }
    public static final int CONTENT=0, SEARCH=1, OPTIONAL=2;
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static final Semaphore CAPACITY=new Semaphore(64);
    private static final ThreadPoolExecutor POOL=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,
            new PriorityBlockingQueue<Runnable>(),r->{Thread t=new Thread(r,"transiva-loading");t.setDaemon(true);return t;});
    static { POOL.allowCoreThreadTimeOut(true); }
    private static final ThreadLocal<Job> CURRENT=new ThreadLocal<>();
    private final Map<String,Job> jobs=new HashMap<>();
    private final LinkedHashMap<String,Job> latest=new LinkedHashMap<>();
    private boolean destroyed;
    private final class Job extends FutureTask<Void> implements Comparable<Job> {
        final String key; final int priority; final long sequence=SEQUENCE.incrementAndGet();
        volatile State state=State.QUEUED; volatile HttpURLConnection connection;
        Job(String key,int priority,Runnable task){super(task,null);this.key=key;this.priority=priority;}
        boolean current(){synchronized(CustomerLoadingCoordinator.this){return !destroyed&&!isCancelled()&&latest.get(key)==this;}}
        public int compareTo(Job b){int p=Integer.compare(priority,b.priority);return p!=0?p:Long.compare(sequence,b.sequence);}
        @Override public void run(){if(isCancelled())return;CURRENT.set(this);state=State.LOADING;try{super.run();}finally{CURRENT.remove();}}
        @Override public boolean cancel(boolean interrupt){boolean result=super.cancel(interrupt);HttpURLConnection c=connection;if(c!=null)c.disconnect();POOL.remove(this);return result;}
        @Override protected void done(){
            state=isCancelled()?State.CANCELLED:State.READY;
            if(!isCancelled())try{get();}catch(Exception e){state=State.ERROR;}
            synchronized(CustomerLoadingCoordinator.this){if(jobs.get(key)==this)jobs.remove(key);}
            CAPACITY.release();
        }
    }
    public synchronized Future<?> execute(String key,int priority,Runnable task,Runnable rejected){
        if(destroyed||task==null)return null;
        if(key==null)key="task-"+SEQUENCE.incrementAndGet();
        cancel(key);
        if(!CAPACITY.tryAcquire()){if(rejected!=null)rejected.run();return null;}
        Job job=new Job(key,priority,task);jobs.put(key,job);latest.put(key,job);
        if(latest.size()>128){java.util.Iterator<Map.Entry<String,Job>> it=latest.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Job> entry=it.next();if(!jobs.containsKey(entry.getKey())){it.remove();break;}}}
        POOL.execute(job);return job;
    }
    public synchronized void cancel(String key){Job old=jobs.remove(key);latest.remove(key);if(old!=null)old.cancel(true);}
    public synchronized State state(String key){Job job=latest.get(key);return job==null?State.CANCELLED:job.state;}
    public synchronized void destroy(){destroyed=true;for(Job j:new ArrayList<>(jobs.values()))j.cancel(true);jobs.clear();latest.clear();}
    static Runnable guardCurrent(Runnable task){
        Job job=CURRENT.get();if(job==null)return task;
        return ()->{if(job.current())task.run();};
    }
    static void attach(HttpURLConnection c)throws InterruptedException{Job j=CURRENT.get();if(j!=null){j.connection=c;if(j.isCancelled()){c.disconnect();throw new InterruptedException("Cancelled");}}}
    static void detach(HttpURLConnection c){Job j=CURRENT.get();if(j!=null&&j.connection==c)j.connection=null;}
}
