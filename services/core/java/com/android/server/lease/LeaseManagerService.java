/*
 *  @author Yiong Hu <hyigong1@jhu.edu>
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
import android.lease.ILeaseManager;
import android.lease.LeaseEvent;
import android.lease.LeaseManager;
import android.lease.ResourceType;
import android.os.Process;
import android.util.Slog;

import java.util.Hashtable;
import java.util.concurrent.locks.Lock;

/**
 * The central lease manager service
 */
public class LeaseManagerService extends ILeaseManager.Stub {

    //Operation failed
    public static final int FAILED = -1;
    private static final String TAG = "LeaseManagerService";
    private final Object mLock = new Object();
    // Table of all leases acquired by services.

    private final Hashtable<Long, Lease> mLeases = new Hashtable();

    //The identifier of the last lease
    private long mLastLeaseId = LeaseManager.LEASE_ID_START;

    private ResourceStatManager mRStatManager;

    private final Context mContext;

    public LeaseManagerService(Context context) {
        super();
        mContext = context;
        mRStatManager = ResourceStatManager.getInstance(mContext);
        Slog.i(TAG, "LeaseManagerService initialized");
    }

    public ResourceStat getCurrentStat(long leaseId) {
        return mRStatManager.getCurrentStat(leaseId);
    }

    /**
     * Create a new lease
     *
     * @param rtype The resource type of the lease
     * @param uid   the identifier of caller
     * @return the lease id
     */
    public long create(ResourceType rtype, int uid) {
        synchronized (mLock) {
            if (uid < Process.FIRST_APPLICATION_UID || uid > Process.LAST_APPLICATION_UID) {
                return Lease.INVALID_LEASE;
            }
            Lease lease = new Lease(mLastLeaseId, uid, rtype, mRStatManager, mContext);
            StatHistory statHistory;
            Slog.i(TAG,
                    "newLease: begin to create a lease " + mLastLeaseId + " for process: " + uid);

            mLeases.put(mLastLeaseId, lease);
            lease.create();
            Slog.d(TAG, "Start to Create a StatHistory for the " + mLastLeaseId);
            statHistory = new StatHistory();
            Slog.d(TAG, "Create a StatHistory for the " + mLastLeaseId);
            switch (rtype) {
                case Wakelock:
                    WakelockStat wStat = new WakelockStat(lease.mBeginTime, uid, mContext);
                    if( wStat.mStatus == LeaseStatus.CHARGING) {
                        lease.mStatus = LeaseStatus.CHARGING;
                    }
                    statHistory.addItem(wStat);
                    mRStatManager.setStatsHistory(lease.mLeaseId, statHistory);
                    break;
                case Location:
                    mRStatManager.setStatsHistory(lease.mLeaseId, statHistory);
                    break;
                case Sensor:
                    mRStatManager.setStatsHistory(lease.mLeaseId, statHistory);
                    break;
            }
            mLastLeaseId++;
            return lease.mLeaseId;
        }
    }

    /**
     * Check the validation of the lease
     *
     * @param leaseid The identifier of lease
     * @return True if the lease is valid
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean check(long leaseid) {
        Lease lease = mLeases.get(leaseid);
        if (lease == null) {
            return false;
        }
        return lease.isActive();
    }


    /**
     * Expire the lease
     *
     * @param leaseid The identifier of lease
     * @return Ture if the lease expire
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean expire(long leaseid) {
        Lease lease = mLeases.get(leaseid);
        if (lease == null) {
            return false;
        }
        return lease.expire();
    }

    /**
     * Renew the lease
     *
     * @param leaseid The identifier of lease
     * @return Ture if the lease is renewed
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean renew(long leaseid) {
        Lease lease = mLeases.get(leaseid);
        if (lease == null) {
            return false;
        }
        return lease.expire();
    }

    /**
     * Remove the lease
     *
     * @param leaseid The identifier of lease
     * @return Ture if the lease is removed from lease table
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean remove(long leaseid) {
        Lease lease = mLeases.get(leaseid);
        if (lease == null) {
            Slog.d(TAG, "remove: can not find lease for id:" + leaseid);
            return false;
        }
        //TODO: how to handler the logic of true or false
        lease.cancelChecks();
        mRStatManager.removeStatHistory(lease.mLeaseId);
        mLeases.remove(leaseid);
        return true;
    }

    public void noteEvent(long leaseId, LeaseEvent event) {
        Lease lease = mLeases.get(leaseId);
        if (lease == null || !lease.isActive()) {
            // if lease is no longer active, ignore the event
            return;
        }
        StatHistory statHistory = mRStatManager.getStatsHistory(leaseId);
        if (statHistory == null) {
            Slog.e(TAG, "No stat history exist for lease " + leaseId + ", possibly a bug");
            return;
        }
        switch (event) {
            case WAKELOCK_ACQUIRE:
                statHistory.noteAcquire();
                break;
            case WAKELOCK_RELEASE:
                statHistory.noteRelease();
                break;
            default:
                Slog.e(TAG, "Unhandled event " + event + " reported for lease " + leaseId);
        }
    }
}