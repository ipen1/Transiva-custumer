package com.transiva.app;

/** Lightweight TransCar entry point; shared passenger transport logic lives in PassengerTransportActivity. */
public class PassengerCarActivity extends PassengerTransportActivity {
    @Override
    protected boolean isCarService() { return true; }
}
