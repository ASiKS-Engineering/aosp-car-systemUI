/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.ndo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Factory for creating view models with additional parameters.
 */
@SysUISingleton
public class NdoViewModelFactory implements ViewModelProvider.Factory {

    private final Context mContext;
    private final InCallServiceManager mServiceManager;
    private final MediaSessionHelper mMediaSessionHelper;

    @Inject
    public NdoViewModelFactory(Context context, InCallServiceManager serviceManager,
            MediaSessionHelper mediaSessionHelper) {
        mContext = context;
        mServiceManager = serviceManager;
        mMediaSessionHelper = mediaSessionHelper;
    }

    /**
     * Returns a BlockerViewModel.
     */
    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(BlockerViewModel.class)) {
            return (T) new BlockerViewModel(mContext, mServiceManager, mMediaSessionHelper);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
