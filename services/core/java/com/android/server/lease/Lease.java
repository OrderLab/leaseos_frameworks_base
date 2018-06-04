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
import android.lease.TimeUtils;
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

    public static int USER_DEFINE_TERM_MS = 30 * 1000; // default 30 seconds, may need to reduce it
    public static int USER_DEFINE_DELAY_TIME = 30 * 1000; // the delay time, default 30 seconds
    public static final int MAX_DELAY_NUMBER = 200;

    private static final int DEFAULT_FREQUENCYASK_TERM_MS = 5 * TimeUtils.MILLIS_PER_SECOND;
    private static final int DEFAULT_FREQUENCYASK_DELAY_MS = 20 * TimeUtils.MILLIS_PER_SECOND;

    private static final int DEFAULT_LONGHOLD_TERM_MS = 10 * TimeUtils.MILLIS_PER_SECOND;
    private static final int DEFAULT_LONGHOLD_DELAY_MS = 50 * TimeUtils.MILLIS_PER_SECOND;

    private static final int DEFAULT_LOWUTILITY_TERM_MS = 5 * TimeUtils.MILLIS_PER_SECOND;
    private static final int DEFAULT_LOWUTILITY_DELAY_MS = 30 * TimeUtils.MILLIS_PER_SECOND;

    private static final int DEFAULT_HIGHDAMAGE_TERM_MS = 10 * TimeUtils.MILLIS_PER_SECOND;
    private static final int DEFAULT_HIGHDAMAGE_DELAY_MS = 2 * TimeUtils.MILLIS_PER_SECOND;

    private static final int DEFAULT_NORMAL_TERM_MS = 15 * TimeUtils.MILLIS_PER_SECOND;
    private static final int DEFAULT_NORMAL_DELAY_MS = 2 * TimeUtils.MILLIS_PER_SECOND;
    public static final int DEFAULT_PROBING_TERM_MS = 5 * TimeUtils.MILLIS_PER_SECOND;

    public boolean UserDefined;
    protected long mLeaseId; // The identifier of lease
    protected int mOwnerId;
            // The identifier of the owner of lease. This variable usually means the UID
    protected ResourceType mType; // The type of resource the lease is assigned
    protected LeaseStatus mStatus; // The status of the lease
    protected int mLength; // The length of this lease term in millisecond
    protected long mBeginTime; // The BeginTime of this lease term
    protected long mEndTime; // The EndTime of this lease term
    protected int mRenewal; // The number of current lease term
    protected final Context mContext; // The context in which the lease is created
    protected ResourceStatManager mRStatManager;  // The record of the history lease term for this lease
    protected LeaseManagerService mLeaseManagerService;
    protected ILeaseProxy mProxy; // the associated lease proxy
    protected long mDelayInterval; // the time interval between two leases
    protected int mDelayCounter; // the counter of delaying times
    BatteryMonitor mBatteryMonitor; // the instance of Battery Monitor
    protected boolean isCharging; // true if the phone is charged during this lease term
    protected boolean isDelay;
    protected boolean isProbing;

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
            cancelDelay();
            renew(true);
        }
    };

    public Lease(long lid, int Oid, ResourceType type, ResourceStatManager RStatManager,
            ILeaseProxy proxy, LeaseWorkerHandler handler, LeaseManagerService leaseManagerService, Context context) {
        mLeaseId = lid;
        mOwnerId = Oid;
        mType = type;
        mStatus = LeaseStatus.INVALID;
        mRStatManager = RStatManager;
        mProxy = proxy;
        mHandler = handler;
        mContext = context;
        mBatteryMonitor = BatteryMonitor.getInstance(context);
        isProbing = false;
        mLeaseManagerService = leaseManagerService;
    }

    /**
     * Create a new lease and the corresponding resource manager
     */
    public void create(long now) {
        mRenewal = 0;
        mStatus = LeaseStatus.ACTIVE;
        mLength = DEFAULT_NORMAL_TERM_MS;
        mDelayInterval = DEFAULT_NORMAL_DELAY_MS;
        mDelayCounter = 0;
        isCharging = mBatteryMonitor.isCharging();
        mBeginTime = now;
        isDelay = false;
        mLeaseManagerService.getAndCleanException(mOwnerId);
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


    public boolean isActiveOrRenew() {
        isCharging = mBatteryMonitor.isCharging();
        if (mStatus == LeaseStatus.ACTIVE || isCharging) {
            return true;
        } else if (mStatus == LeaseStatus.EXPIRED && !isDelay) {
            return RenewDescison(true);
        } else if (mStatus == LeaseStatus.EXPIRED && isDelay) {
            return false;
        }
        return false;
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
     */
    public ResourceStat getCurrentStat() {
        return mRStatManager.getCurrentStat(mLeaseId);
    }

    /**
     * Return the history of resource stat of the lease.
     */
    public StatHistory getStatHistory() {
        return mRStatManager.getStatsHistory(mLeaseId);
    }

    /**
     * Set the associated lease proxy.
     */
    public void setProxy(ILeaseProxy proxy) {
        mProxy = proxy;
    }

    /**
     * Set the associated worker thread for this lease.
     */
    public void setHandler(LeaseWorkerHandler handler) {
        mHandler = handler;
    }

    public static void setDefaultParameter(long leaseTerm, long delayInterval) {
       // Slog.d(TAG, "Set the lease term as " + leaseTerm / 1000 + " seconds and delay interval as "+ delayInterval / 1000 + " seconds");
        USER_DEFINE_TERM_MS = (int) leaseTerm;
        USER_DEFINE_DELAY_TIME = (int) delayInterval;
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
    /*
    public boolean freeze() {
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling onFreeze for uid " + mOwnerId + " to freeze for " +
                mDelayInterval);
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
*/

    /**
     * One lease term has come to an end.
     */
    public void endTerm() {
        //TODO: The way to calculate wakelock end time is not correct when the phone goes into deep sleep
        mEndTime = SystemClock.elapsedRealtime();

        // update the stats for this lease term
        mRStatManager.update(mLeaseId, mBeginTime, mEndTime, mOwnerId);
        if (isCharging == true || mBatteryMonitor.isCharging()) {
           Slog.d(TAG, "The phone is in charging, immediately renew for lease " + mLeaseId);
            renew(true);
            return;
        }
        RenewDescison(false);
    }

    public boolean RenewDescison(boolean isProxy) {
        Decision decision = LeasePolicyRuler.behaviorJudge(this, isProxy);
        switch (decision.mDecision) {
            case EXPIRE:
                isDelay = false;
                expire();
                return true;
            case RENEW:
                Slog.d(TAG, "Start renew action for decision " + decision.mBehaviorType);
                if (isProxy) {
                    mLength = DEFAULT_PROBING_TERM_MS;
                    isProbing = true;
                } else {
                    mLength = DEFAULT_NORMAL_TERM_MS;
                }
                renew(true); // skip checking the status as we just transit from end of term
                return true;
            case DELAY:
                Slog.e(TAG, "Start delay action for decision " + decision.mBehaviorType);
                expire();
                sechduleNextLeaseTerm(decision);
                return false;
        }
        return false;
    }

    public void sechduleNextLeaseTerm(Decision decision) {
        isDelay = true;
        mDelayCounter++;
        boolean flag = false;
        switch (decision.mBehaviorType) {
            case FrequencyAsking:
                mDelayInterval = DEFAULT_FREQUENCYASK_DELAY_MS;
                mLength = DEFAULT_FREQUENCYASK_TERM_MS;
                if (isProbing) {
                    renew(true);
                }
                scheduleDelay(mDelayInterval);
                break;
            case LongHolding:
                mDelayInterval = DEFAULT_LONGHOLD_DELAY_MS;
                mLength = DEFAULT_LONGHOLD_TERM_MS;
                if (isProbing) {
                    renew(true);
                }
                scheduleDelay(mDelayInterval);
                break;
            case LowUtility:
                mDelayInterval = DEFAULT_LOWUTILITY_TERM_MS;
                mLength = DEFAULT_LOWUTILITY_DELAY_MS;
                if (isProbing) {
                    renew(true);
                }
                scheduleDelay(mDelayInterval);
                break;
            case HighDamage:
                mDelayInterval = DEFAULT_HIGHDAMAGE_TERM_MS;
                mLength = DEFAULT_HIGHDAMAGE_DELAY_MS;
                if (isProbing) {
                    renew(true);
                }
                scheduleDelay(mDelayInterval);
                break;
        }
    }

    /**
     * Renew a new lease term for the lease. There are two types of renewal. One is automatic
     * renewal that's granted at the end of a lease term (the original meaning of renew). The other
     * is delayed renewal that's requested from the proxy, e.g., a lease has been expired and
     * then after
     * 5ms an app tries to access the resource again.
     *
     * @param auto should the lease status be checked
     * @return true if the lease is renewed
     */
    public boolean renew(boolean auto) {
        Slog.d(TAG, "Starting renew lease " + mLeaseId + " for " + mLength / 1000 + " second");
        isDelay = false;
        if (!auto && mStatus != LeaseStatus.EXPIRED) {
            // if a renewal is not at the end of a lease term, we must check for status first
            return false;
        }
        isCharging = mBatteryMonitor.isCharging();
        mRenewal++;
        mBeginTime = SystemClock.elapsedRealtime();
        boolean success = false;
        // create a new stat for the new lease term
        switch (mType) {
            case Wakelock:
                // TODO: supply real argument for holding time and usage time.
                WakelockStat wStat = new WakelockStat(mBeginTime, mOwnerId, mContext, mLeaseManagerService, mHandler);
                mStatus = LeaseStatus.ACTIVE;
                success = mRStatManager.addResourceStat(mLeaseId, wStat);
                break;
            case Location:
                LocationStat lStat = new LocationStat(mBeginTime, mOwnerId, mContext, mLeaseManagerService, mHandler);
                mStatus = LeaseStatus.ACTIVE;
                success = mRStatManager.addResourceStat(mLeaseId, lStat);
                break;
            case Sensor:
                SensorStat sStat = new SensorStat(mBeginTime, mOwnerId, mContext, mLeaseManagerService, mHandler);
                mStatus = LeaseStatus.ACTIVE;
                success = mRStatManager.addResourceStat(mLeaseId, sStat);
                break;
        }
        if (auto) {
            // if it's an automatic renewal, we must try to notify the lease proxy
            if (mProxy != null) {
                try {
                 //   Slog.d(TAG, "Calling onRenew for lease " + mLeaseId);
                    mProxy.onRenew(mLeaseId);
                } catch (RemoteException e) {
                //    Slog.e(TAG, "Failed to invoke onExpire for lease " + mLeaseId);
                    success = false;
                }
            } else {
             //   Slog.e(TAG, "No lease proxy for lease " + mLeaseId);
                success = false;
            }
        }
        scheduleExpire(mLength);
        return success;
    }

    public void scheduleDelay(long delayInterval) {
        if (!mScheduled && mHandler != null) {
            Slog.d(TAG, "Renew lease " + mLeaseId + " after " + delayInterval / 1000 + " s");
            mHandler.postDelayed(mRenewRunnable, delayInterval);
            mScheduled = true;
        }
    }

    public void cancelDelay() {
        if (mScheduled && mHandler != null) {
            Slog.d(TAG, "Cancelling delay renew for lease " + mLeaseId);
            mHandler.removeCallbacks(mRenewRunnable);
            mScheduled = false;
        }
    }

    public void scheduleExpire(long leaseTerm) {
        if (!mScheduled && mHandler != null) {
            Slog.d(TAG,
                    "Scheduling expiration check for lease " + mLeaseId + " after " + mLength / 1000
                            + " s");
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