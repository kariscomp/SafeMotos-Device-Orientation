
var SensorFusion = function(azimuth, pitch, roll, timestamp) {
    this.azimuth = azimuth;
    this.pitch = pitch;
    this.roll = roll;
    this.timestamp = timestamp || (new Date()).getTime();
};

module.exports = SensorFusion;
