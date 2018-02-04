    
/*
 *  @author Yigong Hu <hyigong1@jhu.edu>
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


/**
 * which type of resource the lease is related to
 */
public enum ResourceType {
    Wakelock("Wakelock"), // this lease is applied to Powermanager Service
    Location("Location"), // this lease is applied to Locationmanager Service
    Sensor("Sensor");     // this lease is applied to Sensormanager Service

    private String type; // mType of resource

    private ResourceType(String type) {
        this.type = type;
    }

    /**
     * Return the mType of Resource that the lease is related to
     */
    @Override
    public String toString() {
        return type;
    }

    /**
     * Set the mType of Resource that the lease is related to
     */
    public boolean setType(String type) {
        if (type == "Wakelock" || type == "Location" || type == "Sensor") {
            this.type = type;
        } else {
            return false;
        }
        return true;
    }

}
