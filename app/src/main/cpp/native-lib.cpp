#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring

JNICALL
Java_kobayashi_taku_com_fuukeiar_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
