    
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

    public static LeaseSettings readLeaseSettingsLocked(final ContentResolver resolver) {
        LeaseSettings settings = new LeaseSettings();
        settings.serviceEnabled = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LEASE_SERVICE_ENABLED,
                LeaseSettings.SERVICE_ENABLED_DEFAULT ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        return settings;
    }

    public static void writeServiceEnabled(boolean enabled, ContentResolver resolver) {
        Settings.Secure.putInt(resolver,
                Settings.Secure.LEASE_SERVICE_ENABLED, enabled ? 1 : 0);
    }



}