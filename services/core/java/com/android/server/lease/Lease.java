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

import android.content.Context;
import android.databinding.tool.util.L;
import android.lease.LeaseManager;
import android.lease.ResourceType;


import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

import com.android.server.ServiceThread;

/**
 * The struct of lease.
 *
 * TODO: finish the workflow of use wakelock -> check -> policy -> renew part
 */
public class Lease {

    public static final int INVALID_LEASE = -1;

    public static final int DEFAULT_TERM_MS = 30000; // default 30 seconds, may need to reduce it

    //The identifier of lease
    protected long mLeaseId;

    //The identifier of the owner of lease. This variable usually means the UID
    protected int mOwnerId;

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
    protected int mRenewal;

    private ServiceThread mHandlerThread;
    private Handler mHandler;
    private boolean mScheduled;
    private static final String TAG = "LeaseManagerService";

    public Lease(long lid, int Oid, ResourceType type, ResourceStatManager RStatManager, Context context) {
        mLeaseId = lid;
        mOwnerId = Oid;
        mType = type;
        mStatus = LeaseStatus.INVALID;
        mRStatManager = RStatManager;
    }


    /**
     * Create a new lease and the corresponding resource manager
     */
    public void create() {
        mRenewal = 0;
        mStatus = LeaseStatus.ACTIVE;
        mLength = DEFAULT_TERM_MS;
        mBeginTime = SystemClock.elapsedRealtime();

        mHandlerThread = new ServiceThread(TAG,
                Process.THREAD_PRIORITY_DISPLAY, false /*allowIo*/);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        scheduleChecks();
    }

    /**
     * Get the history information of past lease term
     *
     * @return ResourceManager, the manager of history information
     */
    public ResourceStatManager getRStatManager() {
        return mRStatManager;
    }

    /**
     * Check the validation of lease
     *
     * @return true if the lease is valid
     */
    public boolean isValid() {
        return mStatus != LeaseStatus.INVALID;
    }

    public boolean isActive() {
        if (mStatus == LeaseStatus.EXPIRED) {
            startRenewPolicy();
        }
        return mStatus != LeaseStatus.ACTIVE;
    }

    /**
     * Get the length of this lease term
     *
     * @return The length of lease term
     */
    public long getLength() {
        return mLength;
    }

    /**
     * Get the lease id
     *
     * @return Lease id
     */
    public long getId() {
        return mLeaseId;
    }

    /**
     * Get the Owner of the lease
     *
     * @return Owner id
     */
    public int getOwner() {
        return mOwnerId;
    }

    public long getBeginTime() {
        return mBeginTime;
    }

    /**
     * Get the type of lease
     *
     * @return lease type
     */
    public String getTypeStr() {
        return mType.toString();
    }

    /**
     * Get the status of lease
     *
     * @return the status of lease
     */
    public String getStatusStr() {
        return mStatus.toString();
    }

    /**
     * Expire the lease
     *
     * @return true if the lease is successfully expired
     */
    public boolean expire() {
        mEndTime = SystemClock.elapsedRealtime();
        mStatus = LeaseStatus.EXPIRED;
        mRStatManager.update(mLeaseId, mBeginTime, mEndTime);
        if (mRStatManager.isActivateEvent(mLeaseId)) {
            startRenewPolicy();
        }
        //TODO: release the resource and the policy for deciding the renew time
        return true;
    }

    public void startRenewPolicy() {
        //TODO: finish the policy later
        renew();
        scheduleChecks();
    }

    private Runnable mExpireRunnable = new Runnable() {
        @Override
        public void run() {
            expire();
            cancelChecks();
        }
    };

    public void scheduleChecks() {
        if (!mScheduled) {
            //Slog.d(TAG, "Scheduling checker queue [" + mCheckInterval + " ms]");
            mHandler.postDelayed(mExpireRunnable, mLength);
            mScheduled = true;
        }
    }

    public void cancelChecks() {
        if (mScheduled) {
            //Slog.d(TAG, "Canceling checker queue [" + mCheckInterval + " ms]");
            mHandler.removeCallbacks(mExpireRunnable);
            mScheduled = false;
        }
    }

    /**
     * Renew a new lease term for the lease
     *
     * @return true if the lease is renewed
     */
    public boolean renew() {
        boolean success = false;

        if (mStatus == LeaseStatus.ACTIVE) {
            return false;
        }

        mRenewal++;
        mBeginTime = SystemClock.elapsedRealtime();
        mStatus = LeaseStatus.ACTIVE;
        // create a new stat for the new lease term
        switch (mType) {
            case Wakelock:
                // TODO: supply real argument for holding time and usage time.
                WakelockStat wStat = new WakelockStat(mBeginTime, mOwnerId);
                success = mRStatManager.addResourceStat(mLeaseId, wStat);
                break;
            case Location:
                LocationStat lStat = new LocationStat(mBeginTime);
                success = mRStatManager.addResourceStat(mLeaseId, lStat);
                break;
            case Sensor:
                SensorStat sStat = new SensorStat(mBeginTime);
                success = mRStatManager.addResourceStat(mLeaseId, sStat);
                break;
        }

        scheduleChecks();
        //TODO: Acquire the resource again
        return success;
    }

}