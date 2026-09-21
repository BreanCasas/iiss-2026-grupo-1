package com.ioteste.api.repository;

import com.ioteste.api.model.ControllerState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ControllerStateRepository
        extends MongoRepository<ControllerState, String> {
}