package black.android.location;

import android.location.GnssStatus;
import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.location.IGnssStatusListener")
public interface IGnssStatusListener {
    @BMethod
    void onSvStatusChanged(int svCount, int[] svidWithFlags, float[] cn0s, float[] elevations, float[] azimuths);

    @BMethod
    void onSvStatusChanged(int svCount, int[] svidWithFlags, float[] cn0s, float[] elevations, float[] azimuths, float[] carrierFreqs);

    @BMethod
    void onSvStatusChanged(GnssStatus status);

    @BMethod
    void onFirstFix(int ttffMillis);

    @BClassName("android.location.IGnssStatusListener$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
