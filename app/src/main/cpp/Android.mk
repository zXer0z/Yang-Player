LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := yang_audio
LOCAL_SRC_FILES := yang_audio.c
LOCAL_CFLAGS := -std=c11 -O3 -Wall -Wextra -Werror=implicit-function-declaration
LOCAL_LDLIBS := -laaudio -lOpenSLES -llog -lm -ldl
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
