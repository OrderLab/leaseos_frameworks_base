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

import java.util.Iterator;
import java.util.List;

/**
 * Utilities for string-related definitions and methods.
 */
public class StringUtils {
    public static String formatDuration(long duration) {
        return formatDuration(duration, TimeUtils.DURATION_PRECISIONS);
    }

    public static String formatDuration(long duration, int decs) {
        if (duration < TimeUtils.MILLIS_PER_SECOND)
            return duration + "ms";
        else if (duration < TimeUtils.MILLIS_PER_MINUTE)
            return formatDouble((double) duration / TimeUtils.MILLIS_PER_SECOND, decs) + "s";
        else if (duration < TimeUtils.MILLIS_PER_HOUR)
            return formatDouble((double) duration / TimeUtils.MILLIS_PER_MINUTE, decs) + "m";
        else if (duration < TimeUtils.MILLIS_PER_DAY)
            return formatDouble((double) duration / TimeUtils.MILLIS_PER_HOUR, decs) + "h";
        else if (duration < TimeUtils.MILLIS_PER_WEEK)
            return formatDouble((double) duration / TimeUtils.MILLIS_PER_DAY, decs) + "d";
        else
            return formatDouble((double) duration / TimeUtils.MILLIS_PER_WEEK, decs) + "w";
    }

    public static String formatDouble(double value, int decs) {
        return String.format("%." + decs + "f", value);
    }

    public static boolean arrayContains(String[] array, String target, boolean ignoreCase) {
        for (String element:array) {
            if (ignoreCase) {
                if (element.equalsIgnoreCase(target))
                    return true;
            } else {
                if (element.equals(target))
                    return true;
            }
        }
        return false;
    }

    public static String joinList(List<?> list, String seperator) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> iterator = list.iterator();
        while (iterator.hasNext()) {
            sb.append(iterator.next().toString());
            if (iterator.hasNext()) {
                sb.append(seperator);
            }
        }
        return sb.toString();
    }
}