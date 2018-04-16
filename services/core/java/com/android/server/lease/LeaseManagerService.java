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

import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.lease.ILeaseManager;
import android.lease.ILeaseProxy;
import android.lease.LeaseEvent;
import android.lease.LeaseManager;
import android.lease.LeaseSettings;
import android.lease.LeaseSettingsUtils;
import android.lease.ResourceType;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Singleton;
import android.util.Slog;
import android.util.SparseArray;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * The central lease manager service
 */
public class LeaseManagerService extends ILeaseManager.Stub {

    //Operation failed
    public static final int FAILED = -1;
    private static final String TAG = "LeaseManagerService";
    private final Object mLock = new Object();

    private HandlerThread mHandlerThread;
    private LeaseHandler mHandler;

    // Table of all leases acquired by services.
    private final LongSparseArray<Lease> mLeases = new LongSparseArray<>();

    //The identifier of the last lease
    private long mLastLeaseId = LeaseManager.LEASE_ID_START;
    private ResourceStatManager mRStatManager;

    // All registered lease proxies
    private final HashMap<IBinder, LeaseProxy> mProxies = new HashMap<>();
    private final SparseArray<LeaseProxy> mTypedProxies = new SparseArray<>();


    // Each type of lease will get assigned with a different worker thread to
    // handle work related to these leases
    private final SparseArray<LeaseWorkerHandler> mWorkers = new SparseArray<>();
    private final SparseArray<Integer> mExceptionTable = new SparseArray<>();
    private final SparseArray<Integer> mTouchEventTable = new SparseArray<>();

    private final Context mContext;

    private boolean mLeaseRunning = false;

    private LeaseSettings mSettings;
    private SettingsObserver mSettingsObserver;

    private static final String[] OBSERVE_SETTINGS = new String[] {
            /*** Global settings ***/
            Settings.Secure.LEASE_SERVICE_ENABLED,
            Settings.Secure.LEASE_WHITELIST,
            Settings.Secure.LEASE_RATE_LIMIT_WINDOW,
            Settings.Secure.LEASE_GC_WINDOW,

            /*** Lease enabling settings ***/
            Settings.Secure.LEASEOS_WAKELOCK_LEASE_ENABLED,
            Settings.Secure.LEASEOS_LOCATION_LEASE_ENABLED,
            Settings.Secure.LEASEOS_SENSOR_LEASE_ENABLED,
    };

    public LeaseManagerService(Context context) {
        super();
        mContext = context;
        mRStatManager = ResourceStatManager.getInstance(mContext);
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new LeaseHandler(mHandlerThread.getLooper());
        mSettings = LeaseSettings.getDefaultSettings();
        mHandler.sendEmptyMessage(LeaseHandler.MSG_SYNC_SETTINGS);
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
                return LeaseManager.INVALID_LEASE;
            }
            Slog.i(TAG, "Begin to create a lease " + mLastLeaseId + " for process: " + uid);
            Lease lease = new Lease(mLastLeaseId, uid, rtype, mRStatManager, null,
                    null,this, mContext);
            mLeases.put(mLastLeaseId, lease);

            Slog.d(TAG, "Start to Create a StatHistory for the " + mLastLeaseId);
            StatHistory statHistory = new StatHistory();
            Slog.d(TAG, "Create a StatHistory for the " + mLastLeaseId);

            LeaseProxy proxy = null;
            LeaseWorkerHandler handler = null;
            long now = SystemClock.elapsedRealtime();
            switch (rtype) {
                case Wakelock:
                    WakelockStat wStat = new WakelockStat(now, uid, mContext);
                    statHistory.addItem(wStat);
                    proxy = mTypedProxies.get(LeaseManager.WAKELOCK_LEASE_PROXY);
                    handler = mWorkers.get(LeaseManager.WAKELOCK_LEASE_PROXY);
                    break;
                case Location:
                    proxy = mTypedProxies.get(LeaseManager.LOCATION_LEASE_PROXY);
                    handler = mWorkers.get(LeaseManager.LOCATION_LEASE_PROXY);
                    break;
                case Sensor:
                    proxy = mTypedProxies.get(LeaseManager.SENSOR_LEASE_PROXY);
                    handler = mWorkers.get(LeaseManager.SENSOR_LEASE_PROXY);
                    break;
            }
            if (proxy != null && proxy.mProxy != null) {
                lease.setProxy(proxy.mProxy); // set the proxy for this lease
            } else {
                Slog.e(TAG, "No proxy found for lease " + mLastLeaseId);
            }
            if (handler != null) {
                lease.setHandler(handler);
            } else {
                Slog.e(TAG, "No worker thread found for lease " + mLastLeaseId);
            }
            lease.create(now); // at last when proxy and worker thread is ready, create this lease
            mRStatManager.setStatsHistory(lease.mLeaseId, statHistory);
            mLastLeaseId++;

            return lease.mLeaseId;
        }
    }

    /**
     * Check the validation of the lease
     *
     * @param leaseId The identifier of lease
     * @return True if the lease is valid
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean check(long leaseId) {
        synchronized (mLock) {
            Lease lease = mLeases.get(leaseId);
            return lease != null && lease.isActiveOrRenew();
        }
    }


    /**
     * Expire the lease
     *
     * @param leaseId The identifier of lease
     * @return Ture if the lease expire
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean expire(long leaseId) {
        synchronized (mLock) {
            Lease lease = mLeases.get(leaseId);
            return lease != null && lease.expire();
        }
    }

    /**
     * Renew the lease
     *
     * @param leaseId The identifier of lease
     * @return Ture if the lease is renewed
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean renew(long leaseId) {
        Lease lease;
        synchronized (mLock) {
            lease = mLeases.get(leaseId);
            return lease != null;
        }
    }

    /**
     * Remove the lease
     *
     * @param leaseId The identifier of lease
     * @return Ture if the lease is removed from lease table
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean remove(long leaseId) {
        Lease lease;
        synchronized (mLock) {
            lease = mLeases.get(leaseId);
            if (lease == null) {
                Slog.d(TAG, "remove: can not find lease for id:" + leaseId);
                return false;
            }
            Slog.d(TAG, "Removed lease " + leaseId + " in LeaseManagerService");
        }
        //TODO: how to handler the logic of true or false
        lease.cancelExpire();
        mRStatManager.removeStatHistory(lease.mLeaseId);
        mLeases.remove(leaseId);
        return true;
    }

    /**
     * Note that an important event has happened for a lease
     *
     * @param leaseId
     * @param event
     */
    public void noteEvent(long leaseId, LeaseEvent event) {
        StatHistory statHistory;
        synchronized (mLock) {
            Lease lease = mLeases.get(leaseId);
            if (lease == null || !lease.isValid()) {
                // if lease is no longer active, ignore the event
                return;
            }
            statHistory = mRStatManager.getStatsHistory(leaseId);
            if (statHistory == null) {
                Slog.e(TAG, "No stat history exist for lease " + leaseId + ", possibly a bug");
                return;
            }
        }
        switch (event) {
            case WAKELOCK_ACQUIRE:
                Slog.d(TAG, "Note acquire event for lease " + leaseId);
                statHistory.noteAcquire();
                break;
            case WAKELOCK_RELEASE:
                Slog.d(TAG, "Note release event for lease " + leaseId);
                statHistory.noteRelease();
                break;
            default:
                Slog.e(TAG, "Unhandled event " + event + " reported for lease " + leaseId);
        }
    }

    public void noteException(int uid) {
        synchronized (this) {
            Slog.d(TAG, "Note exception for uid " + uid);
            int exceptions;
            if (mExceptionTable.get(uid) != null) {
                exceptions = mExceptionTable.get(uid) + 1;
            } else {
                exceptions = 1;
            }
            mExceptionTable.put(uid, exceptions);
           // Slog.d(TAG,"The number of Exceptions are " + exceptions + ", for uid " + uid + " for " + this + " for address " + mExceptionTable);
        }
    }

    public int getAndCleanException(int uid) {
        if (mExceptionTable.get(uid) == null) {
            return 0;
        }
        int exceptions = mExceptionTable.get(uid);
        mExceptionTable.remove(uid);
        Slog.d(TAG, "The number of exceptions are " + exceptions + " for uid " + uid);
        return exceptions;
    }

    public void noteTouchEvent(int uid) {
        synchronized (this) {
            Slog.d(TAG, "Note touch for uid " + uid);
            int toucnEvent;
            if (mTouchEventTable.get(uid) != null) {
                toucnEvent = mTouchEventTable.get(uid) + 1;
            } else {
                toucnEvent = 1;
            }
            mTouchEventTable.put(uid, toucnEvent);
            // Slog.d(TAG,"The number of Exceptions are " + exceptions + ", for uid " + uid + " for " + this + " for address " + mExceptionTable);
        }
    }

    public int getAndCleanTouchEvent(int uid) {
        if (mTouchEventTable.get(uid) == null) {
            return 0;
        }
        int toucnEvent = mTouchEventTable.get(uid);
        mTouchEventTable.remove(uid);
        Slog.d(TAG, "The number of toucn events are " + toucnEvent + "for uid " + uid);
        return toucnEvent;
    }

    public void systemRunning() {
        Slog.d(TAG, "Ready to start lease");
        synchronized (mLock) {
            // We should ALWAYS register for settings changes!
            // Otherwise we won't get notified when users change
            // from disabling the service to re-enabling the service
            registerSettingsListeners();

            // only start lease when system is up and running,
            // otherwise, there might be some dependency issues,
            // e.g,, alarm service not ready
            // we also do NOT start defense if the service is disabled
            if (mSettings.serviceEnabled)
                runLeaseLocked();
        }
    }

    private void registerSettingsListeners() {
        Slog.d(TAG, "Registering content observer");
        mSettingsObserver = new SettingsObserver(mHandler);
        // Register for settings changes.
        final ContentResolver resolver = mContext.getContentResolver();
        for (String settings:OBSERVE_SETTINGS) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(settings),
                    false, mSettingsObserver, UserHandle.USER_ALL);
        }
    }

    private void runLeaseLocked() {
        if (!mLeaseRunning) {
            mLeaseRunning = true;
            startAllLeaseProxyLocked();
        }
    }

    private void stopLeaseLocked() {
        if (mLeaseRunning) {
            mLeaseRunning = false;
            stopAllLeaseProxyLocked();
            stopAllLeaseLocked();
        }
    }

    /**
     * Called during initial service start or when related settings changed
     */
    private void updateSettingsLocked(LeaseSettings newSettings) {
        // If it's service enabling/disabling change, we need to start
        // or stop the leases
        Slog.d(TAG, "Updating setting");
        if (mSettings.serviceEnabled != newSettings.serviceEnabled) {
            if (newSettings.serviceEnabled) {
                mSettings = newSettings;
                runLeaseLocked();
            } else {
                mSettings = newSettings;
                stopLeaseLocked();
            }
        } else {
            // Otherwise, we need to inform the new settings to guardians if the service is enabled
            if (mSettings.serviceEnabled) {
                updateLeaseSetting(newSettings);
                checkLeaseEnableSettingsLocked(newSettings);
                notifySettingsChangedLocked(newSettings);
                mSettings = newSettings;
            }
        }
    }

    private void updateLeaseSetting(LeaseSettings newSettings) {
        Slog.d(TAG, "The default setting of lease term is " + newSettings.LeaseTermWindow + ", the default setting of delay interval is " + newSettings.DelayWindow);
        for (int i = 0; i < mLeases.size(); i++) {
            Lease lease = mLeases.valueAt(i);
            lease.USER_DEFINE_TERM_MS = (int) newSettings.LeaseTermWindow;
            lease.USER_DEFINE_DELAY_TIME = (int) newSettings.DelayWindow;
        }
        Lease.setDefaultParameter(newSettings.LeaseTermWindow, newSettings.DelayWindow);
    }

    /**
     * Notify registered lease proxies that the settings have changed!
     *
     * @param newSettings
     */
    private void notifySettingsChangedLocked(LeaseSettings newSettings) {
        Slog.d(TAG, "Notifying settings changes to leases...");
        for (LeaseProxy proxy:mProxies.values()) {
            try {
                proxy.mProxy.settingsChanged(newSettings);
            } catch (RemoteException e) {
                Slog.e(TAG, "Fail to notify settings change " + proxy);
            }
        }
    }

    private void checkLeaseEnableSettingsLocked(LeaseSettings newSettings) {
        Slog.d(TAG, "Checking leases enable settings");
        HashSet<Integer> disableLeases = new HashSet<Integer>();
        HashSet<Integer> enableLeases = new HashSet<Integer>();
        if (mSettings.wakelockLeaseEnabled != newSettings.wakelockLeaseEnabled) {
            if (newSettings.wakelockLeaseEnabled)
                enableLeases.add(LeaseManager.WAKELOCK_LEASE_PROXY);
            else
                disableLeases.add(LeaseManager.WAKELOCK_LEASE_PROXY);
        }
        if (mSettings.gpsLeaseEnabled != newSettings.gpsLeaseEnabled) {
            if (newSettings.gpsLeaseEnabled)
                enableLeases.add(LeaseManager.LOCATION_LEASE_PROXY);
            else
                disableLeases.add(LeaseManager.LOCATION_LEASE_PROXY);
        }
        if (mSettings.sensorLeaseEnabled != newSettings.sensorLeaseEnabled) {
            if (newSettings.sensorLeaseEnabled)
                enableLeases.add(LeaseManager.SENSOR_LEASE_PROXY);
            else
                disableLeases.add(LeaseManager.SENSOR_LEASE_PROXY);
        }
        if (disableLeases.isEmpty() && enableLeases.isEmpty()) {
            // Nothing changed
            Slog.d(TAG, "No change to lease enable settings");
            return;
        }

        for (LeaseProxy leaseProxy:mProxies.values()) {
            if (disableLeases.contains(leaseProxy.mType)) {
                try {
                    Slog.d(TAG, "Stopping " + leaseProxy.mType + " lease due to settings change");
                    if (leaseProxy.mType == LeaseManager.WAKELOCK_LEASE_PROXY) {
                        stopLeaseProxyLocked(LeaseManager.WAKELOCK_LEASE_PROXY);
                        stopLeaseLocked(ResourceType.Wakelock);
                    } else if (leaseProxy.mType == LeaseManager.LOCATION_LEASE_PROXY) {
                        stopLeaseProxyLocked(LeaseManager.LOCATION_LEASE_PROXY);
                        stopLeaseLocked(ResourceType.Location);
                    } else if (leaseProxy.mType == LeaseManager.SENSOR_LEASE_PROXY) {
                        stopLeaseProxyLocked(LeaseManager.SENSOR_LEASE_PROXY);
                        stopLeaseLocked(ResourceType.Sensor);
                    } else {
                        Slog.d(TAG, "Unknow type of lease proxy");
                    }
                    leaseProxy.mProxy.stopLease();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            if (enableLeases.contains(leaseProxy.mType)) {
                try {
                    Slog.d(TAG, "Starting guardian " + leaseProxy + " due to settings change");
                    leaseProxy.mProxy.startLease(newSettings);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * Stop all leases proxy registered with the service.
     * We do not remove the lease since we might need to
     * call them later once the service is re-enabled.
     */
    private void stopAllLeaseProxyLocked() {
        Slog.d(TAG, "Stopping all lease proxy...");
        for (LeaseProxy proxy : mProxies.values()) {
            try {
                proxy.mProxy.stopLease();
            } catch (RemoteException e) {
                Slog.e(TAG, "Fail to stop defense " + proxy);
            }
        }
    }

    private void stopLeaseProxyLocked(int type) {
        Slog.d(TAG, "Stopping all " + type + " lease proxy...");
        for (LeaseProxy proxy : mProxies.values()) {
            try {
                if (proxy.mType == type) {
                    proxy.mProxy.stopLease();
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Fail to stop defense " + proxy);
            }
        }
    }

    /**
     * Stop all leases managed by the service.
     * We do not remove the lease since we might need to
     * call them later once the service is re-enabled.
     */
    private void stopAllLeaseLocked() {
        Slog.d(TAG, "Stopping all lease...");
        for (int i = 0; i < mLeases.size(); i++) {
            Lease lease = mLeases.valueAt(i);
            remove(lease.mLeaseId);
        }
        mLeases.clear();
        mRStatManager.clearAll();
    }

    private void  stopLeaseLocked(ResourceType type) {
        Slog.d(TAG, "Stopping all " + type + " lease...");
        for (int i = 0; i < mLeases.size(); i++) {
            Lease lease = mLeases.valueAt(i);
            if (lease.mType == type) {
                remove(lease.mLeaseId);
            }
        }
    }

    /**
     * Start all leases registered with the service. Make sure the system
     * is ready before calling this function.
     */
    private void startAllLeaseProxyLocked() {
        Slog.d(TAG, "Starting all lease proxy...");
        for (LeaseProxy proxy:mProxies.values()) {
            try {
                Slog.d(TAG, "[" + proxy.mName + "]: START");
                proxy.mProxy.startLease(mSettings);
            } catch (RemoteException e) {
                Slog.e(TAG, "Fail to stop defense " + proxy);
            }
        }

    }


    /**
     * Create a lease proxy wrapper and link to death
     *
     * @param proxy
     * @param type
     * @param name
     * @return
     */
    private LeaseProxy newProxyLocked(ILeaseProxy proxy, int type, String name) {
        IBinder binder = proxy.asBinder();
        LeaseProxy wrapper = new LeaseProxy(proxy, type, name);
        mProxies.put(binder, wrapper);
        mTypedProxies.put(type, wrapper); // we only allow one proxy to be registered for one type
        try {
            binder.linkToDeath(wrapper, 0);
        } catch (RemoteException e) {
            Slog.e(TAG, "linkToDeath failed:", e);
            return null;
        }
        if (mWorkers.get(type) == null) {
            String workerName = wrapper.toString() + "-worker";
            HandlerThread hthread = new HandlerThread(workerName);
            hthread.start();
            LeaseWorkerHandler handler = new LeaseWorkerHandler(workerName, hthread.getLooper());
            mWorkers.put(type, handler);
            Slog.d(TAG, "Worker thread for " + LeaseManager.getProxyTypeString(type) + " is started");
        } else {
            Slog.d(TAG, "Worker thread for " + LeaseManager.getProxyTypeString(type) + " already exists");
        }
        Slog.d(TAG, "Lease proxy " + wrapper + " registered");
        return wrapper;
    }

    /**
     * Register a lease proxy with the lease manager service
     *
     * @param type
     * @param name
     * @param proxy
     * @return
     * @throws RemoteException
     */
    @Override
    public boolean registerProxy(int type, String name, ILeaseProxy proxy) throws RemoteException {
        Slog.d(TAG, "Registering lease proxy " + name);
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                LeaseProxy wrapper = mProxies.get(proxy.asBinder());
                if (wrapper != null) {
                    // proxy already existed, silently ignore
                    Slog.d(TAG, "proxy " + name + " is already registered");
                    return false;
                }
                wrapper = newProxyLocked(proxy, type, name);
                return wrapper != null;
            }
        }
        finally {
            Binder.restoreCallingIdentity(identity);
        }

    }

    /**
     * Unregister a lease proxy
     *
     * @param proxy
     * @return
     * @throws RemoteException
     */
    @Override
    public boolean unregisterProxy(ILeaseProxy proxy) throws RemoteException {
        Slog.d(TAG, "Unregistering lease proxy");
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mLock) {
                LeaseProxy wrapper = mProxies.get(proxy.asBinder());
                if (wrapper != null) {
                    mProxies.remove(wrapper.mKey);
                    mTypedProxies.remove(wrapper.mType);
                    Slog.d(TAG, "Lease proxy " + wrapper + " unregistered");
                } else {
                    Slog.e(TAG, "No internal lease proxy object found");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }

    /**
     * Called when a lease proxy died.
     *
     * @param proxy
     */
    private void handleProxyDeath(LeaseProxy proxy) {
        synchronized (mLock) {
            Slog.d(TAG, "Lease proxy " + proxy + " died ...>.<...");
            mProxies.remove(proxy.mKey);
            mTypedProxies.remove(proxy.mType);
        }
    }

    /**
     * Wrapper class around an ILeaseProxy object to make call back to lease proxy
     */
    private class LeaseProxy implements IBinder.DeathRecipient {
        public final ILeaseProxy mProxy;
        public final int mType;
        public final String mName;
        public final IBinder mKey;

        public LeaseProxy(ILeaseProxy proxy, int type, String name) {
            mProxy = proxy;
            mType = type;
            mName = name;
            mKey = proxy.asBinder();
        }

        @Override
        public void binderDied() {
            handleProxyDeath(this);
        }

        @Override
        public String toString() {
            return "[" + LeaseManager.getProxyTypeString(mType) + "]-" + mName;
        }
    }

    public class LeaseHandler extends Handler {
        private static final int MSG_SYNC_SETTINGS = 1;

        public LeaseHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SYNC_SETTINGS:
                    synchronized (mLock) {
                        final ContentResolver resolver = mContext.getContentResolver();
                        final LeaseSettings settings = LeaseSettingsUtils.readLeaseSettingsLocked(resolver);
                        updateSettingsLocked(settings);
                        Slog.d(TAG, "LeaseSettings synced");
                    }
                    break;
                default:
                    Slog.wtf(TAG, "Unknown lease message");
            }
        }
    }

    private class SettingsObserver extends ContentObserver {
        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (mLock) {
                Slog.d(TAG, "LeaseSettings changed");
                final ContentResolver resolver = mContext.getContentResolver();
                final LeaseSettings settings = LeaseSettingsUtils.readLeaseSettingsLocked(resolver);
                updateSettingsLocked(settings);
            }
        }
    }
}