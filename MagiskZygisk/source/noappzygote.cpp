#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <sys/prctl.h>
#include <android/log.h>
#include "zygisk.hpp"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "NoAppZygote", __VA_ARGS__)

class NoAppZygote : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        (void)api;
        this->env = env;

        char name[16] = {};
        prctl(PR_GET_NAME, name);
        if (strncmp(name, "app_zygote", 10) == 0) {
            LOGD("onLoad: detected app_zygote via prctl, exiting");
            _exit(0);
        }
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        if (args->is_child_zygote && *args->is_child_zygote) {
            should_kill = true;
            if (args->nice_name) {
                const char *name = env->GetStringUTFChars(args->nice_name, nullptr);
                LOGD("preAppSpecialize: blocking app_zygote for %s", name);
                env->ReleaseStringUTFChars(args->nice_name, name);
            } else {
                LOGD("preAppSpecialize: blocking app_zygote");
            }
        } else {
            should_kill = false;
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        (void)args;
        if (should_kill) {
            LOGD("postAppSpecialize: killing app_zygote");
            _exit(0);
        }
    }

private:
    JNIEnv *env;
    bool should_kill = false;
};

REGISTER_ZYGISK_MODULE(NoAppZygote)
