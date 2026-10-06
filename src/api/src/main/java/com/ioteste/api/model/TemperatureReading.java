package com.ioteste.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "temperature_readings")
public class TemperatureReading {

    @Id
    private String id;

    private String roomId;
    private BigDecimal tempCelsius;
    private BigDecimal tempFahrenheit;
    private long sourceTs;
    private Instant receivedAt;

    public TemperatureReading() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public BigDecimal getTempCelsius() {
        return tempCelsius;
    }

    public void setTempCelsius(BigDecimal tempCelsius) {
        this.tempCelsius = tempCelsius;
    }

    public BigDecimal getTempFahrenheit() {
        return tempFahrenheit;
    }

    public void setTempFahrenheit(BigDecimal tempFahrenheit) {
        this.tempFahrenheit = tempFahrenheit;
    }

    public long getSourceTs() {
        return sourceTs;
    }

    public void setSourceTs(long sourceTs) {
        this.sourceTs = sourceTs;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}