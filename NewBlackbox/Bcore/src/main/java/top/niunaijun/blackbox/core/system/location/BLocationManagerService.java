package top.niunaijun.blackbox.core.system.location;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import black.android.location.BRILocationListener;
import black.android.location.BRILocationListenerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.entity.location.BCell;
import top.niunaijun.blackbox.entity.location.BGnssStatus;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.entity.location.BLocationConfig;
import top.niunaijun.blackbox.entity.location.BSensorConfig;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;


public class BLocationManagerService extends IBLocationManagerService.Stub implements ISystemService {
    public static final String TAG = "[sandbox] BLocationManagerService";

    private static final BLocationManagerService sService = new BLocationManagerService();
    private final SparseArray<HashMap<String, BLocationConfig>> mLocationConfigs = new SparseArray<>();
    private final BLocationConfig mGlobalConfig = new BLocationConfig();
    private final Map<IBinder, LocationRecord> mLocationListeners = new HashMap<>();
    private final Map<IBinder, LocationRecord> mGnssStatusListeners = new HashMap<>();
    private final Executor mThreadPool = Executors.newCachedThreadPool();
    private final Executor mGnssThreadPool = Executors.newCachedThreadPool();
    private final Random mRandom = new Random();

    private static final int SVID_SHIFT_WIDTH = 12;
    private static final int CONSTELLATION_TYPE_SHIFT_WIDTH = 8;
    private static final int CONSTELLATION_TYPE_MASK = 0xf;
    private static final int CONSTELLATION_GPS = 1;
    private static final int CONSTELLATION_BEIDOU = 5;

    private static final float GPS_L1_FREQ = 1.57542e9f;
    private static final float BDS_B1I_FREQ = 1.561098e9f;

    // Root-mode stable constellation state (reshuffled ~every 90s)
    private static final Object sGnssLock = new Object();
    private static int[] sGnssConstellation;
    private static int[] sGnssSvid;
    private static float[] sGnssCarrierFreq;
    private static float[] sGnssBaseCn0;
    private static float[] sGnssElev;
    private static float[] sGnssAz;
    private static boolean[] sGnssUsedInFix;
    private static long sGnssLastReshuffle = 0;

    public static BLocationManagerService get() {
        return sService;
    }

    private BLocationConfig getOrCreateConfig(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            HashMap<String, BLocationConfig> pkgs = mLocationConfigs.get(userId);
            if (pkgs == null) {
                pkgs = new HashMap<>();
                mLocationConfigs.put(userId, pkgs);
            }
            BLocationConfig config = pkgs.get(pkg);
            if (config == null) {
                config = new BLocationConfig();
                config.pattern = BLocationManager.CLOSE_MODE;
                pkgs.put(pkg, config);
            }
            return config;
        }
    }

    public int getPattern(int userId, String pkg) {
        BLocationConfig config = resolveConfig(userId, pkg);
        Slog.v(TAG, "getPattern userId=" + userId + " pkg=" + pkg + " -> " + config.pattern);
        return config.pattern;
    }

    @Override
    public void setPattern(int userId, String pkg, int pattern) {
        String patternStr = pattern == BLocationManager.CLOSE_MODE ? "CLOSE" : pattern == BLocationManager.GLOBAL_MODE ? "GLOBAL" : "OWN";
        Slog.i(TAG, "setPattern userId=" + userId + " pkg=" + pkg + " pattern=" + pattern + "(" + patternStr + ")");
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).pattern = pattern;
            save();
        }
        Slog.v(TAG, "setPattern saved");
    }

    @Override
    public void setCell(int userId, String pkg, BCell cell) {
        Slog.v(TAG, "setCell userId=" + userId + " pkg=" + pkg + " cell=" + cell);
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).cell = cell;
            save();
        }
    }

    @Override
    public void setAllCell(int userId, String pkg, List<BCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public void setNeighboringCell(int userId, String pkg, List<BCell> cells) {
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).allCell = cells;
            save();
        }
    }

    @Override
    public List<BCell> getNeighboringCell(int userId, String pkg) {
        synchronized (mLocationConfigs) {
            return getOrCreateConfig(userId, pkg).allCell;
        }
    }

    @Override
    public void setGlobalCell(BCell cell) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.cell = cell;
            save();
        }
    }

    @Override
    public void setGlobalAllCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.allCell = cells;
            save();
        }
    }

    @Override
    public void setGlobalNeighboringCell(List<BCell> cells) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.neighboringCellInfo = cells;
            save();
        }
    }

    @Override
    public List<BCell> getGlobalNeighboringCell() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.neighboringCellInfo;
        }
    }

    private BLocationConfig resolveConfig(int userId, String pkg) {
        BLocationConfig config = getOrCreateConfig(userId, pkg);
        if (config.pattern == BLocationManager.CLOSE_MODE) {
            synchronized (mLocationConfigs) {
                HashMap<String, BLocationConfig> pkgs = mLocationConfigs.get(userId);
                if (pkgs != null) {
                    BLocationConfig globalDefault = pkgs.get("");
                    if (globalDefault != null && globalDefault.pattern == BLocationManager.GLOBAL_MODE) {
                        Slog.v(TAG, "resolveConfig: fallback to global default for userId=" + userId + " pkg=" + pkg);
                        return globalDefault;
                    }
                }
            }
        }
        Slog.v(TAG, "resolveConfig: userId=" + userId + " pkg=" + pkg + " -> " + config);
        return config;
    }

    @Override
    public BCell getCell(int userId, String pkg) {
        BLocationConfig config = resolveConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config.cell;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig.cell;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public List<BCell> getAllCell(int userId, String pkg) {
        BLocationConfig config = resolveConfig(userId, pkg);
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                return config.allCell;
            case BLocationManager.GLOBAL_MODE:
                return mGlobalConfig.allCell;
            case BLocationManager.CLOSE_MODE:
            default:
                return null;
        }
    }

    @Override
    public void setLocation(int userId, String pkg, BLocation location) {
        Slog.i(TAG, "setLocation userId=" + userId + " pkg=" + pkg + " loc=" + location);
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).location = location;
            save();
        }
    }

    @Override
    public BLocation getLocation(int userId, String pkg) {
        BLocationConfig config = resolveConfig(userId, pkg);
        BLocation result;
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                result = config.location;
                break;
            case BLocationManager.GLOBAL_MODE:
                result = mGlobalConfig.location;
                break;
            case BLocationManager.CLOSE_MODE:
            default:
                result = null;
                break;
        }
        Slog.v(TAG, "getLocation userId=" + userId + " pkg=" + pkg + " pattern=" + config.pattern + " -> " + result);
        return result;
    }

    @Override
    public void setGlobalLocation(BLocation location) {
        Slog.i(TAG, "setGlobalLocation " + (location != null ? location.toString() : "null"));
        synchronized (mGlobalConfig) {
            mGlobalConfig.location = location;
            save();
        }
        Slog.v(TAG, "setGlobalLocation saved");
    }

    @Override
    public BLocation getGlobalLocation() {
        synchronized (mGlobalConfig) {
            BLocation loc = mGlobalConfig.location;
            Slog.v(TAG, "getGlobalLocation -> " + loc);
            return loc;
        }
    }

    @Override
    public void requestLocationUpdates(IBinder listener, String packageName, int userId) throws RemoteException {
        Slog.i(TAG, "requestLocationUpdates pkg=" + packageName + " userId=" + userId + " listener=" + listener);
        if (listener == null || !listener.pingBinder()) {
            Slog.w(TAG, "requestLocationUpdates: listener is null or dead");
            return;
        }
        if (mLocationListeners.containsKey(listener)) {
            Slog.v(TAG, "requestLocationUpdates: listener already registered, skip");
            return;
        }
        listener.linkToDeath(new DeathRecipient() {
            @Override
            public void binderDied() {
                Slog.i(TAG, "requestLocationUpdates: listener binderDied, removing pkg=" + packageName);
                listener.unlinkToDeath(this, 0);
                mLocationListeners.remove(listener);
            }
        }, 0);
        LocationRecord record = new LocationRecord(packageName, userId);
        mLocationListeners.put(listener, record);
        Slog.i(TAG, "requestLocationUpdates: registered, total listeners=" + mLocationListeners.size());
        addTask(listener);
    }

    @Override
    public void removeUpdates(IBinder listener) throws RemoteException {
        Slog.i(TAG, "removeUpdates listener=" + listener);
        if (listener == null || !listener.pingBinder()) {
            Slog.w(TAG, "removeUpdates: listener is null or dead");
            return;
        }
        LocationRecord removed = mLocationListeners.remove(listener);
        Slog.i(TAG, "removeUpdates: removed=" + (removed != null) + " remaining=" + mLocationListeners.size());
    }

    @Override
    public void setGnssStatus(int userId, String pkg, BGnssStatus status) throws RemoteException {
        Slog.i(TAG, "setGnssStatus userId=" + userId + " pkg=" + pkg + " status=" + status);
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).gnssStatus = status;
            save();
        }
    }

    @Override
    public BGnssStatus getGnssStatus(int userId, String pkg) throws RemoteException {
        BLocationConfig config = resolveConfig(userId, pkg);
        BGnssStatus result;
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                result = config.gnssStatus;
                break;
            case BLocationManager.GLOBAL_MODE:
                result = mGlobalConfig.gnssStatus;
                break;
            case BLocationManager.CLOSE_MODE:
            default:
                result = null;
                break;
        }
        Slog.v(TAG, "getGnssStatus userId=" + userId + " pkg=" + pkg + " pattern=" + config.pattern + " -> " + result);
        return result;
    }

    @Override
    public void setGlobalGnssStatus(BGnssStatus status) throws RemoteException {
        Slog.i(TAG, "setGlobalGnssStatus " + status);
        synchronized (mGlobalConfig) {
            mGlobalConfig.gnssStatus = status;
            save();
        }
    }

    @Override
    public BGnssStatus getGlobalGnssStatus() throws RemoteException {
        synchronized (mGlobalConfig) {
            BGnssStatus status = mGlobalConfig.gnssStatus;
            Slog.v(TAG, "getGlobalGnssStatus -> " + status);
            return status;
        }
    }

    @Override
    public void registerGnssStatusCallback(IBinder listener, String packageName, int userId) throws RemoteException {
        Slog.i(TAG, "registerGnssStatusCallback pkg=" + packageName + " userId=" + userId + " listener=" + listener);
        if (listener == null || !listener.pingBinder()) {
            Slog.w(TAG, "registerGnssStatusCallback: listener is null or dead");
            return;
        }
        if (mGnssStatusListeners.containsKey(listener)) {
            Slog.v(TAG, "registerGnssStatusCallback: listener already registered, skip");
            return;
        }
        listener.linkToDeath(new DeathRecipient() {
            @Override
            public void binderDied() {
                Slog.i(TAG, "registerGnssStatusCallback: listener binderDied, removing pkg=" + packageName);
                listener.unlinkToDeath(this, 0);
                mGnssStatusListeners.remove(listener);
            }
        }, 0);
        LocationRecord record = new LocationRecord(packageName, userId);
        mGnssStatusListeners.put(listener, record);
        Slog.i(TAG, "registerGnssStatusCallback: registered, total=" + mGnssStatusListeners.size());
        addGnssTask(listener);
    }

    @Override
    public void unregisterGnssStatusCallback(IBinder listener) throws RemoteException {
        Slog.i(TAG, "unregisterGnssStatusCallback listener=" + listener);
        if (listener == null || !listener.pingBinder()) {
            Slog.w(TAG, "unregisterGnssStatusCallback: listener is null or dead");
            return;
        }
        LocationRecord removed = mGnssStatusListeners.remove(listener);
        Slog.i(TAG, "unregisterGnssStatusCallback: removed=" + (removed != null) + " remaining=" + mGnssStatusListeners.size());
    }

    @Override
    public void setSensorConfig(int userId, String pkg, BSensorConfig config) throws RemoteException {
        Slog.i(TAG, "setSensorConfig userId=" + userId + " pkg=" + pkg + " config=" + config);
        synchronized (mLocationConfigs) {
            getOrCreateConfig(userId, pkg).sensorConfig = config;
            save();
        }
    }

    @Override
    public BSensorConfig getSensorConfig(int userId, String pkg) throws RemoteException {
        BLocationConfig config = resolveConfig(userId, pkg);
        BSensorConfig result;
        switch (config.pattern) {
            case BLocationManager.OWN_MODE:
                result = config.sensorConfig;
                break;
            case BLocationManager.GLOBAL_MODE:
                result = mGlobalConfig.sensorConfig;
                break;
            case BLocationManager.CLOSE_MODE:
            default:
                result = null;
                break;
        }
        Slog.v(TAG, "getSensorConfig userId=" + userId + " pkg=" + pkg + " -> " + result);
        return result;
    }

    @Override
    public void setGlobalSensorConfig(BSensorConfig config) throws RemoteException {
        Slog.i(TAG, "setGlobalSensorConfig " + config);
        synchronized (mGlobalConfig) {
            mGlobalConfig.sensorConfig = config;
            save();
        }
    }

    @Override
    public BSensorConfig getGlobalSensorConfig() throws RemoteException {
        synchronized (mGlobalConfig) {
            BSensorConfig config = mGlobalConfig.sensorConfig;
            Slog.v(TAG, "getGlobalSensorConfig -> " + config);
            return config;
        }
    }

    private void addTask(IBinder locationListener) {
        mThreadPool.execute(() -> {
            BLocation lastLocation = null;
            long l = System.currentTimeMillis();
            Slog.v(TAG, "addTask: listener thread started");
            while (locationListener.pingBinder()) {
                IInterface iInterface = BRILocationListenerStub.get().asInterface(locationListener);
                LocationRecord locationRecord = mLocationListeners.get(locationListener);
                if (locationRecord == null) {
                    Slog.v(TAG, "addTask: locationRecord is null, waiting...");
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    continue;
                }
                BLocation location = getLocation(locationRecord.userId, locationRecord.packageName);
                if (location == null) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    continue;
                }
                if (location.equals(lastLocation) && (System.currentTimeMillis() - l) < 3000) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                lastLocation = location;
                l = System.currentTimeMillis();
                Slog.v(TAG, "addTask: dispatching to listener pkg=" + locationRecord.packageName
                        + " lat=" + location.getLatitude() + " lng=" + location.getLongitude());
                BlackBoxCore.get().getHandler().post(() -> {
                    try {
                        android.location.Location sysLoc = location.convert2SystemLocation();
                        boolean dispatched = false;
                        for (Method m : iInterface.getClass().getMethods()) {
                            if ("onLocationChanged".equals(m.getName()) && m.getParameterCount() == 2) {
                                m.invoke(iInterface, Collections.singletonList(sysLoc), null);
                                dispatched = true;
                                break;
                            }
                        }
                        if (!dispatched) {
                            BRILocationListener.get(iInterface).onLocationChanged(sysLoc);
                        }
                        Slog.v(TAG, "addTask: dispatched successfully");
                    } catch (Exception e) {
                        Slog.e(TAG, "addTask: dispatch failed", e);
                    }
                });
            }
            Slog.v(TAG, "addTask: listener binder died, thread exiting");
        });
    }

    private void addGnssTask(IBinder gnssListener) {
        mGnssThreadPool.execute(() -> {
            Slog.v(TAG, "addGnssTask: listener thread started");
            while (gnssListener.pingBinder()) {
                LocationRecord record = mGnssStatusListeners.get(gnssListener);
                if (record == null) {
                    Slog.v(TAG, "addGnssTask: record is null, waiting...");
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    continue;
                }
                BLocationConfig config = resolveConfig(record.userId, record.packageName);
                if (config.pattern == BLocationManager.CLOSE_MODE) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    continue;
                }
                if (!BLocationManager.isFakeLocationEnable()) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    continue;
                }
                final BGnssStatus gnssStatus;
                try {
                    BGnssStatus s = getGnssStatus(record.userId, record.packageName);
                    if (s == null || s.isEmpty()) {
                        s = generateDefaultGnssStatus();
                    }
                    gnssStatus = s;
                } catch (RemoteException e) {
                    Slog.w(TAG, "addGnssTask: getGnssStatus failed", e);
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    continue;
                }
                final IBinder fListener = gnssListener;
                Slog.v(TAG, "addGnssTask: dispatching gnss status to pkg=" + record.packageName);
                BlackBoxCore.get().getHandler().post(() -> {
                    try {
                        dispatchGnssStatus(fListener, gnssStatus);
                        Slog.v(TAG, "addGnssTask: gnss dispatched successfully");
                    } catch (Exception e) {
                        Slog.e(TAG, "addGnssTask: dispatch failed", e);
                    }
                });
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
            Slog.v(TAG, "addGnssTask: listener binder died, thread exiting");
        });
    }

    private void dispatchGnssStatus(IBinder gnssListener, BGnssStatus gnssStatus) throws Exception {
        Class<?> stubClass = Class.forName("android.location.IGnssStatusListener$Stub");
        Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
        Object listener = asInterface.invoke(null, gnssListener);
        if (listener == null) {
            Slog.w(TAG, "dispatchGnssStatus: listener is null");
            return;
        }
        boolean dispatched = false;
        for (Method m : listener.getClass().getMethods()) {
            if (!"onSvStatusChanged".equals(m.getName())) continue;
            Class<?>[] paramTypes = m.getParameterTypes();
            try {
                if (paramTypes.length == 5) {
                    m.invoke(listener, gnssStatus.getSvCount(), gnssStatus.getSvidWithFlags(),
                            gnssStatus.getCn0s(), gnssStatus.getElevations(), gnssStatus.getAzimuths());
                    dispatched = true;
                    break;
                } else if (paramTypes.length == 6) {
                    float[] carrierFreqs = gnssStatus.getCarrierFreqs();
                    if (carrierFreqs == null || carrierFreqs.length == 0) {
                        carrierFreqs = new float[gnssStatus.getSvCount()];
                        for (int i = 0; i < carrierFreqs.length; i++) {
                            carrierFreqs[i] = GPS_L1_FREQ;
                        }
                    }
                    m.invoke(listener, gnssStatus.getSvCount(), gnssStatus.getSvidWithFlags(),
                            gnssStatus.getCn0s(), gnssStatus.getElevations(), gnssStatus.getAzimuths(),
                            carrierFreqs);
                    dispatched = true;
                    break;
                } else if (paramTypes.length == 7) {
                    float[] carrierFreqs = gnssStatus.getCarrierFreqs();
                    if (carrierFreqs == null || carrierFreqs.length == 0) {
                        carrierFreqs = new float[gnssStatus.getSvCount()];
                        for (int i = 0; i < carrierFreqs.length; i++) {
                            carrierFreqs[i] = GPS_L1_FREQ;
                        }
                    }
                    float[] basebandCn0s = gnssStatus.getBasebandCn0s();
                    if (basebandCn0s == null || basebandCn0s.length == 0) {
                        basebandCn0s = new float[gnssStatus.getSvCount()];
                        for (int i = 0; i < basebandCn0s.length; i++) {
                            basebandCn0s[i] = gnssStatus.getCn0s()[i] - 2.0f - mRandom.nextFloat() * 3.0f;
                        }
                    }
                    m.invoke(listener, gnssStatus.getSvCount(), gnssStatus.getSvidWithFlags(),
                            gnssStatus.getCn0s(), gnssStatus.getElevations(), gnssStatus.getAzimuths(),
                            carrierFreqs, basebandCn0s);
                    dispatched = true;
                    break;
                } else if (paramTypes.length == 1
                        && paramTypes[0].getName().equals("android.location.GnssStatus")) {
                    Object gnssStatusObj = buildGnssStatusObject(gnssStatus);
                    if (gnssStatusObj != null) {
                        m.invoke(listener, gnssStatusObj);
                        dispatched = true;
                    }
                    break;
                }
            } catch (Exception e) {
                Slog.w(TAG, "dispatchGnssStatus: attempt failed for paramCount=" + paramTypes.length + ": " + e.getMessage());
            }
        }
        if (!dispatched) {
            Slog.w(TAG, "dispatchGnssStatus: no matching onSvStatusChanged method found, trying onFirstFix");
            for (Method m : listener.getClass().getMethods()) {
                if ("onFirstFix".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(listener, 1000);
                    break;
                }
            }
        }
    }

    private Object buildGnssStatusObject(BGnssStatus gnssStatus) {
        try {
            android.location.GnssStatus.Builder b = new android.location.GnssStatus.Builder();
            int n = gnssStatus.getSvCount();
            int[] svidFlags = gnssStatus.getSvidWithFlags();
            float[] cn0s = gnssStatus.getCn0s();
            float[] elevs = gnssStatus.getElevations();
            float[] azs = gnssStatus.getAzimuths();
            float[] carrierFreqs = gnssStatus.getCarrierFreqs();
            float[] basebandCn0s = gnssStatus.getBasebandCn0s();
            boolean basebandOk = (basebandCn0s != null && basebandCn0s.length == n);

            for (int i = 0; i < n && i < svidFlags.length; i++) {
                int svid = svidFlags[i] >> SVID_SHIFT_WIDTH;
                int consType = (svidFlags[i] >> CONSTELLATION_TYPE_SHIFT_WIDTH) & CONSTELLATION_TYPE_MASK;
                boolean used = (svidFlags[i] & (1 << 2)) != 0;
                float cn0 = (i < cn0s.length) ? cn0s[i] : 25f;
                float elev = (i < elevs.length) ? elevs[i] : 30f;
                float az = (i < azs.length) ? azs[i] : 180f;
                float cf = (i < carrierFreqs.length && carrierFreqs[i] > 0) ? carrierFreqs[i] : GPS_L1_FREQ;
                float bb = basebandOk ? basebandCn0s[i] : Math.max(0f, cn0 - 2f);

                b.addSatellite(consType, svid, cn0, elev, az,
                        true, true, used, true, cf, true, bb);
            }
            return b.build();
        } catch (Exception e) {
            Slog.w(TAG, "buildGnssStatusObject with Builder failed: " + e.getMessage());
            return null;
        }
    }

    private void ensureConstellation() {
        long now = System.currentTimeMillis();
        synchronized (sGnssLock) {
            if (sGnssSvid != null && (now - sGnssLastReshuffle) < 90000L) return;
            sGnssLastReshuffle = now;

            int gpsCount = 8 + mRandom.nextInt(5);
            int bdsCount = 9 + mRandom.nextInt(6);
            int total = gpsCount + bdsCount;

            int[] cons = new int[total];
            int[] svid = new int[total];
            float[] carrier = new float[total];
            float[] baseCn0 = new float[total];
            float[] elev = new float[total];
            float[] az = new float[total];
            boolean[] used = new boolean[total];

            java.util.HashSet<Integer> usedGps = new java.util.HashSet<>();
            java.util.HashSet<Integer> usedBds = new java.util.HashSet<>();
            int idx = 0;
            for (int i = 0; i < gpsCount; i++, idx++) {
                int s; int guard = 0;
                do { s = 1 + mRandom.nextInt(32); } while (!usedGps.add(s) && ++guard < 64);
                cons[idx] = CONSTELLATION_GPS; svid[idx] = s; carrier[idx] = GPS_L1_FREQ;
                baseCn0[idx] = 22f + mRandom.nextFloat() * 20f;
                elev[idx] = 5f + mRandom.nextFloat() * 80f;
                az[idx] = mRandom.nextFloat() * 360f;
                used[idx] = i < gpsCount - 2;
            }
            for (int i = 0; i < bdsCount; i++, idx++) {
                int s; int guard = 0;
                do { s = 1 + mRandom.nextInt(63); } while (!usedBds.add(s) && ++guard < 128);
                cons[idx] = CONSTELLATION_BEIDOU; svid[idx] = s; carrier[idx] = BDS_B1I_FREQ;
                baseCn0[idx] = 22f + mRandom.nextFloat() * 20f;
                elev[idx] = 5f + mRandom.nextFloat() * 80f;
                az[idx] = mRandom.nextFloat() * 360f;
                used[idx] = i < bdsCount - 2;
            }

            sGnssConstellation = cons;
            sGnssSvid = svid;
            sGnssCarrierFreq = carrier;
            sGnssBaseCn0 = baseCn0;
            sGnssElev = elev;
            sGnssAz = az;
            sGnssUsedInFix = used;
        }
    }

    private BGnssStatus generateDefaultGnssStatus() {
        ensureConstellation();
        synchronized (sGnssLock) {
            int n = sGnssSvid.length;
            int[] svidWithFlags = new int[n];
            float[] cn0s = new float[n];
            float[] elevs = new float[n];
            float[] azs = new float[n];
            float[] carrierFreqs = new float[n];
            float[] basebandCn0s = new float[n];

            for (int i = 0; i < n; i++) {
                float cn0 = sGnssBaseCn0[i] + (mRandom.nextFloat() * 6f - 3f);
                if (cn0 < 8f) cn0 = 8f;
                if (cn0 > 48f) cn0 = 48f;
                sGnssBaseCn0[i] += (mRandom.nextFloat() * 1.0f - 0.5f);
                if (sGnssBaseCn0[i] < 18f) sGnssBaseCn0[i] = 18f;
                if (sGnssBaseCn0[i] > 44f) sGnssBaseCn0[i] = 44f;

                float elev = sGnssElev[i] + (mRandom.nextFloat() * 1.0f - 0.5f);
                if (elev < 5f) elev = 5f;
                if (elev > 89f) elev = 89f;
                sGnssElev[i] = elev;

                float a = sGnssAz[i] + (mRandom.nextFloat() * 1.0f - 0.5f);
                if (a < 0f) a += 360f;
                if (a >= 360f) a -= 360f;
                sGnssAz[i] = a;

                int flags = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4);
                if (!sGnssUsedInFix[i]) flags &= ~(1 << 2);

                svidWithFlags[i] = (sGnssSvid[i] << SVID_SHIFT_WIDTH)
                        | ((sGnssConstellation[i] & CONSTELLATION_TYPE_MASK) << CONSTELLATION_TYPE_SHIFT_WIDTH)
                        | flags;

                cn0s[i] = cn0;
                elevs[i] = elev;
                azs[i] = a;
                carrierFreqs[i] = sGnssCarrierFreq[i];
                basebandCn0s[i] = Math.max(0f, cn0 - (1f + mRandom.nextFloat() * 2f));
            }

            return new BGnssStatus(n, svidWithFlags, cn0s, elevs, azs, carrierFreqs, basebandCn0s);
        }
    }

    public void save() {
        synchronized (mGlobalConfig) {
            synchronized (mLocationConfigs) {
                Parcel parcel = Parcel.obtain();
                File configFile = BEnvironment.getFakeLocationConf();
                Slog.v(TAG, "save: writing to " + configFile.getAbsolutePath());
                AtomicFile atomicFile = new AtomicFile(configFile);
                FileOutputStream fileOutputStream = null;
                try {
                    mGlobalConfig.writeToParcel(parcel, 0);

                    parcel.writeInt(mLocationConfigs.size());
                    for (int i = 0; i < mLocationConfigs.size(); i++) {
                        int tmpUserId = mLocationConfigs.keyAt(i);
                        HashMap<String, BLocationConfig> configArrayMap = mLocationConfigs.valueAt(i);
                        parcel.writeInt(tmpUserId);
                        parcel.writeMap(configArrayMap);
                    }
                    parcel.setDataPosition(0);
                    fileOutputStream = atomicFile.startWrite();
                    FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                    atomicFile.finishWrite(fileOutputStream);
                    Slog.v(TAG, "save: success (" + mLocationConfigs.size() + " user configs)");
                } catch (Throwable e) {
                    Slog.e(TAG, "save: failed", e);
                    atomicFile.failWrite(fileOutputStream);
                } finally {
                    parcel.recycle();
                    CloseUtils.close(fileOutputStream);
                }
            }
        }
    }

    public void loadConfig() {
        Parcel parcel = Parcel.obtain();
        InputStream is = null;
        try {
            File fakeLocationConf = BEnvironment.getFakeLocationConf();
            Slog.i(TAG, "loadConfig: file=" + fakeLocationConf.getAbsolutePath() + " exists=" + fakeLocationConf.exists());
            if (!fakeLocationConf.exists()) {
                Slog.i(TAG, "loadConfig: no config file, first run");
                return;
            }
            is = new FileInputStream(fakeLocationConf);
            byte[] bytes = FileUtils.toByteArray(is);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            synchronized (mGlobalConfig) {
                mGlobalConfig.refresh(parcel);
                Slog.i(TAG, "loadConfig: globalConfig loaded pattern=" + mGlobalConfig.pattern + " loc=" + mGlobalConfig.location);
            }

            synchronized (mLocationConfigs) {
                mLocationConfigs.clear();
                int size = parcel.readInt();
                Slog.i(TAG, "loadConfig: " + size + " user configs to load");
                for (int i = 0; i < size; i++) {
                    int userId = parcel.readInt();
                    HashMap<String, BLocationConfig> configArrayMap = parcel.readHashMap(BLocationConfig.class.getClassLoader());
                    mLocationConfigs.put(userId, configArrayMap);
                    Slog.d(TAG, "load userId: " + userId + ", config: " + configArrayMap);
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "loadConfig: bad config, deleting file", e);
            FileUtils.deleteDir(BEnvironment.getFakeLocationConf());
        } finally {
            parcel.recycle();
            CloseUtils.close(is);
        }
    }

    @Override
    public void systemReady() {
        Slog.i(TAG, "systemReady: loading config and restoring listeners");
        loadConfig();
        int listenerCount = mLocationListeners.size();
        Slog.i(TAG, "systemReady: restoring " + listenerCount + " location listeners");
        for (IBinder iBinder : mLocationListeners.keySet()) {
            addTask(iBinder);
        }
        int gnssCount = mGnssStatusListeners.size();
        Slog.i(TAG, "systemReady: restoring " + gnssCount + " gnss listeners");
        for (IBinder iBinder : mGnssStatusListeners.keySet()) {
            addGnssTask(iBinder);
        }
        Slog.i(TAG, "systemReady: done");
    }
}
