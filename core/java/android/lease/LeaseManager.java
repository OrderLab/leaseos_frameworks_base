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
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryStats;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.app.IBatteryStats;

import java.util.HashMap;

/**
 *The manager class for Lease, the wrapper of LeaseManagerServices
 */
public final class LeaseManager {
    private static final String TAG = "LeaseManager";

    public static final int WAKELOCK_LEASE_PROXY= 0x00000100;
    public static final int LOCATION_LEASE_PROXY = 0x00000200;
    public static final int SENSOR_LEASE_PROXY = 0x00000400;

    public static final String UNKNOWN_PROXY_STR = "Unknown";
    public static final String WAKELOCK_PROXY_STR = "WakelockLeaseProxy";
    public static final String LOCATION_PROXY_STR = "GPSLeaseProxy";
    public static final String SENSOR_PROXY_STR = "SensorLeaseProxy";

    public static final int INVALID_LEASE = -1;
    public static final long LEASE_ID_START = 1000;

    private ILeaseManager mService;
    private Context mContext;


    public LeaseManager(Context context, ILeaseManager service) {
        Log.d(TAG, "Create a lease manager");
        mContext = context;
        mService = service;
    }

    /**
     * Create a new lease instance, call LeaseManagerService
     *
     * @param resourceType the resource type
     * @param uid the owner id
     * @return lease id
     */
    public long create(ResourceType resourceType, int uid) {
        long leaseId = -1;
        try {
           leaseId = mService.create(resourceType, uid);
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
            Log.wtf(TAG, "Fail to note the event");
        }
        return;
    }


    public void noteEvent(long leaseId, LeaseEvent event, String activityName) {
        if (leaseId < LEASE_ID_START)
            return;
        try {
            mService.noteSensorEvent(leaseId, event, activityName);
        }catch (RemoteException e){
            Log.wtf(TAG, "Fail to note the location event");
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
    public boolean registerProxy(int type, String name, ILeaseProxy proxy, int uid) {
        try {
            return mService.registerProxy(type, name, proxy, uid);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Fail to register lease proxy");
            return false;
        }
    }

    /**
     * Unregister a lease proxy with lease manager service
     *
     * @param proxy
     * @return
     */
    public boolean unregisterProxy(ILeaseProxy proxy) {
        try {
            return mService.unregisterProxy(proxy);
        } catch (RemoteException e) {
            Log.wtf(TAG, "Fail to unregister lease proxy");
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
            case SENSOR_LEASE_PROXY:
                return SENSOR_PROXY_STR;
            default:
                return UNKNOWN_PROXY_STR;
        }
    }


    private IBatteryStats mBatteryStatsService;

    private boolean getService() {
        if (mBatteryStatsService == null) {
            Log.d(TAG, "Getting IBatteryStatsService");
            mBatteryStatsService = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));
        }
        return mBatteryStatsService != null;
    }

    public void getStat() {
        if (!getService()) {
            Log.e(TAG, "Fail to get IBatteryStatsService");
            return ;
        }
        try {
            mBatteryStatsService.refreshStatic();
        }catch (RemoteException e) {
            Log.e(TAG, "Fail to refreshStatic");
            return ;
        }
    }

    public void noteSensorUtility(boolean canScreenOn, boolean canBackground, int minFrequencyUS, int batchReportLatencyUS) {
        int uid = mContext.getApplicationInfo().uid;
        try {
            mService.updateSensorUtility(canScreenOn, canBackground, minFrequencyUS, batchReportLatencyUS, uid);
        }catch (RemoteException e) {
            Log.e(TAG, "Fail to note SensorUtility to lease manager service");
            return;
        }
    }

    public void updateSensorListener(int delayUs, int maxBatchReportLatencyUs, long leaseId) {
        try {
            mService.updateSensorListener(delayUs, maxBatchReportLatencyUs, leaseId);
        }catch (RemoteException e) {
            Log.e(TAG, "Fail to update sensor listener information");
            return;
        }
    }

    public void noteLocationUtility(boolean canScreenOn, boolean canBackground, long minFrequencyMS, float minDistance, int accuracy) {
        int uid = mContext.getApplicationInfo().uid;
        try {
            mService.updateLocationUtility(canScreenOn, canBackground, minFrequencyMS, minDistance, accuracy, uid);
        }catch (RemoteException e) {
            Log.e(TAG, "Fail to note location utility to lease manager service");
            return;
        }
    }

    public void updateLocationListener(long minFrequencyMS,float minDistance,int accuracy, long leaseId) {
        try {
            mService.updateLocationListener(minFrequencyMS, minDistance, accuracy, leaseId);
        }catch (RemoteException e) {
            Log.e(TAG, "Fail to update location listener information");
            return;
        }
    }

    public void noteScreenOff(){
        try{
            mService.noteScreenOff();
        }catch (Exception e) {
            Log.e(TAG, "Fail to note screen off");
            return;
        }
    }

    public void noteScreenOn(){
        try{
            mService.noteScreenOn();
        }catch (Exception e) {
            Log.e(TAG, "Fail to note screen on");
            return;
        }
    }


    public void setUtilitCounter(long leaseId, IUtilityCounter counter) {
        try{
            mService.registerUtilityCounter(leaseId, counter);
        } catch (Exception e) {
            Log.e(TAG, "Fail to register utility counter");
            return;
        }
    }
}