/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.test.messaging.core.remoting.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.messaging.core.remoting.TransportType.TCP;

import java.io.IOException;
import java.util.List;

import org.jboss.messaging.core.remoting.Client;
import org.jboss.messaging.core.remoting.PacketDispatcher;
import org.jboss.messaging.core.remoting.integration.MinaConnector;
import org.jboss.messaging.core.remoting.wireformat.AbstractPacket;
import org.jboss.messaging.core.remoting.wireformat.TextPacket;
import org.jboss.test.messaging.core.remoting.TestPacketHandler;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>.
 * 
 * @version <tt>$Revision$</tt>
 */
public class MinaClientTest extends TestSupport
{
   private ReversePacketHandler serverPacketHandler;

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testConnected() throws Exception
   {
      Client client = new Client(new MinaConnector());

      assertFalse(client.isConnected());

      client.connect("localhost", PORT, TCP);
      assertTrue(client.isConnected());

      assertTrue(client.disconnect());
      assertFalse(client.isConnected());
      assertFalse(client.disconnect());
   }
      
   public void testSendOneWay() throws Exception
   {
      serverPacketHandler.expectMessage(1);

      TextPacket packet = new TextPacket("testSendOneWay");
      packet.setVersion((byte) 1);
      packet.setTargetID(serverPacketHandler.getID());
      client.sendOneWay(packet);

      assertTrue(serverPacketHandler.await(2, SECONDS));

      List<TextPacket> messages = serverPacketHandler.getPackets();
      assertEquals(1, messages.size());
      String response = ((TextPacket) messages.get(0)).getText();
      assertEquals(packet.getText(), response);
   }

   public void testSendManyOneWay() throws Exception
   {
      serverPacketHandler.expectMessage(MANY_MESSAGES);

      TextPacket[] packets = new TextPacket[MANY_MESSAGES];
      for (int i = 0; i < MANY_MESSAGES; i++)
      {
         packets[i] = new TextPacket("testSendManyOneWay " + i);
         packets[i].setVersion((byte) 1);
         packets[i].setTargetID(serverPacketHandler.getID());
         client.sendOneWay(packets[i]);
      }

      assertTrue(serverPacketHandler.await(10, SECONDS));

      List<TextPacket> receivedPackets = serverPacketHandler.getPackets();
      assertEquals(MANY_MESSAGES, receivedPackets.size());
      for (int i = 0; i < MANY_MESSAGES; i++)
      {
         TextPacket receivedPacket = (TextPacket) receivedPackets.get(i);
         assertEquals(packets[i].getText(), receivedPacket.getText());
      }
   }

   public void testSendOneWayWithCallbackHandler() throws Exception
   {
      TestPacketHandler callbackHandler = new TestPacketHandler();
      callbackHandler.expectMessage(1);

      PacketDispatcher.client.register(callbackHandler);

      TextPacket packet = new TextPacket("testSendOneWayWithCallbackHandler");
      packet.setVersion((byte) 1);
      packet.setTargetID(serverPacketHandler.getID());
      packet.setCallbackID(callbackHandler.getID());

      client.sendOneWay(packet);

      assertTrue(callbackHandler.await(5, SECONDS));

      assertEquals(1, callbackHandler.getPackets().size());
      String response = callbackHandler.getPackets().get(0).getText();
      assertEquals(reverse(packet.getText()), response);
   }

   public void testSendBlocking() throws Exception
   {
      TextPacket request = new TextPacket("testSendBlocking");
      request.setVersion((byte) 1);
      request.setTargetID(serverPacketHandler.getID());

      AbstractPacket receivedPacket = client.sendBlocking(request);

      assertNotNull(receivedPacket);
      assertTrue(receivedPacket instanceof TextPacket);
      TextPacket response = (TextPacket) receivedPacket;
      assertEquals(reverse(request.getText()), response.getText());
   }

   public void testSendBlockingWithTimeout() throws Exception
   {
      client.setBlockingRequestTimeout(500, MILLISECONDS);
      serverPacketHandler.setSleepTime(1000, MILLISECONDS);

      AbstractPacket packet = new TextPacket("testSendBlockingWithTimeout");
      packet.setVersion((byte) 1);

      try
      {
         client.sendBlocking(packet);
         fail("a IOException should be thrown");
      } catch (IOException e)
      {
      }
   }

   // TestCase implementation ---------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      startServer(TestSupport.PORT, TRANSPORT);
      startClient(TestSupport.PORT, TRANSPORT);

      serverPacketHandler = new ReversePacketHandler();
      PacketDispatcher.server.register(serverPacketHandler);
   }

   @Override
   protected void tearDown() throws Exception
   {
      PacketDispatcher.server.unregister(serverPacketHandler.getID());

      client.disconnect();
      stopServer();
   }
}
