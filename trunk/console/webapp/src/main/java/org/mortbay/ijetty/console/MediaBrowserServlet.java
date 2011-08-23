//========================================================================
//$Id$
//Copyright 2009 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.ijetty.console;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.IO;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Config;
import android.util.Log;

/**
 * MediaBrowserServlet
 *
 *  /console/browse/media/[type]/[location]/[thumb]/[id][?action=x]
 *  
 *  type: image, audio, video
 *  location: internal, external
 *  action: embed, upload
 *  
 *  
 *  /console/browse/media/image/internal/3
 *  Retrieves image 3 from internal storage
 *  
 *  /console/browse/media/image/internal/thumb/3
 *  Retrieves the thumbnail for image 3 from internal storage

 *  
 *  /console/browse/media/?action=upload
 *  Submits a new file to upload to the Android device
 *  
 *  
 */
public class MediaBrowserServlet extends HttpServlet
{
    public class MyMediaConnectorClient implements MediaScannerConnectionClient
    {
        private MediaScannerConnection _scanner = null;
        private final File _file;

        public MyMediaConnectorClient(File file)
        {
            _file = file;
        }

        public void onMediaScannerConnected()
        {
            _scanner.scanFile(_file.getAbsolutePath(),null);
        }

        public void onScanCompleted(String path, Uri uri)
        {
            Log.i(TAG,"Finished scanning!");
            _scanner.disconnect();
        }

        public void setScanner(MediaScannerConnection scanner)
        {
            _scanner = scanner;
        }
    }
   

    private static final long serialVersionUID = 1L;
    private static final String TAG = "MediaBrowserServlet";


    private int __THUMB_WIDTH = 120;
    private int __THUMB_HEIGHT = 120;
    private ContentResolver resolver;
    private Context context;

    
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        resolver = (ContentResolver)getServletContext().getAttribute("org.mortbay.ijetty.contentResolver");
        context = (Context)getServletContext().getAttribute("org.mortbay.ijetty.context");
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String pathInfo = request.getPathInfo();

        if (pathInfo == null)
        {
            Log.w(TAG,"pathInfo was null, returning 404");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (Config.LOGD)
        {
            Log.d(TAG,"PathInfo: " + pathInfo);
        }

        String type = null;
        String location = null;
        String id = null;
        boolean isThumb = false;

        StringTokenizer strtok = new StringTokenizer(pathInfo,"/");
        if (strtok.hasMoreElements())
        {
            type = strtok.nextToken();
        }

        if (strtok.hasMoreElements())
        {
            location = strtok.nextToken();
        }

        if (strtok.hasMoreElements())
        {
            String tmp = strtok.nextToken();
            if ("thumb".equalsIgnoreCase(tmp.trim()))
                isThumb = true;
            else
                id = tmp;
        }
        
        if (strtok.hasMoreElements())
        {
            id = strtok.nextToken();
        }

        String action = request.getParameter("action");

        if (action !=null && "embed".equals(action.trim()))
        {
            doGetEmbedHtml(request,response,type,location,id);
        }
        else
            doGetFetchMedia(request, response, type, location, id, isThumb);       
    }
    
    
    
    /**
     * Get the content.
     * 
     * @param request
     * @param response
     * @param contenturi
     * @param item
     * @param asThumb
     * @throws ServletException
     * @throws IOException
     */
    public void doGetFetchMedia(HttpServletRequest request, HttpServletResponse response, String type, String location, String item, boolean asThumb)
            throws ServletException, IOException
    {
        if (item == null)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No id");
            return;
        }
        
        
        try
        {
            Uri uri = MediaType.getContentUriByType(type, location);
            Uri content = Uri.withAppendedPath(uri,item);

            if (asThumb)
            {
                //Get a thumbnail                
                Bitmap bitmap_orig = MediaStore.Images.Media.getBitmap(resolver,content);
                if (bitmap_orig != null)
                {
                    response.setStatus(HttpServletResponse.SC_OK);
                    InputStream stream = null;
                    OutputStream os = response.getOutputStream();
                    Bitmap bitmap = null;

                    int width = bitmap_orig.getWidth();
                    int height = bitmap_orig.getHeight();

                    // If the image is too big (AND the width/height isn't 0), scale it
                    if (((width > __THUMB_WIDTH) || (height > __THUMB_HEIGHT)) && (height != 0) && (width != 0))
                    {
                        float scaleWidth = 0;
                        float scaleHeight = 0;

                        if (Config.LOGD)
                        {
                            Log.d(TAG,"orig height = " + height + ", orig width = " + width);
                            Log.d(TAG,"__THUMB_HEIGHT = " + __THUMB_HEIGHT + ", __THUMB_WIDTH = " + __THUMB_WIDTH);
                        }

                        if (width > __THUMB_WIDTH)
                        {
                            scaleWidth = ((float)__THUMB_WIDTH) / width;
                        }

                        if (height > __THUMB_HEIGHT)
                        {
                            scaleHeight = ((float)__THUMB_HEIGHT) / height;
                        }

                        if (Config.LOGD)
                        {
                            Log.d(TAG,"scaleHeight = " + scaleHeight + ", scaleWidth = " + scaleWidth);
                        }

                        if ((scaleHeight < scaleWidth) && (scaleHeight != 0))
                        {
                            scaleWidth = scaleHeight;
                        }
                        else if ((scaleWidth < scaleHeight) && (scaleWidth != 0))
                        {
                            scaleHeight = scaleWidth;
                        }

                        if (scaleWidth == 0)
                        {
                            scaleWidth = scaleHeight;
                        }

                        if (scaleHeight == 0)
                        {
                            scaleHeight = scaleWidth;
                        }

                        if (Config.LOGD)
                        {
                            Log.d(TAG,"scaleHeight = " + scaleHeight + ", scaleWidth = " + scaleWidth + " (final)");
                        }

                        if (scaleHeight == 0)
                        {
                            Log.w(TAG,"scaleHeight and scaleWidth both = 0! Setting scale to 50%.");
                            scaleHeight = 0.5f;
                            scaleWidth = 0.5f;
                        }

                        Matrix matrix = new Matrix();
                        matrix.postScale(scaleWidth,scaleHeight);

                        // recreate the new Bitmap
                        bitmap = Bitmap.createBitmap(bitmap_orig,0,0,width,height,matrix,true);
                        //release original bitmap as soon as possible
                        bitmap_orig.recycle();
                        bitmap_orig = null;
                      
                
                        //TODO - if we updated to newer Api rev level we could use this
                        //Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, Integer.valueOf(item).intValue(), MediaStore.Images.Thumbnails.MINI_KIND, new BitmapFactory.Options());

                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                        if (resolver.getType(content) == "image/gif")
                        {
                            Log.i(TAG,"Original image was gif, exporting thumb as JPEG as workaround");
                            response.setContentType("image/gif");
                            bitmap.compress(Bitmap.CompressFormat.JPEG,90,bytes);
                        }
                        else
                        {
                            Log.i(TAG,"Exporting thumb in png format");
                            response.setContentType("image/png");
                            bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);
                        }

                        try
                        {
                            stream = new ByteArrayInputStream(bytes.toByteArray());
                            IO.copy(stream,os);
                        }
                        finally
                        {
                            bitmap.recycle();
                            bitmap = null;
                        }
                    }
                    else
                    {
                        if ((height == 0) || (width == 0))
                        {
                            Log.w(TAG,"Height or width were 0; sending original image instead!");
                        }
                        else
                        {
                            Log.i(TAG,"Original was smaller than " + __THUMB_HEIGHT + "x" + __THUMB_WIDTH + ", skipping scale.");
                        }
                        // just return the original data from the DB
                        response.setContentType(resolver.getType(content));
                        stream = resolver.openInputStream(content);
                        IO.copy(stream,os);
                    }
                }
            }
            else
            {
                Log.i(TAG,"Exporting original media");
                InputStream stream = null;
                try
                {
                    response.setContentType(resolver.getType(content));
                    stream = resolver.openInputStream(content);
                    OutputStream os = response.getOutputStream();
                    response.setStatus(HttpServletResponse.SC_OK);
                    IO.copy(stream,os);
                }
                finally
                {
                    if (stream != null)
                        stream.close();
                }
            }
        }
        catch (Exception e)
        {
            Log.w(TAG,"Failed to fetch media",e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Get the HTML snippet for embedding the media content in a way suitable for the browser to view the content as
     *
     * @param request
     *            the incoming servlet request
     * @param response
     *            the outgoing servlet response
     * @param type
     *            the type of media interested in (<code>images</code>, <code>audio</code>, or <code>video</code>)
     * @param location
     *            the location of media interested in (<code>internal</code>, or <code>external</code>)
     * @param item
     *            the item reference
     * @throws ServletException
     * @throws IOException
     */
    public void doGetEmbedHtml(HttpServletRequest request, HttpServletResponse response, String type, String location, String item) throws ServletException,
    IOException
    {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html");
        PrintWriter writer = response.getWriter();

        String path = "/console/browse/media/" + type + "/" + location + "/" + item;

        writer.print("<OBJECT ID='MediaPlayer' WIDTH='320' HEIGHT='26'");
        writer.println(" CLASSID='CLSID:22D6F312-B0F6-11D0-94AB-0080C74C7E95'");
        writer.println(" STANDBY='Loading...' TYPE='application/x-oleobject'>");
        writer.printf("  <PARAM NAME='FileName' VALUE='%s'>%n",path);
        writer.printf("  <EMBED TYPE='application/x-mplayer2' SRC='%s' NAME='MediaPlayer' WIDTH='320' HEIGHT='26' autostart='1'></EMBED>%n",path);
        writer.println("</OBJECT>");
    }
    
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String action = request.getParameter("action");
        if (action == null || !"upload".equalsIgnoreCase(action))
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unrecognized action");
            return;
        }
      
        //Upload new media content
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();

        // Should we use just the servlet directory instead?
        File externalDir = Environment.getExternalStorageDirectory();
        File sdcarddir = new File(externalDir,"/jetty/media");

        // Create file upload directory if it doesn't exist
        if (!sdcarddir.exists())
            sdcarddir.mkdir();

        File output = null;

        try
        {
            // Save file to External Storage
            File file = (File)request.getAttribute("fileupload");
            String origName = request.getParameter("fileupload");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IO.copy(new FileInputStream(file),out);

            output = new File(sdcarddir,origName);
            Log.i(TAG,"Writing to: " + output);
            FileOutputStream stream = new FileOutputStream(output);

            out.writeTo(stream);
            stream.close();
        }
        catch (Exception e)
        {
            Log.w(TAG,"Failed to save uploaded file",e);
            printResponse(writer,1,"Could not save uploaded file to sdcard.",-1);
            return;
        }

        // Re-run media scanner, to re-detect media
        MyMediaConnectorClient client = new MyMediaConnectorClient(output);
        MediaScannerConnection scanner = new MediaScannerConnection(context,client);
        client.setScanner(scanner);

        scanner.connect();

        int filetype = -1;
        printResponse(writer,0,"No error",filetype);

    }

    public ContentResolver getContentResolver()
    {
        return resolver;
    }


    

    private void printResponse(PrintWriter writer, int resp, String msg, int filetype)
    {
        writer.println("<script>");
        writer.println("var json = { error: " + resp + ", msg: '" + msg + "', filetype: " + filetype + " };");
        writer.println("if (top.Media) { top.Media.uploadComplete(json); }");
        writer.println("</script>");
    }
   
   
}
