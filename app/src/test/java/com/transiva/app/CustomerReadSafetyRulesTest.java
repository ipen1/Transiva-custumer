package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomerReadSafetyRulesTest {
 @Test public void accountSwitchRejectsOldCallback(){assertFalse(CustomerReadSafetyRules.sameSession("1","a","2","b",true));}
 @Test public void sameAccountNewLoginRejectsOldToken(){assertFalse(CustomerReadSafetyRules.sameSession("1","a","1","b",true));}
 @Test public void logoutRejectsCallbackEvenWhenIdsRemain(){assertFalse(CustomerReadSafetyRules.sameSession("1","a","1","a",false));}
 @Test public void validCurrentSessionCanApply(){assertTrue(CustomerReadSafetyRules.sameSession("1","a","1","a",true));}
 @Test public void foreignAndLegacyCacheNeverAppear(){assertFalse(CustomerReadSafetyRules.fresh("2","1",1000,1001,600000));assertFalse(CustomerReadSafetyRules.fresh("1","",1000,1001,600000));}
 @Test public void expirationAndClockRollbackRejectCache(){assertTrue(CustomerReadSafetyRules.fresh("1","1",1000,601000,600000));assertFalse(CustomerReadSafetyRules.fresh("1","1",1000,601001,600000));assertFalse(CustomerReadSafetyRules.fresh("1","1",1000,999,600000));assertFalse(CustomerReadSafetyRules.fresh("1","1",0,1000,600000));}
}
