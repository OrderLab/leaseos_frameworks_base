    
/*
 *  @author Yigong HU <hyigong1@jhu.edu>
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

import java.util.ArrayList;

/**
 *
 */
public class StatHistory <T> {
    protected ArrayList<T> mStats;


    StatHistory() {
        mStats = new ArrayList<T>();
    }

    public boolean setResourceStat(T resourceStat) {
       return mStats.add(resourceStat);
    }

    public void remove() {
        mStats.clear();
    }

    //TODO:need to implement the judge behavior part
    public BehaviorType judgeHistory() {
        return BehaviorType.FrequencyAsking;
    }



}