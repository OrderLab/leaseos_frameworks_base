/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.lease.IUtilityCounter;
import android.lease.LeaseDescriptor;
import android.lease.LeaseEvent;
import android.lease.LeaseManager;
import android.lease.LeaseProxy;
import android.lease.LeaseStatus;
import android.lease.ResourceType;
import android.lease.UtilityCounter;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;

import dalvik.system.CloseGuard;

import libcore.io.Libcore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Sensor manager implementation that communicates with the built-in
 * system sensors.
 *
 * @hide
 */
public class SystemSensorManager extends SensorManager {
    //TODO: disable extra logging before release
    private static boolean DEBUG_DYNAMIC_SENSOR = true;

    private static native void nativeClassInit();

    private static native long nativeCreate(String opPackageName);

    private static native boolean nativeGetSensorAtIndex(long nativeInstance,
            Sensor sensor, int index);

    private static native void nativeGetDynamicSensors(long nativeInstance, List<Sensor> list);

    private static native boolean nativeIsDataInjectionEnabled(long nativeInstance);

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static boolean sNativeClassInited = false;
    @GuardedBy("sLock")
    private static InjectEventQueue sInjectEventQueue = null;

    private final ArrayList<Sensor> mFullSensorsList = new ArrayList<>();
    private List<Sensor> mFullDynamicSensorsList = new ArrayList<>();
    private boolean mDynamicSensorListDirty = true;

    private final HashMap<Integer, Sensor> mHandleToSensor = new HashMap<>();

    // Listener list
    private final HashMap<SensorEventListener, SensorEventQueue> mSensorListeners =
            new HashMap<SensorEventListener, SensorEventQueue>();
    private final HashMap<TriggerEventListener, TriggerEventQueue> mTriggerListeners =
            new HashMap<TriggerEventListener, TriggerEventQueue>();

    // Dynamic Sensor callbacks
    private HashMap<DynamicSensorCallback, Handler>
            mDynamicSensorCallbacks = new HashMap<>();
    private BroadcastReceiver mDynamicSensorBroadcastReceiver;

    // Looper associated with the context in which this instance was created.
    private final Looper mMainLooper;
    private final int mTargetSdkLevel;
    private final Context mContext;
    private final long mNativeInstance;

    /*** LeaseOS changes ***/

    private SensorLeaseProxy mLeaseProxy;
    private boolean fromProxy = false;
    /************************/

    /** {@hide} */
    public SystemSensorManager(Context context, Looper mainLooper) {
        synchronized (sLock) {
            if (!sNativeClassInited) {
                sNativeClassInited = true;
                nativeClassInit();
            }
        }

        mMainLooper = mainLooper;
        mTargetSdkLevel = context.getApplicationInfo().targetSdkVersion;
        mContext = context;
        mNativeInstance = nativeCreate(context.getOpPackageName());

        // initialize the sensor list
        for (int index = 0; ; ++index) {
            Sensor sensor = new Sensor();
            if (!nativeGetSensorAtIndex(mNativeInstance, sensor, index)) break;
            mFullSensorsList.add(sensor);
            mHandleToSensor.put(sensor.getHandle(), sensor);
        }

        /*** LeaseOS changes ***/

        mLeaseProxy = new SensorLeaseProxy(mContext);
        if (!mLeaseProxy.start()) {
            Log.e(TAG, "Failed to start SensorLeaseProxy");
        } else {
            Log.i(TAG, "SensorLeaseProxy started");
        }
        /**********************/
    }


    /** @hide */
    @Override
    protected List<Sensor> getFullSensorList() {
        return mFullSensorsList;
    }

    /** @hide */
    @Override
    protected List<Sensor> getFullDynamicSensorList() {
        // only set up broadcast receiver if the application tries to find dynamic sensors or
        // explicitly register a DynamicSensorCallback
        setupDynamicSensorBroadcastReceiver();
        updateDynamicSensorList();
        return mFullDynamicSensorsList;
    }

    /** @hide */
    @Override
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
            int delayUs, Handler handler, int maxBatchReportLatencyUs, int reservedFlags) {
        //String packageName = mContext.getPackageName();
        //Log.e(TAG, "register a sensor for app " + packageName);
        if (listener == null || sensor == null) {
            Log.e(TAG, "sensor or listener is null");
            return false;
        }
        // Trigger Sensors should use the requestTriggerSensor call.
        if (sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            Log.e(TAG, "Trigger Sensors should use the requestTriggerSensor.");
            return false;
        }
        if (maxBatchReportLatencyUs < 0 || delayUs < 0) {
            Log.e(TAG, "maxBatchReportLatencyUs and delayUs should be non-negative");
            return false;
        }

        // Invariants to preserve:
        // - one Looper per SensorEventListener
        // - one Looper per SensorEventQueue
        // We map SensorEventListener to a SensorEventQueue, which holds the looper
        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                Looper looper = (handler != null) ? handler.getLooper() : mMainLooper;
                final String fullClassName = listener.getClass().getEnclosingClass() != null ?
                        listener.getClass().getEnclosingClass().getName() :
                        listener.getClass().getName();
                queue = new SensorEventQueue(listener, looper, this, fullClassName);
                if (!queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs)) {
                    queue.dispose();
                    return false;
                }
                mSensorListeners.put(listener, queue);

                /*** LeaseOS changes ***/

                ActivityManager.RunningTaskInfo info = null;
                info = getActivity();
                String packageName = info.topActivity.getPackageName();
                String activityName = info.topActivity.getClassName();
                int uid = Libcore.os.getuid();
             //   Log.d(TAG, "Activity " + activityName + "[package " + packageName + ", uid " + uid+ "]requires sensor " + sensor + ", fromproxy " + fromProxy);
               // Log.d(TAG, "The listener is " + listener);
                if (mLeaseProxy != null && mLeaseProxy.mLeaseServiceEnabled) {
                    SensorLease lease;
                    if (mLeaseProxy.exempt(packageName, uid)) {
                       // Log.d(TAG, "Exempt UID " + uid + " " + packageName + " from lease mechanism");
                        return true;
                    }

                    if (!fromProxy) {
                        // Check if any lease has been created for this request.
                        lease = (SensorLease) mLeaseProxy.getLease(listener);

                        // If no lease has been created for this request, try to request a lease
                        // from the lease manager
                        if (lease == null) {
                            lease = (SensorLease) mLeaseProxy.createLease(listener, uid,
                                    ResourceType.Sensor);
                            if (lease != null) {
                                // hold the internal data structure in case we need it later
                                lease.mLeaseValue = listener;
                                lease.mActivityName = activityName;
                                lease.mSensor = sensor;
                                lease.mDelayUs = delayUs;
                                lease.mHandler = handler;
                                lease.mMaxBatchReportLatencyUs = maxBatchReportLatencyUs;
                                lease.mReservedFlags = reservedFlags;
                                mLeaseProxy.noteEvent(lease.mLeaseId, LeaseEvent.SENSOR_ACQUIRE,
                                        activityName);
                                mLeaseProxy.updateSensorListener(delayUs, maxBatchReportLatencyUs,
                                        lease.mLeaseId);
                            }
                        }

                        // If the request has been bound to a lease, check whether the lease manager
                        // allow this request.
                        if (lease != null) {
                            mLeaseProxy.noteEvent(lease.mLeaseId, LeaseEvent.SENSOR_ACQUIRE,
                                    activityName);
                            lease.mLeaseValue = listener;
                            lease.mActivityName = activityName;
                            lease.mDelayUs = delayUs;
                            lease.mHandler = handler;
                            lease.mMaxBatchReportLatencyUs = maxBatchReportLatencyUs;
                            lease.mReservedFlags = reservedFlags;
                            lease.mSensor = sensor;
                            mLeaseProxy.updateSensorListener(lease.mDelayUs,
                                    lease.mMaxBatchReportLatencyUs, lease.mLeaseId);
                            if (!mLeaseProxy.check(lease.mLeaseId)) {
                                unregisterListenerImpl(lease.mLeaseValue, lease.mSensor);
                                Log.d(TAG, uid + " has been disruptive to lease manager service,"
                                        + " freezing lease requests for a while..");
                            }
                        }
                    }
                }
                /*********************/
                return true;
            } else {

                if (mLeaseProxy != null && mLeaseProxy.mLeaseServiceEnabled) {
                    SensorLease lease = null;
                    //Update the information of listener
                    if (!fromProxy) {
                        lease = (SensorLease) mLeaseProxy.getLease(listener);
                        if (lease != null) {
                            Log.d(TAG, "Update the listener information");
                            lease.mLeaseValue = listener;
                            lease.mDelayUs = delayUs;
                            lease.mHandler = handler;
                            lease.mMaxBatchReportLatencyUs = maxBatchReportLatencyUs;
                            lease.mReservedFlags = reservedFlags;
                            lease.mSensor = sensor;
                            mLeaseProxy.updateSensorListener(lease.mDelayUs,
                                    lease.mMaxBatchReportLatencyUs, lease.mLeaseId);
                        }
                    }
                }
                return queue.addSensor(sensor, delayUs, maxBatchReportLatencyUs);
            }
        }
    }


    /** @hide */
    @Override
    protected void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        // Trigger Sensors should use the cancelTriggerSensor call.
        ActivityManager.RunningTaskInfo info = null;
        info = getActivity();

        String packageName = info.topActivity.getPackageName();
        String activityName = info.topActivity.getClassName();

        int uid = Libcore.os.getuid();
        Log.d(TAG, "Activity " + activityName + "[package " + packageName + ", uid " + uid + "]"
                + "unregister sensor " + sensor);
        if (sensor != null && sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            return;
        }

        /***LeaseOS changes***/
        if (mLeaseProxy != null && mLeaseProxy.mLeaseServiceEnabled) {
            SensorLease lease = (SensorLease) mLeaseProxy.getLease(listener);
            if (lease != null) {
                Log.i(TAG, "Release called on the lease " + lease.mLeaseId);
                // TODO: notify ResourceStatManager about the release event
                if (!fromProxy) {
                    // if the release is not called from within the lease proxy
                    // we let the lease manager service know this event
                    // otherwise, we don't note the event because the callback to
                    // the proxy is from lease manager service who should be expecting
                    // this event
                    Log.d(TAG, "Note the release event");
                    lease.mLeaseValue = null;
                    lease.mSensor = null;
                    lease.mActivityName = null;
                    mLeaseProxy.noteEvent(lease.mLeaseId, LeaseEvent.SENSOR_RELEASE);
                }
            }
        }
        /*********************/

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, true);
                }
                if (result && !queue.hasSensors()) {
                    mSensorListeners.remove(listener);
                    queue.dispose();
                }
            }
        }
    }

    /****** LeaseOS change ******/
    protected boolean registerListenerImpl(SensorEventListener listener, Sensor sensor,
            int delayUs, Handler handler, int maxReportLatencyUs, int reservedFlags, UtilityCounter utilityCounter){
        registerListenerImpl(listener, sensor, delayUs, null, maxReportLatencyUs, 0);
        SensorLease lease = (SensorLease) mLeaseProxy.getLease(listener);
        if (lease == null) {
            Log.d(TAG, "The lease is not created for this request");
            return false;
        }
        CounterTransport counterTransport;
        synchronized (mCounter) {
            counterTransport = mCounter.get(utilityCounter);
            if (counterTransport == null) {
                counterTransport = new CounterTransport(utilityCounter);
            }
            mCounter.put(utilityCounter, counterTransport);
        }
        mLeaseProxy.setUtilitCounter(lease.mLeaseId, counterTransport);
        return true;
    }

    // Map from UtilityCounter to their associated ListenerTransport objects
    private HashMap<UtilityCounter,CounterTransport> mCounter =
            new HashMap<UtilityCounter,CounterTransport>();

    private CounterTransport wrapListener(UtilityCounter utilityCounter) {
        if (utilityCounter == null) return null;
        synchronized (mCounter) {
            CounterTransport transport = mCounter.get(utilityCounter);
            if (transport == null) {
                transport = new CounterTransport(utilityCounter);
            }
            mCounter.put(utilityCounter, transport);
            return transport;
        }
    }

    private class CounterTransport extends IUtilityCounter.Stub {
        private UtilityCounter mUtilityCounter;

        CounterTransport(UtilityCounter utilityCounter) {
            mUtilityCounter = utilityCounter;
        }

        @Override
        public int getScore() {
            return 100;
        }
    }

    public ActivityManager.RunningTaskInfo getActivity() {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(
                Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo info;
        if (manager != null) {
            if (manager.getRunningTasks(1).size() == 0) {
                return null;
            } else {
                info = manager.getRunningTasks(1).get(0);
                return info;
            }

        } else {
            Log.d(TAG, "Can not get the service");
            return null;
        }
    }

    private class SensorLease extends LeaseDescriptor<SensorEventListener> implements
            IBinder.DeathRecipient {
        public SensorEventListener mLeaseValue;
        public String mActivityName;
        public Sensor mSensor;
        public int mDelayUs;
        public Handler mHandler;
        public int mMaxBatchReportLatencyUs;
        public int mReservedFlags;

        public SensorLease(SensorEventListener key, long lid, LeaseStatus status) {
            super(key, lid, status);
        }

        @Override
        public void binderDied() {
            // Wake lock requester is dead. We need to clean up.
            // The reason that we didn't use the Listener's death recipient method is that upon the
            // release, power manager service will call unlinkToDeath, which will deregister the
            // recipient. But the lease table lives longer than the release period.
            mLeaseProxy.removeLease(this);
            Log.i(TAG, "Death of sensor listener. Removed lease " + mLeaseId);
        }
    }

    private class SensorLeaseProxy extends LeaseProxy<SensorEventListener> {

        public SensorLeaseProxy(Context context) {
            super(LeaseManager.SENSOR_LEASE_PROXY, "PMS_PROXY", context);
        }

        @Override
        public LeaseDescriptor<SensorEventListener> newLease(SensorEventListener key, long leaseId,
                LeaseStatus status) {
            SensorLease lease = new SensorLease(key, leaseId, status);
            return lease;
        }

        @Override
        public void onExpire(long leaseId) throws RemoteException {
            Log.d(TAG, "LeaseManagerService instruct me to expire lease " + leaseId);
            SensorLease lease = (SensorLease) mLeaseDescriptors.get(leaseId);
            if (lease != null) {
                SensorEventListener receiver;
                synchronized (mLock) {
                    receiver = lease.mLeaseValue;
                    if (receiver != null) {
                        Log.e(TAG, "Release sensor listener object for lease " + leaseId);
                        fromProxy = true;
                        unregisterListenerImpl(receiver, lease.mSensor);
                        fromProxy = false;
                    }
                    lease.mLeaseStatus = LeaseStatus.EXPIRED;
                }
            }
        }

        @Override
        public void onRenew(long leaseId) throws RemoteException {
            Log.d(TAG, "LeaseManagerService instruct me to renew lease " + leaseId);
            SensorLease lease = (SensorLease) mLeaseDescriptors.get(leaseId);
            if (lease != null) {
                SensorEventListener receiver = lease.mLeaseValue;
                synchronized (mLock) {
                    if (receiver == null) {
                        Log.e(TAG, "Cannot renew because no receiver object is found for lease "
                                + leaseId);
                    } else {
                        if (lease.mLeaseStatus != LeaseStatus.EXPIRED) {
                            Log.e(TAG, "Skip renewing because lease " + leaseId
                                    + " has not been expire before");
                        } else {
                            // re-acquire the lock
                            fromProxy = true;
                            registerListenerImpl(lease.mLeaseValue, lease.mSensor, lease.mDelayUs,
                                    lease.mHandler, lease.mMaxBatchReportLatencyUs,
                                    lease.mReservedFlags);
                            fromProxy = false;
                        }
                        // assume that after this point the lease is active
                        lease.mLeaseStatus = LeaseStatus.ACTIVE;
                    }
                }
            }
        }

        /**
         * Delay the sensor update frequency, if the original frequency is setting faster than
         * Normal(0.2 seconds), we change it to Normal speed. If the orginal frequency is
         * setting to faster than 1s seconds, we change it to update every 1 second. Otherwise,
         * we unregister the listener.
         */
        @Override
        public void weakExpire(long leaseId) throws RemoteException {
            Log.d(TAG, "LeaseManagerService instruct me to delay resource frequency for lease "
                    + leaseId);
            SensorLease lease = (SensorLease) mLeaseDescriptors.get(leaseId);
            if (lease != null) {
                SensorEventListener receiver;
                synchronized (mLock) {
                    receiver = lease.mLeaseValue;
                    if (receiver != null) {

                        fromProxy = true;
                        if (lease.mDelayUs < getDelay(SENSOR_DELAY_NORMAL)
                                && lease.mMaxBatchReportLatencyUs < 200000) {
                            Log.e(TAG, "delay sensor listener object for lease " + leaseId
                                    + " the new sample rate is " + getDelay(SENSOR_DELAY_NORMAL));
                            registerListenerImpl(lease.mLeaseValue, lease.mSensor,
                                    getDelay(SENSOR_DELAY_NORMAL), lease.mHandler, 200000,
                                    lease.mReservedFlags);
                        } else if (lease.mDelayUs < 1000000
                                || lease.mMaxBatchReportLatencyUs < 1000000) {
                            Log.e(TAG, "delay sensor listener object for lease " + leaseId
                                    + " the new sample rate is " + 1000000);
                            registerListenerImpl(lease.mLeaseValue, lease.mSensor, 1000000,
                                    lease.mHandler, 1000000, lease.mReservedFlags);
                        } else {
                            Log.e(TAG, "unregister sensor listener object for lease " + leaseId);
                            unregisterListenerImpl(receiver, lease.mSensor);
                        }
                        fromProxy = false;
                    }
                    lease.mLeaseStatus = LeaseStatus.EXPIRED;
                }
            }
        }


        @Override
        public void onReject(int uid) throws RemoteException {

        }

        @Override
        public void onFreeze(int uid, long freezeDuration, int freeCount) throws RemoteException {

        }

        public void updateSensorListener(int delayUs, int maxBatchReportLatencyUs, long leaseId) {
            mLeaseManager.updateSensorListener(delayUs, maxBatchReportLatencyUs, leaseId);
        }

    }

    /*********************/


    /*********************/


    /** @hide */
    @Override
    protected boolean requestTriggerSensorImpl(TriggerEventListener listener, Sensor sensor) {
        if (sensor == null) throw new IllegalArgumentException("sensor cannot be null");

        if (listener == null) throw new IllegalArgumentException("listener cannot be null");

        if (sensor.getReportingMode() != Sensor.REPORTING_MODE_ONE_SHOT) return false;

        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue == null) {
                final String fullClassName = listener.getClass().getEnclosingClass() != null ?
                        listener.getClass().getEnclosingClass().getName() :
                        listener.getClass().getName();
                queue = new TriggerEventQueue(listener, mMainLooper, this, fullClassName);
                if (!queue.addSensor(sensor, 0, 0)) {
                    queue.dispose();
                    return false;
                }
                mTriggerListeners.put(listener, queue);
                return true;
            } else {
                return queue.addSensor(sensor, 0, 0);
            }
        }
    }

    /** @hide */
    @Override
    protected boolean cancelTriggerSensorImpl(TriggerEventListener listener, Sensor sensor,
            boolean disable) {
        if (sensor != null && sensor.getReportingMode() != Sensor.REPORTING_MODE_ONE_SHOT) {
            return false;
        }
        synchronized (mTriggerListeners) {
            TriggerEventQueue queue = mTriggerListeners.get(listener);
            if (queue != null) {
                boolean result;
                if (sensor == null) {
                    result = queue.removeAllSensors();
                } else {
                    result = queue.removeSensor(sensor, disable);
                }
                if (result && !queue.hasSensors()) {
                    mTriggerListeners.remove(listener);
                    queue.dispose();
                }
                return result;
            }
            return false;
        }
    }

    protected boolean flushImpl(SensorEventListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");

        synchronized (mSensorListeners) {
            SensorEventQueue queue = mSensorListeners.get(listener);
            if (queue == null) {
                return false;
            } else {
                return (queue.flush() == 0);
            }
        }
    }

    protected boolean initDataInjectionImpl(boolean enable) {
        synchronized (sLock) {
            if (enable) {
                boolean isDataInjectionModeEnabled = nativeIsDataInjectionEnabled(mNativeInstance);
                // The HAL does not support injection OR SensorService hasn't been set in DI mode.
                if (!isDataInjectionModeEnabled) {
                    Log.e(TAG, "Data Injection mode not enabled");
                    return false;
                }
                // Initialize a client for data_injection.
                if (sInjectEventQueue == null) {
                    sInjectEventQueue = new InjectEventQueue(mMainLooper, this,
                            mContext.getPackageName());
                }
            } else {
                // If data injection is being disabled clean up the native resources.
                if (sInjectEventQueue != null) {
                    sInjectEventQueue.dispose();
                    sInjectEventQueue = null;
                }
            }
            return true;
        }
    }

    protected boolean injectSensorDataImpl(Sensor sensor, float[] values, int accuracy,
            long timestamp) {
        synchronized (sLock) {
            if (sInjectEventQueue == null) {
                Log.e(TAG, "Data injection mode not activated before calling injectSensorData");
                return false;
            }
            int ret = sInjectEventQueue.injectSensorData(sensor.getHandle(), values, accuracy,
                    timestamp);
            // If there are any errors in data injection clean up the native resources.
            if (ret != 0) {
                sInjectEventQueue.dispose();
                sInjectEventQueue = null;
            }
            return ret == 0;
        }
    }

    private void cleanupSensorConnection(Sensor sensor) {
        mHandleToSensor.remove(sensor.getHandle());

        if (sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
            synchronized (mTriggerListeners) {
                for (TriggerEventListener l : mTriggerListeners.keySet()) {
                    if (DEBUG_DYNAMIC_SENSOR) {
                        Log.i(TAG, "removed trigger listener" + l.toString() +
                                " due to sensor disconnection");
                    }
                    cancelTriggerSensorImpl(l, sensor, true);
                }
            }
        } else {
            synchronized (mSensorListeners) {
                for (SensorEventListener l : mSensorListeners.keySet()) {
                    if (DEBUG_DYNAMIC_SENSOR) {
                        Log.i(TAG, "removed event listener" + l.toString() +
                                " due to sensor disconnection");
                    }
                    unregisterListenerImpl(l, sensor);
                }
            }
        }
    }

    private void updateDynamicSensorList() {
        synchronized (mFullDynamicSensorsList) {
            if (mDynamicSensorListDirty) {
                List<Sensor> list = new ArrayList<>();
                nativeGetDynamicSensors(mNativeInstance, list);

                final List<Sensor> updatedList = new ArrayList<>();
                final List<Sensor> addedList = new ArrayList<>();
                final List<Sensor> removedList = new ArrayList<>();

                boolean changed = diffSortedSensorList(
                        mFullDynamicSensorsList, list, updatedList, addedList, removedList);

                if (changed) {
                    if (DEBUG_DYNAMIC_SENSOR) {
                        Log.i(TAG, "DYNS dynamic sensor list cached should be updated");
                    }
                    mFullDynamicSensorsList = updatedList;

                    for (Sensor s : addedList) {
                        mHandleToSensor.put(s.getHandle(), s);
                    }

                    Handler mainHandler = new Handler(mContext.getMainLooper());

                    for (Map.Entry<DynamicSensorCallback, Handler> entry :
                            mDynamicSensorCallbacks.entrySet()) {
                        final DynamicSensorCallback callback = entry.getKey();
                        Handler handler =
                                entry.getValue() == null ? mainHandler : entry.getValue();

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                for (Sensor s : addedList) {
                                    callback.onDynamicSensorConnected(s);
                                }
                                for (Sensor s : removedList) {
                                    callback.onDynamicSensorDisconnected(s);
                                }
                            }
                        });
                    }

                    for (Sensor s : removedList) {
                        cleanupSensorConnection(s);
                    }
                }

                mDynamicSensorListDirty = false;
            }
        }
    }

    private void setupDynamicSensorBroadcastReceiver() {
        if (mDynamicSensorBroadcastReceiver == null) {
            mDynamicSensorBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == Intent.ACTION_DYNAMIC_SENSOR_CHANGED) {
                        if (DEBUG_DYNAMIC_SENSOR) {
                            Log.i(TAG, "DYNS received DYNAMIC_SENSOR_CHANED broadcast");
                        }
                        // Dynamic sensors probably changed
                        mDynamicSensorListDirty = true;
                        updateDynamicSensorList();
                    }
                }
            };

            IntentFilter filter = new IntentFilter("dynamic_sensor_change");
            filter.addAction(Intent.ACTION_DYNAMIC_SENSOR_CHANGED);
            mContext.registerReceiver(mDynamicSensorBroadcastReceiver, filter);
        }
    }

    private void teardownDynamicSensorBroadcastReceiver() {
        mDynamicSensorCallbacks.clear();
        mContext.unregisterReceiver(mDynamicSensorBroadcastReceiver);
        mDynamicSensorBroadcastReceiver = null;
    }

    /** @hide */
    protected void registerDynamicSensorCallbackImpl(
            DynamicSensorCallback callback, Handler handler) {
        if (DEBUG_DYNAMIC_SENSOR) {
            Log.i(TAG, "DYNS Register dynamic sensor callback");
        }

        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        if (mDynamicSensorCallbacks.containsKey(callback)) {
            // has been already registered, ignore
            return;
        }

        setupDynamicSensorBroadcastReceiver();
        mDynamicSensorCallbacks.put(callback, handler);
    }

    /** @hide */
    protected void unregisterDynamicSensorCallbackImpl(
            DynamicSensorCallback callback) {
        if (DEBUG_DYNAMIC_SENSOR) {
            Log.i(TAG, "Removing dynamic sensor listerner");
        }
        mDynamicSensorCallbacks.remove(callback);
    }

    /*
     * Find the difference of two List<Sensor> assuming List are sorted by handle of sensor,
     * assuming the input list is already sorted by handle. Inputs are ol and nl; outputs are
     * updated, added and removed. Any of the output lists can be null in case the result is not
     * interested.
     */
    private static boolean diffSortedSensorList(
            List<Sensor> oldList, List<Sensor> newList, List<Sensor> updated,
            List<Sensor> added, List<Sensor> removed) {

        boolean changed = false;

        int i = 0, j = 0;
        while (true) {
            if (j < oldList.size() && (i >= newList.size() ||
                    newList.get(i).getHandle() > oldList.get(j).getHandle())) {
                changed = true;
                if (removed != null) {
                    removed.add(oldList.get(j));
                }
                ++j;
            } else if (i < newList.size() && (j >= oldList.size() ||
                    newList.get(i).getHandle() < oldList.get(j).getHandle())) {
                changed = true;
                if (added != null) {
                    added.add(newList.get(i));
                }
                if (updated != null) {
                    updated.add(newList.get(i));
                }
                ++i;
            } else if (i < newList.size() && j < oldList.size() &&
                    newList.get(i).getHandle() == oldList.get(j).getHandle()) {
                if (updated != null) {
                    updated.add(oldList.get(j));
                }
                ++i;
                ++j;
            } else {
                break;
            }
        }
        return changed;
    }

    /*
     * BaseEventQueue is the communication channel with the sensor service,
     * SensorEventQueue, TriggerEventQueue are subclases and there is one-to-one mapping between
     * the queues and the listeners. InjectEventQueue is also a sub-class which is a special case
     * where data is being injected into the sensor HAL through the sensor service. It is not
     * associated with any listener and there is one InjectEventQueue associated with a
     * SensorManager instance.
     */
    private static abstract class BaseEventQueue {
        private static native long nativeInitBaseEventQueue(long nativeManager,
                WeakReference<BaseEventQueue> eventQWeak, MessageQueue msgQ,
                String packageName, int mode, String opPackageName);

        private static native int nativeEnableSensor(long eventQ, int handle, int rateUs,
                int maxBatchReportLatencyUs);

        private static native int nativeDisableSensor(long eventQ, int handle);

        private static native void nativeDestroySensorEventQueue(long eventQ);

        private static native int nativeFlushSensor(long eventQ);

        private static native int nativeInjectSensorData(long eventQ, int handle,
                float[] values, int accuracy, long timestamp);

        private long nSensorEventQueue;
        private final SparseBooleanArray mActiveSensors = new SparseBooleanArray();
        protected final SparseIntArray mSensorAccuracies = new SparseIntArray();
        private final CloseGuard mCloseGuard = CloseGuard.get();
        protected final SystemSensorManager mManager;

        protected static final int OPERATING_MODE_NORMAL = 0;
        protected static final int OPERATING_MODE_DATA_INJECTION = 1;

        BaseEventQueue(Looper looper, SystemSensorManager manager, int mode, String packageName) {
            if (packageName == null) packageName = "";
            nSensorEventQueue = nativeInitBaseEventQueue(manager.mNativeInstance,
                    new WeakReference<>(this), looper.getQueue(),
                    packageName, mode, manager.mContext.getOpPackageName());
            mCloseGuard.open("dispose");
            mManager = manager;
        }

        public void dispose() {
            dispose(false);
        }

        public boolean addSensor(
                Sensor sensor, int delayUs, int maxBatchReportLatencyUs) {
            // Check if already present.
            int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) return false;

            // Get ready to receive events before calling enable.
            mActiveSensors.put(handle, true);
            addSensorEvent(sensor);
            if (enableSensor(sensor, delayUs, maxBatchReportLatencyUs) != 0) {
                // Try continuous mode if batching fails.
                if (maxBatchReportLatencyUs == 0 ||
                        maxBatchReportLatencyUs > 0 && enableSensor(sensor, delayUs, 0) != 0) {
                    removeSensor(sensor, false);
                    return false;
                }
            }
            return true;
        }

        public boolean removeAllSensors() {
            for (int i = 0; i < mActiveSensors.size(); i++) {
                if (mActiveSensors.valueAt(i) == true) {
                    int handle = mActiveSensors.keyAt(i);
                    Sensor sensor = mManager.mHandleToSensor.get(handle);
                    if (sensor != null) {
                        disableSensor(sensor);
                        mActiveSensors.put(handle, false);
                        removeSensorEvent(sensor);
                    } else {
                        // sensor just disconnected -- just ignore.
                    }
                }
            }
            return true;
        }

        public boolean removeSensor(Sensor sensor, boolean disable) {
            final int handle = sensor.getHandle();
            if (mActiveSensors.get(handle)) {
                if (disable) disableSensor(sensor);
                mActiveSensors.put(sensor.getHandle(), false);
                removeSensorEvent(sensor);
                return true;
            }
            return false;
        }

        public int flush() {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            return nativeFlushSensor(nSensorEventQueue);
        }

        public boolean hasSensors() {
            // no more sensors are set
            return mActiveSensors.indexOfValue(true) >= 0;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                dispose(true);
            } finally {
                super.finalize();
            }
        }

        private void dispose(boolean finalized) {
            if (mCloseGuard != null) {
                if (finalized) {
                    mCloseGuard.warnIfOpen();
                }
                mCloseGuard.close();
            }
            if (nSensorEventQueue != 0) {
                nativeDestroySensorEventQueue(nSensorEventQueue);
                nSensorEventQueue = 0;
            }
        }

        private int enableSensor(
                Sensor sensor, int rateUs, int maxBatchReportLatencyUs) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeEnableSensor(nSensorEventQueue, sensor.getHandle(), rateUs,
                    maxBatchReportLatencyUs);
        }

        protected int injectSensorDataBase(int handle, float[] values, int accuracy,
                long timestamp) {
            return nativeInjectSensorData(nSensorEventQueue, handle, values, accuracy, timestamp);
        }

        private int disableSensor(Sensor sensor) {
            if (nSensorEventQueue == 0) throw new NullPointerException();
            if (sensor == null) throw new NullPointerException();
            return nativeDisableSensor(nSensorEventQueue, sensor.getHandle());
        }

        protected abstract void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp);

        protected abstract void dispatchFlushCompleteEvent(int handle);

        protected void dispatchAdditionalInfoEvent(
                int handle, int type, int serial, float[] floatValues, int[] intValues) {
            // default implementation is do nothing
        }

        protected abstract void addSensorEvent(Sensor sensor);

        protected abstract void removeSensorEvent(Sensor sensor);
    }

    static final class SensorEventQueue extends BaseEventQueue {
        private final SensorEventListener mListener;
        private final SparseArray<SensorEvent> mSensorsEvents = new SparseArray<SensorEvent>();

        public SensorEventQueue(SensorEventListener listener, Looper looper,
                SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_NORMAL, packageName);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            SensorEvent t = new SensorEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mSensorsEvents) {
                mSensorsEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mSensorsEvents) {
                mSensorsEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int inAccuracy,
                long timestamp) {
            final Sensor sensor = mManager.mHandleToSensor.get(handle);
            if (sensor == null) {
                // sensor disconnected
                return;
            }

            SensorEvent t = null;
            synchronized (mSensorsEvents) {
                t = mSensorsEvents.get(handle);
            }

            if (t == null) {
                // This may happen if the client has unregistered and there are pending events in
                // the queue waiting to be delivered. Ignore.
                return;
            }
            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.accuracy = inAccuracy;
            t.sensor = sensor;

            // call onAccuracyChanged() only if the value changes
            final int accuracy = mSensorAccuracies.get(handle);
            if ((t.accuracy >= 0) && (accuracy != t.accuracy)) {
                mSensorAccuracies.put(handle, t.accuracy);
                mListener.onAccuracyChanged(t.sensor, t.accuracy);
            }
            mListener.onSensorChanged(t);
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchFlushCompleteEvent(int handle) {
            if (mListener instanceof SensorEventListener2) {
                final Sensor sensor = mManager.mHandleToSensor.get(handle);
                if (sensor == null) {
                    // sensor disconnected
                    return;
                }
                ((SensorEventListener2) mListener).onFlushCompleted(sensor);
            }
            return;
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchAdditionalInfoEvent(
                int handle, int type, int serial, float[] floatValues, int[] intValues) {
            if (mListener instanceof SensorEventCallback) {
                final Sensor sensor = mManager.mHandleToSensor.get(handle);
                if (sensor == null) {
                    // sensor disconnected
                    return;
                }
                SensorAdditionalInfo info =
                        new SensorAdditionalInfo(sensor, type, serial, intValues, floatValues);
                ((SensorEventCallback) mListener).onSensorAdditionalInfo(info);
            }
        }
    }

    static final class TriggerEventQueue extends BaseEventQueue {
        private final TriggerEventListener mListener;
        private final SparseArray<TriggerEvent> mTriggerEvents = new SparseArray<TriggerEvent>();

        public TriggerEventQueue(TriggerEventListener listener, Looper looper,
                SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_NORMAL, packageName);
            mListener = listener;
        }

        @Override
        public void addSensorEvent(Sensor sensor) {
            TriggerEvent t = new TriggerEvent(Sensor.getMaxLengthValuesArray(sensor,
                    mManager.mTargetSdkLevel));
            synchronized (mTriggerEvents) {
                mTriggerEvents.put(sensor.getHandle(), t);
            }
        }

        @Override
        public void removeSensorEvent(Sensor sensor) {
            synchronized (mTriggerEvents) {
                mTriggerEvents.delete(sensor.getHandle());
            }
        }

        // Called from native code.
        @SuppressWarnings("unused")
        @Override
        protected void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp) {
            final Sensor sensor = mManager.mHandleToSensor.get(handle);
            if (sensor == null) {
                // sensor disconnected
                return;
            }
            TriggerEvent t = null;
            synchronized (mTriggerEvents) {
                t = mTriggerEvents.get(handle);
            }
            if (t == null) {
                Log.e(TAG, "Error: Trigger Event is null for Sensor: " + sensor);
                return;
            }

            // Copy from the values array.
            System.arraycopy(values, 0, t.values, 0, t.values.length);
            t.timestamp = timestamp;
            t.sensor = sensor;

            // A trigger sensor is auto disabled. So just clean up and don't call native
            // disable.
            mManager.cancelTriggerSensorImpl(mListener, sensor, false);

            mListener.onTrigger(t);
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {
        }
    }

    final class InjectEventQueue extends BaseEventQueue {
        public InjectEventQueue(Looper looper, SystemSensorManager manager, String packageName) {
            super(looper, manager, OPERATING_MODE_DATA_INJECTION, packageName);
        }

        int injectSensorData(int handle, float[] values, int accuracy, long timestamp) {
            return injectSensorDataBase(handle, values, accuracy, timestamp);
        }

        @SuppressWarnings("unused")
        protected void dispatchSensorEvent(int handle, float[] values, int accuracy,
                long timestamp) {
        }

        @SuppressWarnings("unused")
        protected void dispatchFlushCompleteEvent(int handle) {

        }

        @SuppressWarnings("unused")
        protected void addSensorEvent(Sensor sensor) {

        }

        @SuppressWarnings("unused")
        protected void removeSensorEvent(Sensor sensor) {

        }
    }
}
