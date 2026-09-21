package com.ioteste.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    private String name;
    private Double targetTempC;
    private String thermostatId;
    private String switchId;

    public Room() {
    }

    public Room(
            String id,
            String name,
            Double targetTempC,
            String thermostatId,
            String switchId
    ) {
        this.id = id;
        this.name = name;
        this.targetTempC = targetTempC;
        this.thermostatId = thermostatId;
        this.switchId = switchId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getTargetTempC() {
        return targetTempC;
    }

    public void setTargetTempC(Double targetTempC) {
        this.targetTempC = targetTempC;
    }

    public String getThermostatId() {
        return thermostatId;
    }

    public void setThermostatId(String thermostatId) {
        this.thermostatId = thermostatId;
    }

    public String getSwitchId() {
        return switchId;
    }

    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }
}