package com.nexuschat.messenger.util;

import java.util.concurrent.ExecutionException;

public interface FutureTaskListener<V> {
  public void onSuccess(V result);

  public void onFailure(ExecutionException exception);
}
