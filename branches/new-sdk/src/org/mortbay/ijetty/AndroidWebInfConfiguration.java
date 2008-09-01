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

import org.mortbay.jetty.webapp.WebInfConfiguration;
import org.mortbay.resource.Resource;

import android.util.Log;

public class AndroidWebInfConfiguration extends WebInfConfiguration
{
    @Override
    public void configureClassLoader() throws Exception
    {
        if (getWebAppContext().isStarted())
        {
            Log.d("Jetty", "Cannot configure webapp after it is started");
            return;
        }

        Resource web_inf = _context.getWebInf();

        // Add WEB-INF lib classpaths
        if (web_inf != null && web_inf.isDirectory()
            && 
            _context.getClassLoader() instanceof ClassLoader)
        {
            // Look for jars
            Resource lib = web_inf.addPath("lib/");
            Log.d("Jetty", "Library resource: " + lib.toString());
            if (lib.exists() || lib.isDirectory())
            {
                AndroidClassLoader loader = ((AndroidClassLoader) _context
                        .getClassLoader());
                for (String dex : lib.list())
                {
                    String fullpath = web_inf.addPath("lib/").addPath(dex)
                            .getFile().getAbsolutePath();
                    if (!loader.addDexFile(fullpath))
                    {
                        Log.w("Jetty", "Failed to add DEX file from path: "+ fullpath);
                    }
                }
            }
        }
    }
}
