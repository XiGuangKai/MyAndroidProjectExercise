/*
 * Copyright (C) 2009 The Android Open Source Project
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
 *
 */


#include "led-driver.h"
#include <jni.h>

jint
Java_com_motorola_tcmd_led_Led_setrdon( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_red_on();
}

jint
Java_com_motorola_tcmd_led_Led_setrdoff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_red_off();
}

jint
Java_com_motorola_tcmd_led_Led_setgron( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_green_on();
}

jint
Java_com_motorola_tcmd_led_Led_setgroff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_green_off();
}
jint
Java_com_motorola_tcmd_led_Led_setblon( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_blue_on();
}

jint
Java_com_motorola_tcmd_led_Led_setbloff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led_blue_off();
}

jint
Java_com_motorola_tcmd_led_Led_setchron( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BATTERY, 0x01);
}

jint
Java_com_motorola_tcmd_led_Led_setchroff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BATTERY, 0x0);
}

jint
Java_com_motorola_tcmd_led_Led_setdisplaybacklighton( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BACKLIGHT, 0x01);
}

jint
Java_com_motorola_tcmd_led_Led_setdisplaybacklightoff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BACKLIGHT, 0x0);
}

jint
Java_com_motorola_tcmd_led_Led_setkeybacklighton( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BUTTONS, 0x01);
}

jint
Java_com_motorola_tcmd_led_Led_setkeybacklightoff( JNIEnv*  env,
                                      jobject  this)
{
    return  set_led(LIGHT_INDEX_BUTTONS, 0x0);
}



