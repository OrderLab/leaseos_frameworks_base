/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.location;

import com.android.internal.location.ProviderProperties;

import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.lease.IUtilityCounter;
import android.lease.UtilityCounter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

/**
 * This class provides access to the system location services.  These
 * services allow applications to obtain periodic updates of the
 * device's geographical location, or to fire an application-specified
 * {@link Intent} when the device enters the proximity of a given
 * geographical location.
 *
 * <p>You do not
 * instantiate this class directly; instead, retrieve it through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.LOCATION_SERVICE)}.
 *
 * <p class="note">Unless noted, all Location API methods require
 * the {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} or
 * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} permissions.
 * If your application only has the coarse permission then it will not have
 * access to the GPS or passive location providers. Other providers will still
 * return location results, but the update rate will be throttled and the exact
 * location will be obfuscated to a coarse level of accuracy.
 */
public class LocationManager {
    private static final String TAG = "LocationManager";

    private final Context mContext;
    private final ILocationManager mService;
    private final GnssMeasurementCallbackTransport mGnssMeasurementCallbackTransport;
    private final GnssNavigationMessageCallbackTransport mGnssNavigationMessageCallbackTransport;
    private final HashMap<GpsStatus.Listener, GnssStatusListenerTransport> mGpsStatusListeners =
            new HashMap<>();
    private final HashMap<GpsStatus.NmeaListener, GnssStatusListenerTransport> mGpsNmeaListeners =
            new HashMap<>();
    private final HashMap<GnssStatusCallback, GnssStatusListenerTransport>
            mOldGnssStatusListeners = new HashMap<>();
    private final HashMap<GnssStatus.Callback, GnssStatusListenerTransport> mGnssStatusListeners =
            new HashMap<>();
    private final HashMap<GnssNmeaListener, GnssStatusListenerTransport> mOldGnssNmeaListeners =
            new HashMap<>();
    private final HashMap<OnNmeaMessageListener, GnssStatusListenerTransport> mGnssNmeaListeners =
            new HashMap<>();
    private final HashMap<GnssNavigationMessageEvent.Callback, GnssNavigationMessage.Callback>
            mNavigationMessageBridge = new HashMap<>();
    private GnssStatus mGnssStatus;
    private int mTimeToFirstFix;

    /**
     * Name of the network location provider.
     * <p>This provider determines location based on
     * availability of cell tower and WiFi access points. Results are retrieved
     * by means of a network lookup.
     */
    public static final String NETWORK_PROVIDER = "network";

    /**
     * Name of the GPS location provider.
     *
     * <p>This provider determines location using
     * satellites. Depending on conditions, this provider may take a while to return
     * a location fix. Requires the permission
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION}.
     *
     * <p> The extras Bundle for the GPS location provider can contain the
     * following key/value pairs:
     * <ul>
     * <li> satellites - the number of satellites used to derive the fix
     * </ul>
     */
    public static final String GPS_PROVIDER = "gps";

    /**
     * A special location provider for receiving locations without actually initiating
     * a location fix.
     *
     * <p>This provider can be used to passively receive location updates
     * when other applications or services request them without actually requesting
     * the locations yourself.  This provider will return locations generated by other
     * providers.  You can query the {@link Location#getProvider()} method to determine
     * the origin of the location update. Requires the permission
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION}, although if the GPS is
     * not enabled this provider might only return coarse fixes.
     */
    public static final String PASSIVE_PROVIDER = "passive";

    /**
     * Name of the Fused location provider.
     *
     * <p>This provider combines inputs for all possible location sources
     * to provide the best possible Location fix. It is implicitly
     * used for all API's that involve the {@link LocationRequest}
     * object.
     *
     * @hide
     */
    public static final String FUSED_PROVIDER = "fused";

    /**
     * Key used for the Bundle extra holding a boolean indicating whether
     * a proximity alert is entering (true) or exiting (false)..
     */
    public static final String KEY_PROXIMITY_ENTERING = "entering";

    /**
     * Key used for a Bundle extra holding an Integer status value
     * when a status change is broadcast using a PendingIntent.
     */
    public static final String KEY_STATUS_CHANGED = "status";

    /**
     * Key used for a Bundle extra holding an Boolean status value
     * when a provider enabled/disabled event is broadcast using a PendingIntent.
     */
    public static final String KEY_PROVIDER_ENABLED = "providerEnabled";

    /**
     * Key used for a Bundle extra holding a Location value
     * when a location change is broadcast using a PendingIntent.
     */
    public static final String KEY_LOCATION_CHANGED = "location";

    /**
     * Broadcast intent action indicating that the GPS has either been
     * enabled or disabled. An intent extra provides this state as a boolean,
     * where {@code true} means enabled.
     * @see #EXTRA_GPS_ENABLED
     *
     * @hide
     */
    public static final String GPS_ENABLED_CHANGE_ACTION =
        "android.location.GPS_ENABLED_CHANGE";

    /**
     * Broadcast intent action when the configured location providers
     * change. For use with {@link #isProviderEnabled(String)}. If you're interacting with the
     * {@link android.provider.Settings.Secure#LOCATION_MODE} API, use {@link #MODE_CHANGED_ACTION}
     * instead.
     */
    public static final String PROVIDERS_CHANGED_ACTION =
        "android.location.PROVIDERS_CHANGED";

    /**
     * Broadcast intent action when {@link android.provider.Settings.Secure#LOCATION_MODE} changes.
     * For use with the {@link android.provider.Settings.Secure#LOCATION_MODE} API.
     * If you're interacting with {@link #isProviderEnabled(String)}, use
     * {@link #PROVIDERS_CHANGED_ACTION} instead.
     *
     * In the future, there may be mode changes that do not result in
     * {@link #PROVIDERS_CHANGED_ACTION} broadcasts.
     */
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";

    /**
     * Broadcast intent action indicating that the GPS has either started or
     * stopped receiving GPS fixes. An intent extra provides this state as a
     * boolean, where {@code true} means that the GPS is actively receiving fixes.
     * @see #EXTRA_GPS_ENABLED
     *
     * @hide
     */
    public static final String GPS_FIX_CHANGE_ACTION =
        "android.location.GPS_FIX_CHANGE";

    /**
     * The lookup key for a boolean that indicates whether GPS is enabled or
     * disabled. {@code true} means GPS is enabled. Retrieve it with
     * {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     *
     * @hide
     */
    public static final String EXTRA_GPS_ENABLED = "enabled";

    /**
     * Broadcast intent action indicating that a high power location requests
     * has either started or stopped being active.  The current state of
     * active location requests should be read from AppOpsManager using
     * {@code OP_MONITOR_HIGH_POWER_LOCATION}.
     *
     * @hide
     */
    public static final String HIGH_POWER_REQUEST_CHANGE_ACTION =
        "android.location.HIGH_POWER_REQUEST_CHANGE";

    // Map from LocationListeners to their associated ListenerTransport objects
    private HashMap<LocationListener,ListenerTransport> mListeners =
        new HashMap<LocationListener,ListenerTransport>();

    private class ListenerTransport extends ILocationListener.Stub {
        private static final int TYPE_LOCATION_CHANGED = 1;
        private static final int TYPE_STATUS_CHANGED = 2;
        private static final int TYPE_PROVIDER_ENABLED = 3;
        private static final int TYPE_PROVIDER_DISABLED = 4;

        private LocationListener mListener;
        private final Handler mListenerHandler;

        ListenerTransport(LocationListener listener, Looper looper) {
            mListener = listener;

            if (looper == null) {
                mListenerHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        _handleMessage(msg);
                    }
                };
            } else {
                mListenerHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(Message msg) {
                        _handleMessage(msg);
                    }
                };
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            Message msg = Message.obtain();
            msg.what = TYPE_LOCATION_CHANGED;
            msg.obj = location;
            mListenerHandler.sendMessage(msg);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Message msg = Message.obtain();
            msg.what = TYPE_STATUS_CHANGED;
            Bundle b = new Bundle();
            b.putString("provider", provider);
            b.putInt("status", status);
            if (extras != null) {
                b.putBundle("extras", extras);
            }
            msg.obj = b;
            mListenerHandler.sendMessage(msg);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Message msg = Message.obtain();
            msg.what = TYPE_PROVIDER_ENABLED;
            msg.obj = provider;
            mListenerHandler.sendMessage(msg);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Message msg = Message.obtain();
            msg.what = TYPE_PROVIDER_DISABLED;
            msg.obj = provider;
            mListenerHandler.sendMessage(msg);
        }

        private void _handleMessage(Message msg) {
            switch (msg.what) {
                case TYPE_LOCATION_CHANGED:
                    Location location = new Location((Location) msg.obj);
                    mListener.onLocationChanged(location);
                    break;
                case TYPE_STATUS_CHANGED:
                    Bundle b = (Bundle) msg.obj;
                    String provider = b.getString("provider");
                    int status = b.getInt("status");
                    Bundle extras = b.getBundle("extras");
                    mListener.onStatusChanged(provider, status, extras);
                    break;
                case TYPE_PROVIDER_ENABLED:
                    mListener.onProviderEnabled((String) msg.obj);
                    break;
                case TYPE_PROVIDER_DISABLED:
                    mListener.onProviderDisabled((String) msg.obj);
                    break;
            }
            try {
                mService.locationCallbackFinished(this);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * @hide - hide this constructor because it has a parameter
     * of type ILocationManager, which is a system private class. The
     * right way to create an instance of this class is using the
     * factory Context.getSystemService.
     */
    public LocationManager(Context context, ILocationManager service) {
        mService = service;
        mContext = context;
        mGnssMeasurementCallbackTransport = new GnssMeasurementCallbackTransport(mContext, mService);
        mGnssNavigationMessageCallbackTransport =
                new GnssNavigationMessageCallbackTransport(mContext, mService);
    }

    private LocationProvider createProvider(String name, ProviderProperties properties) {
        return new LocationProvider(name, properties);
    }

    /**
     * Returns a list of the names of all known location providers.
     * <p>All providers are returned, including ones that are not permitted to
     * be accessed by the calling activity or are currently disabled.
     *
     * @return list of Strings containing names of the provider
     */
    public List<String> getAllProviders() {
        try {
            return mService.getAllProviders();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a list of the names of location providers.
     *
     * @param enabledOnly if true then only the providers which are currently
     * enabled are returned.
     * @return list of Strings containing names of the providers
     */
    public List<String> getProviders(boolean enabledOnly) {
        try {
            return mService.getProviders(null, enabledOnly);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the information associated with the location provider of the
     * given name, or null if no provider exists by that name.
     *
     * @param name the provider name
     * @return a LocationProvider, or null
     *
     * @throws IllegalArgumentException if name is null or does not exist
     * @throws SecurityException if the caller is not permitted to access the
     * given provider.
     */
    public LocationProvider getProvider(String name) {
        checkProvider(name);
        try {
            ProviderProperties properties = mService.getProviderProperties(name);
            if (properties == null) {
                return null;
            }
            return createProvider(name, properties);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a list of the names of LocationProviders that satisfy the given
     * criteria, or null if none do.  Only providers that are permitted to be
     * accessed by the calling activity will be returned.
     *
     * @param criteria the criteria that the returned providers must match
     * @param enabledOnly if true then only the providers which are currently
     * enabled are returned.
     * @return list of Strings containing names of the providers
     */
    public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        checkCriteria(criteria);
        try {
            return mService.getProviders(criteria, enabledOnly);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the name of the provider that best meets the given criteria. Only providers
     * that are permitted to be accessed by the calling activity will be
     * returned.  If several providers meet the criteria, the one with the best
     * accuracy is returned.  If no provider meets the criteria,
     * the criteria are loosened in the following sequence:
     *
     * <ul>
     * <li> power requirement
     * <li> accuracy
     * <li> bearing
     * <li> speed
     * <li> altitude
     * </ul>
     *
     * <p> Note that the requirement on monetary cost is not removed
     * in this process.
     *
     * @param criteria the criteria that need to be matched
     * @param enabledOnly if true then only a provider that is currently enabled is returned
     * @return name of the provider that best matches the requirements
     */
    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        checkCriteria(criteria);
        try {
            return mService.getBestProvider(criteria, enabledOnly);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**LeaseOS change**/
    public ActivityManager.RunningTaskInfo getActivity() {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo info;
        if (manager != null) {
            if (manager.getRunningTasks(1).size() == 0) {
                return null;
            } else {
                info = manager.getRunningTasks(1).get(0);
                return info;
            }

        } else {
            Log.d(TAG,"Can not get the service");
            return null;
        }
    }


    /******************/


    /**
     * Register for location updates using the named provider, and a
     * pending intent.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called for
     * each location update
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * on this device
     * @throws IllegalArgumentException if listener is null
     * @throws RuntimeException if the calling thread has no Looper
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            LocationListener listener) {
        checkProvider(provider);
        checkListener(listener);
        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, minTime, minDistance, false);
        requestLocationUpdates(request, listener, null, null);
    }

    /**
     * Register for location updates using the named provider, and a callback on
     * the specified looper thread.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called for
     * each location update
     * @param looper a Looper object whose message queue will be used to
     * implement the callback mechanism, or null to make callbacks on the calling
     * thread
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * @throws IllegalArgumentException if listener is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            LocationListener listener, Looper looper) {
        checkProvider(provider);
        checkListener(listener);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, minTime, minDistance, false);
        requestLocationUpdates(request, listener, looper, null);
    }

    /**
     * Register for location updates using a Criteria, and a callback
     * on the specified looper thread.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     * @param criteria contains parameters for the location manager to choose the
     * appropriate provider and parameters to compute the location
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called for
     * each location update
     * @param looper a Looper object whose message queue will be used to
     * implement the callback mechanism, or null to make callbacks on the calling
     * thread
     *
     * @throws IllegalArgumentException if criteria is null
     * @throws IllegalArgumentException if listener is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(long minTime, float minDistance, Criteria criteria,
            LocationListener listener, Looper looper) {
        checkCriteria(criteria);
        checkListener(listener);

        LocationRequest request = LocationRequest.createFromDeprecatedCriteria(
                criteria, minTime, minDistance, false);
        requestLocationUpdates(request, listener, looper, null);
    }

    /**
     * Register for location updates using the named provider, and a
     * pending intent.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param provider the name of the provider with which to register
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     * @param intent a {@link PendingIntent} to be sent for each location update
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * on this device
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            PendingIntent intent) {
        checkProvider(provider);
        checkPendingIntent(intent);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, minTime, minDistance, false);
        requestLocationUpdates(request, null, null, intent);
    }

    /**
     * Register for location updates using a Criteria and pending intent.
     *
     * <p>The <code>requestLocationUpdates()</code> and
     * <code>requestSingleUpdate()</code> register the current activity to be
     * updated periodically by the named provider, or by the provider matching
     * the specified {@link Criteria}, with location and status updates.
     *
     * <p> It may take a while to receive the first location update. If
     * an immediate location is required, applications may use the
     * {@link #getLastKnownLocation(String)} method.
     *
     * <p> Location updates are received either by {@link LocationListener}
     * callbacks, or by broadcast intents to a supplied {@link PendingIntent}.
     *
     * <p> If the caller supplied a pending intent, then location updates
     * are sent with a key of {@link #KEY_LOCATION_CHANGED} and a
     * {@link android.location.Location} value.
     *
     * <p> The location update interval can be controlled using the minTime parameter.
     * The elapsed time between location updates will never be less than
     * minTime, although it can be more depending on the Location Provider
     * implementation and the update interval requested by other applications.
     *
     * <p> Choosing a sensible value for minTime is important to conserve
     * battery life. Each location update requires power from
     * GPS, WIFI, Cell and other radios. Select a minTime value as high as
     * possible while still providing a reasonable user experience.
     * If your application is not in the foreground and showing
     * location to the user then your application should avoid using an active
     * provider (such as {@link #NETWORK_PROVIDER} or {@link #GPS_PROVIDER}),
     * but if you insist then select a minTime of 5 * 60 * 1000 (5 minutes)
     * or greater. If your application is in the foreground and showing
     * location to the user then it is appropriate to select a faster
     * update interval.
     *
     * <p> The minDistance parameter can also be used to control the
     * frequency of location updates. If it is greater than 0 then the
     * location provider will only send your application an update when
     * the location has changed by at least minDistance meters, AND
     * at least minTime milliseconds have passed. However it is more
     * difficult for location providers to save power using the minDistance
     * parameter, so minTime should be the primary tool to conserving battery
     * life.
     *
     * <p> If your application wants to passively observe location
     * updates triggered by other applications, but not consume
     * any additional power otherwise, then use the {@link #PASSIVE_PROVIDER}
     * This provider does not actively turn on or modify active location
     * providers, so you do not need to be as careful about minTime and
     * minDistance. However if your application performs heavy work
     * on a location update (such as network activity) then you should
     * select non-zero values for minTime and/or minDistance to rate-limit
     * your update frequency in the case another application enables a
     * location provider with extremely fast updates.
     *
     * <p>In case the provider is disabled by the user, updates will stop,
     * and a provider availability update will be sent.
     * As soon as the provider is enabled again,
     * location updates will immediately resume and a provider availability
     * update sent. Providers can also send status updates, at any time,
     * with extra's specific to the provider. If a callback was supplied
     * then status and availability updates are via
     * {@link LocationListener#onProviderDisabled},
     * {@link LocationListener#onProviderEnabled} or
     * {@link LocationListener#onStatusChanged}. Alternately, if a
     * pending intent was supplied then status and availability updates
     * are broadcast intents with extra keys of
     * {@link #KEY_PROVIDER_ENABLED} or {@link #KEY_STATUS_CHANGED}.
     *
     * <p> If a {@link LocationListener} is used but with no Looper specified
     * then the calling thread must already
     * be a {@link android.os.Looper} thread such as the main thread of the
     * calling Activity. If a Looper is specified with a {@link LocationListener}
     * then callbacks are made on the supplied Looper thread.
     *
     * <p class="note"> Prior to Jellybean, the minTime parameter was
     * only a hint, and some location provider implementations ignored it.
     * From Jellybean and onwards it is mandatory for Android compatible
     * devices to observe both the minTime and minDistance parameters.
     *
     * @param minTime minimum time interval between location updates, in milliseconds
     * @param minDistance minimum distance between location updates, in meters
     * @param criteria contains parameters for the location manager to choose the
     * appropriate provider and parameters to compute the location
     * @param intent a {@link PendingIntent} to be sent for each location update
     *
     * @throws IllegalArgumentException if criteria is null
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(long minTime, float minDistance, Criteria criteria,
            PendingIntent intent) {
        checkCriteria(criteria);
        checkPendingIntent(intent);

        LocationRequest request = LocationRequest.createFromDeprecatedCriteria(
                criteria, minTime, minDistance, false);
        requestLocationUpdates(request, null, null, intent);
    }

    /**
     * Register for a single location update using the named provider and
     * a callback.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param provider the name of the provider with which to register
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called when
     * the location update is available
     * @param looper a Looper object whose message queue will be used to
     * implement the callback mechanism, or null to make callbacks on the calling
     * thread
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * @throws IllegalArgumentException if listener is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
        checkProvider(provider);
        checkListener(listener);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, 0, 0, true);
        requestLocationUpdates(request, listener, looper, null);
    }

    /**
     * Register for a single location update using a Criteria and
     * a callback.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param criteria contains parameters for the location manager to choose the
     * appropriate provider and parameters to compute the location
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called when
     * the location update is available
     * @param looper a Looper object whose message queue will be used to
     * implement the callback mechanism, or null to make callbacks on the calling
     * thread
     *
     * @throws IllegalArgumentException if criteria is null
     * @throws IllegalArgumentException if listener is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
        checkCriteria(criteria);
        checkListener(listener);

        LocationRequest request = LocationRequest.createFromDeprecatedCriteria(
                criteria, 0, 0, true);
        requestLocationUpdates(request, listener, looper, null);
    }

    /**
     * Register for a single location update using a named provider and pending intent.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param provider the name of the provider with which to register
     * @param intent a {@link PendingIntent} to be sent for the location update
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestSingleUpdate(String provider, PendingIntent intent) {
        checkProvider(provider);
        checkPendingIntent(intent);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, 0, 0, true);
        requestLocationUpdates(request, null, null, intent);
    }

    /**
     * Register for a single location update using a Criteria and pending intent.
     *
     * <p>See {@link #requestLocationUpdates(long, float, Criteria, PendingIntent)}
     * for more detail on how to use this method.
     *
     * @param criteria contains parameters for the location manager to choose the
     * appropriate provider and parameters to compute the location
     * @param intent a {@link PendingIntent} to be sent for the location update
     *
     * @throws IllegalArgumentException if provider is null or doesn't exist
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if no suitable permission is present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestSingleUpdate(Criteria criteria, PendingIntent intent) {
        checkCriteria(criteria);
        checkPendingIntent(intent);

        LocationRequest request = LocationRequest.createFromDeprecatedCriteria(
                criteria, 0, 0, true);
        requestLocationUpdates(request, null, null, intent);
    }

    /**
     * Register for fused location updates using a LocationRequest and callback.
     *
     * <p>Upon a location update, the system delivers the new {@link Location} to the
     * provided {@link LocationListener}, by calling its {@link
     * LocationListener#onLocationChanged} method.</p>
     *
     * <p>The system will automatically select and enable the best providers
     * to compute a location for your application. It may use only passive
     * locations, or just a single location source, or it may fuse together
     * multiple location sources in order to produce the best possible
     * result, depending on the quality of service requested in the
     * {@link LocationRequest}.
     *
     * <p>LocationRequest can be null, in which case the system will choose
     * default, low power parameters for location updates. You will occasionally
     * receive location updates as available, without a major power impact on the
     * system. If your application just needs an occasional location update
     * without any strict demands, then pass a null LocationRequest.
     *
     * <p>Only one LocationRequest can be registered for each unique callback
     * or pending intent. So a subsequent request with the same callback or
     * pending intent will over-write the previous LocationRequest.
     *
     * <p> If a pending intent is supplied then location updates
     * are sent with a key of {@link #KEY_LOCATION_CHANGED} and a
     * {@link android.location.Location} value. If a callback is supplied
     * then location updates are made using the
     * {@link LocationListener#onLocationChanged} callback, on the specified
     * Looper thread. If a {@link LocationListener} is used
     * but with a null Looper then the calling thread must already
     * be a {@link android.os.Looper} thread (such as the main thread) and
     * callbacks will occur on this thread.
     *
     * <p> Provider status updates and availability updates are deprecated
     * because the system is performing provider fusion on the applications
     * behalf. So {@link LocationListener#onProviderDisabled},
     * {@link LocationListener#onProviderEnabled}, {@link LocationListener#onStatusChanged}
     * will not be called, and intents with extra keys of
     * {@link #KEY_PROVIDER_ENABLED} or {@link #KEY_STATUS_CHANGED} will not
     * be received.
     *
     * <p> To unregister for Location updates, use: {@link #removeUpdates(LocationListener)}.
     *
     * @param request quality of service required, null for default low power
     * @param listener a {@link LocationListener} whose
     * {@link LocationListener#onLocationChanged} method will be called when
     * the location update is available
     * @param looper a Looper object whose message queue will be used to
     * implement the callback mechanism, or null to make callbacks on the calling
     * thread
     *
     * @throws IllegalArgumentException if listener is null
     * @throws SecurityException if no suitable permission is present
     *
     * @hide
     */
    @SystemApi
    public void requestLocationUpdates(LocationRequest request, LocationListener listener,
            Looper looper) {
        checkListener(listener);
        requestLocationUpdates(request, listener, looper, null);
    }


    /**
     * Register for fused location updates using a LocationRequest and a pending intent.
     *
     * <p>Upon a location update, the system delivers the new {@link Location} with your provided
     * {@link PendingIntent}, as the value for {@link LocationManager#KEY_LOCATION_CHANGED}
     * in the intent's extras.</p>
     *
     * <p> To unregister for Location updates, use: {@link #removeUpdates(PendingIntent)}.
     *
     * <p> See {@link #requestLocationUpdates(LocationRequest, LocationListener, Looper)}
     * for more detail.
     *
     * @param request quality of service required, null for default low power
     * @param intent a {@link PendingIntent} to be sent for the location update
     *
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if no suitable permission is present
     *
     * @hide
     */
    @SystemApi
    public void requestLocationUpdates(LocationRequest request, PendingIntent intent) {
        checkPendingIntent(intent);
        requestLocationUpdates(request, null, null, intent);
    }

    private ListenerTransport wrapListener(LocationListener listener, Looper looper) {
        if (listener == null) return null;
        synchronized (mListeners) {
            ListenerTransport transport = mListeners.get(listener);
            if (transport == null) {
                transport = new ListenerTransport(listener, looper);
            }
            mListeners.put(listener, transport);
            return transport;
        }
    }

    /***LeaseOS change **/
    /**
     * Register for location updates using the named provider, and a callback on
     * the specified looper thread, and a utility for lease
     *
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            LocationListener listener, Looper looper, UtilityCounter utilityCounter) {
        checkProvider(provider);
        checkListener(listener);

        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, minTime, minDistance, false);

        /**LeaseOS change**/
        String packageName = mContext.getPackageName();
        ActivityManager.RunningTaskInfo info = null;
        info = getActivity();

        // wrap the listener class
        ListenerTransport transport = wrapListener(listener, looper);
        CounterTransport counterTransport;
        synchronized (mCounter) {
            counterTransport = mCounter.get(utilityCounter);
            if (counterTransport == null) {
                counterTransport = new CounterTransport(utilityCounter);
            }
            mCounter.put(utilityCounter, counterTransport);
        }

        try {
            if (info == null) {
                mService.requestLocationUpdatesUtility(request, transport, null, packageName, null, counterTransport);
            } else {
                Log.d(TAG, "The class name is " + info.topActivity.getClassName());
                mService.requestLocationUpdatesUtility(request, transport, null, packageName, info.topActivity.getClassName(), counterTransport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void removeUpdates(LocationListener listener, UtilityCounter counter) {
        checkListener(listener);
        String packageName = mContext.getPackageName();

        ListenerTransport transport;
        synchronized (mListeners) {
            transport = mListeners.remove(listener);
        }
        if (transport == null) return;

        synchronized (mCounter) {
            mCounter.remove(counter);
        }
        try {
            mService.removeUpdates(transport, null, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
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
    /**************/

    private void requestLocationUpdates(LocationRequest request, LocationListener listener,
            Looper looper, PendingIntent intent) {

        /**LeaseOS change**/
        String packageName = mContext.getPackageName();
        ActivityManager.RunningTaskInfo info = null;
        info = getActivity();

        // wrap the listener class
        ListenerTransport transport = wrapListener(listener, looper);

        try {
            if (info == null) {
                mService.requestLocationUpdates(request, transport, intent, packageName, null);
            } else {
                Log.d(TAG, "The class name is " + info.topActivity.getClassName());
                mService.requestLocationUpdates(request, transport, intent, packageName, info.topActivity.getClassName());
            }

       } catch (RemoteException e) {
           throw e.rethrowFromSystemServer();
       }
    }

    /**
     * Removes all location updates for the specified LocationListener.
     *
     * <p>Following this call, updates will no longer
     * occur for this listener.
     *
     * @param listener listener object that no longer needs location updates
     * @throws IllegalArgumentException if listener is null
     */
    public void removeUpdates(LocationListener listener) {
        checkListener(listener);
        String packageName = mContext.getPackageName();

        ListenerTransport transport;
        synchronized (mListeners) {
            transport = mListeners.remove(listener);
        }
        if (transport == null) return;

        try {
            mService.removeUpdates(transport, null, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes all location updates for the specified pending intent.
     *
     * <p>Following this call, updates will no longer for this pending intent.
     *
     * @param intent pending intent object that no longer needs location updates
     * @throws IllegalArgumentException if intent is null
     */
    public void removeUpdates(PendingIntent intent) {
        checkPendingIntent(intent);
        String packageName = mContext.getPackageName();

        try {
            mService.removeUpdates(null, intent, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set a proximity alert for the location given by the position
     * (latitude, longitude) and the given radius.
     *
     * <p> When the device
     * detects that it has entered or exited the area surrounding the
     * location, the given PendingIntent will be used to create an Intent
     * to be fired.
     *
     * <p> The fired Intent will have a boolean extra added with key
     * {@link #KEY_PROXIMITY_ENTERING}. If the value is true, the device is
     * entering the proximity region; if false, it is exiting.
     *
     * <p> Due to the approximate nature of position estimation, if the
     * device passes through the given area briefly, it is possible
     * that no Intent will be fired.  Similarly, an Intent could be
     * fired if the device passes very close to the given area but
     * does not actually enter it.
     *
     * <p> After the number of milliseconds given by the expiration
     * parameter, the location manager will delete this proximity
     * alert and no longer monitor it.  A value of -1 indicates that
     * there should be no expiration time.
     *
     * <p> Internally, this method uses both {@link #NETWORK_PROVIDER}
     * and {@link #GPS_PROVIDER}.
     *
     * <p>Before API version 17, this method could be used with
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}.
     * From API version 17 and onwards, this method requires
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} permission.
     *
     * @param latitude the latitude of the central point of the
     * alert region
     * @param longitude the longitude of the central point of the
     * alert region
     * @param radius the radius of the central point of the
     * alert region, in meters
     * @param expiration time for this proximity alert, in milliseconds,
     * or -1 to indicate no expiration
     * @param intent a PendingIntent that will be used to generate an Intent to
     * fire when entry to or exit from the alert region is detected
     *
     * @throws SecurityException if {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission is not present
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void addProximityAlert(double latitude, double longitude, float radius, long expiration,
            PendingIntent intent) {
        checkPendingIntent(intent);
        if (expiration < 0) expiration = Long.MAX_VALUE;

        Geofence fence = Geofence.createCircle(latitude, longitude, radius);
        LocationRequest request = new LocationRequest().setExpireIn(expiration);
        try {
            mService.requestGeofence(request, fence, intent, mContext.getPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Add a geofence with the specified LocationRequest quality of service.
     *
     * <p> When the device
     * detects that it has entered or exited the area surrounding the
     * location, the given PendingIntent will be used to create an Intent
     * to be fired.
     *
     * <p> The fired Intent will have a boolean extra added with key
     * {@link #KEY_PROXIMITY_ENTERING}. If the value is true, the device is
     * entering the proximity region; if false, it is exiting.
     *
     * <p> The geofence engine fuses results from all location providers to
     * provide the best balance between accuracy and power. Applications
     * can choose the quality of service required using the
     * {@link LocationRequest} object. If it is null then a default,
     * low power geo-fencing implementation is used. It is possible to cross
     * a geo-fence without notification, but the system will do its best
     * to detect, using {@link LocationRequest} as a hint to trade-off
     * accuracy and power.
     *
     * <p> The power required by the geofence engine can depend on many factors,
     * such as quality and interval requested in {@link LocationRequest},
     * distance to nearest geofence and current device velocity.
     *
     * @param request quality of service required, null for default low power
     * @param fence a geographical description of the geofence area
     * @param intent pending intent to receive geofence updates
     *
     * @throws IllegalArgumentException if fence is null
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission is not present
     *
     * @hide
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public void addGeofence(LocationRequest request, Geofence fence, PendingIntent intent) {
        checkPendingIntent(intent);
        checkGeofence(fence);

        try {
            mService.requestGeofence(request, fence, intent, mContext.getPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes the proximity alert with the given PendingIntent.
     *
     * <p>Before API version 17, this method could be used with
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION}.
     * From API version 17 and onwards, this method requires
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} permission.
     *
     * @param intent the PendingIntent that no longer needs to be notified of
     * proximity alerts
     *
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission is not present
     */
    public void removeProximityAlert(PendingIntent intent) {
        checkPendingIntent(intent);
        String packageName = mContext.getPackageName();

        try {
            mService.removeGeofence(null, intent, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove a single geofence.
     *
     * <p>This removes only the specified geofence associated with the
     * specified pending intent. All other geofences remain unchanged.
     *
     * @param fence a geofence previously passed to {@link #addGeofence}
     * @param intent a pending intent previously passed to {@link #addGeofence}
     *
     * @throws IllegalArgumentException if fence is null
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission is not present
     *
     * @hide
     */
    public void removeGeofence(Geofence fence, PendingIntent intent) {
        checkPendingIntent(intent);
        checkGeofence(fence);
        String packageName = mContext.getPackageName();

        try {
            mService.removeGeofence(fence, intent, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove all geofences registered to the specified pending intent.
     *
     * @param intent a pending intent previously passed to {@link #addGeofence}
     *
     * @throws IllegalArgumentException if intent is null
     * @throws SecurityException if {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission is not present
     *
     * @hide
     */
    public void removeAllGeofences(PendingIntent intent) {
        checkPendingIntent(intent);
        String packageName = mContext.getPackageName();

        try {
            mService.removeGeofence(null, intent, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the current enabled/disabled status of the given provider.
     *
     * <p>If the user has enabled this provider in the Settings menu, true
     * is returned otherwise false is returned
     *
     * <p>Callers should instead use
     * {@link android.provider.Settings.Secure#LOCATION_MODE}
     * unless they depend on provider-specific APIs such as
     * {@link #requestLocationUpdates(String, long, float, LocationListener)}.
     *
     * <p>
     * Before API version {@link android.os.Build.VERSION_CODES#LOLLIPOP}, this
     * method would throw {@link SecurityException} if the location permissions
     * were not sufficient to use the specified provider.
     *
     * @param provider the name of the provider
     * @return true if the provider exists and is enabled
     *
     * @throws IllegalArgumentException if provider is null
     */
    public boolean isProviderEnabled(String provider) {
        checkProvider(provider);

        try {
            return mService.isProviderEnabled(provider);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the last known location.
     *
     * <p>This location could be very old so use
     * {@link Location#getElapsedRealtimeNanos} to calculate its age. It can
     * also return null if no previous location is available.
     *
     * <p>Always returns immediately.
     *
     * @return The last known location, or null if not available
     * @throws SecurityException if no suitable permission is present
     *
     * @hide
     */
    public Location getLastLocation() {
        String packageName = mContext.getPackageName();

        try {
            return mService.getLastLocation(null, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a Location indicating the data from the last known
     * location fix obtained from the given provider.
     *
     * <p> This can be done
     * without starting the provider.  Note that this location could
     * be out-of-date, for example if the device was turned off and
     * moved to another location.
     *
     * <p> If the provider is currently disabled, null is returned.
     *
     * @param provider the name of the provider
     * @return the last known location for the provider, or null
     *
     * @throws SecurityException if no suitable permission is present
     * @throws IllegalArgumentException if provider is null or doesn't exist
     */
    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Location getLastKnownLocation(String provider) {
        checkProvider(provider);
        String packageName = mContext.getPackageName();
        LocationRequest request = LocationRequest.createFromDeprecatedProvider(
                provider, 0, 0, true);

        try {
            return mService.getLastLocation(request, packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    // --- Mock provider support ---
    // TODO: It would be fantastic to deprecate mock providers entirely, and replace
    // with something closer to LocationProviderBase.java

    /**
     * Creates a mock location provider and adds it to the set of active providers.
     *
     * @param name the provider name
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if a provider with the given name already exists
     */
    public void addTestProvider(String name, boolean requiresNetwork, boolean requiresSatellite,
            boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude,
            boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy) {
        ProviderProperties properties = new ProviderProperties(requiresNetwork,
                requiresSatellite, requiresCell, hasMonetaryCost, supportsAltitude, supportsSpeed,
                supportsBearing, powerRequirement, accuracy);
        if (name.matches(LocationProvider.BAD_CHARS_REGEX)) {
            throw new IllegalArgumentException("provider name contains illegal character: " + name);
        }

        try {
            mService.addTestProvider(name, properties, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes the mock location provider with the given name.
     *
     * @param provider the provider name
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void removeTestProvider(String provider) {
        try {
            mService.removeTestProvider(provider, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets a mock location for the given provider.
     * <p>This location will be used in place of any actual location from the provider.
     * The location object must have a minimum number of fields set to be
     * considered a valid LocationProvider Location, as per documentation
     * on {@link Location} class.
     *
     * @param provider the provider name
     * @param loc the mock location
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     * @throws IllegalArgumentException if the location is incomplete
     */
    public void setTestProviderLocation(String provider, Location loc) {
        if (!loc.isComplete()) {
            IllegalArgumentException e = new IllegalArgumentException(
                    "Incomplete location object, missing timestamp or accuracy? " + loc);
            if (mContext.getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.JELLY_BEAN) {
                // just log on old platform (for backwards compatibility)
                Log.w(TAG, e);
                loc.makeComplete();
            } else {
                // really throw it!
                throw e;
            }
        }

        try {
            mService.setTestProviderLocation(provider, loc, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes any mock location associated with the given provider.
     *
     * @param provider the provider name
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void clearTestProviderLocation(String provider) {
        try {
            mService.clearTestProviderLocation(provider, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets a mock enabled value for the given provider.  This value will be used in place
     * of any actual value from the provider.
     *
     * @param provider the provider name
     * @param enabled the mock enabled value
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void setTestProviderEnabled(String provider, boolean enabled) {
        try {
            mService.setTestProviderEnabled(provider, enabled, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes any mock enabled value associated with the given provider.
     *
     * @param provider the provider name
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void clearTestProviderEnabled(String provider) {
        try {
            mService.clearTestProviderEnabled(provider, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets mock status values for the given provider.  These values will be used in place
     * of any actual values from the provider.
     *
     * @param provider the provider name
     * @param status the mock status
     * @param extras a Bundle containing mock extras
     * @param updateTime the mock update time
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void setTestProviderStatus(String provider, int status, Bundle extras, long updateTime) {
        try {
            mService.setTestProviderStatus(provider, status, extras, updateTime,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes any mock status values associated with the given provider.
     *
     * @param provider the provider name
     *
     * @throws SecurityException if {@link android.app.AppOpsManager#OPSTR_MOCK_LOCATION
     * mock location app op} is not set to {@link android.app.AppOpsManager#MODE_ALLOWED
     * allowed} for your app.
     * @throws IllegalArgumentException if no provider with the given name exists
     */
    public void clearTestProviderStatus(String provider) {
        try {
            mService.clearTestProviderStatus(provider, mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    // --- GPS-specific support ---

    // This class is used to send Gnss status events to the client's specific thread.
    private class GnssStatusListenerTransport extends IGnssStatusListener.Stub {

        private final GpsStatus.Listener mGpsListener;
        private final GpsStatus.NmeaListener mGpsNmeaListener;
        private final GnssStatusCallback mOldGnssCallback;
        private final GnssStatus.Callback mGnssCallback;
        private final GnssNmeaListener mOldGnssNmeaListener;
        private final OnNmeaMessageListener mGnssNmeaListener;

        private class GnssHandler extends Handler {
            public GnssHandler(Handler handler) {
                super(handler != null ? handler.getLooper() : Looper.myLooper());
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case NMEA_RECEIVED:
                        synchronized (mNmeaBuffer) {
                            int length = mNmeaBuffer.size();
                            for (int i = 0; i < length; i++) {
                                Nmea nmea = mNmeaBuffer.get(i);
                                mGnssNmeaListener.onNmeaMessage(nmea.mNmea, nmea.mTimestamp);
                            }
                            mNmeaBuffer.clear();
                        }
                        break;
                    case GpsStatus.GPS_EVENT_STARTED:
                        mGnssCallback.onStarted();
                        break;
                    case GpsStatus.GPS_EVENT_STOPPED:
                        mGnssCallback.onStopped();
                        break;
                    case GpsStatus.GPS_EVENT_FIRST_FIX:
                        mGnssCallback.onFirstFix(mTimeToFirstFix);
                        break;
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        mGnssCallback.onSatelliteStatusChanged(mGnssStatus);
                        break;
                    default:
                        break;
                }
            }
        }

        private final Handler mGnssHandler;

        // This must not equal any of the GpsStatus event IDs
        private static final int NMEA_RECEIVED = 1000;

        private class Nmea {
            long mTimestamp;
            String mNmea;

            Nmea(long timestamp, String nmea) {
                mTimestamp = timestamp;
                mNmea = nmea;
            }
        }
        private final ArrayList<Nmea> mNmeaBuffer;

        GnssStatusListenerTransport(GpsStatus.Listener listener) {
            this(listener, null);
        }

        GnssStatusListenerTransport(GpsStatus.Listener listener, Handler handler) {
            mGpsListener = listener;
            mGnssHandler = new GnssHandler(handler);
            mGpsNmeaListener = null;
            mNmeaBuffer = null;
            mOldGnssCallback = null;
            mGnssCallback = mGpsListener != null ? new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    mGpsListener.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED);
                }

                @Override
                public void onStopped() {
                    mGpsListener.onGpsStatusChanged(GpsStatus.GPS_EVENT_STOPPED);
                }

                @Override
                public void onFirstFix(int ttff) {
                    mGpsListener.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX);
                }

                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    mGpsListener.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS);
                }
            } : null;
            mOldGnssNmeaListener = null;
            mGnssNmeaListener = null;
        }

        GnssStatusListenerTransport(GpsStatus.NmeaListener listener) {
            this(listener, null);
        }

        GnssStatusListenerTransport(GpsStatus.NmeaListener listener, Handler handler) {
            mGpsListener = null;
            mGnssHandler = new GnssHandler(handler);
            mGpsNmeaListener = listener;
            mNmeaBuffer = new ArrayList<Nmea>();
            mOldGnssCallback = null;
            mGnssCallback = null;
            mOldGnssNmeaListener = null;
            mGnssNmeaListener = mGpsNmeaListener != null ? new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String nmea, long timestamp) {
                    mGpsNmeaListener.onNmeaReceived(timestamp, nmea);
                }
            } : null;
        }

        GnssStatusListenerTransport(GnssStatusCallback callback) {
            this(callback, null);
        }

        GnssStatusListenerTransport(GnssStatusCallback callback, Handler handler) {
            mOldGnssCallback = callback;
            mGnssCallback = mOldGnssCallback != null ? new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    mOldGnssCallback.onStarted();
                }

                @Override
                public void onStopped() {
                    mOldGnssCallback.onStopped();
                }

                @Override
                public void onFirstFix(int ttff) {
                    mOldGnssCallback.onFirstFix(ttff);
                }

                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    mOldGnssCallback.onSatelliteStatusChanged(status);
                }
            } : null;
            mGnssHandler = new GnssHandler(handler);
            mOldGnssNmeaListener = null;
            mGnssNmeaListener = null;
            mNmeaBuffer = null;
            mGpsListener = null;
            mGpsNmeaListener = null;
        }

        GnssStatusListenerTransport(GnssStatus.Callback callback) {
            this(callback, null);
        }

        GnssStatusListenerTransport(GnssStatus.Callback callback, Handler handler) {
            mOldGnssCallback = null;
            mGnssCallback = callback;
            mGnssHandler = new GnssHandler(handler);
            mOldGnssNmeaListener = null;
            mGnssNmeaListener = null;
            mNmeaBuffer = null;
            mGpsListener = null;
            mGpsNmeaListener = null;
        }

        GnssStatusListenerTransport(GnssNmeaListener listener) {
            this(listener, null);
        }

        GnssStatusListenerTransport(GnssNmeaListener listener, Handler handler) {
            mGnssCallback = null;
            mOldGnssCallback = null;
            mGnssHandler = new GnssHandler(handler);
            mOldGnssNmeaListener = listener;
            mGnssNmeaListener = mOldGnssNmeaListener != null ? new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String message, long timestamp) {
                    mOldGnssNmeaListener.onNmeaReceived(timestamp, message);
                }
            } : null;
            mGpsListener = null;
            mGpsNmeaListener = null;
            mNmeaBuffer = new ArrayList<Nmea>();
        }

        GnssStatusListenerTransport(OnNmeaMessageListener listener) {
            this(listener, null);
        }

        GnssStatusListenerTransport(OnNmeaMessageListener listener, Handler handler) {
            mOldGnssCallback = null;
            mGnssCallback = null;
            mGnssHandler = new GnssHandler(handler);
            mOldGnssNmeaListener = null;
            mGnssNmeaListener = listener;
            mGpsListener = null;
            mGpsNmeaListener = null;
            mNmeaBuffer = new ArrayList<Nmea>();
        }

        @Override
        public void onGnssStarted() {
            if (mGnssCallback != null) {
                Message msg = Message.obtain();
                msg.what = GpsStatus.GPS_EVENT_STARTED;
                mGnssHandler.sendMessage(msg);
            }
        }

        @Override
        public void onGnssStopped() {
            if (mGnssCallback != null) {
                Message msg = Message.obtain();
                msg.what = GpsStatus.GPS_EVENT_STOPPED;
                mGnssHandler.sendMessage(msg);
            }
        }

        @Override
        public void onFirstFix(int ttff) {
            if (mGnssCallback != null) {
                mTimeToFirstFix = ttff;
                Message msg = Message.obtain();
                msg.what = GpsStatus.GPS_EVENT_FIRST_FIX;
                mGnssHandler.sendMessage(msg);
            }
        }

        @Override
        public void onSvStatusChanged(int svCount, int[] prnWithFlags,
                float[] cn0s, float[] elevations, float[] azimuths) {
            if (mGnssCallback != null) {
                mGnssStatus = new GnssStatus(svCount, prnWithFlags, cn0s, elevations, azimuths);

                Message msg = Message.obtain();
                msg.what = GpsStatus.GPS_EVENT_SATELLITE_STATUS;
                // remove any SV status messages already in the queue
                mGnssHandler.removeMessages(GpsStatus.GPS_EVENT_SATELLITE_STATUS);
                mGnssHandler.sendMessage(msg);
            }
        }

        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            if (mGnssNmeaListener != null) {
                synchronized (mNmeaBuffer) {
                    mNmeaBuffer.add(new Nmea(timestamp, nmea));
                }
                Message msg = Message.obtain();
                msg.what = NMEA_RECEIVED;
                // remove any NMEA_RECEIVED messages already in the queue
                mGnssHandler.removeMessages(NMEA_RECEIVED);
                mGnssHandler.sendMessage(msg);
            }
        }
    }

    /**
     * Adds a GPS status listener.
     *
     * @param listener GPS status listener object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @deprecated use {@link #registerGnssStatusCallback(GnssStatus.Callback)} instead.
     */
    @Deprecated
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addGpsStatusListener(GpsStatus.Listener listener) {
        boolean result;

        if (mGpsStatusListeners.get(listener) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport = new GnssStatusListenerTransport(listener);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mGpsStatusListeners.put(listener, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes a GPS status listener.
     *
     * @param listener GPS status listener object to remove
     * @deprecated use {@link #unregisterGnssStatusCallback(GnssStatus.Callback)} instead.
     */
    @Deprecated
    public void removeGpsStatusListener(GpsStatus.Listener listener) {
        try {
            GnssStatusListenerTransport transport = mGpsStatusListeners.remove(listener);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a GNSS status listener.
     *
     * @param callback GNSS status listener object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @removed
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssStatusCallback(GnssStatusCallback callback) {
        return registerGnssStatusCallback(callback, null);
    }

    /**
     * Registers a GNSS status listener.
     *
     * @param callback GNSS status listener object to register
     * @param handler the handler that the callback runs on.
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @removed
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssStatusCallback(GnssStatusCallback callback, Handler handler) {
        boolean result;
        if (mOldGnssStatusListeners.get(callback) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport =
                    new GnssStatusListenerTransport(callback, handler);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mOldGnssStatusListeners.put(callback, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes a GNSS status listener.
     *
     * @param callback GNSS status listener object to remove
     * @removed
     */
    public void unregisterGnssStatusCallback(GnssStatusCallback callback) {
        try {
            GnssStatusListenerTransport transport = mOldGnssStatusListeners.remove(callback);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a GNSS status listener.
     *
     * @param callback GNSS status listener object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssStatusCallback(GnssStatus.Callback callback) {
        return registerGnssStatusCallback(callback, null);
    }

    /**
     * Registers a GNSS status listener.
     *
     * @param callback GNSS status listener object to register
     * @param handler the handler that the callback runs on.
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssStatusCallback(GnssStatus.Callback callback, Handler handler) {
        boolean result;
        if (mGnssStatusListeners.get(callback) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport =
                    new GnssStatusListenerTransport(callback, handler);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mGnssStatusListeners.put(callback, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes a GNSS status listener.
     *
     * @param callback GNSS status listener object to remove
     */
    public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {
        try {
            GnssStatusListenerTransport transport = mGnssStatusListeners.remove(callback);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Adds an NMEA listener.
     *
     * @param listener a {@link GpsStatus.NmeaListener} object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @deprecated use {@link #addNmeaListener(OnNmeaMessageListener)} instead.
     */
    @Deprecated
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addNmeaListener(GpsStatus.NmeaListener listener) {
        boolean result;

        if (mGpsNmeaListeners.get(listener) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport = new GnssStatusListenerTransport(listener);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mGpsNmeaListeners.put(listener, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes an NMEA listener.
     *
     * @param listener a {@link GpsStatus.NmeaListener} object to remove
     * @deprecated use {@link #removeNmeaListener(OnNmeaMessageListener)} instead.
     */
    @Deprecated
    public void removeNmeaListener(GpsStatus.NmeaListener listener) {
        try {
            GnssStatusListenerTransport transport = mGpsNmeaListeners.remove(listener);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Adds an NMEA listener.
     *
     * @param listener a {@link GnssNmeaListener} object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @removed
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addNmeaListener(GnssNmeaListener listener) {
        return addNmeaListener(listener, null);
    }

    /**
     * Adds an NMEA listener.
     *
     * @param listener a {@link GnssNmeaListener} object to register
     * @param handler the handler that the listener runs on.
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     * @removed
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addNmeaListener(GnssNmeaListener listener, Handler handler) {
        boolean result;

        if (mGpsNmeaListeners.get(listener) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport =
                    new GnssStatusListenerTransport(listener, handler);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mOldGnssNmeaListeners.put(listener, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes an NMEA listener.
     *
     * @param listener a {@link GnssNmeaListener} object to remove
     * @removed
     */
    public void removeNmeaListener(GnssNmeaListener listener) {
        try {
            GnssStatusListenerTransport transport = mOldGnssNmeaListeners.remove(listener);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Adds an NMEA listener.
     *
     * @param listener a {@link OnNmeaMessageListener} object to register
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addNmeaListener(OnNmeaMessageListener listener) {
        return addNmeaListener(listener, null);
    }

    /**
     * Adds an NMEA listener.
     *
     * @param listener a {@link OnNmeaMessageListener} object to register
     * @param handler the handler that the listener runs on.
     *
     * @return true if the listener was successfully added
     *
     * @throws SecurityException if the ACCESS_FINE_LOCATION permission is not present
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean addNmeaListener(OnNmeaMessageListener listener, Handler handler) {
        boolean result;

        if (mGpsNmeaListeners.get(listener) != null) {
            // listener is already registered
            return true;
        }
        try {
            GnssStatusListenerTransport transport =
                    new GnssStatusListenerTransport(listener, handler);
            result = mService.registerGnssStatusCallback(transport, mContext.getPackageName());
            if (result) {
                mGnssNmeaListeners.put(listener, transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return result;
    }

    /**
     * Removes an NMEA listener.
     *
     * @param listener a {@link OnNmeaMessageListener} object to remove
     */
    public void removeNmeaListener(OnNmeaMessageListener listener) {
        try {
            GnssStatusListenerTransport transport = mGnssNmeaListeners.remove(listener);
            if (transport != null) {
                mService.unregisterGnssStatusCallback(transport);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * No-op method to keep backward-compatibility.
     * Don't use it. Use {@link #registerGnssMeasurementsCallback} instead.
     * @hide
     * @deprecated Not supported anymore.
     */
    @Deprecated
    @SystemApi
    public boolean addGpsMeasurementListener(GpsMeasurementsEvent.Listener listener) {
        return false;
    }

    /**
     * Registers a GPS Measurement callback.
     *
     * @param callback a {@link GnssMeasurementsEvent.Callback} object to register.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback) {
        return registerGnssMeasurementsCallback(callback, null);
    }

    /**
     * Registers a GPS Measurement callback.
     *
     * @param callback a {@link GnssMeasurementsEvent.Callback} object to register.
     * @param handler the handler that the callback runs on.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback,
            Handler handler) {
        return mGnssMeasurementCallbackTransport.add(callback, handler);
    }

    /**
     * No-op method to keep backward-compatibility.
     * Don't use it. Use {@link #unregisterGnssMeasurementsCallback} instead.
     * @hide
     * @deprecated use {@link #unregisterGnssMeasurementsCallback(GnssMeasurementsEvent.Callback)}
     * instead.
     */
    @Deprecated
    @SystemApi
    public void removeGpsMeasurementListener(GpsMeasurementsEvent.Listener listener) {
    }

    /**
     * Unregisters a GPS Measurement callback.
     *
     * @param callback a {@link GnssMeasurementsEvent.Callback} object to remove.
     */
    public void unregisterGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback) {
        mGnssMeasurementCallbackTransport.remove(callback);
    }

    /**
     * No-op method to keep backward-compatibility.
     * Don't use it. Use {@link #registerGnssNavigationMessageCallback} instead.
     * @hide
     * @deprecated Not supported anymore.
     */
    @Deprecated
    @SystemApi
    public boolean addGpsNavigationMessageListener(GpsNavigationMessageEvent.Listener listener) {
        return false;
    }

    /**
     * No-op method to keep backward-compatibility.
     * Don't use it. Use {@link #unregisterGnssNavigationMessageCallback} instead.
     * @hide
     * @deprecated use {@link #unregisterGnssNavigationMessageCallback(GnssMeasurements.Callback)}
     * instead
     */
    @Deprecated
    @SystemApi
    public void removeGpsNavigationMessageListener(GpsNavigationMessageEvent.Listener listener) {
    }

    /**
     * Registers a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessageEvent.Callback} object to register.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     * @removed
     */
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessageEvent.Callback callback) {
        return registerGnssNavigationMessageCallback(callback, null);
    }

    /**
     * Registers a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessageEvent.Callback} object to register.
     * @param handler the handler that the callback runs on.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     * @removed
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssNavigationMessageCallback(
            final GnssNavigationMessageEvent.Callback callback, Handler handler) {
        GnssNavigationMessage.Callback bridge = new GnssNavigationMessage.Callback() {
            @Override
            public void onGnssNavigationMessageReceived(GnssNavigationMessage message) {
                GnssNavigationMessageEvent event = new GnssNavigationMessageEvent(message);
                callback.onGnssNavigationMessageReceived(event);
            }

            @Override
            public void onStatusChanged(int status) {
                callback.onStatusChanged(status);
            }
        };
        mNavigationMessageBridge.put(callback, bridge);
        return mGnssNavigationMessageCallbackTransport.add(bridge, handler);
    }

    /**
     * Unregisters a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessageEvent.Callback} object to remove.
     * @removed
     */
    public void unregisterGnssNavigationMessageCallback(
            GnssNavigationMessageEvent.Callback callback) {
        mGnssNavigationMessageCallbackTransport.remove(
                mNavigationMessageBridge.remove(
                        callback));
    }

    /**
     * Registers a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to register.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     */
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback) {
        return registerGnssNavigationMessageCallback(callback, null);
    }

    /**
     * Registers a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to register.
     * @param handler the handler that the callback runs on.
     * @return {@code true} if the callback was added successfully, {@code false} otherwise.
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback, Handler handler) {
        return mGnssNavigationMessageCallbackTransport.add(callback, handler);
    }

    /**
     * Unregisters a GNSS Navigation Message callback.
     *
     * @param callback a {@link GnssNavigationMessage.Callback} object to remove.
     */
    public void unregisterGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback) {
        mGnssNavigationMessageCallbackTransport.remove(callback);
    }

    /**
     * Retrieves information about the current status of the GPS engine.
     * This should only be called from the {@link GpsStatus.Listener#onGpsStatusChanged}
     * callback to ensure that the data is copied atomically.
     *
     * The caller may either pass in a {@link GpsStatus} object to set with the latest
     * status information, or pass null to create a new {@link GpsStatus} object.
     *
     * @param status object containing GPS status details, or null.
     * @return status object containing updated GPS status.
     */
    @Deprecated
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public GpsStatus getGpsStatus(GpsStatus status) {
        if (status == null) {
            status = new GpsStatus();
        }
        // When mGnssStatus is null, that means that this method is called outside
        // onGpsStatusChanged().  Return an empty status  to maintain backwards compatibility.
        if (mGnssStatus != null) {
            status.setStatus(mGnssStatus, mTimeToFirstFix);
        }
        return status;
    }

    /**
     * Returns the system information of the GPS hardware.
     * May return 0 if GPS hardware is earlier than 2016.
     * @hide
     */
    @TestApi
    public int getGnssYearOfHardware() {
        try {
            return mService.getGnssYearOfHardware();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sends additional commands to a location provider.
     * Can be used to support provider specific extensions to the Location Manager API
     *
     * @param provider name of the location provider.
     * @param command name of the command to send to the provider.
     * @param extras optional arguments for the command (or null).
     * The provider may optionally fill the extras Bundle with results from the command.
     *
     * @return true if the command succeeds.
     */
    public boolean sendExtraCommand(String provider, String command, Bundle extras) {
        try {
            return mService.sendExtraCommand(provider, command, extras);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Used by NetInitiatedActivity to report user response
     * for network initiated GPS fix requests.
     *
     * @hide
     */
    public boolean sendNiResponse(int notifId, int userResponse) {
        try {
            return mService.sendNiResponse(notifId, userResponse);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private static void checkProvider(String provider) {
        if (provider == null) {
            throw new IllegalArgumentException("invalid provider: " + provider);
        }
    }

    private static void checkCriteria(Criteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("invalid criteria: " + criteria);
        }
    }

    private static void checkListener(LocationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("invalid listener: " + listener);
        }
    }

    private void checkPendingIntent(PendingIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("invalid pending intent: " + intent);
        }
        if (!intent.isTargetedToPackage()) {
            IllegalArgumentException e = new IllegalArgumentException(
                    "pending intent must be targeted to package");
            if (mContext.getApplicationInfo().targetSdkVersion > Build.VERSION_CODES.JELLY_BEAN) {
                throw e;
            } else {
                Log.w(TAG, e);
            }
        }
    }

    private static void checkGeofence(Geofence fence) {
        if (fence == null) {
            throw new IllegalArgumentException("invalid geofence: " + fence);
        }
    }
}
