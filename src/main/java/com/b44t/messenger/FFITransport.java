package com.b44t.messenger;

import chat.nexus.rpc.BaseRpcTransport;

/* RPC transport over C FFI */
public class FFITransport extends BaseRpcTransport {
  private final NcJsonrpcInstance ncJsonrpcInstance;

  public FFITransport(NcJsonrpcInstance ncJsonrpcInstance) {
    this.ncJsonrpcInstance = ncJsonrpcInstance;
  }

  @Override
  protected void sendRequest(String jsonRequest) {
    ncJsonrpcInstance.request(jsonRequest);
  }

  @Override
  protected String getResponse() {
    return ncJsonrpcInstance.getNextResponse();
  }
}
