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
import android.lease.ILeaseProxy;
import android.lease.LeaseEvent;
import android.lease.LeaseManager;
import android.lease.ResourceType;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.LongSparseArray;
import android.util.Slog;
import android.util.SparseArray;

import java.util.HashMap;

/**
 * The central lease manager service
 */
public class LeaseManagerService extends ILeaseManager.Stub {

    //Operation failed
    public static final int FAILED = -1;
    private static final String TAG = "LeaseManagerService";
    private final Object mLock = new Object();

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
                return LeaseManager.INVALID_LEASE;
            }
            Lease lease = new Lease(mLastLeaseId, uid, rtype, mRStatManager, null,
                    null, mContext);
            Slog.i(TAG, "newLease: begin to create a lease " + mLastLeaseId + " for process: " + uid);
            mLeases.put(mLastLeaseId, lease);
            Slog.d(TAG, "Start to Create a StatHistory for the " + mLastLeaseId);
            StatHistory statHistory = new StatHistory();
            Slog.d(TAG, "Create a StatHistory for the " + mLastLeaseId);
            LeaseProxy proxy = null;
            LeaseWorkerHandler handler = null;
            switch (rtype) {
                case Wakelock:
                    WakelockStat wStat = new WakelockStat(lease.mBeginTime, uid, mContext);
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
                Slog.e(TAG, "No proxy found for lease " + lease.mLeaseId);
            }
            if (handler != null) {
                lease.setHandler(handler);
            } else {
                Slog.e(TAG, "No worker thread found for lease " + lease.mLeaseId);
            }
            lease.create(); // at last when proxy and worker thread is ready, create this lease
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
            return lease != null && lease.isActive();
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
            return lease != null && lease.renew(true);
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
            if (lease == null || !lease.isActive()) {
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
                statHistory.noteAcquire();
                break;
            case WAKELOCK_RELEASE:
                statHistory.noteRelease();
                break;
            default:
                Slog.e(TAG, "Unhandled event " + event + " reported for lease " + leaseId);
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
}