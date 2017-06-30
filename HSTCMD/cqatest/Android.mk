LOCAL_PATH:= $(call my-dir)
# ============================================================
# Build CQATest from source by default
BUILD_CQATEST_FROM_SOURCE ?= true

# ============================================================
# com.motorola.android.tcmd.xml
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := com.motorola.android.tcmd.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

# ============================================================
# com.motorola.permission.diag.xml
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := com.motorola.permission.diag.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

# ============================================================
# Build CQATest.apk from source

ifeq ($(BUILD_CQATEST_FROM_SOURCE),true)

include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME := CQATest
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MULTILIB := both
LOCAL_CERTIFICATE := common
LOCAL_DEX_PREOPT := false
LOCAL_JNI_SHARED_LIBRARIES := libdesense
LOCAL_PRIVILEGED_MODULE := true
LOCAL_STATIC_JAVA_LIBRARIES := \
    libcidgdrive \
    libzxing \
    libfingerprintcqa \
    libandroidsupportv4 \
    libmods-static \
    liblua \
    focaltech_test \
    focaltech_comm \
    com.fingerprints.fingerprintengineering

LOCAL_REQUIRED_MODULES := \
    com.motorola.android.tcmd.xml \
    com.motorola.permission.diag.xml \
    com.fingerprints.fingerprintengineering

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libcidgdrive:libs/MotCidGDrive.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libzxing:libs/core.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libfingerprintcqa:libs/synaptics-sys.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libandroidsupportv4:libs/android-support-v4.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += libmods-static:libs/modlib-static.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += liblua:libs/luaj-jse-3.0.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += focaltech_test:libs/com.focaltech.tp.test.jar
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += focaltech_comm:libs/com.focaltech.tp.comm.jar

include $(BUILD_MULTI_PREBUILT)
include $(call all-makefiles-under,$(LOCAL_PATH))

else
# ============================================================
# Use pre-built CQATest.apk
include $(CLEAR_VARS)
LOCAL_MODULE := CQATest
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_REQUIRED_MODULES := com.motorola.android.tcmd.xml \
                          com.motorola.permission.diag.xml \
                          com.fingerprints.fingerprintengineering
include $(BUILD_PREBUILT)

endif
