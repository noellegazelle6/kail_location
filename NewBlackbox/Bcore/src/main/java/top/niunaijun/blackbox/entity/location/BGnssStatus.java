package top.niunaijun.blackbox.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public class BGnssStatus implements Parcelable {

    private int mSvCount;
    private int[] mSvidWithFlags;
    private float[] mCn0s;
    private float[] mElevations;
    private float[] mAzimuths;
    private float[] mCarrierFreqs;
    private float[] mBasebandCn0s;

    public BGnssStatus() {
        mSvCount = 0;
        mSvidWithFlags = new int[0];
        mCn0s = new float[0];
        mElevations = new float[0];
        mAzimuths = new float[0];
        mCarrierFreqs = new float[0];
        mBasebandCn0s = new float[0];
    }

    public BGnssStatus(int svCount, int[] svidWithFlags, float[] cn0s,
                       float[] elevations, float[] azimuths,
                       float[] carrierFreqs, float[] basebandCn0s) {
        this.mSvCount = svCount;
        this.mSvidWithFlags = svidWithFlags;
        this.mCn0s = cn0s;
        this.mElevations = elevations;
        this.mAzimuths = azimuths;
        this.mCarrierFreqs = carrierFreqs;
        this.mBasebandCn0s = basebandCn0s;
    }

    public BGnssStatus(Parcel in) {
        this.mSvCount = in.readInt();
        this.mSvidWithFlags = in.createIntArray();
        this.mCn0s = in.createFloatArray();
        this.mElevations = in.createFloatArray();
        this.mAzimuths = in.createFloatArray();
        this.mCarrierFreqs = in.createFloatArray();
        this.mBasebandCn0s = in.createFloatArray();
    }

    public int getSvCount() { return mSvCount; }
    public void setSvCount(int svCount) { this.mSvCount = svCount; }

    public int[] getSvidWithFlags() { return mSvidWithFlags; }
    public void setSvidWithFlags(int[] svidWithFlags) { this.mSvidWithFlags = svidWithFlags; }

    public float[] getCn0s() { return mCn0s; }
    public void setCn0s(float[] cn0s) { this.mCn0s = cn0s; }

    public float[] getElevations() { return mElevations; }
    public void setElevations(float[] elevations) { this.mElevations = elevations; }

    public float[] getAzimuths() { return mAzimuths; }
    public void setAzimuths(float[] azimuths) { this.mAzimuths = azimuths; }

    public float[] getCarrierFreqs() { return mCarrierFreqs; }
    public void setCarrierFreqs(float[] carrierFreqs) { this.mCarrierFreqs = carrierFreqs; }

    public float[] getBasebandCn0s() { return mBasebandCn0s; }
    public void setBasebandCn0s(float[] basebandCn0s) { this.mBasebandCn0s = basebandCn0s; }

    public boolean isEmpty() {
        return mSvCount == 0 || mSvidWithFlags == null || mSvidWithFlags.length == 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mSvCount);
        dest.writeIntArray(this.mSvidWithFlags);
        dest.writeFloatArray(this.mCn0s);
        dest.writeFloatArray(this.mElevations);
        dest.writeFloatArray(this.mAzimuths);
        dest.writeFloatArray(this.mCarrierFreqs);
        dest.writeFloatArray(this.mBasebandCn0s);
    }

    public static final Creator<BGnssStatus> CREATOR = new Creator<BGnssStatus>() {
        @Override
        public BGnssStatus createFromParcel(Parcel source) {
            return new BGnssStatus(source);
        }

        @Override
        public BGnssStatus[] newArray(int size) {
            return new BGnssStatus[size];
        }
    };

    @Override
    public String toString() {
        return "BGnssStatus{svCount=" + mSvCount
                + ", svidWithFlags=" + Arrays.toString(mSvidWithFlags)
                + ", cn0s=" + Arrays.toString(mCn0s)
                + ", elevations=" + Arrays.toString(mElevations)
                + ", azimuths=" + Arrays.toString(mAzimuths)
                + ", carrierFreqs=" + Arrays.toString(mCarrierFreqs)
                + ", basebandCn0s=" + Arrays.toString(mBasebandCn0s)
                + "}";
    }
}
