package org.onepf.oms.appstore.fortumo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import org.onepf.oms.Appstore;
import org.onepf.oms.AppstoreInAppBillingService;
import org.onepf.oms.DefaultAppstore;
import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabException;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;

import java.util.concurrent.CountDownLatch;

/**
 * Fortumo, an international mobile payment provider, is not actually an app store.
 * This class was made to provide in-app purchasing compatibility with other, "real", stores.
 *
 * @author akarimova@onepf.org
 * @since 23.12.13
 */
public class FortumoStore extends DefaultAppstore {
    private static final String TAG = FortumoStore.class.getSimpleName();
    private Boolean isBillingAvailable;

    /**
     * Contains information about all in-app products
     */
    public static final String IN_APP_PRODUCTS_FILE_NAME = "inapps_products.xml";

    /**
     * Contains additional information about Fortumo services
     */
    public static final String FORTUMO_DETATILS_FILE_NAME = "fortumo_inapps_details.xml";

    private static boolean isDebugLog() {
        return OpenIabHelper.isDebugLog();
    }

    private Context context;
    private FortumoBillingService billingService;

    public FortumoStore(Context context) {
        this.context = context.getApplicationContext();
    }


    @Override
    public boolean isPackageInstaller(String packageName) {
        //Fortumo is not an app. It can't be an installer.
        return false;
    }

    @Override
    public boolean isBillingAvailable(String packageName) {
        if (isBillingAvailable != null) {
            return isBillingAvailable;
        }
        //todo this check must be move to OpenIabHelper#checkFortumo after adding Nook support
        //must be if isNook then we don't need telephony
        //SMS are required to make payments
        final boolean hasTelephonyFeature = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (isDebugLog()) {
            Log.d(TAG, "isBillingAvailable: has FEATURE_TELEPHONY " + hasTelephonyFeature);
        }
        if (!hasTelephonyFeature) {
            return isBillingAvailable = false;
        }
        billingService = (FortumoBillingService) getInAppBillingService();
        isBillingAvailable = billingService.setupBilling();
        if (isDebugLog()) {
            Log.d(TAG, "isBillingAvailable: " + isBillingAvailable);
        }
        return isBillingAvailable;
    }

    @Override
    public int getPackageVersion(String packageName) {
        return Appstore.PACKAGE_VERSION_UNDEFINED;
    }

    @Override
    public String getAppstoreName() {
        return OpenIabHelper.NAME_FORTUMO;
    }

    @Override
    public AppstoreInAppBillingService getInAppBillingService() {
        if (billingService == null) {
            billingService = new FortumoBillingService(context);
        }
        return billingService;
    }

    //todo rename the method
    public static FortumoStore initFortumoStore(Context context, final boolean checkInventory) {
        final FortumoStore[] storeToReturn = {null};
        final FortumoStore fortumoStore = new FortumoStore(context);
        if (fortumoStore.isBillingAvailable(context.getPackageName())) {
            final CountDownLatch latch = new CountDownLatch(1);
            fortumoStore.getInAppBillingService().startSetup(new IabHelper.OnIabSetupFinishedListener() {
                @Override
                public void onIabSetupFinished(IabResult setupResult) {
                    if (setupResult.isSuccess()) {
                        if (checkInventory) {
                            try {
                                final Inventory inventory = fortumoStore.getInAppBillingService().queryInventory(false, null, null);
                                if (!inventory.getAllPurchases().isEmpty()) {
                                    storeToReturn[0] = fortumoStore;
                                }
                            } catch (IabException e) {
                                Log.e(TAG, "Purchases not found", e);
                            }
                        } else {
                            storeToReturn[0] = fortumoStore;
                        }
                    }
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e(TAG, "Setup was interrupted", e);
            }
        }
        return storeToReturn[0];
    }
}
