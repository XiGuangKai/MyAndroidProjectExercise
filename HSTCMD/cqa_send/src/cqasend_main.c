/*==================================================================================================

    Module Name:  cqasend_main.c

    General Description: This file implements the communication with CommServer of CQATest apk

====================================================================================================

                              Motorola Confidential Proprietary
                    (c) Copyright Motorola 2012 All Rights Reserved

Revision History:
                            Modification     Tracking
Author                          Date          Number       Description of Changes
-------------------------   ------------    ----------     -----------------------------------------
Min  Dong  - cqd487         2012/12/07      IKMAIN-49320   Support loop test and wait time functions
Min  Dong  - cqd487         2012/10/17      IKMAIN-49270   Creation

====================================================================================================
                                            INCLUDE FILES
==================================================================================================*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/poll.h>
#include <cutils/log.h>
#include <private/android_filesystem_config.h>
#include <sys/socket.h>
#include <cutils/sockets.h>
#include <pthread.h>
#include <time.h>

/*==================================================================================================
                                           LOCAL CONSTANTS
==================================================================================================*/

/*==================================================================================================
                                            LOCAL MACROS
==================================================================================================*/

#define CQASEND_CMD_MAX_LEN               100       /* Maximum command length */
#define CQASEND_RSP_TYPE_MAX_LEN          20        /* Maximum rsp type length */
#define CQASEND_SEQ_TAG_LEN               10        /* 10 bytes for hex string */
#define CQASEND_BUFFER_MAX_LEN            1024 * 5  /* Maximum reading buffer length */

#define CQASEND_CMD_DISCONNECT            "disconnect"
#define CQASEND_CMD_WAIT                  "WAIT"
#define CQASEND_HEART_BEAT_MSG            "MESSAGE=HEY PC YOU THERE?"
#define CQASEND_HEART_BEAT_ACK_MSG        "&#14;YEAH I AM HERE&#14;"

#define CQASEND_CONNECT_MAX_TRY           10        /* Number of times to try to connect to server*/
#define CQASEND_POLL_TIMEOUT              200
#define CQASEND_HEART_BEAT_INTERVAL_MS    10000
#define CQASEND_SOLICITED_RSP_TIMEOUT_S   10
#define CQASEND_LAUNCH_COMMSERVER         "am startservice -a com.motorola.motocit.LocalCommServer"
#define CQASEND_STOP_COMMSERVER           "am force-stop com.motorola.motocit"
#define CQASEND_OUTPUT_FILE               "/data/local/12m/cqa_send.log"


#ifndef LOGD
    #define LOGD ALOGD
#endif

#define CQASEND_DBG_TRACE(x ...) \
    do \
    { \
      if (cqasend_main_debug == true) \
      { \
          LOGD("CQA_SEND: "x); \
      } \
    } while (0)

#define CQASEND_PRINTF_INFO(x ...) \
    do \
    { \
        while(cqasend_main_printf_lock) \
        { \
            usleep(1);\
        } \
        cqasend_main_printf_lock = true; \
        printf(x); \
        printf("\n"); \
        if (cqasend_main_out_file_fp != NULL) \
        { \
            fprintf(cqasend_main_out_file_fp, x); \
            fprintf(cqasend_main_out_file_fp, "\n"); \
        } \
        cqasend_main_printf_lock = false; \
    } while (0)

/*==================================================================================================
                             LOCAL TYPEDEFS (STRUCTURES, UNIONS, ENUMS)
==================================================================================================*/

typedef struct
{
    char     seq_tag[CQASEND_SEQ_TAG_LEN+1];
    char     cmd[CQASEND_CMD_MAX_LEN];
    char     data[CQASEND_BUFFER_MAX_LEN];
}CQASEND_SEND_DATA_PACKET_T;

typedef struct
{
    char     seq_tag[CQASEND_SEQ_TAG_LEN+1];
    char     rsp_type[CQASEND_RSP_TYPE_MAX_LEN];
    char     cmd[CQASEND_CMD_MAX_LEN];
    char     data[CQASEND_BUFFER_MAX_LEN];
}CQASEND_RCV_DATA_PACKET_T;

typedef enum
{
    PING,
    STOP,
    STOP_ALL,
    START,
    TELL,
    LOG_DEBUG,
    REBOOT,
    HELP
}CQASEND_SERVER_CMD_TYPE_T;

typedef enum
{
    ACK,
    NACK,
    INFO,
    UNSOLICITED,
    UNKNOWN
}CQASEND_SERVER_RSP_TYPE_T;

static char *cqasend_main_server_cmd_type[] = { "PING", "STOP", "STOP_ALL", "START", "TELL",
                                                "LOG_DEBUG", "REBOOT", "HELP" };

static char *cqasend_main_server_rsp_type[] = { "ACK", "NACK", "INFO", "UNSOLICITED", "UNKNOWN" };
/*==================================================================================================
                                          GLOBAL VARIABLES
==================================================================================================*/

/*==================================================================================================
                                          LOCAL VARIABLES
==================================================================================================*/
static FILE *cqasend_main_in_file_fp = NULL;
static FILE *cqasend_main_out_file_fp = NULL;
static int   cqasend_main_fd_socket = -1;
static int   cqasend_main_loop_count = 0;
static int   cqasend_main_wait_time;
static bool  cqasend_main_debug = false;
static bool  cqasend_main_connected_server = false;
static bool  cqasend_main_socket_lock = false;
static bool  cqasend_main_printf_lock = false;
static bool  cqasend_main_solicited_rsp_complete = true;
static bool  cqasend_main_execute_single_cmd = false;
static bool  cqasend_main_execute_disconnect = false;
static long  cqasend_main_last_heartbeat_time;
static long  cqasend_main_current_time;
static long  cqasend_main_send_packet_time;
static long  cqasend_main_rcv_packet_time;
static unsigned int cqasend_main_solicited_seq_tag = 0x10000000;
static CQASEND_SEND_DATA_PACKET_T cqasend_main_send_data_packet;
static CQASEND_RCV_DATA_PACKET_T  cqasend_main_rcv_data_packet;

/*==================================================================================================
                                      LOCAL FUNCTION PROTOTYPES
==================================================================================================*/
static void  cqasend_main_connect_server(void);
static void* cqasend_main_send_req_handler(void* buffer);
static void* cqasend_main_rcv_rsp_handler(void* thread_args);
static int   cqasend_main_send_req_process(FILE *data_in);
static int   cqasend_main_rcv_rsp(char *data);
static bool  cqasend_main_process_raw_command (char *command);
static bool  cqasend_main_respond_heart_beat(void);
static bool  cqasend_main_cmd_check(char *cmd);
static void  cqasend_main_split_string(char *command, int *offset, int *len);
static void  cqasend_main_stop_server(void);

/*===================================================================================================*/
/*==================================================================================================
                                          GLOBAL FUNCTIONS
==================================================================================================*/

int main(int argc, char **argv)
{
    pthread_t     tid1;
    pthread_t     tid2;
    void         *tret;
    int           argnum = 1;
    char          buffer[CQASEND_BUFFER_MAX_LEN] = {0};
    unsigned  int offset = 0;

    if (getuid() != AID_ROOT)
    {
        printf ("You must run this tool as ROOT user!\n");
        exit (1);
    }

    printf ("Enter cqa send main application\n");

    printf ("+++ Reading options...\n");
    while ((argc > 1) && (*argv[argnum] == '-'))
    {
        switch (argv[argnum][1])
        {
            case 'f':
                if(argc < argnum + 1)
                {
                    printf ("No filename\n");
                    exit (1);
                }

                argnum++;
                cqasend_main_in_file_fp = fopen (argv[argnum], "r");
                if (cqasend_main_in_file_fp == NULL)
                {
                    printf ("-- Unable to open file '%s': %s\n", argv[argnum], strerror(errno));
                    exit (1);
                }
                break;

            case 'l':
                cqasend_main_debug = true;
                break;

            case 'o':
                cqasend_main_out_file_fp = fopen (CQASEND_OUTPUT_FILE, "w+");
                if (cqasend_main_out_file_fp == NULL)
                {
                    printf("Opening file %s failed, errno = %d (%s)", CQASEND_OUTPUT_FILE, errno, strerror(errno));
                    exit (1);
                }
                break;

            case 'i':
                if(argc < argnum + 2)
                {
                    printf ("no command\n");
                    exit (1);
                }

                argnum++;
                while (argnum < argc)
                {
                    memcpy(buffer + offset, argv[argnum], strlen(argv[argnum]));
                    offset += strlen(argv[argnum]);
                    memcpy(buffer + offset," ", 1);
                    offset++;
                    argnum++;
                }

                cqasend_main_execute_single_cmd = true;
                break;

            case 'c':
                if(argc < argnum + 1)
                {
                    printf ("No loop count\n");
                    exit (1);
                }
                else
                {
                    argnum++;
                    cqasend_main_loop_count = atoi(argv[argnum]);
                }
                break;

            case 'h':
                printf ("Usage: %s [-h] | [-o] [-l] | [-o] [-l] [-f file_name] [-c loop count]| [-o] [-l] [-i command]\n", argv[0]);
                printf ("\t\t-h  - help info \n");
                printf ("\t\t-l  - enable debug trace log\n");
                printf ("\t\t-o  - output test result to /data/local/12m/cqa_send.log\n");
                printf ("\t\t-f  filename - read commands from file \n");
                printf ("\t\t-i  command - execute one command at a time \n");
                printf ("\t\t-c  loop count - used for loop test. Only applicable for command execution from file\n");
                exit (0);
                break;

            default:
                printf ("-- Unknown option '%s'\n", argv[argnum]);
                exit (1);
        }

        argnum++;
        if (argnum >= argc)
        {
            break;
        }
    }

    CQASEND_PRINTF_INFO("+++ Establishing connection with CommServer...\n");
    cqasend_main_connect_server();

    CQASEND_PRINTF_INFO("+++ Processing commands...\n\n");

    if (pthread_create(&tid1, NULL, cqasend_main_send_req_handler, (void *)buffer) != 0)
    {
        CQASEND_PRINTF_INFO("Error creating thread send req handler.");
        exit(0);
    }
    else
    {
        CQASEND_DBG_TRACE("Created client thread (%d) to handle connection socket: %d",
                     (int)tid1, cqasend_main_fd_socket);
    }

    if (pthread_create(&tid2, NULL, cqasend_main_rcv_rsp_handler, NULL) != 0)
    {
        CQASEND_PRINTF_INFO("Error creating client thread rcv rsp handler.");
        exit(0);
    }
    else
    {
        CQASEND_DBG_TRACE("Created client thread (%d) to handle connection socket: %d",
                     (int)tid2, cqasend_main_fd_socket);
    }

    if (pthread_join(tid1, &tret))
    {
        CQASEND_PRINTF_INFO("can't join with thread send\n");
    }

    if (pthread_join(tid2, &tret))
    {
        CQASEND_PRINTF_INFO("can't join with thread rcv\n");
    }

    CQASEND_PRINTF_INFO("\n+++--------------------------------------+++\n");

    if (cqasend_main_fd_socket > 0)
    {
        CQASEND_DBG_TRACE("+++ close socket\n");
        shutdown(cqasend_main_fd_socket, SHUT_RDWR);
        close(cqasend_main_fd_socket);

        if (cqasend_main_execute_disconnect == true)
        {
            cqasend_main_stop_server();
            CQASEND_PRINTF_INFO("+++ Disconnected connection with CommServer\n");
        }
    }

    CQASEND_PRINTF_INFO("+++ Done\n");

    if (cqasend_main_out_file_fp != NULL)
    {
        fflush(cqasend_main_out_file_fp);
        fsync(fileno(cqasend_main_out_file_fp));
        fclose(cqasend_main_out_file_fp);
        cqasend_main_out_file_fp = NULL;
    }

    return (0);
}

/*==================================================================================================
                                          LOCAL FUNCTIONS
==================================================================================================*/
/*=============================================================================================*//**
@brief Establish connection with CommServer

*//*==============================================================================================*/
void cqasend_main_connect_server(void)
{
    FILE*  f = NULL;
    char   buf[512] = {0};
    int    try_count = 1;
    bool   launch_server = false;

    CQASEND_DBG_TRACE("+++ Establishing connection with CommServer...");
    cqasend_main_connected_server = false;
    do
    {
        cqasend_main_fd_socket = socket_local_client( "local_commserver",
                              ANDROID_SOCKET_NAMESPACE_ABSTRACT,
                              SOCK_STREAM );
        if (cqasend_main_fd_socket < 0)
        {
            CQASEND_DBG_TRACE("Connect to CommServer try %d fails", try_count);
            if(try_count == CQASEND_CONNECT_MAX_TRY)
            {
                CQASEND_DBG_TRACE(" -- Unable to establish connection with CommServer after try %d times",
                          try_count);
                cqasend_main_stop_server();
                exit(0);
            }
            else
            {
                // Try to connect again
                if (launch_server != true)
                {
                    launch_server = true;
                    if ((f = popen(CQASEND_LAUNCH_COMMSERVER, "r")) == NULL)
                    {
                        CQASEND_DBG_TRACE("failed to run: %s", CQASEND_LAUNCH_COMMSERVER);
                    }
                    else
                    {
                        /* dump the output */
                        while (fgets(buf, sizeof(buf), f))
                        {
                            CQASEND_DBG_TRACE("%s", buf);
                        }
                    }
                }
                try_count++;
                sleep(1);
            }
        }
        else
        {
            CQASEND_DBG_TRACE ("Connect to CommServer successfully\n");
            cqasend_main_connected_server = true;
        }

    }while(cqasend_main_fd_socket < 0);
}

/*=============================================================================================*//**
@brief Force stop CommServer service

*//*==============================================================================================*/
void cqasend_main_stop_server(void)
{
    int sys_ret;

    sys_ret = system(CQASEND_STOP_COMMSERVER);
    CQASEND_DBG_TRACE("Executed stop server script, sys_ret = %d", sys_ret);
    if (!WIFEXITED(sys_ret))
    {
        CQASEND_DBG_TRACE("Failed to execute stop server script, did not end correctly");
    }
    else if (WEXITSTATUS(sys_ret) != 0)
    {
        CQASEND_DBG_TRACE("Failed to execute stop server script, return = %d",
                       WEXITSTATUS(sys_ret));
    }
    else
    {
        CQASEND_DBG_TRACE("Successfully executed stop server script");
    }
}

/*=============================================================================================*//**
@brief Thread to handle request

@return  = NULL
*//*==============================================================================================*/
void* cqasend_main_send_req_handler(void* buffer)
{
    bool  ret = false;
    int   err = 0;
    int   loop_count = 1;

    CQASEND_DBG_TRACE("enter cqasend_main_send_req_handler");
    if (cqasend_main_fd_socket < 0)
    {
        CQASEND_DBG_TRACE("error socket fd, cqasend_main_fd_socket= %d", cqasend_main_fd_socket);
    }
    else
    {
        if (cqasend_main_execute_single_cmd == true)
        {
            // Execute command from adb shell
            cqasend_main_solicited_seq_tag++;
            CQASEND_PRINTF_INFO("\n-->Sending command: \n %s\n", (char*)buffer);
            if (strcasestr(buffer, CQASEND_CMD_DISCONNECT) != NULL)
            {
                CQASEND_PRINTF_INFO(CQASEND_CMD_DISCONNECT);
                CQASEND_DBG_TRACE("disconnect command received");
                cqasend_main_connected_server = false;
                cqasend_main_execute_disconnect = true;
            }
            else
            {
                cqasend_main_solicited_rsp_complete = false;
                ret = cqasend_main_process_raw_command((char*)buffer);
                if (ret != true)
                {
                    CQASEND_DBG_TRACE("Failed to send data: %s", (char*)buffer);
                }
                else
                {
                    // Wait for rsp complete
                    while (cqasend_main_solicited_rsp_complete == false)
                    {
                        usleep(200000);
                    }
                }

                cqasend_main_connected_server = false;
            }
        }
        else if (cqasend_main_in_file_fp != NULL)
        {
            do
            {
                if (cqasend_main_loop_count > 0)
                {
                    cqasend_main_loop_count--;
                    CQASEND_PRINTF_INFO("+++--------------------------------------+++");
                    CQASEND_PRINTF_INFO("+++            TEST LOOP %d               +++", loop_count);
                    CQASEND_PRINTF_INFO("+++--------------------------------------+++");
                    CQASEND_DBG_TRACE("TEST LOOP: %d", loop_count);
                    loop_count++;
                }

                // Execute commands from file
                while (cqasend_main_connected_server == true)
                {
                    // set default wait time between commands
                    cqasend_main_wait_time = 500000;

                    if (cqasend_main_solicited_rsp_complete == true)
                    {
                        err = cqasend_main_send_req_process(cqasend_main_in_file_fp);
                        if (err != 0)
                        {
                            break;
                        }
                    }

                    //It is important to add sleep.
                    usleep(cqasend_main_wait_time);
                }

                fseek(cqasend_main_in_file_fp, 0, SEEK_SET);
            }while (cqasend_main_loop_count > 0);

            fclose (cqasend_main_in_file_fp);
            cqasend_main_in_file_fp = NULL;
        }
        else
        {
            while (cqasend_main_connected_server == true)
            {
                if (cqasend_main_solicited_rsp_complete == true)
                {
                    CQASEND_PRINTF_INFO("\n+++--------------------------------------+++\n");
                    CQASEND_PRINTF_INFO("+++ Please input CQA CommServer command:\n");

                    err = cqasend_main_send_req_process(stdin);
                    if( err != 0)
                    {
                        CQASEND_DBG_TRACE("cqasend_main_send_req_process err or disconnect server");
                        break;
                    }
                }

                //It is important to add sleep.
                usleep(20000);
            }
        }
    }

    return NULL;
}
/*=============================================================================================*//**
@brief Process send request

@param[in]   data_in  - Input handler

@return  Status of operation
*//*==============================================================================================*/
int cqasend_main_send_req_process(FILE *data_in)
{
    char   buffer[CQASEND_BUFFER_MAX_LEN] = {0};
    char   first_str[50] = {0};
    bool   ret = false;
    int    err = 0;
    int    retry_count = 0;

    CQASEND_DBG_TRACE("enter cqasend_main_send_req_process");

    if (fgets (buffer, CQASEND_BUFFER_MAX_LEN, data_in) != NULL)
    {
        if (cqasend_main_connected_server != true)
        {
            CQASEND_DBG_TRACE("Thread rcv stoped. Exit send thread");
        }
        else
        {
            sscanf(buffer, "%s", first_str);
            if (*first_str == 0)
            {
                CQASEND_DBG_TRACE("no command data");
            }
            else if (*first_str == '#')
            {
                CQASEND_DBG_TRACE("comments, skip it.");
            }
            else
            {
                if (cqasend_main_in_file_fp != NULL)
                {
                    CQASEND_PRINTF_INFO("\n+++--------------------------------------+++\n");
                    CQASEND_PRINTF_INFO("+++ Reading CQA CommServer command from file\n");
                }

                cqasend_main_solicited_rsp_complete = false;

                if (buffer [strlen(buffer)-1] == '\n')
                {
                    buffer [strlen(buffer)-1] = 0;
                }

                if (strcasestr(buffer, CQASEND_CMD_DISCONNECT) != NULL)
                {
                    CQASEND_PRINTF_INFO(CQASEND_CMD_DISCONNECT);
                    CQASEND_DBG_TRACE("disconnect command received");

                    if (cqasend_main_loop_count == 0)
                    {
                        cqasend_main_connected_server = false;
                        cqasend_main_execute_disconnect = true;
                    }

                    cqasend_main_solicited_rsp_complete = true;
                    err = -1;
                }
                else if (strcasestr(buffer, CQASEND_CMD_WAIT) != NULL)
                {
                    sscanf(buffer, "%*s %s", first_str);
                    cqasend_main_wait_time = atoi(first_str) * 1000;
                    CQASEND_PRINTF_INFO("WAIT:  %dms", cqasend_main_wait_time/1000);
                    CQASEND_DBG_TRACE("WAIT: %dus", cqasend_main_wait_time);
                    cqasend_main_solicited_rsp_complete = true;
                }
                else
                {
                    cqasend_main_solicited_seq_tag++;
                    do
                    {
                        CQASEND_PRINTF_INFO("\n-->Sending command: \n %s\n", buffer);
                        ret = cqasend_main_process_raw_command(buffer);
                        if(ret != true)
                        {
                            cqasend_main_connect_server();
                        }
                    }while(ret !=true && retry_count++ < 3);

                    if(ret != true)
                    {
                        CQASEND_DBG_TRACE("Failed to send data: %s, retry: %d ", buffer, retry_count);
                        cqasend_main_connected_server = false;
                        cqasend_main_execute_disconnect = true;
                        err = -1;
                    }
                }
            }
        }
    }

    return err;
}

/*=============================================================================================*//**
@brief Process tcmd raw command

@param[in]   command       - The raw command string pointer

@return  Status of operation
*//*==============================================================================================*/
bool cqasend_main_process_raw_command (char *command)
{
    char   data[CQASEND_BUFFER_MAX_LEN] = {0};
    char   seq_str[CQASEND_SEQ_TAG_LEN + 1] = {0};
    char   activity_name[100] = {0};
    int    offset = 0;
    int    len = 0;
    int    ret = 0;
    bool   is_success = false;
    struct timeval current_time;

    CQASEND_DBG_TRACE("-->Sending command: %s", command);

    memset(&cqasend_main_send_data_packet, 0 , sizeof(CQASEND_SEND_DATA_PACKET_T));
    sscanf(command, "%s", cqasend_main_send_data_packet.cmd);

    // check if the command is valid.
    if (cqasend_main_cmd_check(cqasend_main_send_data_packet.cmd) != true)
    {
        CQASEND_PRINTF_INFO("\n-->FAILED: Invalid CommServer CMD: %s\n", cqasend_main_send_data_packet.cmd);
        CQASEND_DBG_TRACE("-->FAILED: Invalid CommServer CMD: %s", cqasend_main_send_data_packet.cmd);
        cqasend_main_solicited_rsp_complete = true;
        is_success = true;
    }
    else
    {
        CQASEND_DBG_TRACE("valid command: %s", cqasend_main_send_data_packet.cmd);

        // build send packet
        sprintf(cqasend_main_send_data_packet.seq_tag, "0x%x", cqasend_main_solicited_seq_tag);

        if (strcasecmp(cqasend_main_send_data_packet.cmd, cqasend_main_server_cmd_type[TELL]) == 0)
        {
            sscanf(command, "%*s %s %s", activity_name, cqasend_main_send_data_packet.cmd);
        }

        //can check activity name here if needed
        sprintf(data, "%s %s\n", cqasend_main_send_data_packet.seq_tag, command);
        CQASEND_DBG_TRACE("cmd: %s", cqasend_main_send_data_packet.cmd);
        CQASEND_DBG_TRACE("command data:  %s", data);

        while (cqasend_main_socket_lock == true)
        {
            usleep(5);
        }

        cqasend_main_socket_lock = true;

        len = strlen(data);
        do
        {
            ret = write(cqasend_main_fd_socket, data, len);
        } while (ret < 0 && errno == EINTR);
        cqasend_main_socket_lock = false;

        if (ret != len)
        {
            CQASEND_DBG_TRACE("Fail to send command to Server: %s ", strerror(errno));
            close(cqasend_main_fd_socket);
            cqasend_main_fd_socket = -1;
        }
        else
        {
            is_success = true;
            CQASEND_DBG_TRACE("successfully write command data to socket ");
            //get time
            gettimeofday(&current_time, NULL);
            cqasend_main_send_packet_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
        }
    }

    return is_success;
}
/*=============================================================================================*//**
@brief Thread to handle response

@return  = NULL
*//*==============================================================================================*/
void* cqasend_main_rcv_rsp_handler(void* thread_args)
{
    char   data[CQASEND_BUFFER_MAX_LEN] = {0};
    char  *split_data;
    int    len =0;
    int    offset = 0;
    bool   ret = false;
    struct timeval current_time;
    int    retry_count = 0;
    int    heartbeat_retry_count = 0;

    if(cqasend_main_fd_socket < 0)
    {
        CQASEND_DBG_TRACE("error socket fd: %d", cqasend_main_fd_socket);
    }
    else
    {
        // init timers
        gettimeofday(&current_time, NULL);
        cqasend_main_last_heartbeat_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
        cqasend_main_current_time = cqasend_main_last_heartbeat_time;

        while (cqasend_main_connected_server == true)
        {
            // receive data from socket
            usleep(5000);
            memset(data, 0, CQASEND_BUFFER_MAX_LEN);

            // Wait for response
            ret = cqasend_main_rcv_rsp(data);
            if(ret != 0)
            {
                CQASEND_DBG_TRACE("socket fd error.");
            }
            else if (*data == 0)
            {
                CQASEND_DBG_TRACE("no data");
                if (cqasend_main_solicited_rsp_complete == false)
                {
                    //Exepect a solicited response
                    gettimeofday(&current_time, NULL);
                    cqasend_main_rcv_packet_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
                    if (((int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) / 1000) > CQASEND_SOLICITED_RSP_TIMEOUT_S)
                    {
                        CQASEND_PRINTF_INFO("-->FAILED: timeout to get response.");
                        CQASEND_DBG_TRACE("-->FAILED: timeout to get response.");
                        CQASEND_PRINTF_INFO("\n+++Executed Time: %d.%ds,      time stamp = %d",
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) / 1000,
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) % 1000,
                                                (int)(cqasend_main_solicited_seq_tag - 0x10000000));
                        cqasend_main_solicited_rsp_complete = true;
                    }
                }
            }
            else
            {
                CQASEND_DBG_TRACE("-->Received data:\n %s", data);
                memset(&cqasend_main_rcv_data_packet, 0, sizeof(CQASEND_RCV_DATA_PACKET_T));
                sscanf(data, "%s %s %s %[^\0]", cqasend_main_rcv_data_packet.seq_tag, cqasend_main_rcv_data_packet.rsp_type,
                                        cqasend_main_rcv_data_packet.cmd, cqasend_main_rcv_data_packet.data);

                CQASEND_DBG_TRACE("-->seq tag: %s, ", cqasend_main_rcv_data_packet.seq_tag);
                CQASEND_DBG_TRACE("-->rsp_type: %s, ", cqasend_main_rcv_data_packet.rsp_type);
                CQASEND_DBG_TRACE("-->cmd: %s", cqasend_main_rcv_data_packet.cmd);
                CQASEND_DBG_TRACE("-->data: %s", cqasend_main_rcv_data_packet.data);

                //check seq tag
                if (strncasecmp(cqasend_main_send_data_packet.seq_tag, cqasend_main_rcv_data_packet.seq_tag, CQASEND_SEQ_TAG_LEN) == 0)
                {
                    //check ack
                    if(strcasecmp(cqasend_main_server_rsp_type[ACK], cqasend_main_rcv_data_packet.rsp_type) == 0)
                    {
                        if(strcasecmp(cqasend_main_send_data_packet.cmd, cqasend_main_rcv_data_packet.cmd) == 0)
                        {
                            CQASEND_DBG_TRACE("-->Received solicited ack data: %s", cqasend_main_rcv_data_packet.data);
                            CQASEND_DBG_TRACE("-->CMD returned passed\n");
                            CQASEND_PRINTF_INFO("\n-->CMD returned passed\n");
                            cqasend_main_solicited_rsp_complete = true;

                            //get time
                            gettimeofday(&current_time, NULL);
                            cqasend_main_rcv_packet_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
                            CQASEND_PRINTF_INFO("\n+++Executed Time: %d.%ds,      time stamp = %d",
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) / 1000,
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) % 1000,
                                                (int)(cqasend_main_solicited_seq_tag - 0x10000000));
                        }
                        else
                        {
                            CQASEND_DBG_TRACE("-->Received unsolicited ack data: %s\n", cqasend_main_rcv_data_packet.data);
                        }
                    }
                    else if(strcasecmp(cqasend_main_server_rsp_type[NACK], cqasend_main_rcv_data_packet.rsp_type) == 0)
                    {
                        if(strcasecmp(cqasend_main_send_data_packet.cmd, cqasend_main_rcv_data_packet.cmd) == 0)
                        {
                            CQASEND_PRINTF_INFO("\n-->FAILED: CMD returned failed\n");

                            if (strlen(cqasend_main_rcv_data_packet.data) != 0)
                            {
                                CQASEND_PRINTF_INFO("Error info: %s\n", cqasend_main_rcv_data_packet.data);
                            }

                            CQASEND_DBG_TRACE("-->Received solicited nack data: %s\n", cqasend_main_rcv_data_packet.data);
                            cqasend_main_solicited_rsp_complete = true;

                            //get time
                            gettimeofday(&current_time, NULL);
                            cqasend_main_rcv_packet_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
                            CQASEND_PRINTF_INFO("\n+++Executed Time: %d.%ds,      time stamp = %d",
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) / 1000,
                                                (int)(cqasend_main_rcv_packet_time - cqasend_main_send_packet_time) % 1000,
                                                (int)(cqasend_main_solicited_seq_tag - 0x10000000));
                        }
                        else
                        {
                            CQASEND_DBG_TRACE("-->Received unsolicited nack data: %s\n", cqasend_main_rcv_data_packet.data);
                        }
                    }
                    else if(strcasecmp(cqasend_main_server_rsp_type[INFO], cqasend_main_rcv_data_packet.rsp_type) == 0)
                    {
                        CQASEND_PRINTF_INFO("\n-->Received info data:\n");
                        offset = 0;
                        len = 0;
                        split_data = cqasend_main_rcv_data_packet.data;
                        while (*split_data != 0)
                        {
                            memset(data, 0 , sizeof(data));
                            cqasend_main_split_string(split_data, &offset, &len);

                            if(*(split_data + offset) == '\"')
                            {
                                memcpy(data, split_data + offset + 1, len - 1);
                            }
                            else
                            {
                                memcpy(data, split_data + offset, len);
                            }

                            CQASEND_PRINTF_INFO("    %s", data);
                            split_data = split_data + offset + len;
                        }
                    }
                    else
                    {
                        CQASEND_DBG_TRACE("\n-->Received unknown data: %s\n", data);
                    }
                }
                else
                {
                    CQASEND_DBG_TRACE("--> recieved unsolicited seq tag \n");
                    if(strcasecmp(cqasend_main_server_rsp_type[UNSOLICITED], cqasend_main_rcv_data_packet.rsp_type) == 0)
                    {
                        if(strcasecmp(cqasend_main_server_cmd_type[PING], cqasend_main_rcv_data_packet.cmd) == 0)
                        {
                            if (strstr(data, CQASEND_HEART_BEAT_MSG) != NULL)
                            {
                                CQASEND_DBG_TRACE("\n-->Received heart beat message: %s\n", data);
                                gettimeofday(&current_time, NULL);
                                cqasend_main_last_heartbeat_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;

                                if ((ret = cqasend_main_respond_heart_beat()) != true)
                                {
                                    CQASEND_DBG_TRACE("Fail to respond beart beat.");
                                    cqasend_main_connected_server = false;
                                    break;
                                }
                            }
                        }
                        else
                        {
                            CQASEND_DBG_TRACE("--> recieved unsolicited data: %s", data);
                        }
                    }
                }
            }

            //check heart beat
            gettimeofday(&current_time, NULL);
            cqasend_main_current_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
            if ((cqasend_main_current_time - cqasend_main_last_heartbeat_time) > CQASEND_HEART_BEAT_INTERVAL_MS)
            {
               // something wrong with heart beat. retry and disconnect connection.
                if(heartbeat_retry_count++ < 3)
                {
                    CQASEND_DBG_TRACE(" retry to reconnect server");
                    cqasend_main_connect_server();

                    // init timers
                    gettimeofday(&current_time, NULL);
                    cqasend_main_last_heartbeat_time = ((long)current_time.tv_sec)*1000+(long)current_time.tv_usec/1000;
                    cqasend_main_current_time = cqasend_main_last_heartbeat_time;
                }
                else
                {
                    CQASEND_DBG_TRACE("connection error. exit rcv thread.");
                    cqasend_main_connected_server = false;
                    cqasend_main_execute_disconnect = true;
                }
            }
        }
    }

    return NULL;
}

/*=============================================================================================*//**
@brief Receive data from CommServer

@param[out]   data       - The pointer for data buffer

@return  Status of operation
*//*==============================================================================================*/
int cqasend_main_rcv_rsp(char *data)
{
    struct        pollfd fds[1];
    bool          is_success  = false;
    int           rsp_size;
    int           err         = 0;
    int           ret         = 0;
    unsigned int  retry_count = 1;

    CQASEND_DBG_TRACE("cqasend_main_rcv_rsp\n");
    if (cqasend_main_fd_socket < 0)
    {
        err = -1;
        CQASEND_DBG_TRACE("invalid socket fd: %d\n",cqasend_main_fd_socket);
    }
    else
    {
        CQASEND_DBG_TRACE("cqasend_main_rcv_rsp: reading data\n");
        fds[0].fd = cqasend_main_fd_socket;
        fds[0].events = POLLIN | POLLHUP;
        fds[0].revents = 0;

        ret = poll(fds, 1, CQASEND_POLL_TIMEOUT);
        CQASEND_DBG_TRACE("poll returns: %d, retry_count: %d\n", ret, retry_count);
        if (ret > 0)
        {
            if (fds[0].revents & POLLIN)
            {
                rsp_size = recv(cqasend_main_fd_socket, data, CQASEND_BUFFER_MAX_LEN, 0);
                CQASEND_DBG_TRACE("data Pollin ");
                if (rsp_size < 0)
                {
                    CQASEND_DBG_TRACE("fail to read data\n");
                }
                else if(rsp_size == 0)
                {
                    CQASEND_DBG_TRACE("remote socket closed\n");
                }
                else
                {
                    is_success = true;
                }
            }
        }
    }

    CQASEND_DBG_TRACE("is_success: %d \n", is_success);
    return err;
}

/*=============================================================================================*//**
@brief Respond heart beat

@return  Status of operation
*//*==============================================================================================*/
bool cqasend_main_respond_heart_beat(void)
{
    char   data[CQASEND_BUFFER_MAX_LEN] = {0};
    int    len = 0;
    int    ret = 0;
    bool   is_success = false;

    // build heart beat packet
    sprintf(data, "%s %s %s %s\n", cqasend_main_rcv_data_packet.seq_tag, cqasend_main_server_rsp_type[ACK],
                       cqasend_main_server_cmd_type[PING], CQASEND_HEART_BEAT_ACK_MSG);

    CQASEND_DBG_TRACE("-->Respond heart beat: %s", data);
    len = strlen(data);
    while (cqasend_main_socket_lock == true)
    {
        usleep(5);
    }

    cqasend_main_socket_lock = true;
    do
    {
        ret = write(cqasend_main_fd_socket, data, len);
    } while (ret < 0 && errno == EINTR);
    cqasend_main_socket_lock = false;

    if (ret != len)
    {
        CQASEND_DBG_TRACE("Fail to respond heart beat: %s ", strerror(errno));
        close(cqasend_main_fd_socket);
        cqasend_main_fd_socket = -1;
    }
    else
    {
        is_success = true;
        CQASEND_DBG_TRACE("successfully respond heart beat");
    }

    return is_success;
}
/*=============================================================================================*//**
@brief Check if the command is valid

@param[in]   cmd     - CommServer command

@return true = valid, false = invalid
*//*==============================================================================================*/
bool cqasend_main_cmd_check(char *cmd)
{
    bool is_valid = false;
    int i;
    for(i = PING; i <= HELP; i++)
    {
        if (strcasecmp(cqasend_main_server_cmd_type[i], cmd) == 0)
        {
            is_valid = true;
            break;
        }
    }

    return is_valid;
}

/*=============================================================================================*//**
@brief Split string

@param[in]    data       - The string pointer
@param[out]   offset     - The offset of the first non space character
@param[out]   len        - The length of first string
*//*==============================================================================================*/
void cqasend_main_split_string(char *data, int *offset, int *len)
{
    char *p1 = data;
    char *p2;
    int   len1 = 0;
    int   len2 = 0;

    *offset = 0;
    *len = 0;

    while(*p1 != 0)
    {
        if(*p1++ != ' ')
        {
            break;
        }
        len1++;
    }

    *offset = len1;
    if(*(p1 - 1) != 0)
    {
        p2=p1;
        len2 = len1 + 1;
        while(*p2 != 0)
        {
            if(*(p1 - 1) == '\"')
            {
                if(*p2++ == '\"')
                {
                    break;
                }
            }
            else
            {
                if(*p2++ == ' ')
                {
                    break;
                }
            }
            len2++;
        }

        *len = len2 - len1;
    }
}
