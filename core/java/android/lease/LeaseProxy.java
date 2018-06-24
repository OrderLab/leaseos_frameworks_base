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

import libcore.io.Libcore;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * A proxy to be used in each important internal system service to (1). connect to LeaseManager
 * service to make lease related APIs such as create a lease. (2). allow LeaseManager to call back
 * to perform certain action such as release the corresponding resources.
 *
 */
public abstract class LeaseProxy<S> extends ILeaseProxy.Stub {
    private static final String TAG = "LeaseProxy";

    protected final Object mLock = new Object();
    protected int mType;
    protected String mName;
    protected Context mContext;
    protected boolean mReady;
    protected boolean mSystemAppQueried;
    public boolean mLeaseServiceEnabled;
    protected LeaseSettings mSettings;

    protected final ArrayList<Integer> mSystemAppUids;
    protected final LeaseWhiteList mWhiteList;
    protected final Hashtable<S,  LeaseDescriptor<S>> mLeaseTable;
    protected final LongSparseArray<LeaseDescriptor<S>> mLeaseDescriptors;
    protected final RequestFreezer<Integer> mUidFreezer;
    private int index = 0;

    public LeaseManager mLeaseManager;

    public LeaseProxy(int type, String name, Context context) {
        mType = type;
        mName = name;
        mContext = context;
        mSystemAppQueried = false;
        mReady = false;
        mSystemAppUids = new ArrayList<>();
        mSettings = LeaseSettings.getDefaultSettings();
        mWhiteList = new LeaseWhiteList(mSettings.whiteList);
        mLeaseTable = new Hashtable<>();
        mLeaseDescriptors =  new LongSparseArray<>();
        mUidFreezer = new RequestFreezer<>();
        mLeaseServiceEnabled =  LeaseSettings.getDefaultSettings().serviceEnabled;
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
        int uid = Libcore.os.getuid();
        if (mLeaseManager != null) {
            mLeaseManager.registerProxy(mType, mName, this, uid);
            mReady = true;
           // Slog.i(TAG, "Lease proxy " + this + " started");
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
           // Slog.i(TAG, "Lease proxy " + this + " stopped");
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
    public LeaseDescriptor<S> getLease(S key) {
        Enumeration e = mLeaseTable.keys();
        return mLeaseTable.get(key);
    }

    public LeaseDescriptor<S> createLease(S key, int uid, ResourceType resourceType) {
        if (mLeaseManager != null) {
            long leaseId = mLeaseManager.create(resourceType, uid);
            if (leaseId < LeaseManager.LEASE_ID_START) {
                Slog.i(TAG,"Skip invalid lease");
                return null;
            }
            LeaseDescriptor<S> lease = newLease(key, leaseId, LeaseStatus.ACTIVE); // a new lease
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

    public boolean checkorRenew(long leaseId) {
        if (mLeaseManager != null) {
            return mLeaseManager.check(leaseId);
        }
        return true;
    }

    public boolean renewLease(LeaseDescriptor<S> lease) {
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

    public abstract LeaseDescriptor<S> newLease(S key, long leaseId, LeaseStatus status);

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

    public void noteEvent(long leaseId, LeaseEvent event, String activityName) {
        if (mLeaseManager != null) {
            mLeaseManager.noteEvent(leaseId, event, activityName);
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
                    final List<ApplicationInfo> appInfos = pm.getInstalledApplications(
                            PackageManager.GET_META_DATA);
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
                    Slog.d(TAG, "Updated system app list with " + mSystemAppUids.size() + " apps " + mSystemAppUids);
                } else {
                    Slog.e(TAG, "Package manager is not ready yet");
                }
            }
        }
    }


    /**
     * Inform lease proxy that the lease settings has changed
     *
     * @param settings
     */
    public void settingsChanged(LeaseSettings settings) {
        // Update the internal settings. There are two ways clients of defense machines
        // may receive updates of defense settings:
        //
        // 1). they use the IDefenseSettingsProvider
        // that DefenseGuardian provides, which always have the latest copy of defense settings.
        // Clients that passively use the settings therefore does not need to listen for updates.
        //
        // 2). They register to listen for changes to the defense machine.

        synchronized (mLock) {
            Slog.d(TAG, "Received LeaseSettings updates...");
            updateSettingsLocked(settings);
        }
    }

    private void updateSettingsLocked(LeaseSettings settings) {
        mSettings = settings;
        mWhiteList.reset(mSettings.whiteList); // update white list
    }


    /**
     * Inform guardian it can start defense
     */
    public void startLease(LeaseSettings settings) {
        Slog.d(TAG, "[" + mName + "]: Lease");
        synchronized (mLock) {
            if (!mSystemAppQueried) {
                Slog.d(TAG, "[" + mName + "]: UPDATE SYSTEM APPS");
                updateSystemAppsLocked();
            }
            if (mLeaseManager == null && mContext != null) {
                mLeaseManager = (LeaseManager) mContext.getSystemService(Context.LEASE_SERVICE);
            }
            updateSettingsLocked(settings); // update settings
            mLeaseServiceEnabled = true;
        }
    }

    /**
     * Inform guardian it should stop defense
     */
    public void stopLease() {
        synchronized (mLock) {
            Slog.d(TAG, "Stop lease proxy " + this);
            mLeaseServiceEnabled = false;
            mLeaseTable.clear();
            mLeaseDescriptors.clear();
            mUidFreezer.clear();
        }
    }

    @Override
    public String toString() {
        return "[" + LeaseManager.getProxyTypeString(mType) + "]-" + mName;
    }

}