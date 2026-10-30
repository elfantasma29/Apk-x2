package com.ivona.tts;

import android.content.Context;
import android.content.pm.PackageInfo;

final class BuildInfo {

    private BuildInfo() {
    }

    static long version(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.getLongVersionCode();
        } catch (Throwable t) {
            return 0;
        }
    }

    static String nombreVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
}
