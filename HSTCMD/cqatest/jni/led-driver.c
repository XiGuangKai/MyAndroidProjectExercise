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
#include <fcntl.h>


int set_leds_state(const char* file,int value)
{
    int     len;
    int     ret = 6;
    char    buf[20] ;
    int     fd;
    ssize_t amt;
    
    if(access(file, R_OK|W_OK) != 0)
    {
    	 ret = 1;
    }
    
    else
    {
        fd = open(file, O_RDWR);
        if (fd < 0)
        {
            ret = 2;
        }
        else
        {
            len = sprintf(buf, "%d", value);
            amt = write(fd, buf, len);

            if (amt < 0)
            {
                ret = amt;
            }

            close(fd);
        }
     }
    
    return ret;
}

int set_led_red_on()
{
	int ret = 5;
	ret = set_leds_state("/sys/class/leds/red/brightness", 0x77);
	return ret;
}

int set_led_red_off()
{
	int ret;
	ret = set_leds_state("/sys/class/leds/red/brightness",0x0);
	return ret;
}

int set_led_green_on()
{
        int ret = 5;
        ret = set_leds_state("/sys/class/leds/green/brightness", 0x77);
        return ret;
}

int set_led_green_off()
{
        int ret;
        ret = set_leds_state("/sys/class/leds/green/brightness",0x0);
        return ret;
}

int set_led_blue_on()
{
        int ret = 5;
        ret = set_leds_state("/sys/class/leds/blue/brightness", 0x77);
        return ret;
}

int set_led_blue_off()
{
        int ret;
        ret = set_leds_state("/sys/class/leds/blue/brightness",0x0);
        return ret;
}

int set_led_keypad_backlight_on()
{
        int ret;
        ret = set_leds_state("/sys/class/leds/button-backlight/brightness",0x77);
        return ret;

}

int set_led_keypad_backlight_off()
{
        int ret;
        ret = set_leds_state("/sys/class/leds/button-backlight/brightness",0x0);
        return ret;

}


