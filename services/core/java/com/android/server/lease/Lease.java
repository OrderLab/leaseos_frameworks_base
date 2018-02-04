    
/*
 *  @author Yigong Hu <hyigong1@jhu.edu>
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
package com.android.server.lease;

import android.location.Location;
import android.os.IBinder;

import com.android.internal.os.BatteryStatsImpl;

/**
 * The struct of lease
 */
public class Lease {
    protected long mLeaseid;
    protected long mOwnerid;
    protected IBinder mToken;
    protected ResourceType mType;
    protected LeaseStatus mStatus;
    protected ResourceStatManager mRStatManager;
    protected int mLength; // in millisecond
    protected long mBeginTime;
    protected long mEndTime;
    protected int mLeaseTerm;

    public Lease(long lid, long Oid, String type) {
        mLeaseid = lid;
        mOwnerid = Oid;
        mType.setType(type);
        mStatus.setStatus("invalid");
    }

    public void createLease() {
        mLeaseTerm = 0;
        mStatus.setStatus("active");
        mLength = 5;
        mBeginTime = System.currentTimeMillis();
        switch (mType.toString()) {
            case "Wakelock":
                mRStatManager = new ResourceStatManager<WakelockStat>();
                break;
            case "Location":
                mRStatManager = new ResourceStatManager<LocationStat>();
                break;
            case "Sensor":
                mRStatManager = new ResourceStatManager<SensorStat>();
                break;

        }
    }

    public ResourceStatManager getRStatManager() {
        return mRStatManager;
    }

    public boolean isvalid() {
        for (LeaseStatus status : LeaseStatus.values()) {
            if (mStatus == status) {
                return true;
            }
        }
        return false;
    }

    public long getLength() {
        return mLength;
    }

    public long getLease() {
        return mLeaseid;
    }

    public long getOwner() {
        return mOwnerid;
    }

    public String getLeaseType() {
        return mType.toString();
    }

    public String getLeaseStatus() {
        return mStatus.toString();
    }


    public boolean expire() {
        mEndTime = System.currentTimeMillis();
        mStatus.setStatus("expired");
        switch (mType.toString()) {
            case "Wakelock":
                WakelockStat wStat = new WakelockStat(mBeginTime, mEndTime);
                mRStatManager.setResourceStat(wStat);
                break;
            case "Location":
                LocationStat lStat = new LocationStat(mBeginTime, mEndTime);
                mRStatManager.setResourceStat(lStat);
                break;
            case "Sensor":
                SensorStat sStat = new SensorStat(mBeginTime, mEndTime);
                mRStatManager.setResourceStat(sStat);
                break;
        }

        return false;
    }

    public boolean renew() {
        mLeaseTerm++;
        mBeginTime = System.currentTimeMillis();
        return false;
    }

}