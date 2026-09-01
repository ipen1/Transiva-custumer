package com.transiva.app;
import org.junit.Test; import static org.junit.Assert.*;
public class OrderStateMachineTest {
 @Test public void canonicalFlowNeverMovesBackward(){ String s="driver_accepted"; s=OrderStateMachine.advance(s,"arrived_pickup"); s=OrderStateMachine.advance(s,"pending"); assertEquals("arrived_pickup",s); s=OrderStateMachine.advance(s,"on_delivery"); s=OrderStateMachine.advance(s,"arrived_delivery"); s=OrderStateMachine.advance(s,"finished"); assertEquals("finished",s); }
 @Test public void acceptedStopsRadar(){ assertTrue(OrderStateMachine.shouldStopSearch("driver_accepted")); assertFalse(OrderStateMachine.shouldStopSearch("pending")); }
}
