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

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.lease.BehaviorType;
import android.lease.ILeaseProxy;
import android.lease.IUtilityCounter;
import android.lease.LeaseStatus;
import android.lease.ResourceType;
import android.lease.TimeUtils;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Slog;

import java.util.List;

/**
 * The struct of lease.
 *
 *
 */
public class Lease {
    private static final String TAG = "Lease";

    public static int USER_DEFINE_TERM_MS = 30 * 1000; // default 30 seconds, may need to reduce it
    public static int USER_DEFINE_DELAY_TIME = 30 * 1000; // the delay time, default 30 seconds

    private static final int DELAY = 1;
    private static final int WEAK_DELAY = 2;
    private static final int INVALID = 3;

    private int mRatio;  // The ratio of lease term and delay interval
    protected long mLeaseId; // The identifier of lease
    protected int mOwnerId;
            // The identifier of the owner of lease. This variable usually means the UID
    protected ResourceType mType; // The type of resource the lease is assigned
    protected LeaseStatus mStatus; // The status of the lease
    protected int mLength; // The length of this lease term in millisecond
    protected long mBeginTime; // The BeginTime of this lease term
    protected long mEndTime; // The EndTime of this lease term
    protected int mNormal; // The number of continue normal behavior
    protected final Context mContext; // The context in which the lease is created
    protected ResourceStatManager mRStatManager;
            // The record of the history lease term for this lease
    protected LeaseManagerService mLeaseManagerService;
    protected ILeaseProxy mProxy; // the associated lease proxy
    protected long mDelayInterval; // the time interval between two leases
    BatteryMonitor mBatteryMonitor; // the instance of Battery Monitor
    protected boolean isCharging; // true if the phone is charged during this lease term
    protected long lastNormal;
    protected IUtilityCounter mCounter = null;

    private LeaseWorkerHandler mHandler;
    private boolean mScheduled;

    public boolean isMatch;

    private final int wakelock_lease = 5 * TimeUtils.MILLIS_PER_MINUTE;
    private final int sensor_lease = 10* TimeUtils.MILLIS_PER_MINUTE;

    private final int DEFAULT_LEASE_TERM_MS = 5 * TimeUtils.MILLIS_PER_SECOND;
    private int DEFAULT_DELAY_MS = DEFAULT_LEASE_TERM_MS * mRatio;

    private final int DEFAULT_PROBING_TERM_MS = 30 * TimeUtils.MILLIS_PER_SECOND;

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
            ILeaseProxy proxy, LeaseWorkerHandler handler, LeaseManagerService leaseManagerService,
            Context context) {
        mLeaseId = lid;
        mOwnerId = Oid;
        mType = type;
        if (mType == ResourceType.Wakelock) {
            mLength = wakelock_lease;
        } else {
            mLength = sensor_lease;
        }
        mStatus = LeaseStatus.ACTIVE;
        mRStatManager = RStatManager;
        mProxy = proxy;
        mHandler = handler;
        mContext = context;
        mBatteryMonitor = BatteryMonitor.getInstance(context);
        mLeaseManagerService = leaseManagerService;
        lastNormal = SystemClock.elapsedRealtime();
    }

    /**
     * Create a new lease and the corresponding resource manager
     */
    public void create(long now) {
        mNormal = 0;
        mStatus = LeaseStatus.ACTIVE;
        //mLength = DEFAULT_LEASE_TERM_MS;
        mRatio = mLeaseManagerService.getRatio();
        mDelayInterval = DEFAULT_DELAY_MS;

        isCharging = mBatteryMonitor.isCharging();
        mBeginTime = now;
        mLeaseManagerService.getAndCleanException(mOwnerId);
        checkUtilityMatch();
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


    public boolean check() {
        isCharging = mBatteryMonitor.isCharging();
        if (mStatus == LeaseStatus.ACTIVE || isCharging) {
            return true;
        } else if (mStatus == LeaseStatus.DELAY) {
            return false;
        } else {
            lastNormal = SystemClock.elapsedRealtime();
            mLength = DEFAULT_PROBING_TERM_MS;
            return renew(false);
        }
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
    public ResourceType getType() {
        return mType;
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

    public void setCounter(IUtilityCounter counter) {
            mCounter = counter;
    }

    public static void setDefaultParameter(long leaseTerm, long delayInterval) {
        // Slog.d(TAG, "Set the lease term as " + leaseTerm / 1000 + " seconds and delay interval
        // as "+ delayInterval / 1000 + " seconds");
        USER_DEFINE_TERM_MS = (int) leaseTerm;
        USER_DEFINE_DELAY_TIME = (int) delayInterval;
    }

    public void checkUtilityMatch() {
        boolean isBackground;

        Slog.d(TAG, "Checking the utility of lease " + mLeaseId);
        LeaseManagerService.UtilityStat utilityStat = mLeaseManagerService.getUtilityStat(mOwnerId,
                mType);
        if (utilityStat == null) {
            return;
        }

        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList;
        try {
            runningAppProcessInfoList = ActivityManagerNative.getDefault().getRunningAppProcesses();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get app process");
            runningAppProcessInfoList = null;
        }

        if (runningAppProcessInfoList == null) {
            isBackground = true;
        } else {
            isBackground = true;
            for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcessInfoList) {
                if (mOwnerId == processInfo.uid) {
                    isBackground = false;
                    break;
                }
            }
        }

        if (!(utilityStat.mCanScreenOn || mLeaseManagerService.isScreenOff)) {
            getStatHistory().getCurrentStat().setMatch(false);
        }

        if (!(isBackground || utilityStat.mCanBackground)) {
            getStatHistory().getCurrentStat().setMatch(false);
        }

        switch (mType) {
            case Wakelock:
                break;
            case Location:
                LeaseManagerService.LocationListener locationListener =
                        mLeaseManagerService.getLocationListener(mLeaseId);
                if (locationListener == null) {
                    Slog.e(TAG, "Failed to get location information");
                } else if (locationListener.mFrequencyMS > utilityStat.mLocationMinFrequencyMS
                        || locationListener.mMinDistance > utilityStat.mMinDistance) {
                    getStatHistory().getCurrentStat().setMatch(false);
                }
                break;
            case Sensor:
                LeaseManagerService.SensorListener sensorListener =
                        mLeaseManagerService.getsensorListener(mLeaseId);
                if (sensorListener == null) {
                    Slog.e(TAG, "Failed to get sensor information");
                } else if (sensorListener.mDelayUs > utilityStat.mSensorMinFrequencyUS
                        || sensorListener.mMaxBatchReportLatencyUs
                        > utilityStat.mBatchReportLatencyUS) {
                    getStatHistory().getCurrentStat().setMatch(false);
                }
                break;
            default:
                Slog.d(TAG, "Unknow type of resource");
        }
    }

    /**
     * Weak Expire the lease, which just delay the update frequency of resource
     *
     * @return true if the lease is successfully expired
     */
    public boolean weakExpire() {
        if (mStatus != LeaseStatus.EXPIRED) {
            Slog.e(TAG, "Skip expiring an inactive lease " + mLeaseId);
            return false;
        }
        mStatus = LeaseStatus.DELAY;
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling weakExpire for lease " + mLeaseId);
                mProxy.weakExpire(mLeaseId);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke weakExpire for lease " + mLeaseId);
                return false;
            }
            return true;
        } else {
            Slog.e(TAG, "No lease proxy for lease " + mLeaseId);
            return false;
        }
    }

    /**
     * Expire the lease
     *
     * @return true if the lease is successfully expired
     */
    public boolean expire() {
        if (mStatus != LeaseStatus.EXPIRED) {
            Slog.e(TAG, "Skip expiring an inactive lease " + mLeaseId);
            return false;
        }
        mStatus = LeaseStatus.DELAY;
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
     * Invalid the lease
     *
     * @return true if the lease is successfully invalid
     */
    public boolean invalid() {
        if (mStatus != LeaseStatus.EXPIRED) {
            Slog.e(TAG, "Skip inactive an non-expired lease " + mLeaseId);
            return false;
        }
        mStatus = LeaseStatus.INVALID;
        if (mProxy != null) {
            try {
                Slog.d(TAG, "Calling invalid for lease " + mLeaseId);
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
     * Freeze the request for this uid: Lower the frequency and accuracy of sensor or Location
     * listener
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


    /**
     * One lease term has come to an end. We need to update the statistics usage of this lease
     * term to resource stat manager and decide the next state of this lease
     */
    public void endTerm() {
        // update the stats for this lease term
        if (mCounter != null) {
            try {
                int score = mCounter.getScore();
                mRStatManager.setScore(mLeaseId, score);
            } catch (RemoteException e) {
                Slog.wtf(TAG, "Failed to get score");
            }
        }
        mEndTime = SystemClock.elapsedRealtime();
        mRStatManager.update(mLeaseId, mBeginTime, mEndTime, mOwnerId);
        if (isCharging == true || mBatteryMonitor.isCharging()) {
            Slog.d(TAG, "The phone is in charging, immediately renew for lease " + mLeaseId);
           // renew(true);
            return;
        }
        mStatus = LeaseStatus.EXPIRED;
        expire();
        return;
        /*
        StatHistory statHistory = getStatHistory();
        if (statHistory == null) {
            Slog.d(TAG, "The lease does not have a stat history");
        } else {
            if (statHistory.judgeHistory() == BehaviorType.Normal) {
                lastNormal = SystemClock.elapsedRealtime();
            }
        }
        RenewDescison();*/
    }

    public boolean RenewDescison() {
        Decision decision = LeasePolicyRuler.behaviorJudge(this);
        switch (decision.mDecision) {
            case INVALID:
                invalid();
                return true;
            case REACTIVATE:
                Slog.d(TAG, "Start renew action for decision " + decision.mBehaviorType);
                mNormal++;
                if (mNormal > 12 && mNormal < 12 * 10) {
                    mLength =  12 * DEFAULT_LEASE_TERM_MS; // extend the lease term to 1 min if the lease are normal in the past 12 terms.
                } else if (mNormal > 12 * 10) {
                    mLength = 5 * 12 * DEFAULT_LEASE_TERM_MS; // extend the lease term to 5 mins if the lease are normal in the past 120 terms.
                } else {
                    mLength = DEFAULT_LEASE_TERM_MS;
                }
                renew(true); // skip checking the status as we just transit from end of term
                return true;
            case DELAY:
                mNormal = 0;
                Slog.e(TAG, "Start delay action for decision " + decision.mBehaviorType);
                if (SystemClock.elapsedRealtime() - lastNormal < 5 * TimeUtils.MILLIS_PER_MINUTE) {
                    weakExpire();
                } else {
                    expire();
                }
                mLength = DEFAULT_LEASE_TERM_MS;
                mRatio = mLeaseManagerService.getRatio();
                mDelayInterval = DEFAULT_DELAY_MS;
                scheduleDelay(mDelayInterval);
                //sechduleNextLeaseTerm(decision);
                return false;
        }
        return false;
    }

/*
    public void sechduleNextLeaseTerm(Decision decision) {
        mDelayCounter++;
        switch (decision.mBehaviorType) {
            case FrequencyAsking:
                mDelayInterval = DEFAULT_FREQUENCYASK_DELAY_MS;
                mLength = DEFAULT_FREQUENCYASK_TERM_MS;
                scheduleDelay(mDelayInterval);
                break;
            case LongHolding:
                mDelayInterval = DEFAULT_LONGHOLD_DELAY_MS;
                mLength = DEFAULT_LONGHOLD_TERM_MS;
                scheduleDelay(mDelayInterval);
                break;
            case LowUtility:
                mDelayInterval = DEFAULT_LOWUTILITY_TERM_MS;
                mLength = DEFAULT_LOWUTILITY_DELAY_MS;
                scheduleDelay(mDelayInterval);
                break;
            case HighDamage:
                mDelayInterval = DEFAULT_HIGHDAMAGE_TERM_MS;
                mLength = DEFAULT_HIGHDAMAGE_DELAY_MS;
                scheduleDelay(mDelayInterval);
                break;
        }
    }

    /**
     * Renew a new lease term for the lease. There are two types of renewal. One is automatic
     * renewal that's granted at the end of a lease term (the original meaning of renew). The other
     * is valid renewal that's requested from the proxy, e.g., a lease has been invalided and
     * then after 5ms the lease holder tries to access the resource again.
     *
     * @param auto should the lease status be checked
     * @return true if the lease is renewed
     */
    public boolean renew(boolean auto) {
        Slog.d(TAG, "Starting renew lease " + mLeaseId + " for " + mLength / 1000 + " second");
        if (!auto && mStatus == LeaseStatus.ACTIVE) {
            // if a renewal is not at the end of a lease term, we must check for status first
            return true;
        }
        isCharging = mBatteryMonitor.isCharging();
        mBeginTime = SystemClock.elapsedRealtime();
        boolean success = false;
        mStatus = LeaseStatus.ACTIVE;
        // create a new stat for the new lease term
        switch (mType) {
            case Wakelock:
                WakelockStat wStat = new WakelockStat(mBeginTime, mOwnerId, mContext,
                        mLeaseManagerService, mHandler);
                success = mRStatManager.addResourceStat(mLeaseId, wStat);
                break;
            case Location:
                LocationStat lStat = new LocationStat(mBeginTime, mOwnerId, mContext,
                        mLeaseManagerService, mHandler);
                success = mRStatManager.addResourceStat(mLeaseId, lStat);
                break;
            case Sensor:
                SensorStat sStat = new SensorStat(mBeginTime, mOwnerId, mContext,
                        mLeaseManagerService, mHandler);
                success = mRStatManager.addResourceStat(mLeaseId, sStat);
                break;
        }

        // if it's an automatic renewal, we must try to notify the lease proxy
        if (auto) {
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
        }

        checkUtilityMatch();
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