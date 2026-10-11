package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomerRefreshRulesTest {
 @Test public void downwardAtTopStarts(){assertTrue(CustomerRefreshRules.begin(2,30,8,true,false,1));}
 @Test public void horizontalSwipeDoesNotRefresh(){assertFalse(CustomerRefreshRules.begin(80,30,8,true,false,1));}
 @Test public void upwardSwipeDoesNotRefresh(){assertFalse(CustomerRefreshRules.begin(0,-40,8,true,false,1));}
 @Test public void scrollingContentDoesNotRefresh(){assertFalse(CustomerRefreshRules.begin(0,40,8,false,false,1));}
 @Test public void activeRequestDoesNotDuplicate(){assertFalse(CustomerRefreshRules.begin(0,40,8,true,true,1));}
 @Test public void multiplePointersCancel(){assertFalse(CustomerRefreshRules.begin(0,40,8,true,false,2));}
 @Test public void smallPullCancels(){assertFalse(CustomerRefreshRules.release(63,64));assertTrue(CustomerRefreshRules.release(64,64));}
}
