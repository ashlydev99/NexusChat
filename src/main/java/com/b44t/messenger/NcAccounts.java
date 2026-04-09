package com.b44t.messenger;

public class NcAccounts {

  public NcAccounts(String dir, NcEventChannel channel) {
    accountsCPtr = createAccountsCPtr(dir, channel);
    if (accountsCPtr == 0) throw new RuntimeException("createAccountsCPtr() returned null pointer");
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    unref();
  }

  public void unref() {
    if (accountsCPtr != 0) {
      unrefAccountsCPtr();
      accountsCPtr = 0;
    }
  }

  public NcEventEmitter getEventEmitter() {
    return new NcEventEmitter(getEventEmitterCPtr());
  }

  public NcJsonrpcInstance getJsonrpcInstance() {
    return new NcJsonrpcInstance(getJsonrpcInstanceCPtr());
  }

  public native void startIo();

  public native void stopIo();

  public native void maybeNetwork();

  public native void setPushDeviceToken(String token);

  public native boolean backgroundFetch(int timeoutSeconds);

  public native void stopBackgroundFetch();

  public native int migrateAccount(String dbfile);

  public native boolean removeAccount(int accountId);

  public native int[] getAll();

  public NcContext getAccount(int accountId) {
    return new NcContext(getAccountCPtr(accountId));
  }

  public NcContext getSelectedAccount() {
    return new NcContext(getSelectedAccountCPtr());
  }

  public native boolean selectAccount(int accountId);

  // working with raw c-data
  private long accountsCPtr; // CAVE: the name is referenced in the JNI

  private native long createAccountsCPtr(String dir, NcEventChannel channel);

  private native void unrefAccountsCPtr();

  private native long getEventEmitterCPtr();

  private native long getJsonrpcInstanceCPtr();

  private native long getAccountCPtr(int accountId);

  private native long getSelectedAccountCPtr();

  public boolean isAllChatmail() {
    for (int accountId : getAll()) {
      NcContext ncContext = getAccount(accountId);
      if (!ncContext.isChatmail()) {
        return false;
      }
    }
    return true;
  }
}
