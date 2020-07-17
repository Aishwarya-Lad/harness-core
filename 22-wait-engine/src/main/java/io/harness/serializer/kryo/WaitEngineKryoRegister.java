package io.harness.serializer.kryo;

import com.esotericsoftware.kryo.Kryo;
import io.harness.serializer.KryoRegistrar;
import io.harness.waiter.ListNotifyResponseData;
import io.harness.waiter.StringNotifyResponseData;

public class WaitEngineKryoRegister implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ListNotifyResponseData.class, 5133);
    kryo.register(StringNotifyResponseData.class, 5271);
  }
}
