    
/*
 *  @author Yigong Hu <hyigong1@cs.jhu.edu>
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

import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class LeaseSettingsUtils {

    public static String setToWhiteList(Set<String> pkgSet) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (String pkg:pkgSet) {
            sb.append(sep).append(pkg);
            sep = LeaseSettings.WHITELIST_DELIM;
        }
        return sb.toString();
    }

    public static Set<String> whitelistToSet(String whiteList) {
        String [] pkgs = whiteList.split(LeaseSettings.WHITELIST_DELIM);
        HashSet<String> pkgSet = new HashSet<String>();
        for (String pkg : pkgs) {
            pkgSet.add(pkg);
        }
        return pkgSet;
    }

    public static LeaseSettings readLeaseSettingsLocked(final ContentResolver resolver) {
        LeaseSettings settings = new LeaseSettings();
        settings.serviceEnabled = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LEASE_SERVICE_ENABLED,
                LeaseSettings.SERVICE_ENABLED_DEFAULT ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);

        settings.whiteList = Settings.Secure.getStringForUser(resolver,
                Settings.Secure.LEASE_WHITELIST,
                UserHandle.USER_CURRENT);
        // If not present, set default white list
        if (settings.whiteList == null)
            settings.whiteList = LeaseSettings.WHITE_LIST_DEFAULT;

        settings.rateLimitWindow = Settings.Secure.getLongForUser(resolver,
                Settings.Secure.LEASE_RATE_LIMIT_WINDOW,
                LeaseSettings.RATE_LIMIT_WINDOW_DEFAULT,
                UserHandle.USER_CURRENT);
        settings.gcWindow = Settings.Secure.getLongForUser(resolver,
                Settings.Secure.LEASE_GC_WINDOW,
                LeaseSettings.GC_WINDOW_DEFAULT,
                UserHandle.USER_CURRENT);

        settings.wakelockLeaseEnabled = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LEASEOS_WAKELOCK_LEASE_ENABLED,
                LeaseSettings.WAKELOCK_LEASE_ENABLED ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        settings.gpsLeaseEnabled = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LEASEOS_LOCATION_LEASE_ENABLED,
                LeaseSettings.LOCATION_LEASE_ENABLED ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        settings.sensorLeaseEnabled = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LEASEOS_SENSOR_LEASE_ENABLED,
                LeaseSettings.SENSOR_LEASE_ENABLED ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        
        return settings;
    }

    public static void writeServiceEnabled(boolean enabled, ContentResolver resolver) {
        Settings.Secure.putInt(resolver,
                Settings.Secure.LEASE_SERVICE_ENABLED, enabled ? 1 : 0);
    }

    public static void writeWhiteList(String whiteList, ContentResolver resolver) {
        Settings.Secure.putString(resolver,
                Settings.Secure.LEASE_WHITELIST, whiteList);
    }

    public static void writeRateLimitWindow(long rateLimitWindow, ContentResolver resolver) {
        Settings.Secure.putLong(resolver,
                Settings.Secure.LEASE_RATE_LIMIT_WINDOW, rateLimitWindow);
    }

    public static void writeGCWindow(long gcWindow, ContentResolver resolver) {
        Settings.Secure.putLong(resolver,
                Settings.Secure.LEASE_GC_WINDOW, gcWindow);
    }

    public static void writeWakelockLeaseEnabled(boolean enabled, ContentResolver resolver) {
        Settings.Secure.putInt(resolver,
                Settings.Secure.LEASEOS_WAKELOCK_LEASE_ENABLED, enabled ? 1 : 0);
    }

    public static void writeLocationLeaseEnabled(boolean enabled, ContentResolver resolver) {
        Settings.Secure.putInt(resolver,
                Settings.Secure.LEASEOS_LOCATION_LEASE_ENABLED, enabled ? 1 : 0);
    }

    public static void writeSensorLeaseEnabled(boolean enabled, ContentResolver resolver) {
        Settings.Secure.putInt(resolver,
                Settings.Secure.LEASEOS_SENSOR_LEASE_ENABLED, enabled ? 1 : 0);
    }

}