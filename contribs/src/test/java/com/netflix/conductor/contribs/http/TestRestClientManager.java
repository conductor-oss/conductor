package com.netflix.conductor.contribs.http;

import com.netflix.conductor.core.config.Configuration;
import com.sun.jersey.api.client.Client;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRestClientManager {

  @Test
  public void setConfigFromConfiguration() {
    Configuration mockConfiguration = Mockito.mock(Configuration.class);
    Mockito.when(mockConfiguration.getIntProperty(Mockito.eq(RestClientManager.HTTP_TASK_CONNECT_TIMEOUT),
                                                  Mockito.eq(RestClientManager.DEFAULT_CONNECT_TIMEOUT))).thenReturn(200);
    Mockito.when(mockConfiguration.getIntProperty(Mockito.eq(RestClientManager.HTTP_TASK_READ_TIMEOUT),
                                                  Mockito.eq(RestClientManager.DEFAULT_READ_TIMEOUT))).thenReturn(170);
    RestClientManager clientManager = new RestClientManager(mockConfiguration);
    Client client =  clientManager.getClient(null);
    Assert.assertEquals(client.getProperties().get("com.sun.jersey.client.property.readTimeout"),170);
    Assert.assertEquals(client.getProperties().get("com.sun.jersey.client.property.connectTimeout"),200);
  }


  @Test
  public void clientDefaultsSetWhenRequestedAgain() {
    Configuration mockConfiguration = Mockito.mock(Configuration.class);
    Mockito.when(mockConfiguration.getIntProperty(Mockito.eq(RestClientManager.HTTP_TASK_CONNECT_TIMEOUT),
                                                  Mockito.eq(RestClientManager.DEFAULT_CONNECT_TIMEOUT))).thenReturn(200);
    Mockito.when(mockConfiguration.getIntProperty(Mockito.eq(RestClientManager.HTTP_TASK_READ_TIMEOUT),
                                                  Mockito.eq(RestClientManager.DEFAULT_READ_TIMEOUT))).thenReturn(170);
    RestClientManager clientManager = new RestClientManager(mockConfiguration);
    Client client =  clientManager.getClient(null);
    client.setReadTimeout(90);
    client.setConnectTimeout(90);
    client =  clientManager.getClient(null);
    Assert.assertEquals(client.getProperties().get("com.sun.jersey.client.property.readTimeout"),170);
    Assert.assertEquals(client.getProperties().get("com.sun.jersey.client.property.connectTimeout"),200);
  }

  @Test
  public void differentObjectsForDifferentThreads() throws InterruptedException {
    Configuration mockConfiguration = Mockito.mock(Configuration.class);
    RestClientManager clientManager = new RestClientManager(mockConfiguration);
    final Client client = clientManager.getClient(null);
    final StringBuilder result= new StringBuilder();
    Thread t1 = new Thread(()-> {Client client1 =clientManager.getClient(null);
    if(client1!=client) {
      result.append("different");
    }
    } );
    t1.start();
    t1.join();
    Assert.assertEquals(result.toString(),"different");
  }

  @Test
  public void sameObjectForSameThread() throws InterruptedException {
    Configuration mockConfiguration = Mockito.mock(Configuration.class);
    RestClientManager clientManager = new RestClientManager(mockConfiguration);
    Client client1 = clientManager.getClient(null);
    Client client2 = clientManager.getClient(null);
    Assert.assertTrue(client1==client2);
    Assert.assertTrue(client1!=null);
  }


}
