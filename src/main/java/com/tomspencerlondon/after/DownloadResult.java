package com.tomspencerlondon.after;

public class DownloadResult
{
  enum Code
  {
    Success,
    InProgress,
    ConnectionError,
    Timeout,
    HttpError,
  }

  public Code getCode()
  {
    return Code.Success;
  }

  public int getHTTPCode()
  {
    return 200;
  }
}
