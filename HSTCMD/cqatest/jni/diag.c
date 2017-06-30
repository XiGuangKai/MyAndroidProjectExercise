#include <string.h>
#include <errno.h>
#include <jni.h>
#include <sys/ioctl.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <android/log.h>

#define READ_TIMEOUT 10 // 10 seconds

#define DIAG_IOCTL_SWITCH_LOGGING   7
#define DIAG_IOCTL_LSM_DEINIT       9
#define DIAG_IOCTL_REMOTE_DEV      32

#define DIAG_MD_NONE			0
#define DIAG_MD_NORMAL			1
#define DIAG_MD_PERIPHERAL		2

#define USB_MODE                1
#define MEMORY_DEVICE_MODE      2
#define NO_LOGGING_MODE         3
#define UART_MODE               4
#define SOCKET_MODE             5
#define CALLBACK_MODE           6
#define TTY_MODE                8

/* List of processors */
#define DIAG_ALL_PROC		-1
#define DIAG_MODEM_PROC		0
#define DIAG_LPASS_PROC		1
#define DIAG_WCNSS_PROC		2
#define DIAG_SENSORS_PROC	3
#define NUM_PERIPHERALS		4
#define DIAG_APPS_PROC		(NUM_PERIPHERALS)

#define DIAG_CON_APSS		(0x0001)	/* Bit mask for APSS */
#define DIAG_CON_MPSS		(0x0002)	/* Bit mask for MPSS */
#define DIAG_CON_LPASS		(0x0004)	/* Bit mask for LPASS */
#define DIAG_CON_WCNSS		(0x0008)	/* Bit mask for WCNSS */
#define DIAG_CON_SENSORS	(0x0010)	/* Bit mask for Sensors */
#define DIAG_CON_NONE		(0x0000)	/* Bit mask for No SS*/
#define DIAG_CON_ALL		(DIAG_CON_APSS | DIAG_CON_MPSS \
				| DIAG_CON_LPASS | DIAG_CON_WCNSS \
				| DIAG_CON_SENSORS)


// For pack type ???
#define MSG_MASKS_TYPE       0x00000001
#define LOG_MASKS_TYPE       0x00000002
#define EVENT_MASKS_TYPE     0x00000004
#define PKT_TYPE             0x00000008
#define DEINIT_TYPE          0x00000010
#define USER_SPACE_DATA_TYPE 0x00000020
#define DCI_DATA_TYPE        0x00000040
#define CALLBACK_DATA_TYPE   0x00000080
#define DCI_LOG_MASKS_TYPE   0x00000100
#define DCI_EVENT_MASKS_TYPE 0x00000200
#define DCI_PKT_TYPE         0x00000400

struct diag_logging_mode_param_t {
	uint32_t req_mode;
	uint32_t peripheral_mask;
	uint8_t mode_param;
};

static int diag_fd = -1;

static void timeout_handler(int sig)
{
    __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Timeout waiting for response\n");
    // De-init fd to wake up read thread
    if(!ioctl(diag_fd, DIAG_IOCTL_LSM_DEINIT, NULL, 0, NULL, 0, NULL, NULL)) {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: DIAG de-init failed.");
    }
}

JNIEXPORT int JNICALL
Java_com_motorola_desense_DiagSocket_openSocket( JNIEnv* env, jobject obj )
{
    //int mode = MEMORY_DEVICE_MODE;
    int mode = SOCKET_MODE; // TODO: Works now? Test with GNSS

    struct diag_logging_mode_param_t params;
	params.req_mode = mode;
	params.mode_param = DIAG_MD_PERIPHERAL;
	params.peripheral_mask = DIAG_CON_ALL;

    if ((diag_fd = open("/dev/diag", O_RDWR)) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Failed to open the diag device.\n");
        return -1;
    }

    // struct is 8996+, but works with other modems due to req_mode being first in struct
    if(ioctl(diag_fd, DIAG_IOCTL_SWITCH_LOGGING, &params, sizeof(struct diag_logging_mode_param_t), NULL, 0, NULL, NULL) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Failed to switch the logging mode, errno: %d\n", errno);
        return -2;
    }

    /*if(ioctl(diag_fd, DIAG_IOCTL_SWITCH_LOGGING, &mode, 0, NULL, 0, NULL, NULL) < 0) {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Failed to switch the logging mode, errno: %d\n", errno);
        return -2;
    }*/

    signal(SIGALRM, timeout_handler);

    return 0;
}

JNIEXPORT void JNICALL
Java_com_motorola_desense_DiagSocket_closeSocket( JNIEnv* env, jobject obj )
{
    if(!ioctl(diag_fd, DIAG_IOCTL_LSM_DEINIT, NULL, 0, NULL, 0, NULL, NULL)) {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: DIAG de-init failed.");
    }

    close(diag_fd);
    diag_fd = -1;

    signal(SIGALRM, SIG_DFL);
}

JNIEXPORT int JNICALL
Java_com_motorola_desense_DiagSocket_receive( JNIEnv* env, jobject obj, jbyteArray data )
{
    jbyte* buf = (*env)->GetByteArrayElements(env, data, NULL);
    int length = (*env)->GetArrayLength(env, data);

    alarm(READ_TIMEOUT);
    int read_bytes = read(diag_fd, (void *)buf, length);
    alarm(0); // cancel alarm

    //if(read_bytes >= 0) __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "ftm: Read %d bytes.\n", read_bytes);

    //int i;
    //for(i = 0; i < read_bytes; i++)
    //    __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "ftm: Read byte %02x.\n", buf[i]);

    if(read_bytes < 0)
    {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: DIAG read failed, return val: %d, ERRNO: %d\n", read_bytes, errno);
        (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
        return read_bytes;
    }

    if(*(int*)buf != USER_SPACE_DATA_TYPE) // If we get a non user-space log packet, just drop it.
    {
        //__android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Read non-user-space packet type 0x%02x.\n", *(int*)buf);
        (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
        return 0;
    }

    (*env)->ReleaseByteArrayElements(env, data, buf, 0);
    return read_bytes;
}

// NOTE: mask_request_validate function in diagchar_core.c only allows certain packet types to be sent:
/*
0x00:    // Version Number
0x0C:    // CDMA status packet
0x1C:    // Diag Version
0x1D:    // Time Stamp
0x60:    // Event Report Control
0x63:    // Status snapshot
0x73:    // Logging Configuration
0x7C:    // Extended build ID
0x7D:    // Extended Message configuration
0x81:    // Event get mask
0x82:    // Set the event mask
*/
JNIEXPORT int JNICALL
Java_com_motorola_desense_DiagSocket_send( JNIEnv* env, jobject obj, jbyteArray data )
{
    jbyte* buf = (*env)->GetByteArrayElements(env, data, NULL);
    int length = (*env)->GetArrayLength(env, data);

    int num = write(diag_fd, (void *)buf, length);
    if(num < 0) __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: DIAG write return val: %d, ERRNO: %d\n", num, errno);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return num;
}


/*==========================================================================
FUNCTION   diag_has_remote_device

DESCRIPTION
  This function queries the kernel to determine if the target device has a
  remote device or not.
  remote_mask - 0, if the target does not have a remote device; otherwise
	a bit mask representing remote channels supported by the target
===========================================================================*/
JNIEXPORT int JNICALL
Java_com_motorola_desense_DiagSocket_hasRemoteDevice( JNIEnv* env, jobject obj )
{
    //int diag_has_remote_device(uint16 *remote_mask)
    jshort remote_mask = 0;

	if(ioctl(diag_fd, DIAG_IOCTL_REMOTE_DEV, &remote_mask, 0, NULL, 0, NULL, NULL) < 0)
    {
        __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: Failed to check diag remote device, errno: %d\n", errno);
        return -1;
    }

    __android_log_print(ANDROID_LOG_VERBOSE, "DSENSE", "FTM: remote mask: %d\n", (int)remote_mask);

    return (int)remote_mask;
}

