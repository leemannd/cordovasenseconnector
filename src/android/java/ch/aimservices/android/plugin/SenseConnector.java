package ch.aimservices.android.plugin;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;

import org.apache.commons.io.FileUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.ICordovaHttpAuthHandler;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Collection;

import ch.aimservices.android.plugin.action.ChangePwdAction;
import ch.aimservices.android.plugin.action.EnrollAction;
import ch.aimservices.android.plugin.action.ExitAppAction;
import ch.aimservices.android.plugin.action.InitializeAction;
import ch.aimservices.android.plugin.action.LoginAction;
import ch.aimservices.android.plugin.action.TerminateAction;
import ch.aimservices.android.plugin.action.UpdateAppAction;

import static ch.sysmosoft.sense.android.core.service.Sense.SenseServices;
import static ch.sysmosoft.sense.android.core.service.Sense.SessionService;

/**
 * Created by IntelliJ IDEA.
 * User: pblanco
 * Date: 17.07.14
 * Time: 08:17
 * To change this template use File | Settings | File Templates.
 */
public class SenseConnector extends CordovaPlugin implements SenseServicesContext {

    private static final String LOG_TAG = "SenseConnector";

    private final Collection<Action> actions = new ArrayList<Action>();

    private SessionService sessionService;
    private SenseServices senseServices;

    public SenseConnector() {
    }

    @Override
    public void initialize(final CordovaInterface cordova, final CordovaWebView webview) {
        super.initialize(cordova, webview);
        initializeActions();
        initializeSense();
        disableCookies(cordova.getActivity());
        setWebViewSettings();
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        return internalExecute(true, action, args, callbackContext);
    }

    @Override
    public void onDestroy() {
		clearWebViewData();
        internalExecute(false, "terminate", null, null);
        super.onDestroy();
    }

    private boolean internalExecute(final boolean onlyExternal, final String action, final JSONArray args, final CallbackContext callbackContext) {
        final Action act = getAction(action);
        if (act != null && !(onlyExternal && act.internal())) {
            try {
                return act.execute(action, args, callbackContext);
            } catch (final Throwable t) {
                Log.e(LOG_TAG, "The following error occurred while executing " + action + " action", t);
            }
        }
        return false;
    }

    private Action getAction(final String name) {
        for (final Action action : actions) {
            if (action.supports(name)) {
                return action;
            }
        }
        return null;
    }

    private void initializeActions() {
        actions.add(new InitializeAction(this.webView, this.cordova, this));
        actions.add(new LoginAction(this.webView, this.cordova, this));
        actions.add(new EnrollAction(this.webView, this.cordova, this));
        actions.add(new ChangePwdAction(this.webView, this.cordova, this));
        actions.add(new TerminateAction(this.webView, this.cordova, this));
        actions.add(new ExitAppAction(this.webView, this.cordova, this));
		actions.add(new UpdateAppAction(this.webView, this.cordova, this));
    }

    private void initializeSense() {
        internalExecute(false, "initialize", null, null);
    }

    private void disableCookies(final Context context) {
        final CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(context);
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        cookieManager.setAcceptCookie(false);
        cookieSyncManager.sync();
    }

	private void setWebViewSettings() {
		this.webView.setDrawingCacheEnabled(false);
		final WebSettings settings = webView.getSettings();
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setJavaScriptEnabled(true);
		settings.setAppCacheEnabled(false);
		settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		settings.setDefaultTextEncodingName("utf-8");
		settings.setSavePassword(false);
		settings.setSaveFormData(false);
		settings.setDatabaseEnabled(false);
		settings.setGeolocationEnabled(false);
		settings.setAppCacheMaxSize(1);
		settings.setPluginState(WebSettings.PluginState.OFF);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);
	}

    private void clearWebViewData() {
        if (webView != null) {
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearHistory();
            webView.clearSslPreferences();
            webView.clearMatches();
        }
        final Activity activity = cordova.getActivity();
        activity.deleteDatabase("webview.db");
        activity.deleteDatabase("webviewCache.db");
        activity.deleteDatabase("webviewCookiesChromium.db");
        activity.deleteDatabase("webviewCookiesChromiumPrivate.db");

        FileUtils.deleteQuietly(new File(activity.getApplicationInfo().dataDir + File.separator + "app_icons"));
        clearWebViewCacheDir();
    }

    /*
     * This is a "last resort" operation that can be perform to clear
     * completely all the files created by the webview.
     * TODO: check on different rooted devices and Android version if
     * this folder is always created.
     */
    private void clearWebViewCacheDir() {
        final Activity activity = cordova.getActivity();
        if(activity != null) {
            FileUtils.deleteQuietly(new File(activity.getApplicationInfo().dataDir + File.separator + "app_webview"));
            FileUtils.deleteQuietly(activity.getCacheDir());
        }
    }

    /* ======================================= */
    /* = SenseServicesContext Implementation = */
    /* ======================================= */

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Override
    public void setSessionService(final SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public SenseServices getSenseServices() {
        return senseServices;
    }

    @Override
    public void setSenseServices(final SenseServices services) {
        this.senseServices = services;
    }
	
    /* =========================== */
    /* = CordovaPlugin overrides = */
    /* =========================== */
	@Override
    public boolean onReceivedHttpAuthRequest(final CordovaWebView view, final ICordovaHttpAuthHandler handler, final String host, final String realm) {
		final PasswordAuthentication auth = senseServices.getProxyConfig().getPasswordAuthentication();
		handler.proceed(auth.getUserName(), String.valueOf(auth.getPassword()));
        return true;
    }
	
}