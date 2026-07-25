package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.RemoteException;

import top.niunaijun.blackbox.utils.Slog;

import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.location.IBLocationManagerService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BGnssStatus;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BSensorConfig;


public class BLocationManager extends BlackManager<IBLocationManagerService> {
    private static final String TAG = "[sandbox] BLocationManager";
    private static final BLocationManager sLocationManager = new BLocationManager();

    public static final int CLOSE_MODE = 0;
    public static final int GLOBAL_MODE = 1;
    public static final int OWN_MODE = 2;

    public static BLocationManager get() {
        return sLocationManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.LOCATION_MANAGER;
    }

    public static boolean isFakeLocationEnable() {
        boolean enabled = get().getPattern(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) != CLOSE_MODE;
        Slog.v(TAG, "isFakeLocationEnable userId=" + BActivityThread.getUserId() + " pkg=" + BActivityThread.getAppPackageName() + " -> " + enabled);
        return enabled;
    }

    public static void disableFakeLocation(int userId,String pkg){
        Slog.i(TAG, "disableFakeLocation userId=" + userId + " pkg=" + pkg);
        get().setPattern(userId,pkg,CLOSE_MODE);
    }

    public void setPattern(int userId, String pkg, int pattern) {
        String patternStr = pattern == CLOSE_MODE ? "CLOSE" : pattern == GLOBAL_MODE ? "GLOBAL" : "OWN";
        Slog.i(TAG, "setPattern userId=" + userId + " pkg=" + pkg + " pattern=" + pattern + "(" + patternStr + ")");
        try {
            getService().setPattern(userId, pkg, pattern);
            Slog.v(TAG, "setPattern succeeded");
        } catch (RemoteException e) {
            Slog.e(TAG, "setPattern failed", e);
        }
    }

    public int getPattern(int userId, String pkg) {
        try {
            int pattern = getService().getPattern(userId, pkg);
            String patternStr = pattern == CLOSE_MODE ? "CLOSE" : pattern == GLOBAL_MODE ? "GLOBAL" : "OWN";
            Slog.v(TAG, "getPattern userId=" + userId + " pkg=" + pkg + " -> " + pattern + "(" + patternStr + ")");
            return pattern;
        } catch (RemoteException e) {
            Slog.e(TAG, "getPattern failed", e);
        }
        return CLOSE_MODE;
    }

    public void setCell(int userId, String pkg, BCell cell) {
        Slog.v(TAG, "setCell userId=" + userId + " pkg=" + pkg + " cell=" + cell);
        try {
            getService().setCell(userId, pkg, cell);
        } catch (RemoteException e) {
            Slog.e(TAG, "setCell failed", e);
        }
    }

    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        Slog.v(TAG, "setAllCell userId=" + userId + " pkg=" + pkg + " count=" + (cells != null ? cells.size() : 0));
        try {
            getService().setAllCell(userId, pkg, cells);
        } catch (RemoteException e) {
            Slog.e(TAG, "setAllCell failed", e);
        }
    }

    public List<BCell> getNeighboringCell(int userId, String pkg) {
        try {
            List<BCell> result = getService().getNeighboringCell(userId, pkg);
            Slog.v(TAG, "getNeighboringCell userId=" + userId + " pkg=" + pkg + " -> count=" + (result != null ? result.size() : 0));
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getNeighboringCell failed", e);
        }
        return null;
    }

    public List<BCell> getGlobalNeighboringCell() {
        try {
            List<BCell> result = getService().getGlobalNeighboringCell();
            Slog.v(TAG, "getGlobalNeighboringCell -> count=" + (result != null ? result.size() : 0));
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getGlobalNeighboringCell failed", e);
        }
        return null;
    }

    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        Slog.v(TAG, "setNeighboringCell userId=" + userId + " pkg=" + pkg + " count=" + (cells != null ? cells.size() : 0));
        try {
            getService().setNeighboringCell(userId, pkg, cells);
        } catch (RemoteException e) {
            Slog.e(TAG, "setNeighboringCell failed", e);
        }
    }

    public void setGlobalCell(BCell cell) {
        Slog.v(TAG, "setGlobalCell cell=" + cell);
        try {
            getService().setGlobalCell(cell);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalCell failed", e);
        }
    }

    public void setGlobalAllCell(List<BCell> cells) {
        Slog.v(TAG, "setGlobalAllCell count=" + (cells != null ? cells.size() : 0));
        try {
            getService().setGlobalAllCell(cells);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalAllCell failed", e);
        }
    }

    public void setGlobalNeighboringCell(List<BCell> cells) {
        Slog.v(TAG, "setGlobalNeighboringCell count=" + (cells != null ? cells.size() : 0));
        try {
            getService().setGlobalNeighboringCell(cells);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalNeighboringCell failed", e);
        }
    }

    public BCell getCell(int userId, String pkg) {
        try {
            BCell result = getService().getCell(userId, pkg);
            Slog.v(TAG, "getCell userId=" + userId + " pkg=" + pkg + " -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getCell failed", e);
        }
        return null;
    }

    public List<BCell> getAllCell(int userId, String pkg) {
        try {
            List<BCell> result = getService().getAllCell(userId, pkg);
            Slog.v(TAG, "getAllCell userId=" + userId + " pkg=" + pkg + " -> count=" + (result != null ? result.size() : 0));
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getAllCell failed", e);
        }
        return new ArrayList<>();
    }

    public void setLocation(int userId, String pkg, BLocation location) {
        Slog.i(TAG, "setLocation userId=" + userId + " pkg=" + pkg + " loc=(" + location + ")");
        try {
            getService().setLocation(userId, pkg, location);
        } catch (RemoteException e) {
            Slog.e(TAG, "setLocation failed", e);
        }
    }

    public BLocation getLocation(int userId, String pkg) {
        try {
            BLocation result = getService().getLocation(userId, pkg);
            Slog.v(TAG, "getLocation userId=" + userId + " pkg=" + pkg + " -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getLocation failed", e);
        }
        return null;
    }

    public void setGlobalLocation(BLocation location) {
        Slog.i(TAG, "setGlobalLocation loc=(" + location + ")");
        try {
            getService().setGlobalLocation(location);
            Slog.v(TAG, "setGlobalLocation succeeded");
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalLocation failed", e);
        }
    }

    public BLocation getGlobalLocation() {
        try {
            BLocation result = getService().getGlobalLocation();
            Slog.v(TAG, "getGlobalLocation -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getGlobalLocation failed", e);
        }
        return null;
    }

    public void requestLocationUpdates(IBinder listener) {
        String pkg = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        Slog.i(TAG, "requestLocationUpdates pkg=" + pkg + " userId=" + userId + " listener=" + listener);
        try {
            getService().requestLocationUpdates(listener, pkg, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "requestLocationUpdates failed", e);
        }
    }

    public void removeUpdates(IBinder listener) {
        Slog.i(TAG, "removeUpdates listener=" + listener);
        try {
            getService().removeUpdates(listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "removeUpdates failed", e);
        }
    }

    public void setGnssStatus(int userId, String pkg, BGnssStatus status) {
        Slog.i(TAG, "setGnssStatus userId=" + userId + " pkg=" + pkg + " status=" + status);
        try {
            getService().setGnssStatus(userId, pkg, status);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGnssStatus failed", e);
        }
    }

    public BGnssStatus getGnssStatus(int userId, String pkg) {
        try {
            BGnssStatus result = getService().getGnssStatus(userId, pkg);
            Slog.v(TAG, "getGnssStatus userId=" + userId + " pkg=" + pkg + " -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getGnssStatus failed", e);
        }
        return null;
    }

    public void setGlobalGnssStatus(BGnssStatus status) {
        Slog.i(TAG, "setGlobalGnssStatus status=" + status);
        try {
            getService().setGlobalGnssStatus(status);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalGnssStatus failed", e);
        }
    }

    public BGnssStatus getGlobalGnssStatus() {
        try {
            BGnssStatus result = getService().getGlobalGnssStatus();
            Slog.v(TAG, "getGlobalGnssStatus -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getGlobalGnssStatus failed", e);
        }
        return null;
    }

    public void registerGnssStatusCallback(IBinder listener) {
        String pkg = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        Slog.i(TAG, "registerGnssStatusCallback pkg=" + pkg + " userId=" + userId + " listener=" + listener);
        try {
            getService().registerGnssStatusCallback(listener, pkg, userId);
        } catch (RemoteException e) {
            Slog.e(TAG, "registerGnssStatusCallback failed", e);
        }
    }

    public void unregisterGnssStatusCallback(IBinder listener) {
        Slog.i(TAG, "unregisterGnssStatusCallback listener=" + listener);
        try {
            getService().unregisterGnssStatusCallback(listener);
        } catch (RemoteException e) {
            Slog.e(TAG, "unregisterGnssStatusCallback failed", e);
        }
    }

    public void setSensorConfig(int userId, String pkg, BSensorConfig config) {
        Slog.i(TAG, "setSensorConfig userId=" + userId + " pkg=" + pkg + " config=" + config);
        try {
            getService().setSensorConfig(userId, pkg, config);
        } catch (RemoteException e) {
            Slog.e(TAG, "setSensorConfig failed", e);
        }
    }

    public BSensorConfig getSensorConfig(int userId, String pkg) {
        try {
            BSensorConfig result = getService().getSensorConfig(userId, pkg);
            Slog.v(TAG, "getSensorConfig userId=" + userId + " pkg=" + pkg + " -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getSensorConfig failed", e);
        }
        return null;
    }

    public void setGlobalSensorConfig(BSensorConfig config) {
        Slog.i(TAG, "setGlobalSensorConfig config=" + config);
        try {
            getService().setGlobalSensorConfig(config);
        } catch (RemoteException e) {
            Slog.e(TAG, "setGlobalSensorConfig failed", e);
        }
    }

    public BSensorConfig getGlobalSensorConfig() {
        try {
            BSensorConfig result = getService().getGlobalSensorConfig();
            Slog.v(TAG, "getGlobalSensorConfig -> " + result);
            return result;
        } catch (RemoteException e) {
            Slog.e(TAG, "getGlobalSensorConfig failed", e);
        }
        return null;
    }
}
