    
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

    //The identifier of lease
    protected long mLeaseid;

    //The identifier of the owner of lease. This variable usually means the UID
    protected long mOwnerid;

    //The token of the request from user process
    protected IBinder mToken;

    //The type of resource the lease is assigned
    protected ResourceType mType;

    //The status of the lease
    protected LeaseStatus mStatus;

    //The record of the history lease term for this lease
    protected ResourceStatManager mRStatManager;

    //The length of this lease term
    protected int mLength; // in millisecond

    //The BeginTime of this lease term
    protected long mBeginTime;

    //The EndTime of this lease term
    protected long mEndTime;

    //The number of current lease term
    protected int mLeaseTerm;

    public Lease(long lid, long Oid, String type) {
        mLeaseid = lid;
        mOwnerid = Oid;
        mType.setType(type);
        mStatus.setStatus("invalid");
    }


    /**
     * Create a new lease and the corresponding resource manager
     */
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

    /**
     * Get the history information of past lease term
     * @return ResourceManager, the manager of history information
     */
    public ResourceStatManager getRStatManager() {
        return mRStatManager;
    }

    /**
     * Check the validation of lease
     * @return true if the lease is valid
     */
    public boolean isvalid() {
        for (LeaseStatus status : LeaseStatus.values()) {
            if (mStatus == status) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the length of this lease term
     * @return The length of lease term
     */
    public long getLength() {
        return mLength;
    }

    /**
     * Get the lease id
     * @return Lease id
     */
    public long getLease() {
        return mLeaseid;
    }

    /**
     * Get the Owner of the lease
     * @return Owner id
     */
    public long getOwner() {
        return mOwnerid;
    }

    /**
     * Get the type of lease
     * @return lease type
     */
    public String getLeaseType() {
        return mType.toString();
    }

    /**
     * Get the status of lease
     * @return the status of lease
     */
    public String getLeaseStatus() {
        return mStatus.toString();
    }

    /**
     * Expire the lease
     * @return true if the lease is successfully expired
     */
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

    /**
     * Renew a new lease term for the lease
     * @return true if the lease is renewed
     */
    public boolean renew() {
        mLeaseTerm++;
        mBeginTime = System.currentTimeMillis();
        return false;
    }

}