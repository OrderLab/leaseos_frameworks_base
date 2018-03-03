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

    public static final int WAKELOCK_LEASE_PROXY= 0x00000100;
    public static final int LOCATION_LEASE_PROXY = 0x00000200;
    public static final int ALARM_LEASE_PROXY= 0x00000300;
    public static final int SENSOR_LEASE_PROXY = 0x00000400;
    public static final int NETWORK_LEASE_PROXY = 0x00000500;
    public static final int NOTIFICATION_LEASE_PROXY = 0x00000600;
    public static final int CPU_LEASE_PROXY = 0x00000700;
    public static final int STORAGE_LEASE_PROXY = 0x00000800;
    public static final int BLUETOOTH_LEASE_PROXY = 0x00000900;
    public static final int LIGHT_LEASE_PROXY = 0x00000a00;
    public static final int CAMERA_LEASE_PROXY = 0x00000b00;
    public static final int IO_LEASE_PROXY = 0x00000c00;

    public static final String UNKNOWN_PROXY_STR = "Unknown";
    public static final String WAKELOCK_PROXY_STR = "WakelockLeaseProxy";
    public static final String LOCATION_PROXY_STR = "GPSLeaseProxy";
    public static final String ALARM_PROXY_STR = "AlarmLeaseProxy";
    public static final String SENSOR_PROXY_STR = "SensorLeaseProxy";
    public static final String NETWORK_PROXY_STR = "NetworkLeaseProxy";
    public static final String NOTIFICATION_PROXY_STR = "NotificationLeaseProxy";
    public static final String CPU_PROXY_STR = "CPULeaseProxy";
    public static final String STORAGE_PROXY_STR = "StorageLeaseProxy";
    public static final String BLUETOOTH_PROXY_STR = "BluetoothLeaseProxy";
    public static final String LIGHT_PROXY_STR = "LightLeaseProxy";
    public static final String CAMERA_PROXY_STR = "CameraLeaseProxy";
    public static final String IO_PROXY_STR = "IOLeaseProxy";


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

    /**
     * Register a lease proxy with lease manager service
     *
     * @param type
     * @param name
     * @param proxy
     * @return
     */
    public boolean registerProxy(int type, String name, ILeaseProxy proxy) {
        try {
            return mService.registerProxy(type, name, proxy);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Fail to register lease proxy");
            return false;
        }
    }

    /**
     * Return the string representation of the given type
     *
     * @param type
     * @return
     */
    public static String getProxyTypeString(int type) {
        switch(type) {
            case WAKELOCK_LEASE_PROXY:
                return WAKELOCK_PROXY_STR;
            case LOCATION_LEASE_PROXY:
                return LOCATION_PROXY_STR;
            case ALARM_LEASE_PROXY:
                return ALARM_PROXY_STR;
            case SENSOR_LEASE_PROXY:
                return SENSOR_PROXY_STR;
            case NETWORK_LEASE_PROXY:
                return NETWORK_PROXY_STR;
            case NOTIFICATION_LEASE_PROXY:
                return NOTIFICATION_PROXY_STR;
            case CPU_LEASE_PROXY:
                return CPU_PROXY_STR;
            case STORAGE_LEASE_PROXY:
                return STORAGE_PROXY_STR;
            case BLUETOOTH_LEASE_PROXY:
                return BLUETOOTH_PROXY_STR;
            case LIGHT_LEASE_PROXY:
                return LIGHT_PROXY_STR;
            case CAMERA_LEASE_PROXY:
                return CAMERA_PROXY_STR;
            case IO_LEASE_PROXY:
                return IO_PROXY_STR;
            default:
                return UNKNOWN_PROXY_STR;
        }
    }
}