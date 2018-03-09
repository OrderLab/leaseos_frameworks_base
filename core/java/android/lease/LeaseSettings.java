    
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
    public static final boolean SERVICE_ENABLED_DEFAULT = true;
    public static final LeaseSettings DEFAULT_SETTINGS = getDefaultSettings();

    // Whether the service is enabled or not
    public boolean serviceEnabled;

    public LeaseSettings() {
    }

    public LeaseSettings(Parcel in) {
        serviceEnabled = (in.readInt() != 0);
    }

    public static LeaseSettings getDefaultSettings() {
        LeaseSettings settings = new LeaseSettings();
        settings.serviceEnabled = SERVICE_ENABLED_DEFAULT;
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