/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration.client;

import junit.framework.Assert;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.tests.integration.IntegrationTestLogger;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.ServiceTestBase;
import org.hornetq.tests.util.UnitTestCase;

/**
 * A MessagePriorityTest
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class MessagePriorityTest extends UnitTestCase
{

   // Constants -----------------------------------------------------

   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;


   // Attributes ----------------------------------------------------

   private HornetQServer server;

   private ClientSession session;

   private ClientSessionFactory sf;
   private ServerLocator locator;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testMessagePriority() throws Exception
   {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(address, queue, false);

      ClientProducer producer = session.createProducer(address);

      for (int i = 0; i < 10; i++)
      {
         ClientMessage m = createTextMessage(session, Integer.toString(i));
         m.setPriority((byte)i);
         producer.send(m);
      }

      ClientConsumer consumer = session.createConsumer(queue);

      session.start();

      // expect to consumer message with higher priority first
      for (int i = 9; i >= 0; i--)
      {
         ClientMessage m = consumer.receive(500);
         Assert.assertNotNull(m);
         Assert.assertEquals(i, m.getPriority());
      }

      consumer.close();
      session.deleteQueue(queue);
   }

   /**
    * in this tests, the session is started and the consumer created *before* the messages are sent.
    * each message which is sent will be received by the consumer in its buffer and the priority won't be taken
    * into account.
    * We need to implement client-side message priority to handle this case: https://jira.jboss.org/jira/browse/JBMESSAGING-1560
    */
   public void testMessagePriorityWithClientSidePrioritization() throws Exception
   {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(address, queue, false);

      session.start();

      ClientProducer producer = session.createProducer(address);

      ClientConsumer consumer = session.createConsumer(queue);

      for (int i = 0; i < 10; i++)
      {
         ClientMessage m = createTextMessage(session, Integer.toString(i));
         m.setPriority((byte)i);
         producer.send(m);
      }

      //Wait for msgs to reach client side

      Thread.sleep(1000);

      // expect to consume message with higher priority first
      for (int i = 9; i >= 0; i--)
      {
         ClientMessage m = consumer.receive(500);

         log.info("received msg " + m.getPriority());

         Assert.assertNotNull(m);
         Assert.assertEquals(i, m.getPriority());
      }

      consumer.close();
      session.deleteQueue(queue);
   }

   public void testMessageOrderWithSamePriority() throws Exception
   {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(address, queue, false);

      ClientProducer producer = session.createProducer(address);

      ClientMessage[] messages = new ClientMessage[10];

      // send 3 messages with priority 0
      // 3 7
      // 3 3
      // 1 9
      messages[0] = createTextMessage(session, "a");
      messages[0].setPriority((byte)0);
      messages[1] = createTextMessage(session, "b");
      messages[1].setPriority((byte)0);
      messages[2] = createTextMessage(session, "c");
      messages[2].setPriority((byte)0);

      messages[3] = createTextMessage(session, "d");
      messages[3].setPriority((byte)7);
      messages[4] = createTextMessage(session, "e");
      messages[4].setPriority((byte)7);
      messages[5] = createTextMessage(session, "f");
      messages[5].setPriority((byte)7);

      messages[6] = createTextMessage(session, "g");
      messages[6].setPriority((byte)3);
      messages[7] = createTextMessage(session, "h");
      messages[7].setPriority((byte)3);
      messages[8] = createTextMessage(session, "i");
      messages[8].setPriority((byte)3);

      messages[9] = createTextMessage(session, "j");
      messages[9].setPriority((byte)9);

      for (int i = 0; i < 10; i++)
      {
         producer.send(messages[i]);
      }

      ClientConsumer consumer = session.createConsumer(queue);

      session.start();

      // 1 message with priority 9
      MessagePriorityTest.expectMessage((byte)9, "j", consumer);
      // 3 messages with priority 7
      MessagePriorityTest.expectMessage((byte)7, "d", consumer);
      MessagePriorityTest.expectMessage((byte)7, "e", consumer);
      MessagePriorityTest.expectMessage((byte)7, "f", consumer);
      // 3 messages with priority 3
      MessagePriorityTest.expectMessage((byte)3, "g", consumer);
      MessagePriorityTest.expectMessage((byte)3, "h", consumer);
      MessagePriorityTest.expectMessage((byte)3, "i", consumer);
      // 3 messages with priority 0
      MessagePriorityTest.expectMessage((byte)0, "a", consumer);
      MessagePriorityTest.expectMessage((byte)0, "b", consumer);
      MessagePriorityTest.expectMessage((byte)0, "c", consumer);

      consumer.close();
      session.deleteQueue(queue);

   }

   // https://jira.jboss.org/jira/browse/HORNETQ-275
   public void testOutOfOrderAcknowledgement() throws Exception
   {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(address, queue, false);

      ClientProducer producer = session.createProducer(address);

      ClientConsumer consumer = session.createConsumer(queue);

      session.start();

      for (int i = 0; i < 10; i++)
      {
         ClientMessage m = createTextMessage(session, Integer.toString(i));
         m.setPriority((byte)i);
         producer.send(m);
      }

      Thread.sleep(500);

      // Now we wait a little bit to make sure the messages are in the client side buffer

      // They should have been added to the delivering list in the ServerConsumerImpl in the order
      // they were sent, not priority order

      //We receive one of the messages
      ClientMessage m = consumer.receive(500);
      Assert.assertNotNull(m);
      Assert.assertEquals(9, m.getPriority());

      //Ack it
      m.acknowledge();

      consumer.close();

      //Close and try and receive the other ones

      consumer = session.createConsumer(queue);

      // Other messages should be received now
      // Previously there was a bug whereby if deliveries were stored on server side in send order
      // then if received in priority order, and acked
      // the ack would ack all messages up to the one received - resulting in acking
      // messages that hadn't been delivered yet
      for (int i = 8; i >= 0; i--)
      {
         m = consumer.receive(500);
         Assert.assertNotNull(m);
         Assert.assertEquals(i, m.getPriority());

         m.acknowledge();
      }

      consumer.close();

      session.deleteQueue(queue);
   }


   public void testManyMessages() throws Exception
   {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();

      session.createQueue(address, queue, false);

      ClientProducer producer = session.createProducer(address);

      for (int i = 0 ; i < 777; i++)
      {
         ClientMessage msg = session.createMessage(true);
         msg.setPriority((byte)5);
         msg.putBooleanProperty("fast", false);
         producer.send(msg);
      }

      for (int i = 0 ; i < 333; i++)
      {
         ClientMessage msg = session.createMessage(true);
         msg.setPriority((byte)6);
         msg.putBooleanProperty("fast", true);
         producer.send(msg);
      }

      ClientConsumer consumer = session.createConsumer(queue);

      session.start();


      for (int i = 0 ; i < 333; i++)
      {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertTrue(msg.getBooleanProperty("fast"));
      }

      for (int i = 0 ; i < 777; i++)
      {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertFalse(msg.getBooleanProperty("fast"));
      }

      consumer.close();

      session.deleteQueue(queue);

      session.close();
   }


   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      Configuration config = createDefaultConfig();
      config.getAcceptorConfigurations().add(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()));
      config.setSecurityEnabled(false);
      server = addServer(HornetQServers.newHornetQServer(config, false));
      server.start();
      locator =
               addServerLocator(HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(
                                                                                                      ServiceTestBase.INVM_CONNECTOR_FACTORY)));
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }

   // Private -------------------------------------------------------

   private static void expectMessage(final byte expectedPriority,
                                     final String expectedStringInBody,
                                     final ClientConsumer consumer) throws Exception
   {
      ClientMessage m = consumer.receive(500);
      Assert.assertNotNull(m);
      Assert.assertEquals(expectedPriority, m.getPriority());
      Assert.assertEquals(expectedStringInBody, m.getBodyBuffer().readString());
   }

   // Inner classes -------------------------------------------------

}
