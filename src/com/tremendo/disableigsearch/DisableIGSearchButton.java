package com.tremendo.disableigsearch;

import android.app.*;
import android.content.*;
import android.content.pm.*;
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

		final String mainActivityName = findMainActivityName();

		if (mainActivityName == null) {
			return;
		}

		tryHookMethod(mainActivityName, lpparam.classLoader,
		"onResume", new XC_MethodHook() {
			@Override
			public void afterHookedMethod(final MethodHookParam param) throws Throwable {
				Activity activity = (Activity) param.thisObject;
				View activityRootView = activity.getWindow().getDecorView().getRootView();
				ViewGroup tabBar = activityRootView.findViewById(getResourceId("tab_bar"));
				if (tabBar == null) {
					XposedBridge.log("DisableIGSearchButton: Didn't find tab bar.  Instagram version not supported");
					return;
				}
				View searchButton = findViewWithSearchTag(tabBar);
				if (searchButton == null) {
					XposedBridge.log("DisableIGSearchButton: Didn't find search button in tab bar.  Instagram version not supported");
					return;
				}

				final XSharedPreferences modulePreferences = new XSharedPreferences("com.tremendo.disableigsearch");
				modulePreferences.makeWorldReadable();

				boolean disableSearch = modulePreferences.getBoolean("key_disable_search", false);
				boolean disableSearchLongPress = modulePreferences.getBoolean("key_disable_search_long", false);
				boolean hideButton = modulePreferences.getBoolean("key_hide_button", false);

				if (disableSearch || disableSearchLongPress || hideButton) {

					if (hideButton) {
						searchButton.setVisibility(View.GONE);
						return;
					} else searchButton.setVisibility(View.VISIBLE);

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


		tryHookMethod(mainActivityName, lpparam.classLoader,
		"onPause", new XC_MethodHook() {
			@Override
			public void beforeHookedMethod(final MethodHookParam param) throws Throwable {
				removeAdditionalInstanceField(param.thisObject, "toast"); // Clear any stored reference to a toast
			}
		});

	}


	private static String findMainActivityName() {
		Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
		Context context = (Context) callMethod(activityThread, "getSystemContext");
		PackageManager packageManager = context.getPackageManager();
		try {
			PackageInfo packageInfo = packageManager.getPackageInfo("com.instagram.android", PackageManager.GET_ACTIVITIES);
			if (packageInfo != null) {
				for (ActivityInfo activityInfo : packageInfo.activities) {
					String activityName = activityInfo.name;
					if (activityName != null && activityName.contains(".MainActivity")) {
						return activityName;
					}
				}
			}
		} catch (PackageManager.NameNotFoundException e) {
			XposedBridge.log("DisableIGSearchButton: Didn't find MainActivity.  Instagram version not supported");
		}
		return null;
	}


	private static View findViewWithSearchTag(ViewGroup viewGroup) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View view = viewGroup.getChildAt(i);
			if ("SEARCH".equals(String.valueOf(view.getTag()))) {
				return view;
			}
		}
		return null;
	}


	private static int getResourceId(String resName) {
		return AndroidAppHelper.currentApplication().getApplicationContext().getResources().getIdentifier(resName, "id", AndroidAppHelper.currentPackageName());
	}


	private static void showToastIfEnabled(Object activityObject, XSharedPreferences prefs , CharSequence info) {
		prefs.reload();
		if (!prefs.getBoolean("key_show_toast", false)) return;
		// Check if toast is already shown so that it could be replaced, rather than stacked one after another (for managing repeated clicks)
		Toast toast = (Toast) getAdditionalInstanceField(activityObject, "toast");
		if (toast != null && toast.getView() != null && toast.getView().isShown()) {
			toast.cancel();
		}
		toast = Toast.makeText(AndroidAppHelper.currentApplication(), info, 0);
		setAdditionalInstanceField(activityObject, "toast", toast); // Store a reference to the toast, so it could be replaced if needed
		toast.show();
	}


	private static void tryHookMethod(String className, ClassLoader classLoader, final String methodName, final Object... parameterTypesAndCallback) {
		try {
			findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
		} catch (NoSuchMethodError e) {
			XposedBridge.log(String.format("DisableIGSearchButton: Couldn't find method '%s' in class '%s'.  Instagram version not supported", methodName, className));
		} catch (XposedHelpers.ClassNotFoundError e) {
			XposedBridge.log(String.format("DisableIGSearchButton: Couldn't find class '%s'.  Instagram version not supported", className));
		}
	}

}
