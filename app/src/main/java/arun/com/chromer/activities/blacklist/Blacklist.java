/*
 * Chromer
 * Copyright (C) 2017 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.activities.blacklist;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Comparator;
import java.util.List;

import arun.com.chromer.activities.mvp.Base;
import arun.com.chromer.data.apps.AppRepository;
import arun.com.chromer.data.common.App;
import arun.com.chromer.util.RxUtils;
import arun.com.chromer.util.Utils;
import rx.Observable;
import rx.SingleSubscriber;
import rx.Subscription;
import timber.log.Timber;

/**
 * Blacklist screen interface holding View and Presenter classes.
 */
interface Blacklist {
    interface View extends Base.View {
        void setApps(@NonNull List<App> apps);

        void setRefreshing(boolean refreshing);
    }

    /**
     * Presenter containing all business logic for this screen.
     */
    class Presenter extends Base.Presenter<View> {
        void loadAppList(@NonNull final Context context) {
            if (isViewAttached()) {
                getView().setRefreshing(true);
            }
            final Comparator<App> appComparator = new App.BlackListComparator();
            final PackageManager pm = context.getApplicationContext().getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

            final Subscription subscription = Observable
                    .fromCallable(() -> pm.queryIntentActivities(intent, 0))
                    .flatMapIterable(resolveInfos -> resolveInfos)
                    .filter(resolveInfo -> resolveInfo != null
                            && !resolveInfo.activityInfo.packageName.equalsIgnoreCase(context.getPackageName()))
                    .map(resolveInfo -> {
                        final App app = Utils.createApp(context, resolveInfo.activityInfo.packageName);
                        app.blackListed = AppRepository.getInstance(context).isPackageBlacklisted(app.packageName);
                        return app;
                    }).distinct()
                    .toSortedList(appComparator::compare)
                    .compose(RxUtils.applySchedulers())
                    .toSingle()
                    .subscribe(new SingleSubscriber<List<App>>() {
                        @Override
                        public void onSuccess(List<App> apps) {
                            if (isViewAttached()) {
                                getView().setRefreshing(false);
                                getView().setApps(apps);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            Timber.e(error.toString());
                        }
                    });
            compositeSubscription.add(subscription);
        }

        void updateBlacklist(@NonNull Context context, @Nullable App app) {
            if (app != null) {
                if (app.blackListed) {
                    AppRepository.getInstance(context)
                            .setPackageBlacklisted(app.packageName)
                            .compose(RxUtils.applySchedulers())
                            .subscribe();
                } else {
                    AppRepository.getInstance(context)
                            .removeBlacklist(app.packageName)
                            .compose(RxUtils.applySchedulers())
                            .subscribe();
                }
            }
        }

        void handleSelections(final Context context, Observable<App> clicks) {
            final Subscription subscription = clicks.subscribe(app -> updateBlacklist(context, app));
            compositeSubscription.add(subscription);
        }
    }
}
