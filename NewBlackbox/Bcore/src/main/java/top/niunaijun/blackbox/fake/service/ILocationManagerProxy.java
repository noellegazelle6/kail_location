package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.location.LocationManager;
import android.os.IInterface;

import top.niunaijun.blackbox.utils.Slog;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import black.android.location.BRILocationListener;
import black.android.location.BRILocationManagerStub;
import black.android.location.provider.BRProviderProperties;
import black.android.location.provider.ProviderProperties;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.location.BLocation;
import top.niunaijun.blackbox.fake.frameworks.BLocationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class ILocationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "[sandbox] ILocationManagerProxy";

    public ILocationManagerProxy() {
        super(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRILocationManagerStub.get().asInterface(BRServiceManager.get().getService(Context.LOCATION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        MethodParameterUtils.replaceFirstAppPkg(args);
        
        String methodName = method.getName();
        String packageName = BActivityThread.getAppPackageName();
        int userId = BActivityThread.getUserId();
        
        Slog.v(TAG, "invoke: method=" + methodName + " pkg=" + packageName + " userId=" + userId);
        
        if (packageName != null && packageName.equals("com.google.android.gms")) {
            
            if (methodName.equals("getLastLocation") || 
                methodName.equals("getLastKnownLocation") ||
                methodName.equals("requestLocationUpdates") ||
                methodName.equals("registerLocationListener") ||
                methodName.equals("requestListenerUpdates")) {
                Slog.w(TAG, "Blocking location request from Google Play Services to prevent crash");
                return null;
            }
        }
        
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("registerGnssStatusCallback")
    public static class RegisterGnssStatusCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof IInterface) {
                        IInterface listener = (IInterface) args[i];
                        Slog.i(TAG, "registerGnssStatusCallback INTERCEPTED pkg=" + pkg + " userId=" + userId
                                + " listener=" + listener.asBinder() + " argIdx=" + i);
                        BLocationManager.get().registerGnssStatusCallback(listener.asBinder());
                        return true;
                    }
                }
                Slog.w(TAG, "registerGnssStatusCallback: no IInterface found, pass-through");
            }
            Slog.v(TAG, "registerGnssStatusCallback PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "registerGnssStatusCallback permission denied, returning false");
                    return false;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("unregisterGnssStatusCallback")
    public static class UnregisterGnssStatusCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args[0] instanceof IInterface) {
                IInterface listener = (IInterface) args[0];
                Slog.i(TAG, "unregisterGnssStatusCallback listener=" + listener.asBinder());
                BLocationManager.get().unregisterGnssStatusCallback(listener.asBinder());
                return null;
            }
            Slog.v(TAG, "unregisterGnssStatusCallback PASS-THROUGH");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addGnssMeasurementsListener")
    public static class AddGnssMeasurementsListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "addGnssMeasurementsListener: blocked (return null)");
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeGnssMeasurementsListener")
    public static class RemoveGnssMeasurementsListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "removeGnssMeasurementsListener: pass-through");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addGnssNavigationMessageListener")
    public static class AddGnssNavigationMessageListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "addGnssNavigationMessageListener: blocked (return null)");
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeGnssNavigationMessageListener")
    public static class RemoveGnssNavigationMessageListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "removeGnssNavigationMessageListener: pass-through");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addGnssAntennaInfoListener")
    public static class AddGnssAntennaInfoListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "addGnssAntennaInfoListener: blocked (return null)");
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeGnssAntennaInfoListener")
    public static class RemoveGnssAntennaInfoListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "removeGnssAntennaInfoListener: pass-through");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerGnssNmeaCallback")
    public static class RegisterGnssNmeaCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "registerGnssNmeaCallback: blocked (return null)");
                return null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("unregisterGnssNmeaCallback")
    public static class UnregisterGnssNmeaCallback extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "unregisterGnssNmeaCallback: pass-through");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getLastLocation")
    public static class GetLastLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation fakeLoc = BLocationManager.get().getLocation(userId, pkg);
                Slog.i(TAG, "getLastLocation FAKED pkg=" + pkg + " userId=" + userId + " -> " + fakeLoc);
                if (fakeLoc != null) {
                    return fakeLoc.convert2SystemLocation();
                }
                Slog.w(TAG, "getLastLocation: fake enabled but location is null, returning real");
                return method.invoke(who, args);
            }
            Slog.v(TAG, "getLastLocation PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "getLastLocation permission denied, returning null");
                    return null;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getLastKnownLocation")
    public static class GetLastKnownLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation fakeLoc = BLocationManager.get().getLocation(userId, pkg);
                Slog.i(TAG, "getLastKnownLocation FAKED pkg=" + pkg + " userId=" + userId + " -> " + fakeLoc);
                if (fakeLoc != null) {
                    return fakeLoc.convert2SystemLocation();
                }
                Slog.w(TAG, "getLastKnownLocation: fake enabled but location is null, returning real");
                return method.invoke(who, args);
            }
            Slog.v(TAG, "getLastKnownLocation PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "getLastKnownLocation permission denied, returning null");
                    return null;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("requestLocationUpdates")
    public static class RequestLocationUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof IInterface) {
                        IInterface listener = (IInterface) args[i];
                        Slog.i(TAG, "requestLocationUpdates INTERCEPTED pkg=" + pkg + " userId=" + userId
                                + " listener=" + listener.asBinder() + " argIdx=" + i);
                        BLocationManager.get().requestLocationUpdates(listener.asBinder());
                        return 0;
                    }
                }
                Slog.w(TAG, "requestLocationUpdates: no IInterface found in " + args.length + " args, pass-through");
            }
            Slog.v(TAG, "requestLocationUpdates PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "requestLocationUpdates permission denied, returning 0");
                    return 0;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("registerLocationListener")
    public static class RegisterLocationListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof IInterface) {
                        IInterface listener = (IInterface) args[i];
                        Slog.i(TAG, "registerLocationListener INTERCEPTED pkg=" + pkg + " userId=" + userId
                                + " listener=" + listener.asBinder() + " argIdx=" + i);
                        BLocationManager.get().requestLocationUpdates(listener.asBinder());
                        return 0;
                    }
                }
                Slog.w(TAG, "registerLocationListener: no IInterface found in " + args.length + " args, pass-through");
            }
            Slog.v(TAG, "registerLocationListener PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "registerLocationListener permission denied, returning 0");
                    return 0;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("requestListenerUpdates")
    public static class RequestListenerUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof IInterface) {
                        IInterface listener = (IInterface) args[i];
                        Slog.i(TAG, "requestListenerUpdates INTERCEPTED pkg=" + pkg + " userId=" + userId
                                + " listener=" + listener.asBinder() + " argIdx=" + i);
                        BLocationManager.get().requestLocationUpdates(listener.asBinder());
                        return 0;
                    }
                }
                Slog.w(TAG, "requestListenerUpdates: no IInterface found");
            }
            Slog.v(TAG, "requestListenerUpdates PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                if (e.getCause() instanceof SecurityException) {
                    Slog.w(TAG, "requestListenerUpdates permission denied, returning 0");
                    return 0;
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getCurrentLocation")
    public static class GetCurrentLocation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = BActivityThread.getAppPackageName();
            int userId = BActivityThread.getUserId();
            if (BLocationManager.isFakeLocationEnable()) {
                BLocation fakeLoc = BLocationManager.get().getLocation(userId, pkg);
                Slog.i(TAG, "getCurrentLocation FAKED pkg=" + pkg + " userId=" + userId + " -> " + fakeLoc);
                if (fakeLoc != null) {
                    return fakeLoc.convert2SystemLocation();
                }
            }
            Slog.v(TAG, "getCurrentLocation PASS-THROUGH pkg=" + pkg + " userId=" + userId);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeUpdates")
    public static class RemoveUpdates extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args[0] instanceof IInterface) {
                IInterface listener = (IInterface) args[0];
                Slog.i(TAG, "removeUpdates listener=" + listener.asBinder());
                BLocationManager.get().removeUpdates(listener.asBinder());
                return 0;
            }
            Slog.v(TAG, "removeUpdates PASS-THROUGH");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("unregisterLocationListener")
    public static class UnregisterLocationListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args[0] instanceof IInterface) {
                IInterface listener = (IInterface) args[0];
                Slog.i(TAG, "unregisterLocationListener listener=" + listener.asBinder());
                BLocationManager.get().removeUpdates(listener.asBinder());
                return 0;
            }
            Slog.v(TAG, "unregisterLocationListener PASS-THROUGH");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getProviderProperties")
    public static class GetProviderProperties extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object providerProperties = method.invoke(who, args);
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "getProviderProperties: modifying requirements (no network/cell)");
                BRProviderProperties.get(providerProperties)._set_mHasNetworkRequirement(false);
                if (BLocationManager.get().getCell(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) == null) {
                    BRProviderProperties.get(providerProperties)._set_mHasCellRequirement(false);
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("removeGpsStatusListener")
    public static class RemoveGpsStatusListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "removeGpsStatusListener: blocked (return 0)");
            return 0;
        }
    }

    @ProxyMethod("getBestProvider")
    public static class GetBestProvider extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BLocationManager.isFakeLocationEnable()) {
                Slog.v(TAG, "getBestProvider: returning GPS_PROVIDER (fake mode)");
                return LocationManager.GPS_PROVIDER;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAllProviders")
    public static class GetAllProviders extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "getAllProviders: returning [GPS, NETWORK]");
            return Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER);
        }
    }

    @ProxyMethod("isProviderEnabledForUser")
    public static class isProviderEnabledForUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String provider = (String) args[0];
            boolean result = Objects.equals(provider, LocationManager.GPS_PROVIDER);
            Slog.v(TAG, "isProviderEnabledForUser provider=" + provider + " -> " + result);
            return result;
        }
    }

    @ProxyMethod("setExtraLocationControllerPackageEnabled")
    public static class setExtraLocationControllerPackageEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.v(TAG, "setExtraLocationControllerPackageEnabled: blocked (return 0)");
            return 0;
        }
    }
}
