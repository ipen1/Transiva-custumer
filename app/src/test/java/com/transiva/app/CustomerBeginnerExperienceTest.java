package com.transiva.app;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class CustomerBeginnerExperienceTest {
    @Test public void shippingDoesNotTellCustomerTheyAreRidingInTheVehicle() throws Exception {
        String[] goods={"TransSend","TransShop","pickup"};
        for(String type:goods){
            JSONObject o=new JSONObject().put("order_type",type).put("status","on_delivery").put("driver_name","Driver Anda");
            assertEquals("Barang Sedang Diantar",OrderStatusPresentation.label("on_delivery",type));
            assertTrue(OrderStatusPresentation.description(o).contains("barang"));
            assertFalse(OrderStatusPresentation.description(o).contains("mengantar Anda"));
        }
    }
    @Test public void legacyDeliveryStatusStillShowsNextAction() throws Exception {
        JSONObject o=new JSONObject().put("order_type","TransSend").put("status","on_trip");
        assertTrue(OrderStatusPresentation.description(o).contains("penerima"));
        assertEquals("on_delivery",CustomerOrderState.normalize("on_trip"));
    }
    @Test public void shopStoredAsMotorTransportStillShowsDeliveryInstructions() throws Exception {
        JSONObject order=new JSONObject().put("order_type","Transbike").put("status","on_delivery").put("note","{\"type\":\"shop\"}");
        assertEquals("shop",OrderStatusPresentation.serviceType(order));
        assertTrue(OrderStatusPresentation.description(order).contains("barang"));
        assertEquals("on_delivery",order.optString("status"));
    }
    @Test public void quickReportsMeetExistingServerValidationWithoutExtraTyping(){
        for(int i=0;i<CustomerBeginnerCopy.SUPPORT_CHOICES.length;i++){
            assertTrue(CustomerBeginnerCopy.SUPPORT_CHOICES[i].length()>=5);
            assertTrue(CustomerBeginnerCopy.SUPPORT_CHOICES[i].length()<=150);
            assertTrue(CustomerBeginnerCopy.supportDetail(i).length()>=15);
            assertTrue(CustomerBeginnerCopy.supportDetail(i).length()<=4000);
            assertTrue(CustomerBeginnerCopy.supportCategory(i)>=0&&CustomerBeginnerCopy.supportCategory(i)<6);
        }
    }
    @Test public void foodAndRideInstructionsRemainDifferent() throws Exception {
        JSONObject food=new JSONObject().put("order_type","TransFood").put("status","arrived_pickup");
        JSONObject ride=new JSONObject().put("order_type","TransRide").put("status","arrived_pickup");
        assertTrue(OrderStatusPresentation.description(food).contains("alamat antar"));
        assertTrue(OrderStatusPresentation.description(ride).contains("titik jemput"));
    }
}
