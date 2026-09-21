package com.ioteste.api.repository;

import com.ioteste.api.model.TemperatureReading;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TemperatureReadingRepository
        extends MongoRepository<TemperatureReading, String> {

    List<TemperatureReading> findByRoomId(String roomId);
}