package com.tomspencerlondon.after;

public class DownloadState
{
  private String url;
  private Download currentDownload;
  private State state;
  private int attempts;

  public enum State
  {
    Pending,
    InProgress,
    Complete
  }

  public DownloadState(String url)
  {
    this.url = url;
    this.state = State.Pending;
    this.currentDownload = null;
    this.attempts = 0;
  }

  public void setDownloader(Download download)
  {
    this.currentDownload = download;
  }

  public Download getDownloader()
  {
    return this.currentDownload;
  }

  public void incrementAttempts()
  {
    this.attempts++;
  }

  public int getAttempts()
  {
    return attempts;
  }

  public String getUrl()
  {
    return this.url;
  }

  public State getState()
  {
    return this.state;
  }

  public void moveTo(State state)
  {
    this.state = state;
  }
}
