    
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

    public Lease(long lid, long Oid, ResourceType type, ResourceStatManager RStatManager) {
        mLeaseid = lid;
        mOwnerid = Oid;
        mType = type;
        mStatus = LeaseStatus.INVALID;
        mRStatManager = RStatManager;
    }


    /**
     * Create a new lease and the corresponding resource manager
     */
    public void create() {
        mLeaseTerm = 0;
        mStatus = LeaseStatus.ACTIVE;
        mLength = 5;
        mBeginTime = System.currentTimeMillis();
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
    public boolean isValid() {
        return mStatus !=  LeaseStatus.INVALID;
    }

    public boolean isActive() {
        return mStatus !=  LeaseStatus.ACTIVE;
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
    public long getId() {
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
    public String getTypeStr() {
        return mType.toString();
    }

    /**
     * Get the status of lease
     * @return the status of lease
     */
    public String getStatusStr() {
        return mStatus.toString();
    }

    /**
     * Expire the lease
     * @return true if the lease is successfully expired
     */
    public boolean expire() {
        mEndTime = System.currentTimeMillis();
        mStatus = LeaseStatus.EXPIRED;
        switch (mType) {
            case Wakelock:
                // TODO: supply real argument for holding time and usage time.
                WakelockStat wStat = new WakelockStat(mBeginTime, mEndTime,0,0,0);
                mRStatManager.setResourceStat(this, wStat);
                break;
            case Location:
                LocationStat lStat = new LocationStat(mBeginTime, mEndTime);
                mRStatManager.setResourceStat(this, lStat);
                break;
            case Sensor:
                SensorStat sStat = new SensorStat(mBeginTime, mEndTime);
                mRStatManager.setResourceStat(this, sStat);
                break;
        }
        return false;
    }

    /**
     * Renew a new lease term for the lease
     * @return true if the lease is renewed
     */
    public boolean renew() {
        if (mStatus == LeaseStatus.ACTIVE)
            return false;
        mLeaseTerm++;
        mBeginTime = System.currentTimeMillis();
        mStatus = LeaseStatus.ACTIVE;
        //TODO: Acquire the resource again
        return false;
    }

}