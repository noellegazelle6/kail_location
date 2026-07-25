package android.hardware;

import android.os.Handler;
import android.os.MemoryFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;



import android.util.Log;
import top.niunaijun.blackbox.utils.Slog;

public class BridgedSensorManager extends SensorManager {
    private static final String TAG = "[sandbox] BridgedSensorManager";

    private SensorManager mReal;
    private final Map<SensorEventListener, Sensor> mStepListeners = new ConcurrentHashMap<>();
    private final Map<SensorEventListener, Sensor> mAccelListeners = new ConcurrentHashMap<>();
    private boolean mStepEnabled;
    private float mStepCadenceSpm = 120f;
    private boolean mAccelEnabled;
    private float mAccelAmplitude = 3.0f;
    private float mStepCounter = 0f;
    private long mStartTimeNanos = System.nanoTime();
    private ScheduledExecutorService mExecutor;

    public static BridgedSensorManager wrap(SensorManager real) {
        try {
            BridgedSensorManager wrapper = new BridgedSensorManager();
            wrapper.mReal = real;
            wrapper.copyFields(real);
            wrapper.ensureThread();
            Slog.i(TAG, "SensorManager wrapped successfully");
            return wrapper;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to wrap SensorManager", e);
            return null;
        }
    }

    private void copyFields(SensorManager real) {
        for (Field f : SensorManager.class.getDeclaredFields()) {
            try { f.setAccessible(true); f.set(this, f.get(real)); } catch (Exception ignored) {}
        }
    }

    public void setConfig(boolean stepEnabled, float stepCadenceSpm,
                          boolean accelEnabled, float accelAmplitude) {
        this.mStepEnabled = stepEnabled;
        if (stepCadenceSpm > 0) this.mStepCadenceSpm = stepCadenceSpm;
        this.mAccelEnabled = accelEnabled;
        if (accelAmplitude > 0) this.mAccelAmplitude = accelAmplitude;
    }

    private void ensureThread() {
        if (mExecutor != null) return;
        mExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KailSensorPusher");
            t.setDaemon(true); return t;
        });
        mStartTimeNanos = System.nanoTime();
        mExecutor.scheduleAtFixedRate(this::pushEvents, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void pushEvents() {
        try {
            long now = System.nanoTime();
            if (mStepEnabled) pushStep(now);
            if (mAccelEnabled) pushAccel(now);
        } catch (Exception e) {
            Slog.w(TAG, "push error: " + e.getMessage());
        }
    }

    private void pushStep(long now) {
        if (mStepListeners.isEmpty()) return;
        long elapsedMs = (now - mStartTimeNanos) / 1000000;
        float expected = (elapsedMs / 60000f) * mStepCadenceSpm;
        if (expected <= mStepCounter + 0.5f) return;
        for (Map.Entry<SensorEventListener, Sensor> e : mStepListeners.entrySet()) {
            SensorEvent ev = newSensorEvent(1); if (ev == null) continue;
            Sensor s = e.getValue();
            if (s.getType() == Sensor.TYPE_STEP_COUNTER) { mStepCounter += 1f; ev.values[0] = mStepCounter; }
            else { ev.values[0] = 1f; }
            ev.sensor = s; ev.timestamp = now; ev.accuracy = 3;
            try { e.getKey().onSensorChanged(ev); } catch (Exception ex) { mStepListeners.remove(e.getKey()); }
        }
    }

    private void pushAccel(long now) {
        if (mAccelListeners.isEmpty()) return;
        long elapsedMs = (now - mStartTimeNanos) / 1000000;
        float t = elapsedMs / 1000f;
        float freq = mStepCadenceSpm / 60f;
        float phase = t * freq * 2f * (float) Math.PI;
        for (Map.Entry<SensorEventListener, Sensor> e : mAccelListeners.entrySet()) {
            SensorEvent ev = newSensorEvent(3); if (ev == null) continue;
            double g = 9.81;
            ev.values[0] = (float)(mAccelAmplitude*0.3*Math.sin(phase) + Math.sin(phase*2.17)*0.2);
            ev.values[1] = (float)(g + mAccelAmplitude*0.8*Math.sin(phase*2+1.2) + Math.sin(phase*3.4)*0.3);
            ev.values[2] = (float)(mAccelAmplitude*0.5*Math.sin(phase+0.8) + Math.cos(phase*1.7)*0.15);
            ev.sensor = e.getValue(); ev.timestamp = now; ev.accuracy = 3;
            try { e.getKey().onSensorChanged(ev); } catch (Exception ex) { mAccelListeners.remove(e.getKey()); }
        }
    }

    private SensorEvent newSensorEvent(int size) {
        try { Constructor<SensorEvent> c = SensorEvent.class.getDeclaredConstructor(int.class);
            c.setAccessible(true); return c.newInstance(size); } catch (Exception e) { return null; }
    }

    public List<Sensor> getFullSensorList() {
        try {
            java.lang.reflect.Method m = SensorManager.class.getMethod("getFullSensorList");
            return (List<Sensor>) m.invoke(mReal);
        } catch (Exception e) { return java.util.Collections.emptyList(); }
    }
    public List<Sensor> getFullDynamicSensorList() {
        try {
            java.lang.reflect.Method m = SensorManager.class.getMethod("getFullDynamicSensorList");
            return (List<Sensor>) m.invoke(mReal);
        } catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public boolean registerListenerImpl(SensorEventListener listener, Sensor sensor, int delayUs,
                                        Handler handler, int maxBatchReportLatencyUs, int reservedFlags) {
        if (listener == null || sensor == null) return false;
        int type = sensor.getType();
        Log.w(TAG, "registerListenerImpl type=" + type + " stepEnabled=" + mStepEnabled + " accelEnabled=" + mAccelEnabled);
        if ((type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR) && mStepEnabled) {
            Log.w(TAG, "registerListenerImpl: captured step listener");
            mStepListeners.put(listener, sensor); return true;
        }
        if (type == Sensor.TYPE_ACCELEROMETER && mAccelEnabled) {
            Log.w(TAG, "registerListenerImpl: captured accel listener");
            mAccelListeners.put(listener, sensor); return true;
        }
        try {
            java.lang.reflect.Method m = SensorManager.class.getMethod("registerListenerImpl",
                    SensorEventListener.class, Sensor.class, int.class, Handler.class, int.class, int.class);
            return (Boolean) m.invoke(mReal, listener, sensor, delayUs, handler, maxBatchReportLatencyUs, reservedFlags);
        } catch (Exception e) { return false; }
    }

    public boolean unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        mStepListeners.remove(listener); mAccelListeners.remove(listener);
        try {
            java.lang.reflect.Method m = SensorManager.class.getMethod("unregisterListenerImpl", SensorEventListener.class, Sensor.class);
            return (Boolean) m.invoke(mReal, listener, sensor);
        } catch (Exception e) { return false; }
    }

    public Sensor getDefaultSensor(int type) {
        Log.w(TAG, "getDefaultSensor(" + type + ") IN");
        Sensor s = mReal != null ? mReal.getDefaultSensor(type) : null;
        Log.w(TAG, "getDefaultSensor(" + type + ") real=" + s + " stepEnabled=" + mStepEnabled);
        if (s != null) return s;
        return makeFakeSensorIfEnabled(type);
    }
    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        Log.w(TAG, "getDefaultSensor(" + type + "," + wakeUp + ") IN");
        Sensor s = mReal != null ? mReal.getDefaultSensor(type, wakeUp) : null;
        Log.w(TAG, "getDefaultSensor(" + type + "," + wakeUp + ") real=" + s + " stepEnabled=" + mStepEnabled);
        if (s != null) return s;
        return makeFakeSensorIfEnabled(type);
    }
    public List<Sensor> getSensorList(int type) {
        List<Sensor> list = mReal != null ? mReal.getSensorList(type) : null;
        if (list != null && !list.isEmpty()) return list;
        if (mReal != null) {
            list = mReal.getSensorList(type);
            if (list != null && !list.isEmpty()) return list;
        }
        Sensor fake = makeFakeSensorIfEnabled(type);
        if (fake != null) return java.util.Collections.singletonList(fake);
        return java.util.Collections.emptyList();
    }

    private static Object sUnsafe;
    private static Object getUnsafe() {
        if (sUnsafe == null) {
            try { Class<?> clz = Class.forName("sun.misc.Unsafe");
                Field f = clz.getDeclaredField("theUnsafe"); f.setAccessible(true); sUnsafe = f.get(null); }
            catch (Exception e) { return null; }
        }
        return sUnsafe;
    }

    private Sensor makeFakeSensorIfEnabled(int type) {
        Log.w(TAG, "makeFakeSensorIfEnabled(" + type + ") stepEnabled=" + mStepEnabled + " accelEnabled=" + mAccelEnabled);
        if (!isSimulatedType(type)) return null;
        Object u = getUnsafe();
        if (u == null) { Log.w(TAG, "makeFakeSensorIfEnabled: Unsafe not available"); return null; }
        try {
            Sensor fake = (Sensor) u.getClass().getMethod("allocateInstance", Class.class).invoke(u, Sensor.class);
            setField(fake, "mName", sensorName(type));
            setField(fake, "mVendor", "Kail Sandbox");
            setField(fake, "mVersion", 1);
            setField(fake, "mType", type);
            setField(fake, "mResolution", 1.0f);
            setField(fake, "mMaxRange", 1000000.0f);
            setField(fake, "mPower", 0.0f);
            setField(fake, "mMinDelay", 20000);
            return fake;
        } catch (Exception e) {
            Slog.w(TAG, "makeFakeSensor error for type " + type + ": " + e.getMessage());
            return null;
        }
    }

    private boolean isSimulatedType(int type) {
        switch (type) {
            case Sensor.TYPE_STEP_COUNTER:
            case Sensor.TYPE_STEP_DETECTOR:
                return mStepEnabled;
            case Sensor.TYPE_ACCELEROMETER:
                return mAccelEnabled;
            default:
                return false;
        }
    }

    private static String sensorName(int type) {
        switch (type) {
            case Sensor.TYPE_STEP_COUNTER: return "Kail Step Counter";
            case Sensor.TYPE_STEP_DETECTOR: return "Kail Step Detector";
            case Sensor.TYPE_ACCELEROMETER: return "Kail Accelerometer";
            default: return "Kail Sensor";
        }
    }

    private static void setField(Object obj, String name, Object val) throws Exception {
        String[] candidates = {name, "_" + name, "m" + name.substring(0, 1).toUpperCase() + name.substring(1)};
        for (String cn : candidates) {
            try { Field f = Sensor.class.getDeclaredField(cn); f.setAccessible(true);
                if (val instanceof Integer) f.setInt(obj, (Integer) val);
                else if (val instanceof Float) f.setFloat(obj, (Float) val);
                else f.set(obj, val);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        for (Field f : Sensor.class.getDeclaredFields()) {
            String fn = f.getName().toLowerCase();
            String ln = name.toLowerCase();
            if (fn.contains(ln) || ln.contains(fn)) {
                f.setAccessible(true);
                if (val instanceof Integer) f.setInt(obj, (Integer) val);
                else if (val instanceof Float) f.setFloat(obj, (Float) val);
                else f.set(obj, val);
                return;
            }
        }
    }
    public int getSensors() { return mReal != null ? mReal.getSensors() : 0; }
    public boolean flush(SensorEventListener l) { return mReal != null && mReal.flush(l); }
    public boolean isDynamicSensorDiscoverySupported() { return mReal != null && mReal.isDynamicSensorDiscoverySupported(); }
    public void registerDynamicSensorCallback(DynamicSensorCallback cb, Handler h) { if (mReal != null) mReal.registerDynamicSensorCallback(cb, h); }
    public void unregisterDynamicSensorCallback(DynamicSensorCallback cb) { if (mReal != null) mReal.unregisterDynamicSensorCallback(cb); }
}
