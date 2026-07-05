#include <unistd.h>
#include <android/log.h>
#include "zygisk.hpp"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "NoAppZygote", __VA_ARGS__)

class NoAppZygote : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        (void)api;
        this->env = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        if (args->is_child_zygote && *args->is_child_zygote) {
            should_kill = true;
            if (args->nice_name) {
                const char *name = env->GetStringUTFChars(args->nice_name, nullptr);
                LOGD("Blocking app_zygote spawn for %s", name);
                env->ReleaseStringUTFChars(args->nice_name, name);
            } else {
                LOGD("Blocking app_zygote spawn");
            }
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        (void)args;
        if (should_kill) {
            should_kill = false;
            LOGD("Killing app_zygote process");
            _exit(0);
        }
    }

private:
    JNIEnv *env;
    bool should_kill = false;
};

REGISTER_ZYGISK_MODULE(NoAppZygote)
