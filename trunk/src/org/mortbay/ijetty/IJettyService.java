//========================================================================
//$Id$
//Copyright 2008 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.ijetty;

import java.io.InputStream;
import java.io.File;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.deployer.ContextDeployer;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.*;

public class IJettyService extends Service
{
    private static final String[] __configurationClasses = 
        new String[] {
        "org.mortbay.ijetty.AndroidWebInfConfiguration",
        "org.mortbay.jetty.webapp.WebXmlConfiguration",
        "org.mortbay.jetty.webapp.JettyWebXmlConfiguration",
        "org.mortbay.jetty.webapp.TagLibConfiguration" };
    private NotificationManager mNM;

    private Server server;

    private static Resources __resources;

    private SharedPreferences preferences;
   

    public void onCreate()
    {
            Log.i("Jetty", "onCreate called");
            __resources = getResources();
    }


    public void onStart(Intent intent, int startId)
    {
        Log.i("Jetty", "onStart called");
        if (server != null)
        {
            Log.i("Jetty", "already running");
            return;
        }
        
        try
        {
            Bundle bundle = intent.getExtras();

            Log.i("Jetty", "port = "+bundle.getString(IJetty.__PORT));
            Log.i("Jetty", "nio = "+bundle.getBoolean(IJetty.__NIO));
            Log.i("Jetty", "pwd = "+bundle.getString(IJetty.__CONSOLE_PWD));

            startJetty();
            preferences = getSharedPreferences("jetty", MODE_WORLD_READABLE);
            Editor editor = preferences.edit();
            editor.putBoolean("isRunning", true);
            editor.commit();
            mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            // This is who should be launched if the user selects our persistent
            // notification.
            Toast.makeText(IJettyService.this, R.string.jetty_started,
                    Toast.LENGTH_SHORT).show();

            // The PendingIntent to launch IJetty activity if the user selects this notification
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, IJetty.class), 0);

            CharSequence text = getText(R.string.manage_jetty);

            Notification notification = new Notification(R.drawable.jicon, 
                    text, 
                    System.currentTimeMillis());

            notification.setLatestEventInfo(this, getText(R.string.app_name),
                    text, contentIntent);

            mNM.notify(R.string.jetty_started, notification);
            Log.i("Jetty", "Jetty started");
            super.onStart(intent, startId);
        }
        catch (Exception e)
        {
            Log.e("Jetty", "Error starting jetty", e);
            Toast.makeText(this, getText(R.string.jetty_not_started),
                    Toast.LENGTH_SHORT).show();
        }
    }


    public void onDestroy()
    {
        try
        {
            if (server != null)
            {
                stopJetty();
                // Cancel the persistent notification.
                mNM.cancel(R.string.jetty_started);
                // Tell the user we stopped.
                Toast.makeText(this, getText(R.string.jetty_stopped),
                        Toast.LENGTH_SHORT).show();
                Editor editor = preferences.edit();
                editor.putBoolean("isRunning", false);
                editor.commit();
                Log.i("Jetty", "Jetty stopped");
                __resources = null;
            }
            else
                Log.i("Jetty", "Jetty not running");
        }
        catch (Exception e)
        {
            Log.e("Jetty", "Error stopping jetty", e);
            Toast.makeText(this, getText(R.string.jetty_not_stopped),
                    Toast.LENGTH_SHORT).show();
        }
    }
    
    

    public void onLowMemory()
    {
        Log.i("Jetty", "Low on memory");
        super.onLowMemory();
    }


    /**
     * Hack to get around bug in ResourceBundles
     * 
     * @param id
     * @return
     */
    public static InputStream getStreamToRawResource(int id)
    {
        if (__resources != null)
            return __resources.openRawResource(id);
        else
            return null;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.d("Jetty", "onBind called");
        return null;
    }

    private void startJetty() throws Exception
    {
        // TODO - get ports and types of connector from SharedPrefs?
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setUseDirectBuffers(false);
        connector.setPort(8080);
        server.setConnectors(new Connector[] { connector });

        // Bridge Jetty logging to Android logging
        System.setProperty("org.mortbay.log.class",
                "org.mortbay.log.AndroidLog");
        org.mortbay.log.Log.setLog(new AndroidLog());

        HandlerCollection handlers = new HandlerCollection();
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        handlers.setHandlers(new Handler[] {contexts, new DefaultHandler()});
        server.setHandler(handlers);

        File jettyDir = new File("/sdcard/jetty");
        
        // Load any webapps we find on the card.
        if (jettyDir.exists())
        {
            System.setProperty ("jetty.home", "/sdcard/jetty");
            AndroidWebAppDeployer staticDeployer = null;
            
            // Deploy any static webapps we have.
            if (new File(jettyDir, "webapps").exists())
            {
                staticDeployer = new AndroidWebAppDeployer();
                staticDeployer.setWebAppDir("/sdcard/jetty/webapps");
                staticDeployer.setDefaultsDescriptor("/sdcard/jetty/etc/webdefault.xml");
                staticDeployer.setContexts(contexts);
                staticDeployer.setContentResolver (getContentResolver());
                staticDeployer.setConfigurationClasses(__configurationClasses);
            }
            ContextDeployer contextDeployer = null;
            // Use a ContextDeploy so we can hot-deploy webapps and config at startup.
            if (new File(jettyDir, "contexts").exists())
            {
                contextDeployer = new ContextDeployer();
                contextDeployer.setScanInterval(0); // Don't eat the battery (scan only at server-start)
                contextDeployer.setConfigurationDir("/sdcard/jetty/contexts");
                contextDeployer.setContexts(contexts);
            }
            File realmProps = new File("/sdcard/jetty/etc/realm.properties");
            if (realmProps.exists())
            {
                HashUserRealm realm = new HashUserRealm("Console", "/sdcard/jetty/etc/realm.properties");
                realm.setRefreshInterval(0);
                server.addUserRealm(realm);
            }

            if (contextDeployer != null)
                server.addLifeCycle(contextDeployer);

            if (staticDeployer != null)
                server.addLifeCycle(staticDeployer);
        }
        else
        {
            Log.w("Jetty", "Not loading any webapps - none on SD card.");
        }
        
        server.start();
    }

    private void stopJetty() throws Exception
    {
        Log.i("Jetty", "Jetty stopping");
        server.stop();
        server.join();
        server = null;
    }
}
