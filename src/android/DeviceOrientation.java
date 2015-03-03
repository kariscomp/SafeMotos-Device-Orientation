
package com.safemotos.deviceorientation;

import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;
import android.os.Looper;
import com.goatstone.util;

/**
 * This class listens to the accelerometer sensor and stores the latest
 * acceleration values x,y,z.
 */
public class DeviceOrientation extends CordovaPlugin implements SensorEventListener {

    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;
   
    private float azimuth,pitch,roll;                                // most recent acceleration values
    private long timestamp;                         // time of most recent value
    private int status;                                 // status of listener
    private int accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    private SensorManager sensorManager;    // Sensor manager
    private Sensor mSensor;                           // Acceleration sensor returned by sensor manager

    private SensorFusion sensorFusion;

    private CallbackContext callbackContext;              // Keeps track of the JS callback context.

    private Handler mainHandler=null;
    private Runnable mainRunnable =new Runnable() {
        public void run() {
            DeviceOrientation.this.timeout();
        }
    };

    /**
     * Create an accelerometer listener.
     */
    public DeviceOrientation() {
        this.azimuth = 0;
        this.pitch = 0;
        this.roll = 0;
        this.timestamp = 0;
        this.setStatus(DeviceOrientation.STOPPED);
     }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Executes the request.
     *
     * @param action        The action to execute.
     * @param args          The exec() arguments.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              Whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("start")) {
            this.callbackContext = callbackContext;
            if (this.status != DeviceOrientation.RUNNING) {
                // If not running, then this is an async call, so don't worry about waiting
                // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
                this.start();
            }
        }
        else if (action.equals("stop")) {
            if (this.status == DeviceOrientation.RUNNING) {
                this.stop();
            }
        } else {
          // Unsupported action
            return false;
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, "");
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
        return true;
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        this.stop();
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    //
    /**
     * Start listening for acceleration sensor.
     * 
     * @return          status of listener
    */
    private int start() {
        // If already starting or running, then just return
        if ((this.status == DeviceOrientation.RUNNING) || (this.status == DeviceOrientation.STARTING)) {
            return this.status;
        }

        this.setStatus(DeviceOrientation.STARTING);

        // Get accelerometer from sensor manager
        List<Sensor> list = this.sensorManager.getSensorList(Sensor.TYPE_ALL);

        // If found, then register as listener
        String sensorList = "";
        for(Sensor sensor : list) {
          sensorList+= sensor+",";
        }

        if ((list != null) && (list.size() > 0)) {
          // this.mSensor = list.get(0);
          // this.sensorManager.registerListener(this, this.mSensor, SensorManager.SENSOR_DELAY_UI);
          this.setStatus(DeviceOrientation.STARTING);
        } else {
          this.setStatus(DeviceOrientation.ERROR_FAILED_TO_START);
          this.fail(DeviceOrientation.ERROR_FAILED_TO_START, "Not all sensors available."+sensorList);
          return this.status;
        }

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        registerSensorManagerListeners();

        sensorFusion = new SensorFusion();
        sensorFusion.setMode(SensorFusion.Mode.ACC_MAG)

        // Set a timeout callback on the main thread.
        stopTimeout();
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(mainRunnable, 2000);

        return this.status;
    }

    public void registerSensorManagerListeners() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void stopTimeout() {
        if(mainHandler!=null){
            mainHandler.removeCallbacks(mainRunnable);
        }
    }
    /**
     * Stop listening to acceleration sensor.
     */
    private void stop() {
        // Stop it at the Sensor Fusion Level
        super.onStop();
        stopTimeout();
        if (this.status != DeviceOrientation.STOPPED) {
            this.sensorManager.unregisterListener(this);
        }
        this.setStatus(DeviceOrientation.STOPPED);
        this.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    }

    /**
     * Returns an error if the sensor hasn't started.
     *
     * Called two seconds after starting the listener.
     */
    private void timeout() {
        if (this.status == DeviceOrientation.STARTING) {
            this.setStatus(DeviceOrientation.ERROR_FAILED_TO_START);
            this.fail(DeviceOrientation.ERROR_FAILED_TO_START, "Sensor Listening could not be started");
        }
    }

    /**
     * Called when the accuracy of the sensor has changed.
     *
     * @param sensor
     * @param accuracy
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Only look at accelerometer events
        // if (sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
        //     return;
        // }

        // If not running, then just return
        if (this.status == DeviceOrientation.STOPPED) {
            return;
        }
        this.accuracy = accuracy;
    }

    /**
     * Sensor listener event.
     *
     * @param SensorEvent event
     */
    public void onSensorChanged(SensorEvent event) {

        // If not running, then just return
        if (this.status == DeviceOrientation.STOPPED) {
            return;
        }
        
        this.setStatus(DeviceOrientation.RUNNING);

        // We take care of the bad accuracy by 
        // fusing the sensors 

        switch (event.sensor.getType()) {

          case Sensor.TYPE_ACCELEROMETER:
              sensorFusion.setAccel(event.values);
              sensorFusion.calculateAccMagOrientation();
              break;

          case Sensor.TYPE_GYROSCOPE:
              sensorFusion.gyroFunction(event);
              break;

          case Sensor.TYPE_MAGNETIC_FIELD:
              sensorFusion.setMagnet(event.values);
              break;
        }

        // Save time that event was received
        this.timestamp = System.currentTimeMillis();
        this.azimuth = sensorFusion.getAzimuth();
        this.pitch = sensorFusion.getRoll();
        this.roll = sensorFusion.getPitch();

        this.win();
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (this.status == DeviceOrientation.RUNNING) {
            this.stop();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerSensorManagerListeners();
    }

    // @Override
    // public void onStop() {
    //     super.onStop();
    //     sensorManager.unregisterListener(this);
    // }


    // Sends an error back to JS
    private void fail(int code, String message) {
        // Error object
        JSONObject errorObj = new JSONObject();
        try {
            errorObj.put("code", code);
            errorObj.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    private void win() {
        // Success return object
        PluginResult result = new PluginResult(PluginResult.Status.OK, this.getAccelerationJSON());
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void setStatus(int status) {
        this.status = status;
    }
    private JSONObject getAOrientationJSON() {
        JSONObject r = new JSONObject();
        try {
            r.put("azimuth", this.azimuth);
            r.put("pitch", this.pitch);
            r.put("roll", this.roll);
            r.put("timestamp", this.timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return r;
    }
}
