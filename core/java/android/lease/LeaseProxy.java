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
    protected Context mContext;
    protected boolean mReady;
    protected boolean mSystemAppQueried;
    protected final HashSet<Integer> mSystemAppUids;
    protected final LeaseWhiteList mWhiteList;

    protected LeaseManager mLeaseManager;
    protected Hashtable<S, T> mLeaseTable;

    public LeaseProxy(Context context) {
        mContext = context;
        mSystemAppQueried = false;
        mReady = false;
        mSystemAppUids = new HashSet<>();
        mWhiteList = new LeaseWhiteList(LeaseWhiteList.WHITELIST_DEFAULT);
        mLeaseTable = new Hashtable<>();
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
        mReady = (mLeaseManager != null);
        return true;
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

    public T getOrCreateLease(S key, int uid) {
        if (mLeaseManager != null) {
            T lease = mLeaseTable.get(key);
            if (lease != null)
                return lease;
            long leaseId = mLeaseManager.create(ResourceType.Wakelock, uid);
            if (leaseId < LeaseManager.LEASE_ID_START) {
                Slog.i(TAG,"Skip invalid lease");
                return null;
            }
            lease = newLease(key, leaseId);
            mLeaseTable.put(key, lease);
            Slog.i(TAG, "Created new lease " + leaseId + ". The lease table size is "
                    + mLeaseTable.size());
            return lease;
        } else {
            Slog.i(TAG, "LeaseManager is not ready");
            return null;
        }
    }

    public abstract T newLease(S key, long leaseId);

    public void removeLease(LeaseDescriptor<S> descriptor) {
        if (mLeaseManager != null) {
            mLeaseManager.remove(descriptor.mLeaseId);
            mLeaseTable.remove(descriptor.mLeaseKey);
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
                }
            }
        }
    }
}