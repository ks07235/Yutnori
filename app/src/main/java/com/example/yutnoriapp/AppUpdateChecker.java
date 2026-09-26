package com.example.yutnoriapp;

import android.content.Context;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;

final class AppUpdateChecker {
    private final AppUpdateManager appUpdateManager;
    private boolean checkInFlight;

    AppUpdateChecker(Context context) {
        appUpdateManager = AppUpdateManagerFactory.create(context.getApplicationContext());
    }

    void check(Listener listener) {
        if (checkInFlight) {
            return;
        }
        checkInFlight = true;

        try {
            appUpdateManager.getAppUpdateInfo().addOnSuccessListener(info -> {
                checkInFlight = false;
                if (shouldPrompt(info, BuildConfig.VERSION_CODE)) {
                    listener.onUpdateAvailable(info.availableVersionCode());
                }
            }).addOnFailureListener(error -> checkInFlight = false);
        } catch (RuntimeException error) {
            checkInFlight = false;
            throw error;
        }
    }

    static boolean shouldPrompt(AppUpdateInfo info, int installedVersionCode) {
        return shouldPrompt(
                info.updateAvailability(),
                info.availableVersionCode(),
                installedVersionCode);
    }

    static boolean shouldPrompt(
            int updateAvailability,
            int availableVersionCode,
            int installedVersionCode) {
        return updateAvailability == UpdateAvailability.UPDATE_AVAILABLE
                && availableVersionCode > installedVersionCode;
    }

    interface Listener {
        void onUpdateAvailable(int availableVersionCode);
    }
}
