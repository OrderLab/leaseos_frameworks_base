/*
 *  @author Yigong Hu <hyigong1@cs.jhu.edu>
 *
 *  The LeaseOS Project
 *
 *  Copyright (c) 2018, Johns Hopkins University - Order Lab.
 *      All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package android.lease;

import android.os.Parcel;
import android.os.Parcelable;

/**
 *
 */
public class LeaseSettings implements Parcelable {
    public static final String WHITELIST_DELIM = ",";

    private static final int MILLIS_PER_MINUTE = 60 * 1000;

    public static final boolean SERVICE_ENABLED_DEFAULT = true;
    public static final String WHITE_LIST_DEFAULT = "android,com.android.phone,com.quicin.trepn";
    public static final long RATE_LIMIT_WINDOW_DEFAULT = 3 * MILLIS_PER_MINUTE; // 3 minutes
    public static final long GC_WINDOW_DEFAULT = 10 * MILLIS_PER_MINUTE; // 10 minutes
    public static final LeaseSettings DEFAULT_SETTINGS = getDefaultSettings();

    public static final boolean WAKELOCK_LEASE_ENABLED = true;
    public static final boolean LOCATION_LEASE_ENABLED = true;
    public static final boolean SENSOR_LEASE_ENABLED = true;

    // Whether the service is enabled or not
    public boolean serviceEnabled;
    // Comma separated white list of package names.
    public String whiteList;
    // Min observation window for rate limiting
    public long LeaseTermWindow;
    // Min window for GC;
    public long DelayWindow;

    public boolean wakelockLeaseEnabled;
    public boolean gpsLeaseEnabled;
    public boolean sensorLeaseEnabled;

    public LeaseSettings() {
    }

    public LeaseSettings(Parcel in) {
        serviceEnabled = (in.readInt() != 0);
        whiteList = in.readString();
        LeaseTermWindow = in.readLong();
        DelayWindow = in.readLong();

        wakelockLeaseEnabled = (in.readInt() != 0);
        gpsLeaseEnabled = (in.readInt() != 0);
        sensorLeaseEnabled = (in.readInt() != 0);
    }

    public static LeaseSettings getDefaultSettings() {
        LeaseSettings settings = new LeaseSettings();
        settings.serviceEnabled = SERVICE_ENABLED_DEFAULT;
        settings.whiteList = WHITE_LIST_DEFAULT;
        settings.LeaseTermWindow = RATE_LIMIT_WINDOW_DEFAULT;
        settings.DelayWindow = GC_WINDOW_DEFAULT;

        settings.wakelockLeaseEnabled = WAKELOCK_LEASE_ENABLED;
        settings.gpsLeaseEnabled = LOCATION_LEASE_ENABLED;
        settings.sensorLeaseEnabled = SENSOR_LEASE_ENABLED;
        return settings;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest  The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *              May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(serviceEnabled ? 1 : 0);
        dest.writeString(whiteList);
        dest.writeLong(LeaseTermWindow);
        dest.writeLong(DelayWindow);

        dest.writeInt(wakelockLeaseEnabled ? 1 : 0);
        dest.writeInt(gpsLeaseEnabled ? 1 : 0);
        dest.writeInt(sensorLeaseEnabled ? 1 : 0);
    }

    public static final Creator<LeaseSettings> CREATOR = new Creator<LeaseSettings>() {
        @Override
        public LeaseSettings createFromParcel(Parcel source) {
            return new LeaseSettings(source);
        }

        @Override
        public LeaseSettings[] newArray(int size) {
            return new LeaseSettings[size];
        }
    };
}