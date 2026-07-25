package top.niunaijun.blackbox.entity.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;


public class BLocationConfig implements Parcelable {

    public int pattern;
    public BCell cell;
    public List<BCell> allCell;
    public List<BCell> neighboringCellInfo;
    public BLocation location;
    public BGnssStatus gnssStatus;
    public BSensorConfig sensorConfig;

    @Override
    public int describeContents() {
        return 0;
    }

    public BLocationConfig() {
    }

    public BLocationConfig(Parcel in) {
        refresh(in);
    }

    public void refresh(Parcel in) {
        this.pattern = in.readInt();
        this.cell = in.readParcelable(BCell.class.getClassLoader());
        this.allCell = in.createTypedArrayList(BCell.CREATOR);
        this.neighboringCellInfo = in.createTypedArrayList(BCell.CREATOR);
        this.location = in.readParcelable(BLocation.class.getClassLoader());
        this.gnssStatus = in.readParcelable(BGnssStatus.class.getClassLoader());
        this.sensorConfig = in.readParcelable(BSensorConfig.class.getClassLoader());
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pattern);
        dest.writeParcelable(this.cell, flags);
        dest.writeTypedList(this.allCell);
        dest.writeTypedList(this.neighboringCellInfo);
        dest.writeParcelable(this.location, flags);
        dest.writeParcelable(this.gnssStatus, flags);
        dest.writeParcelable(this.sensorConfig, flags);
    }

    @Override
    public String toString() {
        String patternStr = pattern == 0 ? "CLOSE" : pattern == 1 ? "GLOBAL" : "OWN";
        return "BLocationConfig{pattern=" + patternStr + "(" + pattern + "), location=" + location + ", cell=" + cell + ", gnssStatus=" + gnssStatus + ", sensorConfig=" + sensorConfig + "}";
    }

    public static final Creator<BLocationConfig> CREATOR = new Creator<BLocationConfig>() {
        @Override
        public BLocationConfig createFromParcel(Parcel source) {
            return new BLocationConfig(source);
        }

        @Override
        public BLocationConfig[] newArray(int size) {
            return new BLocationConfig[size];
        }
    };
}
