/*
 *  @author Ryan Huang <huang@cs.jhu.edu>
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
 * An important event has happened for a given lease. For example, an acquire call is performed.
 */
public enum LeaseEvent implements Parcelable {
    WAKELOCK_ACQUIRE,
    WAKELOCK_RELEASE,
    LOCATION_ACQUIRE,
    LOCATION_NETWORK_ACQUIRE,
    LOCATION_RELEASE,
    LOCATION_CHANGE,
    SENSOR_ACQUIRE,
    SENSOR_RELEASE,
    BACKGROUNDAPP;


    private static LeaseEvent[] VALUES = LeaseEvent.values();

    public static final Creator<LeaseEvent> CREATOR = new Creator<LeaseEvent>() {
        @Override
        public LeaseEvent createFromParcel(Parcel in) {
            return VALUES[in.readInt()];
        }

        @Override
        public LeaseEvent[] newArray(int size) {
            return new LeaseEvent[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
}