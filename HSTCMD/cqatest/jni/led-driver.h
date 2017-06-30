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
#ifndef LED-DRIVER_H 
#define LED-DRIVER_H 
enum {
    LIGHT_INDEX_BACKLIGHT = 0,
    LIGHT_INDEX_KEYBOARD = 1,
    LIGHT_INDEX_BUTTONS = 2,
    LIGHT_INDEX_BATTERY = 3,
    LIGHT_INDEX_NOTIFICATIONS = 4,
    LIGHT_INDEX_ATTENTION = 5,
    LIGHT_INDEX_BLUETOOTH = 6,
    LIGHT_INDEX_WIFI = 7,
    // Motorola, w30350, 01/27/11 - IKHALFWWK-252 - Support for CAPS & ALT LED
    LIGHT_INDEX_ALT = 8,
    LIGHT_INDEX_SHIFT = 9,
    // END of IKHALFWWK-252
    LIGHT_COUNT
};

extern int set_leds_state();
extern int set_led_red_on();
extern int set_led_red_off();
extern int set_led_green_on();
extern int set_led_green_off();
extern int set_led_blue_on();
extern int set_led_blue_off();
extern int set_charger_led();
#endif /*LED-DRIVER_H */
