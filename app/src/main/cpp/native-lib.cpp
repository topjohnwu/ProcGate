#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <jni.h>
#include <android/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "POC", __VA_ARGS__)

static JNIEnv *gEnv;
static jobject gThis;
static jmethodID jAddText;
static bool haveLeak;

static void addText(const char *fmt, ...) {
	char buf[1024];
	va_list args;
	va_start(args, fmt);
	vsprintf(buf, fmt, args);
	va_end(args);
	jstring jstr = gEnv->NewStringUTF(buf);
	gEnv->CallVoidMethod(gThis, jAddText, jstr);
	gEnv->DeleteLocalRef(jstr);
}

static bool isDigit(const char *s) {
	for (const char *c = s; *c; ++c) {
		if (*c < '0' || *c > '9')
			return false;
	}
	return true;
}

static void tryOpen(const char *pid) {
	char buf[128];
	FILE *f;
	struct stat st;
	sprintf(buf, "/proc/%s", pid);
	if (stat(buf, &st))
		return;
	/* Do not print process with same UID */
	if (st.st_uid == getuid())
		return;
	sprintf(buf, "/proc/%s/cmdline", pid);
	if ((f = fopen(buf, "r"))) {
		haveLeak = true;
		if (fgets(buf, sizeof(buf), f) == 0)
			buf[0] = '\0';
		addText("Leak PID=[%s] UID=[%d] cmdline=[%s]\n", pid, st.st_uid, buf);
		fclose(f);
	}
}

extern "C"
JNIEXPORT void JNICALL
Java_com_topjohnwu_procgate_MainActivity_inspectProcFS(JNIEnv *env, jobject _this) {
	gEnv = env;
	gThis = _this;
	jclass clazz = env->GetObjectClass(_this);
	jAddText = env->GetMethodID(clazz, "addText", "(Ljava/lang/String;)V");

	pid_t pid = getpid();
	DIR *procfs = opendir("/proc");
	struct dirent *dir;
	haveLeak = false;
	while ((dir = readdir(procfs))) {
		if (isDigit(dir->d_name) && atoi(dir->d_name) != pid)
			tryOpen(dir->d_name);
	}
	if (!haveLeak)
		addText("No leaks detected!\n");
	closedir(procfs);
}
