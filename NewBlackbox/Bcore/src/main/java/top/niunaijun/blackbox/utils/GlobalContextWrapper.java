package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.SensorManager;

import top.niunaijun.blackbox.BlackBoxCore;
import android.hardware.BridgedSensorManager;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;


public class GlobalContextWrapper extends ContextWrapper {
    private static final String TAG = "GlobalContextWrapper";
    
    private final Context fallbackContext;
    private final String packageName;
    
    public GlobalContextWrapper(Context base, String packageName) {
        super(base != null ? base : BlackBoxCore.getContext());
        this.fallbackContext = BlackBoxCore.getContext();
        this.packageName = packageName;
    }
    
    public GlobalContextWrapper(Context base) {
        this(base, base != null ? base.getPackageName() : "unknown");
    }
    
    @Override
    public Context getBaseContext() {
        Context base = super.getBaseContext();
        return base != null ? base : fallbackContext;
    }
    
    @Override
    public String getPackageName() {
        return packageName != null ? packageName : 
               (getBaseContext() != null ? getBaseContext().getPackageName() : "unknown");
    }
    
    @Override
    public Resources getResources() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getResources();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting resources from base context: " + e.getMessage());
        }
        
        
        try {
            return fallbackContext.getResources();
        } catch (Exception e) {
            Slog.w(TAG, "Error getting fallback resources: " + e.getMessage());
            
            try {
                return new Resources(null, null, null);
            } catch (Exception e2) {
                Slog.w(TAG, "Error creating minimal resources: " + e2.getMessage());
                
                return null;
            }
        }
    }
    
    @Override
    public PackageManager getPackageManager() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getPackageManager();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting package manager from base context: " + e.getMessage());
        }
        
        
        try {
            return fallbackContext.getPackageManager();
        } catch (Exception e) {
            Slog.w(TAG, "Error getting fallback package manager: " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public Context getApplicationContext() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getApplicationContext();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting application context from base context: " + e.getMessage());
        }
        
        return fallbackContext.getApplicationContext();
    }
    
    @Override
    public ClassLoader getClassLoader() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getClassLoader();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting class loader from base context: " + e.getMessage());
        }
        
        return fallbackContext.getClassLoader();
    }
    
    @Override
    public android.content.ContentResolver getContentResolver() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getContentResolver();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting content resolver from base context: " + e.getMessage());
        }
        
        return fallbackContext.getContentResolver();
    }
    
    @Override
    public AssetManager getAssets() {
        try {
            Context base = getBaseContext();
            if (base != null) {
                return base.getAssets();
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting assets from base context: " + e.getMessage());
        }
        
        return fallbackContext.getAssets();
    }
    
    @Override
    public Object getSystemService(String name) {
        try {
            Context base = getBaseContext();
            if (base != null) {
                Object service = base.getSystemService(name);
                if (Context.SENSOR_SERVICE.equals(name) && service instanceof SensorManager) {
                    return wrapSensorManager((SensorManager) service);
                }
                return service;
            }
        } catch (Exception e) {
            Slog.w(TAG, "Error getting system service from base context: " + e.getMessage());
        }

        Object service = fallbackContext.getSystemService(name);
        if (Context.SENSOR_SERVICE.equals(name) && service instanceof SensorManager) {
            return wrapSensorManager((SensorManager) service);
        }
        return service;
    }

    private Object wrapSensorManager(SensorManager real) {
        try {
            top.niunaijun.blackbox.entity.location.BSensorConfig cfg =
                    BLocationManager.get().getSensorConfig(
                            top.niunaijun.blackbox.app.BActivityThread.getUserId(),
                            top.niunaijun.blackbox.app.BActivityThread.getAppPackageName());
            if (cfg == null) cfg = BLocationManager.get().getGlobalSensorConfig();
            if (cfg != null && (cfg.stepEnabled || cfg.accelEnabled)) {
                BridgedSensorManager wrapper = BridgedSensorManager.wrap(real);
                if (wrapper != null) {
                    wrapper.setConfig(cfg.stepEnabled, cfg.stepCadenceSpm,
                            cfg.accelEnabled, cfg.accelAmplitude);
                    Slog.i(TAG, "SENSOR_SERVICE wrapped: step=" + cfg.stepEnabled
                            + " accel=" + cfg.accelEnabled);
                    return wrapper;
                }
            }
        } catch (Exception e) {
            Slog.w(TAG, "wrapSensorManager error: " + e.getMessage());
        }
        return real;
    }
    
    
    public static Context createSafeContext(Context context, String packageName) {
        if (context == null) {
            return new GlobalContextWrapper(null, packageName);
        }
        
        if (context instanceof GlobalContextWrapper) {
            return context;
        }
        
        if (context instanceof ContextWrapper) {
            
            try {
                Context baseContext = ((ContextWrapper) context).getBaseContext();
                if (baseContext == null) {
                    return new GlobalContextWrapper(context, packageName);
                }
            } catch (Exception e) {
                Slog.w(TAG, "Error checking base context: " + e.getMessage());
                return new GlobalContextWrapper(context, packageName);
            }
        }
        
        return new GlobalContextWrapper(context, packageName);
    }
    
    
    public static Context createSafeContext(Context context) {
        String packageName = context != null ? context.getPackageName() : "unknown";
        return createSafeContext(context, packageName);
    }
}
