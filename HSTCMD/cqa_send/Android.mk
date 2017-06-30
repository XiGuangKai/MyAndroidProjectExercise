#===================================================================================================
#
#   Module Name:  Android.mk
#
#   General Description: Android Makefile for cqa send application
#
#===================================================================================================
#
#                             Motorola Confidential Proprietary
#                     (c) Copyright Motorola 2012, All Rights Reserved
#
# Revision History:
#                             Modification     Tracking
# Author                          Date          Number     Description of Changes
# -------------------------   ------------    ----------   -----------------------------------------
#Min  Dong  - cqd487         2012/10/17      IKMAIN-49270   Creation

ifeq ($(strip $(filter sdk generic full, $(TARGET_PRODUCT))),)

ifeq ($(strip $(filter sdk_addon blur_sdk mcts, $(MAKECMDGOALS))),)

#===================================================
# TCMD SEND application Config
#===================================================
LOCAL_PATH:= $(call my-dir)

#Only build for engineering/userdebug builds
ifneq ($(filter userdebug eng, $(TARGET_BUILD_VARIANT)),)

#===================================================
# CQA_SEND executable
#===================================================
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := libcutils liblog

LOCAL_MODULE := cqa_send
LOCAL_MODULE_TAGS := eng debug


LOCAL_SRC_FILES := \
    src/cqasend_main.c

include $(BUILD_EXECUTABLE)

endif #end of ifeq ($(TARGET_BUILD_VARIANT), eng)

endif

endif
