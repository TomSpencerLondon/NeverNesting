package com.tomspencerlondon.before;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Check if cache if needed
public class DownloadThread implements Runnable
{
  private String downloadDir;
  private int maxAttempts;
  ConcurrentLinkedQueue<String> requestedUrls = new ConcurrentLinkedQueue<String>();
  ConcurrentLinkedQueue<String> failedUrls = new ConcurrentLinkedQueue<String>();
  LinkedList<DownloadState> downloads = new LinkedList<DownloadState>();
  Lock requestLock = new ReentrantLock();
  private Condition newRequest = requestLock.newCondition();

  private boolean connectionDisabled = false;

  public void appendDownload(String url)
  {
    requestLock.lock();

    try
    {
      requestedUrls.offer(url);
      newRequest.signalAll();
    }
    finally
    {
      requestLock.unlock();
    }
  }

  private void processPendingDownload(DownloadState download)
  {
    Download downloader = new Download(download.getUrl(), this.downloadDir);
    downloader.start();
    download.setDownloader(downloader);

    download.moveTo(DownloadState.State.InProgress);
  }

  public void run()
  {
    // load cache
    while(true)
    {
      String path;
      while ((path = requestedUrls.poll()) != null)
      {
        downloads.add(new DownloadState(path));
      }

      if (!this.connectionDisabled)
      {
        ListIterator<DownloadState> iterator = downloads.listIterator();

        while (iterator.hasNext() && !this.connectionDisabled)
        {
          DownloadState download = iterator.next();

          if (download.getState() == DownloadState.State.Pending)
          {
            processPendingDownload(download);
          }

          if (download.getState() == DownloadState.State.InProgress)
          {
            DownloadResult result = download.getDownloader().process();

            switch(result.getCode())
            {
              case Success:
                download.moveTo(DownloadState.State.Complete);
                break;
              case InProgress:
                /* Nothing to do */
                break;
              case Timeout:
              case ConnectionError:
                if (download.getAttempts() > this.maxAttempts)
                {
                  this.connectionDisabled = true;
                }
                else
                {
                  download.moveTo(DownloadState.State.InProgress);
                }
                break;
              case HttpError:
                int HTTP_REQUEST_TIMEOUT = 408;
                int HTTP_BAD_GATEWAY = 502;
                int HTTP_SERVICE_UNAVALIABLE = 503;
                int HTTP_GATEWAY_TIMEOUT = 504;

                int httpCode = result.getHTTPCode();
                if (httpCode == HTTP_REQUEST_TIMEOUT ||
                    httpCode == HTTP_BAD_GATEWAY ||
                    httpCode == HTTP_SERVICE_UNAVALIABLE ||
                    httpCode == HTTP_GATEWAY_TIMEOUT)
                {
                  if (download.getAttempts() > this.maxAttempts)
                  {
                    download.moveTo(DownloadState.State.Complete);
                  }
                  else
                  {
                    download.moveTo(DownloadState.State.InProgress);
                  }
                }
                else
                {
                  failedUrls.offer(download.getUrl());
                  download.moveTo(DownloadState.State.Complete);
                }

                break;
            }
          }

          if (download.getState() == DownloadState.State.Complete)
          {
            iterator.remove(); //Check where the iterator goes
          }
        }
      }

      if (this.connectionDisabled)
      {
        while (downloads.size() > 0)
        {
          DownloadState download = downloads.removeFirst();
          if (download.getState() == DownloadState.State.InProgress)
          {
            download.getDownloader().cancel();
          }
          failedUrls.offer(download.getUrl());
        }
      }

      // lock mutex
      if (downloads.isEmpty() || requestedUrls.isEmpty())
      {
        requestLock.lock();
        try
        {
          newRequest.await();
        }
        catch(InterruptedException e)
        {
          return;
        }
        finally
        {
          requestLock.unlock();
        }
      }
    }
  }
}
