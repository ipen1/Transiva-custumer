package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomerStallTrackerTest {
 @Test public void heartbeatBeforeDeadlineDoesNotReport(){CustomerStallTracker t=new CustomerStallTracker();long id=t.begin(100);assertFalse(t.reportIfStalled(6099,6000));t.recover(id);assertFalse(t.reportIfStalled(8000,6000));}
 @Test public void longStallReportsOnce(){CustomerStallTracker t=new CustomerStallTracker();t.begin(100);assertTrue(t.reportIfStalled(6100,6000));assertFalse(t.reportIfStalled(100000,6000));}
 @Test public void newStallAfterRecoveryIsReported(){CustomerStallTracker t=new CustomerStallTracker();long id=t.begin(100);assertTrue(t.reportIfStalled(6100,6000));t.recover(id);t.begin(8000);assertTrue(t.reportIfStalled(14000,6000));}
 @Test public void oldQueuedHeartbeatCannotEraseNewEpisode(){CustomerStallTracker t=new CustomerStallTracker();long old=t.begin(100);t.recover(old);t.begin(8000);t.recover(old);assertTrue(t.waiting());assertTrue(t.reportIfStalled(14000,6000));}
}
