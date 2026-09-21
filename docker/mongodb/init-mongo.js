db = db.getSiblingDB("ioteste");

db.rooms.insertMany([
    {
        _id: "room1",
        name: "Living",
        thermostatId: "ht-sim-room1",
        switchId: "pro1pm-room1",
        targetTempC: 21.5
    },
    {
        _id: "room2",
        name: "Bedroom",
        thermostatId: "ht-sim-room2",
        switchId: "pro1pm-room2",
        targetTempC: 20.0
    }
]);

db.controller_state.insertOne({
    _id: "controller",
    enabled: false
});