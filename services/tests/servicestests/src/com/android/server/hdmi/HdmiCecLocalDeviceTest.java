/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.server.hdmi;

import static android.hardware.hdmi.HdmiDeviceInfo.DEVICE_TV;
import static com.android.server.hdmi.Constants.ADDR_BROADCAST;
import static com.android.server.hdmi.Constants.ADDR_TV;
import static com.android.server.hdmi.Constants.ADDR_UNREGISTERED;
import static com.android.server.hdmi.Constants.MESSAGE_DEVICE_VENDOR_ID;
import static com.android.server.hdmi.Constants.MESSAGE_REPORT_PHYSICAL_ADDRESS;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.hardware.hdmi.HdmiPortInfo;
import android.os.test.TestLooper;
import android.support.test.filters.SmallTest;
import android.os.MessageQueue;
import com.android.server.hdmi.HdmiCecController.NativeWrapper;
import junit.framework.Assert;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SmallTest
@RunWith(JUnit4.class)
/**
 * Tests for {@link HdmiCecLocalDevice} class.
 */
public class HdmiCecLocalDeviceTest {

    private static final class NativeWrapperImpl implements NativeWrapper {

        @Override
        public long nativeInit(HdmiCecController handler, MessageQueue messageQueue) {
            return 1L;
        }

        @Override
        public int nativeSendCecCommand(long controllerPtr, int srcAddress, int dstAddress,
            byte[] body) {
            return SendCecCommandFactory(srcAddress, dstAddress, body);
        }

        @Override
        public int nativeAddLogicalAddress(long controllerPtr, int logicalAddress) {
            return 0;
        }

        @Override
        public void nativeClearLogicalAddress(long controllerPtr) {

        }

        @Override
        public int nativeGetPhysicalAddress(long controllerPtr) {
            return mPhysicalAddr;
        }

        @Override
        public int nativeGetVersion(long controllerPtr) {
            return 0;
        }

        @Override
        public int nativeGetVendorId(long controllerPtr) {
            return 0;
        }

        @Override
        public HdmiPortInfo[] nativeGetPortInfos(long controllerPtr) {
            return new HdmiPortInfo[0];
        }

        @Override
        public void nativeSetOption(long controllerPtr, int flag, boolean enabled) {

        }

        @Override
        public void nativeSetLanguage(long controllerPtr, String language) {

        }

        @Override
        public void nativeEnableAudioReturnChannel(long controllerPtr, int port, boolean flag) {

        }

        @Override
        public boolean nativeIsConnected(long controllerPtr, int port) {
            return false;
        }
    }

    private static int SendCecCommandFactory(int srcAddress, int dstAddress, byte[] body) {
        switch(body[0] & 0xFF) {
            /** {@link Constants#MESSAGE_GIVE_PHYSICAL_ADDRESS} */
            case MESSAGE_REPORT_PHYSICAL_ADDRESS:
            case MESSAGE_DEVICE_VENDOR_ID:
                return srcAddress == mSrcAddr &&
                    dstAddress == mDesAddr &&
                    Arrays.equals(Arrays.copyOfRange(body, 1, body.length), param)? 0 : 1;
            default:
                return 1;
        }
    }

    private class MyHdmiCecLocalDevice extends HdmiCecLocalDevice {


        protected MyHdmiCecLocalDevice(HdmiControlService service, int deviceType) {
            super(service, deviceType);
        }

        @Override
        protected void onAddressAllocated(int logicalAddress, int reason) {

        }

        @Override
        protected int getPreferredAddress() {
            return 0;
        }

        @Override
        protected void setPreferredAddress(int addr) {

        }
    }

    private MyHdmiCecLocalDevice mHdmiLocalDevice;
    private HdmiControlService mHdmiControlService;
    private HdmiCecController mHdmiCecController;
    private TestLooper mTestLooper = new TestLooper();
    private static int mDesAddr = -1;
    private static int mSrcAddr = -1;
    private static int mPhysicalAddr = 2;
    private int callbackResult;
    private HdmiCecMessageValidator mMessageValidator;
    private static byte[] param;

    @Before
    public void SetUp() {
        mHdmiControlService = new HdmiControlService(null);
        mHdmiControlService.setIoLooper(mTestLooper.getLooper());
        mHdmiCecController = HdmiCecController.createWithNativeWrapper(
            mHdmiControlService, new NativeWrapperImpl());
        mHdmiControlService.setCecController(mHdmiCecController);
        mHdmiLocalDevice = new MyHdmiCecLocalDevice(
            mHdmiControlService, DEVICE_TV);
        mMessageValidator = new HdmiCecMessageValidator(mHdmiControlService){
            @Override
            int isValid(HdmiCecMessage message) {
                return HdmiCecMessageValidator.OK;
            }
        };
        mHdmiControlService.setMessageValidator(mMessageValidator);
    }

    @Test
    public void dispatchMessage_desNotValid() {
        HdmiCecMessage msg = new HdmiCecMessage(
            ADDR_TV, ADDR_TV, Constants.MESSAGE_CEC_VERSION, HdmiCecMessage.EMPTY_PARAM);
        boolean handleResult = mHdmiLocalDevice.dispatchMessage(msg);
        assertFalse(handleResult);
    }

    @Test
    public void handleGivePhysicalAddress_success() {
        mSrcAddr = ADDR_UNREGISTERED;
        mDesAddr = ADDR_BROADCAST;
        param = new byte[] {
            (byte) ((mPhysicalAddr >> 8) & 0xFF),
            (byte) (mPhysicalAddr & 0xFF),
            (byte) (DEVICE_TV & 0xFF)
        };
        callbackResult = -1;
        boolean handleResult = mHdmiLocalDevice.handleGivePhysicalAddress(
            (int finalResult) -> callbackResult = finalResult);
        mTestLooper.dispatchAll();
        /**
         * Test if CecMessage is sent successfully
         * SendMessageResult#SUCCESS is defined in HAL as 0
         */
        assertEquals(0, callbackResult);
        assertTrue(handleResult);
    }

    @Test
    public void handleGiveDeviceVendorId_success() {
        mSrcAddr = ADDR_UNREGISTERED;
        mDesAddr = ADDR_BROADCAST;
        /** nativeGetVendorId returns 0 */
        param = new byte[] {
            (byte) ((0 >> 8) & 0xFF),
            (byte) (0 & 0xFF),
            (byte) (0 & 0xFF)
        };
        callbackResult = -1;
        mHdmiLocalDevice.handleGiveDeviceVendorId(
            (int finalResult) -> callbackResult = finalResult);
        mTestLooper.dispatchAll();
        assertEquals(0, callbackResult);
    }
}
