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
import java.util.Hashtable;
import android.lease.*;
import android.util.Log;

/**
 * The central lease manager service
 */
public class LeaseManagerService extends ILeaseManager.Stub{

    private static final String TAG = "L"
    //Operation failed
    public static final int FAILED = -1;

    // Table of all leases acquired by services.
    //TODO: change the hash tableeasaseeaseM
    private final Hashtable<Long, Lease> mLeases = new Hashtable();

    //The identifier of the last lease
    private long mLastLeaseId = 1000;

    private ResourceStatManager mRStatManager;

    private Context mContext;

    public LeaseManagerService(Context context) {
        super();
        mContext = context;
        mRStatManager = new ResourceStatManager();
    }

    /**
     * Create a new lease
     *
     * @param RType The resource type of the lease
     * @param uid   the identifier of caller
     * @return the lease id
     */
    public long newLease(ResourceType RType, long uid) {

        /*if (!validateTypeParameters(RType)) {
            return FAILED;
        }*/
        Lease lease = new Lease(mLastLeaseId, uid, RType, mRStatManager);
        StatHistory statHistory;

        Log.i(TAG, "newLease: begin to create a lease for process: " +_ uid + );
        mLeases.put(mLastLeaseId, lease);
        lease.create();
        switch (RType) {
            case Wakelock:
                statHistory = new StatHistory<WakelockStat>();
                mRStatManager.setStatsHistory(lease, statHistory);
                break;
            case Location:
                statHistory = new StatHistory<LocationStat>();
                mRStatManager.setStatsHistory(lease, statHistory);
                break;
            case Sensor:
                statHistory = new StatHistory<SensorStat>();
                mRStatManager.setStatsHistory(lease, statHistory);
                break;

        }
        mLastLeaseId++;
        return lease.mLeaseid;
    }

    /**
     * Check the validation of the lease
     *
     * @param leaseid The identifier of lease
     * @return True if the lease is valid
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean check(long leaseid)  {
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
    public boolean expire(long leaseid){
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
            return false;
        }
        //TODO: how to handler the logic of true or false
        lease.expire();
        mRStatManager.removeStatHistory(lease);
        mLeases.remove(leaseid, lease);
        return true;
    }

    /**
     * Verify the type parameter is vaild
     *
     * @param RType The resource type of the lease
     * @return true if the type is valid
     */
    /*
    public static boolean validateTypeParameters(String RType) {
        for (ResourceType type : ResourceType.values()) {
            if (type.toString() == RType) {
                return true;
            }
        }
        return false;
    }*/

    /**
     * Find the lease index by the Leaseid
     *
     * @param Leaseid The identifier of lease
     * @return The lease index or -1 if can not find the lease
     */
    /*
    private int findLeaseIndex(int Leaseid) {
        final int count = mLeases.size();
        for (int i = 0; i < count; i++) {
            if (mLeases.get(i).mLeaseid == Leaseid) {
                return i;
            }
        }
        return FAILED;
    }
*/
}