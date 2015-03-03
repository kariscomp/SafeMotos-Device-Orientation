

/**
 * This class provides access to fused device orientation data.
 * @constructor
 */
var argscheck = require('cordova/argscheck'),
    utils = require("cordova/utils"),
    exec = require("cordova/exec"),
    SensorFusioning = require('./SensorFusioning');

// Is the accel sensor running?
var running = false;

// Keeps reference to watchSensorFusioning calls.
var timers = {};

// Array of listeners; used to keep track of when we should call start and stop.
var listeners = [];

// Last returned SensorFusioning object from native
var accel = null;

// Tells native to start.
function start() {
    exec(function(a) {
        var tempListeners = listeners.slice(0);
        sf = new SensorFusioning(a.azimuth, a.pitch, a.roll, a.timestamp);
        for (var i = 0, l = tempListeners.length; i < l; i++) {
            tempListeners[i].win(sf);
        }
    }, function(e) {
        var tempListeners = listeners.slice(0);
        for (var i = 0, l = tempListeners.length; i < l; i++) {
            tempListeners[i].fail(e);
        }
    }, "DeviceOrientation", "start", []);
    running = true;
}

// Tells native to stop.
function stop() {
    exec(null, null, "DeviceOrientation", "stop", []);
    running = false;
}

// Adds a callback pair to the listeners array
function createCallbackPair(win, fail) {
    return {win:win, fail:fail};
}

// Removes a win/fail listener pair from the listeners array
function removeListeners(l) {
    var idx = listeners.indexOf(l);
    if (idx > -1) {
        listeners.splice(idx, 1);
        if (listeners.length === 0) {
            stop();
        }
    }
}

var sensorfusion = {
    /**
     * Asynchronously acquires the current SensorFusioning.
     *
     * @param {Function} successCallback    The function to call when the SensorFusioning data is available
     * @param {Function} errorCallback      The function to call when there is an error getting the SensorFusioning data. (OPTIONAL)
     * @param {SensorFusioningOptions} options The options for getting the sensorfusion data such as timeout. (OPTIONAL)
     */
    getCurrentSensorFusioning: function(successCallback, errorCallback, options) {
        argscheck.checkArgs('fFO', 'sensorfusion.getCurrentSensorFusioning', arguments);

        var p;
        var win = function(a) {
            removeListeners(p);
            successCallback(a);
        };
        var fail = function(e) {
            removeListeners(p);
            errorCallback && errorCallback(e);
        };

        p = createCallbackPair(win, fail);
        listeners.push(p);

        if (!running) {
            start();
        }
    },

    /**
     * Asynchronously acquires the SensorFusioning repeatedly at a given interval.
     *
     * @param {Function} successCallback    The function to call each time the SensorFusioning data is available
     * @param {Function} errorCallback      The function to call when there is an error getting the SensorFusioning data. (OPTIONAL)
     * @param {SensorFusioningOptions} options The options for getting the sensorfusion data such as timeout. (OPTIONAL)
     * @return String                       The watch id that must be passed to #clearWatch to stop watching.
     */
    watchSensorFusioning: function(successCallback, errorCallback, options) {
        argscheck.checkArgs('fFO', 'sensorfusion.watchSensorFusioning', arguments);
        // Default interval (10 sec)
        var frequency = (options && options.frequency && typeof options.frequency == 'number') ? options.frequency : 10000;

        // Keep reference to watch id, and report accel readings as often as defined in frequency
        var id = utils.createUUID();

        var p = createCallbackPair(function(){}, function(e) {
            removeListeners(p);
            errorCallback && errorCallback(e);
        });
        listeners.push(p);

        timers[id] = {
            timer:window.setInterval(function() {
                if (accel) {
                    successCallback(accel);
                }
            }, frequency),
            listeners:p
        };

        if (running) {
            // If we're already running then immediately invoke the success callback
            // but only if we have retrieved a value, sample code does not check for null ...
            if (accel) {
                successCallback(accel);
            }
        } else {
            start();
        }

        return id;
    },

    /**
     * Clears the specified sensorfusion watch.
     *
     * @param {String} id       The id of the watch returned from #watchSensorFusioning.
     */
    clearWatch: function(id) {
        // Stop javascript timer & remove from timer list
        if (id && timers[id]) {
            window.clearInterval(timers[id].timer);
            removeListeners(timers[id].listeners);
            delete timers[id];
        }
    }
};
module.exports = SensorFusioning;
