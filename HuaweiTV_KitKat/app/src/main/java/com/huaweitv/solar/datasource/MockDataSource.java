package com.huaweitv.solar.datasource;

import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;

import java.util.Random;

/**
 * Simulated {@link InverterDataSource} for UI testing and demo mode without a physical inverter.
 */
public class MockDataSource implements InverterDataSource {

    private final InverterBrand brand;
    private final Random random = new Random();
    private volatile boolean connected = false;
    private double step = 0.0;
    private double baseDailyYield = 14.85;

    public MockDataSource(InverterBrand brand) {
        this.brand = brand != null ? brand : InverterBrand.HUAWEI;
    }

    @Override
    public InverterBrand getBrand() {
        return brand;
    }

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public SolarTelemetry fetchTelemetry() {
        if (!connected) connect();
        step += 0.1;
        baseDailyYield += 0.01;

        int pv = Math.max(0, (int) (3500 + Math.sin(step) * 600 + random.nextInt(100) - 50));
        int house = Math.max(100, (int) (1600 + Math.sin(step * 0.7) * 400 + random.nextInt(60) - 30));
        double batterySoc = Math.min(100.0, Math.max(10.0, 78.5 + Math.sin(step * 0.2) * 4.0));

        int batteryPower;
        if (pv > house + 800) {
            batteryPower = Math.max(0, 1200 + random.nextInt(80) - 40);
        } else {
            batteryPower = -(600 + random.nextInt(40) - 20);
        }

        int gridPower = pv - batteryPower - house;
        int activePower = house + gridPower;

        return new SolarTelemetry(
            pv,
            activePower,
            house,
            baseDailyYield,
            gridPower,
            batterySoc,
            batteryPower,
            System.currentTimeMillis(),
            true,
            true,
            true,
            null
        );
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
