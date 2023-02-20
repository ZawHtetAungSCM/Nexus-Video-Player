package com.example.videoplayer.task;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadFileTask extends AsyncTask<Void, Void, Void> {

    private final String mUrl;
    private final File mFile;

    public DownloadFileTask(String url, File file) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("You need to supply a url to a clear MP4 file to download.");
        }
        mUrl = url;
        mFile = file;
    }

    private void downloadVideoFile() throws Exception {

        URL url = new URL(mUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("server error: " + connection.getResponseCode() + ", " + connection.getResponseMessage());
        }

        InputStream inputStream = connection.getInputStream();
        FileOutputStream fileOutputStream = new FileOutputStream(mFile);

        byte buffer[] = new byte[1024 * 1024];

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        connection.disconnect();
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            downloadVideoFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onPostExecute(Void aVoid) {
        Log.d(getClass().getCanonicalName(), "Download : done");
    }
}
