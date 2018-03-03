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
package android.lease;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

/**
 *The manager class for Lease, the wrapper of LeaseManagerServices
 */
public final class LeaseManager {
    private static final String TAG = "LeaseManager";

    public static final int INVALID_LEASE = -1;
    public static final long LEASE_ID_START = 1000;

    private ILeaseManager mService;
    private Context mContext;

    public LeaseManager(Context context, ILeaseManager service) {
        mContext = context;
        mService = service;
    }

    /**
     * Create a new lease instance, call LeaseManagerService
     *
     * @param rtype the resource type
     * @param uid the owner id
     * @return lease id
     */
    public long create(ResourceType rtype, int uid) {
        long leaseId = -1;
        try {
           leaseId = mService.create(rtype, uid);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Fail to create new lease");
        }
        return leaseId;
    }

    /**
     * Check the status of the lease
     *
     * @param leaseId lease id
     * @return true if the lease is active
     */
    public boolean check(long leaseId) {
        if (leaseId < LEASE_ID_START)
            return false;
        boolean success = false;
        try {
             success  = mService.check(leaseId);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Fail to check the lease status");
        }
        return success;
    }

    /**
     * Expire the lease
     *
     * @param leaseId lease id
     * @return ture if the lease is successfully expired
     */
    public boolean expire(long leaseId) {
        if (leaseId < LEASE_ID_START)
            return false;
        boolean success= false;
        try{
            success = mService.expire(leaseId);
        } catch (RemoteException e){
            Log.wtf(TAG, "Fail to expire the lease");
        }
        return success;
    }

    /**
     * Renew the lease
     *
     * @param leaseId lease id
     * @return ture if the lease is successfully renewed
     */
    public boolean renew(long leaseId) {
        if (leaseId < LEASE_ID_START)
            return false;
        boolean success = false;
        try {
            success = mService.renew(leaseId);
        }catch (RemoteException e){
            Log.wtf(TAG, "Fail to renew the lease");
        }
        return success;
    }

    /**
     * Remove the lease
     *
     * @param leaseId lease id
     * @return true if the lease is successfully removed
     */
    public boolean remove(long leaseId) {
        if (leaseId < LEASE_ID_START)
            return false;
        boolean success = false;
        try {
            success = mService.remove(leaseId);
        }catch (RemoteException e){
            Log.wtf(TAG, "Fail to remove the lease");
        }
        return success;
    }

    /**
     * Notify lease manager about an event for a lease id
     *
     * @param leaseId
     * @param event
     */
    public void noteEvent(long leaseId, LeaseEvent event) {
        if (leaseId < LEASE_ID_START)
            return;
        try {
            mService.noteEvent(leaseId, event);
        }catch (RemoteException e){
            Log.wtf(TAG, "Fail to remove the lease");
        }
        return;
    }
}