package top.niunaijun.blackbox.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

public class BSensorConfig implements Parcelable {

    public boolean stepEnabled;
    public float stepCadenceSpm;  // steps per minute
    public boolean accelEnabled;
    public float accelAmplitude;  // acceleration amplitude m/s²

    public BSensorConfig() {
        this.stepEnabled = false;
        this.stepCadenceSpm = 120f;
        this.accelEnabled = false;
        this.accelAmplitude = 3.0f;
    }

    public BSensorConfig(boolean stepEnabled, float stepCadenceSpm,
                         boolean accelEnabled, float accelAmplitude) {
        this.stepEnabled = stepEnabled;
        this.stepCadenceSpm = stepCadenceSpm;
        this.accelEnabled = accelEnabled;
        this.accelAmplitude = accelAmplitude;
    }

    public BSensorConfig(Parcel in) {
        this.stepEnabled = in.readBoolean();
        this.stepCadenceSpm = in.readFloat();
        this.accelEnabled = in.readBoolean();
        this.accelAmplitude = in.readFloat();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(this.stepEnabled);
        dest.writeFloat(this.stepCadenceSpm);
        dest.writeBoolean(this.accelEnabled);
        dest.writeFloat(this.accelAmplitude);
    }

    public static final Creator<BSensorConfig> CREATOR = new Creator<BSensorConfig>() {
        @Override
        public BSensorConfig createFromParcel(Parcel source) {
            return new BSensorConfig(source);
        }

        @Override
        public BSensorConfig[] newArray(int size) {
            return new BSensorConfig[size];
        }
    };

    @Override
    public String toString() {
        return "BSensorConfig{step=" + stepEnabled + "(" + stepCadenceSpm + "spm)"
                + ", accel=" + accelEnabled + "(" + accelAmplitude + "m/s²)}";
    }
}
