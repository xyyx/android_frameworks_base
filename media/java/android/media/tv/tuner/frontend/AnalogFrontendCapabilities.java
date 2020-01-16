/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.tv.tuner.frontend;

/**
 * Analog Capabilities.
 * @hide
 */
public class AnalogFrontendCapabilities extends FrontendCapabilities {
    private final int mTypeCap;
    private final int mSifStandardCap;

    AnalogFrontendCapabilities(int typeCap, int sifStandardCap) {
        mTypeCap = typeCap;
        mSifStandardCap = sifStandardCap;
    }
    /**
     * Gets type capability.
     */
    @AnalogFrontendSettings.SignalType
    public int getTypeCapability() {
        return mTypeCap;
    }
    /**
     * Gets SIF standard capability.
     */
    @AnalogFrontendSettings.SifStandard
    public int getSifStandardCapability() {
        return mSifStandardCap;
    }
}
