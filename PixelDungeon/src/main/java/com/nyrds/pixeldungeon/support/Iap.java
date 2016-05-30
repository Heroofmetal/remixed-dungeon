package com.nyrds.pixeldungeon.support;

import android.app.Activity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.nyrds.android.google.util.IabHelper;
import com.nyrds.android.google.util.IabResult;
import com.nyrds.android.google.util.Inventory;
import com.nyrds.android.google.util.Purchase;
import com.nyrds.android.google.util.SkuDetails;
import com.nyrds.android.util.Util;
import com.nyrds.pixeldungeon.items.accessories.Accessory;
import com.nyrds.pixeldungeon.ml.BuildConfig;
import com.nyrds.pixeldungeon.ml.EventCollector;
import com.nyrds.pixeldungeon.ml.R;
import com.watabou.noosa.Game;
import com.watabou.pixeldungeon.PixelDungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 24.05.2016.
 */
public class Iap {
	static final int RC_REQUEST = (int) (Math.random() * 0xffff);

	private static final String SKU_LEVEL_1 = "supporter_level_1";
	private static final String SKU_LEVEL_2 = "supporter_level_2";
	private static final String SKU_LEVEL_3 = "supporter_level_3";

	private static IabHelper mHelper    = null;
	private static Inventory mInventory = null;

	private static Activity mContext;

	private static volatile boolean m_iapReady = false;

	private static boolean googleIapUsable() {
		return android.os.Build.VERSION.SDK_INT >= 9;
	}

	public static boolean isReady() {
		return m_iapReady;
	}

	private static void initIapPhase2() {
		if (mHelper != null) {
			return;
		}

		String base64EncodedPublicKey = Game.getVar(R.string.iapKey);

		mHelper = new IabHelper(mContext, base64EncodedPublicKey);

		mHelper.enableDebugLogging(BuildConfig.DEBUG);

		mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			@Override
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					complain("Problem setting up in-app billing: " + result);
					return;
				}

				if (mHelper == null)
					return;

				queryItems();
			}
		});
	}

	public static void queryItemsPrice(List<String> items) {
		try {
			mHelper.queryInventoryAsync(true, items, null, mGotInventoryListener);
		} catch (IabHelper.IabAsyncInProgressException e) {
			EventCollector.logException(e);
		}
	}

	private static void queryItems() {
		List<String> donations = new ArrayList<>();

		donations.add(SKU_LEVEL_1);
		donations.add(SKU_LEVEL_2);
		donations.add(SKU_LEVEL_3);

		donations.addAll(Accessory.getAccessoriesList());

		queryItemsPrice(donations);
	}


	public static void initIap(Activity context) {
		mContext = context;

		if (googleIapUsable()) {
			if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext) != ConnectionResult.SUCCESS) {
				return; // no services - no iap :(
			}
		}

		new Thread() {
			@Override
			public void run() {
				if (Util.isConnectedToInternet()) {
					initIapPhase2();
				}
			}
		}.start();
	}

	private static void checkPurchases() {
		PixelDungeon.setDonationLevel(0);

		Purchase check = mInventory.getPurchase(SKU_LEVEL_1);
		if (check != null && verifyDeveloperPayload(check)) {
			PixelDungeon.setDonationLevel(1);
		}

		check = mInventory.getPurchase(SKU_LEVEL_2);
		if (check != null && verifyDeveloperPayload(check)) {
			PixelDungeon.setDonationLevel(2);
		}

		check = mInventory.getPurchase(SKU_LEVEL_3);
		if (check != null && verifyDeveloperPayload(check)) {
			PixelDungeon.setDonationLevel(3);
		}
	}

	static IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		@Override
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			if (mHelper == null)
				return;

			if (result.isFailure()) {
				complain("Failed to query inventory: " + result);
				return;
			}

			mInventory = inventory;
			checkPurchases();
			m_iapReady = true;
		}
	};

	static String formatSkuPrice(SkuDetails sku) {
		if(sku == null) {
			return null;
		}
		return sku.getPrice() + "("+ sku.getPriceCurrencyCode()+")";
	}


	public static String getDonationPriceString(int level) {
		if (mInventory == null) {
			return null;
		}

		mInventory.getSkuDetails(SKU_LEVEL_1);

		switch (level) {
			case 1:
				return formatSkuPrice(mInventory.getSkuDetails(SKU_LEVEL_1));
			case 2:
				return formatSkuPrice(mInventory.getSkuDetails(SKU_LEVEL_2));
			case 3:
				return formatSkuPrice(mInventory.getSkuDetails(SKU_LEVEL_3));
		}
		return null;
	}

	private static void doPurchase(String sku) {
		if (!m_iapReady) {
			EventCollector.logEvent("fail","purchase not ready");
			return;
		}

		String payload = "";

		m_iapReady = false;
		try {
			mHelper.launchPurchaseFlow(mContext, sku, RC_REQUEST, mPurchaseFinishedListener, payload);
		} catch (IabHelper.IabAsyncInProgressException e) {
			EventCollector.logException(e);
		}
	}

	static boolean verifyDeveloperPayload(Purchase p) {
		String payload = p.getDeveloperPayload();

		return true;
	}

	public static void donate(int level) {
		switch (level) {
			case 1:
				doPurchase(SKU_LEVEL_1);
				break;
			case 2:
				doPurchase(SKU_LEVEL_2);
				break;
			case 3:
				doPurchase(SKU_LEVEL_3);
				break;
		}
	}

	static IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
			if (mHelper == null)
				return;

			if (result.isFailure()) {
				complain("Error purchasing: " + result);
				m_iapReady = true;
				return;
			}

			if (!verifyDeveloperPayload(purchase)) {
				complain("Error purchasing. Authenticity verification failed.");
				return;
			}

			if (purchase.getSku().equals(SKU_LEVEL_1)) {
				PixelDungeon.setDonationLevel(1);
			}

			if (purchase.getSku().equals(SKU_LEVEL_2)) {
				PixelDungeon.setDonationLevel(2);
			}

			if (purchase.getSku().equals(SKU_LEVEL_3)) {
				PixelDungeon.setDonationLevel(3);
			}

			m_iapReady = true;
		}
	};

	static void complain(String message) {
		EventCollector.logEvent("iap error",message);
		Log.e("GAME", "**** IAP Error: " + message);
	}

}