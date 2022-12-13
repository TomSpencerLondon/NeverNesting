package com.tomspencerlondon.after;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

  private void processPending(DownloadState download)
  {
    Download downloader = new Download(download.getUrl(), this.downloadDir);
    downloader.start();
    download.setDownloader(downloader);

    download.moveTo(DownloadState.State.InProgress);
  }

  private void handleHttpError(DownloadState download, int httpCode)
  {
    int HTTP_REQUEST_TIMEOUT = 408;
    int HTTP_BAD_GATEWAY = 502;
    int HTTP_SERVICE_UNAVALIABLE = 503;
    int HTTP_GATEWAY_TIMEOUT = 504;

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
  }

  private boolean processInProgress(DownloadState download)
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
          return true;
        }

        download.moveTo(DownloadState.State.InProgress);
        break;
      case HttpError:
        handleHttpError(download, result.getHTTPCode());
        break;
    }

    return false;
  }

  void processIncomingRequests()
  {
    String path;
    while ((path = requestedUrls.poll()) != null)
    {
      downloads.add(new DownloadState(path));
    }
  }

  boolean processDownload(ListIterator<DownloadState> iterator)
  {
    DownloadState download = iterator.next();

    if (download.getState() == DownloadState.State.Pending)
    {
      processPending(download);
    }

    if (download.getState() == DownloadState.State.InProgress)
    {
      if (processInProgress(download))
      {
        return true;
      }
    }

    if (download.getState() == DownloadState.State.Complete)
    {
      iterator.remove(); //Check where the iterator goes
    }

    return false;
  }

  boolean processDownloads()
  {
    ListIterator<DownloadState> iterator = downloads.listIterator();

    while (iterator.hasNext() && !this.connectionDisabled)
    {
      if (processDownload(iterator))
      {
        return true;
      }
    }
    return false;
  }

  private void clearDownloads()
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

  boolean waitForRequest()
  {
    requestLock.lock();
    try
    {
      newRequest.await();
    }
    catch(InterruptedException e)
    {
      return false;
    }
    finally
    {
      requestLock.unlock();
    }

    return true;
  }

  public void run()
  {
    boolean running = true;
    while (running)
    {
      processIncomingRequests();

      if (!this.connectionDisabled)
      {
        this.connectionDisabled = processDownloads();
      }

      if (this.connectionDisabled)
      {
        clearDownloads();
      }

      if (downloads.isEmpty() || requestedUrls.isEmpty())
      {
        running = waitForRequest();
      }
    }
  }
}
