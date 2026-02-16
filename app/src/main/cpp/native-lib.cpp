#include <jni.h>
#include "whisper.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_speech2text_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

    return env->NewStringUTF(whisper_print_system_info());
}
