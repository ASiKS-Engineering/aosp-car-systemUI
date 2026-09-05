/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.platform.test.annotations.DisableFlags;
import android.test.mock.MockContext;
import android.util.Singleton;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.car.oem.tokens.Token;
import com.android.systemui.Flags;
import com.android.systemui.car.shared.R;

import org.junit.Rule;
import org.mockito.Mockito;

import java.util.concurrent.Executor;

//TODO(b/430358177) Re-enable this flag
@DisableFlags(Flags.FLAG_SCENE_CONTAINER)
public class CarSysuiTestCase extends SysuiTestCase {

    @Rule
    public SysuiTestableContext mContext = createTestableContext();

    private SysuiTestableContext createTestableContext() {
        SysuiTestableContext context = new SysuiTestableContext(
                getTestableContextBase(), getLeakCheck());

        if (isRobolectricTest()) {
            // Manually associate a Display to context for Robolectric test. Similar to b/214297409
            SysuiTestableContext displayContext = context.createDefaultDisplayContext();
            Token.applyOemTokenStyle(displayContext);
            displayContext.getTheme().applyStyle(R.style.CarSystemUIThemeOverlay, true);
            return displayContext;
        } else {
            return context;
        }
    }

    @NonNull
    private Context getTestableContextBase() {
        if (isRavenwoodTest()) {
            // TODO(b/292141694): build out Ravenwood support for Context
            // Ravenwood doesn't yet provide a Context, but many SysUI tests assume one exists;
            // so here we construct just enough of a Context to be useful; this will be replaced
            // as more of the Ravenwood environment is built out
            return new MockContext() {
                @Override
                public void setTheme(int resid) {
                    // TODO(b/318393625): build out Ravenwood support for Resources
                    // until then, ignored as no-op
                }

                @Override
                public Resources getResources() {
                    // TODO(b/318393625): build out Ravenwood support for Resources
                    return Mockito.mock(Resources.class);
                }

                private Singleton<Executor> mMainExecutor = new Singleton<>() {
                    @Override
                    protected Executor create() {
                        return new HandlerExecutor(new Handler(Looper.getMainLooper()));
                    }
                };

                @Override
                public Executor getMainExecutor() {
                    return mMainExecutor.get();
                }
            };
        } else {
            return InstrumentationRegistry.getContext().getApplicationContext();
        }
    }

    @Override
    public SysuiTestableContext getContext() {
        return mContext;
    }
}
