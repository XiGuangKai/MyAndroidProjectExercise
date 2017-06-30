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
#include <string.h>
#include <errno.h>

#include <hardware/lights.h>
#include <hardware/hardware.h>

#include <stdio.h>

#if 0
int set_charger_led_bak(int value)
{
    int ret = 6; 
    int fd = -1;
    int                          ioctl_status = -1;
    enum  cpcap_reg              reg_enum_value = CPCAP_REG_CRM;
    struct cpcap_regacc          pwr_ic_access_rdwr_spi;
    if(access("/dev/cpcap" , R_OK|W_OK) != 0)
    {
         ret = 1;
    }
    else
    {     
        fd = open("/dev/cpcap", O_RDWR);
        if (fd < 0)
        {
            ret = 2;
        }
        else
        {
            pwr_ic_access_rdwr_spi.reg = reg_enum_value;
            pwr_ic_access_rdwr_spi.value = value;
            pwr_ic_access_rdwr_spi.mask = 0x2000;

            ioctl_status = ioctl(fd, CPCAP_IOCTL_TEST_WRITE_REG, &pwr_ic_access_rdwr_spi);
            if(ioctl_status != 0)
            {
                ret = 3;
            } 
            close(fd);
        } 
    }
    return ret;
} 
#endif

int  set_led(int id,int value)
{
    int ret = 0;
    int err = -1;

    hw_module_t* module;
    hw_device_t* device;

    struct light_device_t* light_device;
    struct light_state_t state;

    err = hw_get_module(LIGHTS_HARDWARE_MODULE_ID, (hw_module_t const**)&module);
    if (err == 0)
    {
        if(id == LIGHT_INDEX_BATTERY) 
        { 
            err = module->methods->open(module, LIGHT_ID_BATTERY, &device);
        } 
        else if(id == LIGHT_INDEX_BUTTONS)
        {
            err = module->methods->open(module, LIGHT_ID_BUTTONS, &device);  
        }  
        else if(id == LIGHT_INDEX_BACKLIGHT)
        {
            err = module->methods->open(module, LIGHT_ID_BACKLIGHT, &device);
        }
        if(err == 0)
        {

               light_device = (struct light_device_t *)device;
               if(value == 0x01)
               {
               state.color = 0x00ffffff;
               } 
               else
               {
                  state.color = 0x0;
               } 
               state.flashMode = LIGHT_FLASH_NONE;
               state.flashOnMS = 0;
               state.flashOffMS = 0;
               state.brightnessMode = 0;
               err = light_device->set_light(light_device, &state);
               if(err == 0)
               {
                   ret = 3; 
               }
               else
               {
                   ret = 4;
               } 
        }  
        else
        {
            ret = 2;
        }

    }
    else
    {
        ret = 1;
    }
    return ret;
}
