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
import android.lease.ILeaseProxy;
import android.lease.LeaseStatus;
import android.lease.ResourceType;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Slog;

/**
 * The struct of lease.
 *
 * TODO: finish the workflow of use wakelock -> check -> policy -> renew part
 */
public class Lease {
    private static final String TAG = "Lease";

    public static final int DEFAULT_TERM_MS = 60 * 1000; // default 60 seconds, may need to reduce it
    public static final int DEFAULT_DELY_TIME = 20 * 1000; // the delay time, default 20 second
    public static final int MAX_DELAY_NUMBER = 200  ;

    protected long mLeaseId; // The identifier of lease
    protected int mOwnerId;  // The identifier of the owner of lease. This variable usually means the UID
    protected ResourceType mType; // The type of resource the lease is assigned
    protected LeaseStatus mStatus; // The status of the lease
    protected int mLength; // The length of this lease term in millisecond
    protected long mBeginTime; // The BeginTime of this lease term
    protected long mEndTime; // The EndTime of this lease term
    protected int mRenewal; // The number of current lease term
    protected final Context mContext; // The context in which the lease is created
    protected ResourceStatManager mRStatManager; // The record of the history lease term for this lease
    protected ILeaseProxy mProxy; // the associated lease proxy
    protected long mDelayInterval; // the time interval between two leases
    protected long mDelayCounter; // the counter of delaying times
    BatteryMonitor mBatteryMonitor; // the instance of Battery Monitor
    protected boolean isCharging; // true if the phone is charged during this lease term

    private LeaseWorkerHandler mHandler;
    private boolean mScheduled;


    private Runnable mExpireRunnable = new Runnable() {
        @Override
        public void run() {
            cancelExpire();
            endTerm(); // call end of term at the end
        }
    };

    private Runnable mRenewRunnable = new Runnable() {
        @Override
        public void run() {
            renew(true);
            scheduleExpire(mLength);
            cancelDelay();
        }
    };

    public Lease(long lid, int Oid, ResourceType type, ResourceStatManager RStatManager,
            ILeaseProxy proxy, LeaseWorkerHandler handler, Context context) {
        mLeaseId = lid;
        mOwnerId = Oid;
        mType = type;
        mStatus = LeaseStatus.INVALID;
        mRStatManager = RStatManager;
        mProxy = proxy;
        mHandler = handler;
        mContext = context;
        mBatteryMonitor = BatteryMonitor.getInstance(context);
    }

    /**
     * Create a new lease and the corresponding resource manager
     */
    public void create() {
        mRenewal = 0;
        mStatus = LeaseStatus.ACTIVE;
        mLength = DEFAULT_TERM_MS;
        mBeginTime = SystemClock.elapsedRealtime();
        mDelayInterval = DEFAULT_DELY_TIME;
        mDelayCounter = 0;
        if(mBatteryMonitor.isCharging()) {
            isCharging = true;
        } else {
            isCharging = true;
        }
        scheduleExpire(mLength);
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
        return mStatus == LeaseStatus.ACTIVE;
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
     * Get the owner of the lease
     *
     * @return Owner id
     */
    public int getOwner() {
        return mOwnerId;
    }

    /**
     * Get start time of this lease
     *
     * @return
     */
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
    public LeaseStatus getStatus() {
        return mStatus;
    }

    /**
     * Return the current resource stat of the lease
     *
     * @return
     */
    public ResourceStat getCurrentStat() {
        return mRStatManager.getCurrentStat(mLeaseId);
    }

    /**
     * Return the history of resource stat of the lease.
     *
     * @return
     */
    public StatHistory getStatHistory() {
        return mRStatManager.getStatsHistory(mLeaseId);
    }

    /**
     * Set the associated lease proxy.
     *
     * @param proxy
     */
    public void setProxy(ILeaseProxy proxy) {
        mProxy = proxy;
    }

    /**
     * Set the associated worker thread for this lease.
     *
     * @param handler
     */
    public void setHandler(LeaseWorkerHandler handler) {
        mHandler = handler;
    }

    /**
     * Expire the lease
     *
     * @return true if the lease is successfully expired
     */
    public boolean expire() {
        if (mStatus != LeaseStatus.ACTIVE) {
            Slog.e(TAG, "Skip expiring an inactive lease " + mLeaseId);
            return false;
        }
        mStatus = LeaseStatus.EXPIRED;
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling onExpire for lease " + mLeaseId);
                mProxy.onExpire(mLeaseId);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke onExpire for lease " + mLeaseId);
                return false;
            }
            return true;
        } else {
            Slog.e(TAG, "No lease proxy for lease " + mLeaseId);
            return false;
        }
    }

    /**
     * Freeze the request of wakelock for this uid
     *
     * @return true if the lease is successfully expired
     */
    public boolean freeze() {
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling onFreeze for uid " + mOwnerId + " to freeze for " + mDelayInterval);
                mProxy.onFreeze(mOwnerId, mDelayInterval, MAX_DELAY_NUMBER);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke onExpire for lease " + mLeaseId);
                return false;
            }
            return true;
        } else {
            Slog.e(TAG, "No lease proxy for lease " + mLeaseId);
            return false;
        }
    }



    /**
     * One lease term has come to an end.
     */
    public void endTerm() {
        mEndTime = SystemClock.elapsedRealtime();
        // update the stats for this lease term
        mRStatManager.update(mLeaseId, mBeginTime, mEndTime, mOwnerId);
        if (isCharging == true || mBatteryMonitor.isCharging()) {
            Slog.d(TAG,"The phone is in charing, immediately renew for lease " + mLeaseId);
            renew(false);
            return;
        }
        LeasePolicyRuler.Decision decision = LeasePolicyRuler.behaviorJudge(this);
        switch (decision) {
            case EXPIRE:
                //TODO: the goal of expire case is unclear
                expire();
                break;
            case RENEW:
                renew(false); // skip checking the status as we just transit from end of term
                break;
            case DELAY:
                expire();
                freeze();
                sechduleNextLeaseTerm();
            default:
                Slog.e(TAG, "Unimplemented action for decision " + decision);
        }
    }

    public void sechduleNextLeaseTerm() {
        mDelayInterval = mDelayCounter * (mDelayCounter + 1);
        mDelayCounter++;
        scheduleDelay(mDelayInterval);
    }

    /**
     * Renew a new lease term for the lease
     *
     * @param check should the lease status be checked
     * @return true if the lease is renewed
     */
    public boolean renew(boolean check) {
        Slog.d(TAG, "Starting renew lease " + mLeaseId + " for " + mLength / 1000 + " second");
        if (check && mStatus == LeaseStatus.ACTIVE) {
            return false;
        }
        if (mBatteryMonitor.isCharging()) {
            isCharging = true;
        } else {
            isCharging = false;
        }
        mRenewal++;
        mBeginTime = SystemClock.elapsedRealtime();
        boolean success = false;
        // create a new stat for the new lease term
        switch (mType) {
            case Wakelock:
                // TODO: supply real argument for holding time and usage time.
                WakelockStat wStat = new WakelockStat(mBeginTime, mOwnerId, mContext);
                mStatus = LeaseStatus.ACTIVE;
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
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling onRenew for lease " + mLeaseId);
                mProxy.onRenew(mLeaseId);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke onExpire for lease " + mLeaseId);
                success = false;
            }
        } else {
            Slog.e(TAG, "No lease proxy for lease " + mLeaseId);
            success = false;
        }
        scheduleExpire(mLength);
        return success;
    }

    public void scheduleDelay (long delayInterval) {
        if (!mScheduled && mHandler != null) {
            Slog.d(TAG, "Renew lease " + mLeaseId + " after " + mDelayInterval + " ms");
            mHandler.postDelayed(mRenewRunnable,delayInterval);
        }
    }

    public void cancelDelay () {
        if (mScheduled && mHandler != null) {
            Slog.d(TAG, "Cancelling delay renew for lease " + mLeaseId);
            mHandler.removeCallbacks(mExpireRunnable);
            mScheduled = false;
        }
    }

    public void scheduleExpire(long leaseTerm) {
        if (!mScheduled && mHandler != null) {
            Slog.d(TAG, "Scheduling expiration check for lease " + mLeaseId + " after " + mLength + " ms");
            mHandler.postDelayed(mExpireRunnable, leaseTerm);
            mScheduled = true;
        }
    }

    public void cancelExpire() {
        if (mScheduled && mHandler != null) {
            Slog.d(TAG, "Cancelling expiration check for lease " + mLeaseId);
            mHandler.removeCallbacks(mExpireRunnable);
            mScheduled = false;
        }
    }
}