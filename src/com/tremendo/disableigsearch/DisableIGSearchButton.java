package com.tremendo.disableigsearch;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static de.robv.android.xposed.XposedHelpers.*;


public class DisableIGSearchButton implements IXposedHookLoadPackage {

	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

		if (!"com.instagram.android".equals(lpparam.packageName)) {
			return;
		}
		
		Class<?> MainTabActivityClass; // May depend on app version
		try {
			MainTabActivityClass = findClass("com.instagram.android.activity.MainTabActivity", lpparam.classLoader);
		} catch (XposedHelpers.ClassNotFoundError e) {
			MainTabActivityClass = findClass("com.instagram.mainactivity.MainTabActivity", lpparam.classLoader);
		}
		
		
		findAndHookMethod(MainTabActivityClass,
		"onCreate", Bundle.class, new XC_MethodHook() {
			@Override
			public void afterHookedMethod(final MethodHookParam param) throws Throwable {

				final XSharedPreferences modulePreferences = new XSharedPreferences("com.tremendo.disableigsearch");
				modulePreferences.makeWorldReadable();

				final boolean disableSearch = modulePreferences.getBoolean("key_disable_search", false);
				final boolean disableSearchLongPress = modulePreferences.getBoolean("key_disable_search_long", false);
				final boolean hideButton = modulePreferences.getBoolean("key_hide_button", false);
				
				if (disableSearch || disableSearchLongPress || hideButton) {

					Activity thisActivity = (Activity) param.thisObject;
					ViewGroup tabBar = (ViewGroup) thisActivity.findViewById(getResourceId("tabs", "android"));
					
					// Tab buttons are tagged ("FEED", "SEARCH", "PROFILE", etc).  Look for appropriate button
					int searchButtonIndex = -1;
					for (int index = 0; index < tabBar.getChildCount(); index++) {
						View button = tabBar.getChildAt(index);
						if ("SEARCH".equals(String.valueOf(button.getTag()))) {
							searchButtonIndex = index;
							break;
						}
					}
					if (searchButtonIndex == -1) {
						return; // Couldn't find search button
					}

					View searchButton = tabBar.getChildAt(searchButtonIndex);

					if (hideButton) {
						searchButton.setVisibility(View.GONE);
						return;
					}

					if (disableSearch) {
						searchButton.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								showToastIfEnabled(param.thisObject, modulePreferences, "Search button disabled");
							}
						});
					}

					if (disableSearchLongPress) {
						searchButton.setOnLongClickListener(new View.OnLongClickListener() {
							@Override
							public boolean onLongClick(View view) {
								showToastIfEnabled(param.thisObject, modulePreferences, "Search button long-press disabled");
								return true;
							}
						});
					}
				}
			}
		});

		findAndHookMethod(MainTabActivityClass,
		"onPause", new XC_MethodHook() {
			@Override
			public void beforeHookedMethod(final MethodHookParam param) throws Throwable {
				removeAdditionalInstanceField(param.thisObject, "toast"); // Clear any stored reference to a toast
			}
		});
				
	}
		
	
	private static void showToastIfEnabled(Object activityObject, XSharedPreferences prefs , CharSequence info) {
		prefs.reload();
		if (!prefs.getBoolean("key_show_toast", false)) return;
		// Check if toast is already shown so that it could be replaced, rather than stacked one after another (for repeated clicks)
		Toast toast = (Toast) getAdditionalInstanceField(activityObject, "toast");
		if (toast != null && toast.getView() != null && toast.getView().isShown()) {
			toast.cancel();
		}
		toast = Toast.makeText(AndroidAppHelper.currentApplication(), info, 0);
		setAdditionalInstanceField(activityObject, "toast", toast); // Store a reference to the toast, so it could be replaced if needed
		toast.show();
	}


	private static int getResourceId(String resourceName, String pkgName) {
		return AndroidAppHelper.currentApplication().getApplicationContext().getResources().getIdentifier(resourceName, "id", pkgName);
	}

}
