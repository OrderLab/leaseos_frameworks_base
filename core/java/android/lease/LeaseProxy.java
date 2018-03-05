/*
 *  @author Ryan Huang <huang@cs.jhu.edu>
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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.LongSparseArray;
import android.util.Slog;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

/**
 * A proxy to be used in each important internal system service to (1). connect to LeaseManager
 * service to make lease related APIs such as create a lease. (2). allow LeaseManager to call back
 * to perform certain action such as release the corresponding resources.
 *
 */
public abstract class LeaseProxy<S, T extends LeaseDescriptor<S>> extends ILeaseProxy.Stub {
    private static final String TAG = "LeaseProxy";

    protected final Object mLock = new Object();
    protected int mType;
    protected String mName;
    protected Context mContext;
    protected boolean mReady;
    protected boolean mSystemAppQueried;

    protected final HashSet<Integer> mSystemAppUids;
    protected final LeaseWhiteList mWhiteList;
    protected final Hashtable<S, T> mLeaseTable;
    protected final LongSparseArray<T> mLeaseDescriptors;
    protected final RequestFreezer<Integer> mUidFreezer;


    protected LeaseManager mLeaseManager;

    public LeaseProxy(int type, String name, Context context) {
        mType = type;
        mName = name;
        mContext = context;
        mSystemAppQueried = false;
        mReady = false;
        mSystemAppUids = new HashSet<>();
        mWhiteList = new LeaseWhiteList(LeaseWhiteList.WHITELIST_DEFAULT);
        mLeaseTable = new Hashtable<>();
        mLeaseDescriptors =  new LongSparseArray<>();
        mUidFreezer = new RequestFreezer<>();
    }

    public LeaseManager getManager() {
        return mLeaseManager;
    }

    /**
     * Start the proxy: mainly perform some initialization task.
     *
     * @return
     */
    public boolean start() {
        mLeaseManager = (LeaseManager) mContext.getSystemService(Context.LEASE_SERVICE);
        updateSystemAppsLocked();
        if (mLeaseManager != null) {
            mLeaseManager.registerProxy(mType, mName, this);
            mReady = true;
            Slog.i(TAG, "Lease proxy " + this + " started");
        } else {
            Slog.e(TAG, "Fail to start lease proxy " + this);
        }
        return mReady;
    }

    /**
     * Stop the proxy: unregister with lease manager service
     * @return
     */
    public boolean stop() {
        if (mReady) {
            mLeaseManager.unregisterProxy(this);
            mReady = false;
            Slog.i(TAG, "Lease proxy " + this + " stopped");
            return true;
        } else {
            Slog.e(TAG, "Fail to stop lease proxy " + this);
            return false;
        }
    }

    /**
     * Test if a uid should be frozen from talking to lease manager service for a while.
     *
     * @param uid
     * @return
     */
    public boolean shouldFreezeUid(int uid) {
        return mUidFreezer.freeze(uid);
    }

    /**
     * Add a UID to freezer for a short duration.
     *
     * @param uid
     * @param freezeDuration
     * @param freeCount
     * @return
     */
    public boolean freezeUid(int uid, long freezeDuration, int freeCount) {
        return mUidFreezer.addToFreezer(uid, freezeDuration, freeCount);
    }

    /**
     * Given a key to a lease, return the lease descriptor
     *
     * @param key
     * @return
     */
    public T getLease(S key) {
        return mLeaseTable.get(key);
    }

    public T createLease(S key, int uid) {
        if (mLeaseManager != null) {
            long leaseId = mLeaseManager.create(ResourceType.Wakelock, uid);
            if (leaseId < LeaseManager.LEASE_ID_START) {
                Slog.i(TAG,"Skip invalid lease");
                return null;
            }
            T lease = newLease(key, leaseId, LeaseStatus.ACTIVE); // a new lease
            mLeaseTable.put(key, lease);
            mLeaseDescriptors.put(leaseId, lease);
            Slog.i(TAG, "Created new lease " + leaseId + ". The lease table size is "
                    + mLeaseTable.size());
            return lease;
        } else {
            Slog.i(TAG, "LeaseManager is not ready");
            return null;
        }
    }

    public boolean renewLease(T lease) {
        if (!mLeaseManager.renew(lease.mLeaseId)) {
            // Possible failure reason: there has been too many lease requests from this UID
            // the lease manager decides to reject the requests for a while.
            Slog.d(TAG, "Failed to renew lease " + lease.mLeaseId + " from lease manager");
            return false;
        }
        Slog.d(TAG, "Successfully renewed lease " + lease.mLeaseId + " from lease manager");
        lease.mLeaseStatus = LeaseStatus.ACTIVE;
        return true;
    }

    public abstract T newLease(S key, long leaseId, LeaseStatus status);

    public void removeLease(LeaseDescriptor<S> descriptor) {
        if (mLeaseManager != null) {
            mLeaseManager.remove(descriptor.mLeaseId);
            mLeaseTable.remove(descriptor.mLeaseKey);
            mLeaseDescriptors.remove(descriptor.mLeaseId);
        }
    }

    public void noteEvent(long leaseId, LeaseEvent event) {
        if (mLeaseManager != null) {
            mLeaseManager.noteEvent(leaseId, event);
        }
    }

    /**
     * Should a given package name or UID be exempted. The package name can be empty
     * if a UID is specified. Package name is useful for white list.
     *
     * @param pkg
     * @param uid
     * @return
     */
    public boolean exempt(String pkg, int uid) {
        // ignore any non-app process
        if (uid < android.os.Process.FIRST_APPLICATION_UID ||
                uid > android.os.Process.LAST_APPLICATION_UID)
            return true;
        // also ignore any system app or a white listed app
        return mSystemAppUids.contains(uid) || mWhiteList.isVIP(pkg);
    }

    /**
     * Update the list of system apps.
     */
    protected void updateSystemAppsLocked() {
        if (!mSystemAppQueried) {
            Slog.d(TAG, "Trying to update system app list");
            if (mContext != null) {
                final PackageManager pm = mContext.getPackageManager();
                if (pm != null) {
                    final List<ApplicationInfo> appInfos = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                    for (ApplicationInfo appInfo : appInfos) {
                        // First it must be an APP
                        if (appInfo.uid >= android.os.Process.FIRST_APPLICATION_UID &&
                                appInfo.uid <= android.os.Process.LAST_APPLICATION_UID) {
                            // Only add these system uids in app UID range
                            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                                mSystemAppUids.add(appInfo.uid);
                            }
                        }
                    }
                    mSystemAppQueried = true;
                    Slog.d(TAG, "Updated system app list with " + mSystemAppUids.size() + " apps");
                } else {
                    Slog.e(TAG, "Package manager is not ready yet");
                }
            }
        }
    }

    @Override
    public String toString() {
        return "[" + LeaseManager.getProxyTypeString(mType) + "]-" + mName;
    }
}