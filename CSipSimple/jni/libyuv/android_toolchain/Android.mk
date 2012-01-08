LOCAL_PATH := $(call my-dir)/../files/

include $(CLEAR_VARS)
LOCAL_MODULE    := libyuv
LOCAL_CFLAGS := $(MY_PJSIP_FLAGS)

LOCAL_CPP_EXTENSION := .cc

LOCAL_C_INCLUDES += $(LOCAL_PATH)/include $(LOCAL_PATH)/source

LOCAL_SRC_FILES := source/compare.cc \
	source/convert.cc \
	source/convertfrom.cc \
	source/cpu_id.cc \
	source/format_conversion.cc \
	source/planar_functions.cc \
	source/rotate.cc \
	source/row_common.cc \
	source/scale.cc \
	source/video_common.cc \
	source/row_posix.cc

LOCAL_STATIC_LIBRARIES := cpufeatures

include $(BUILD_STATIC_LIBRARY)

$(call import-module,android/cpufeatures)