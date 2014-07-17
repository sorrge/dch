/*
 * The MIT License
 *
 * Copyright 2014 sorrge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.nyan.dch.communication.transport.tcpip;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import org.junit.Test;
import org.nyan.dch.communication.AlwaysAccepting;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAcceptance;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IConnections;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.IDiscoveryListener;
import org.nyan.dch.communication.INetworkTransport;
import org.nyan.dch.communication.IRemoteHost;
import org.nyan.dch.communication.IRemoteHostListener;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.communication.RemoteNodeMessages;
import org.nyan.dch.communication.transport.simulator.Address;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Storage;
import org.nyan.dch.posts.StorageTest;

/**
 *
 * @author sorrge
 */
public class TCPServerTest
{
  static class DiscoveryStub implements IDiscovery
  {
    private final AlwaysAccepting acceptance = new AlwaysAccepting();
    
    @Override
    public void BeginDiscovery()
    {
    }

    @Override
    public void EndDiscovery()
    {
    }

    @Override
    public void SetDiscoveryListener(IDiscoveryListener listener)
    {
    }

    @Override
    public void Close()
    {
    }

    @Override
    public IAddress GetMyAddress()
    {
      return new Address(new Random(12));
    }

    @Override
    public void Restart()
    {
    }

    @Override
    public IAcceptance GetAcceptance()
    {
      return acceptance;
    }
  }
  
  static class ConnectionsStub implements IConnections, IRemoteHostListener
  {
    final HashSet<IRemoteHost> connected = new HashSet<>();
    final ArrayList<SHA256Hash> received = new ArrayList<>();

    @Override
    public boolean AddAddress(IAddress address)
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void Connected(IRemoteHost host, boolean iAmTheInitiator)
    {
      connected.add(host);
      host.SetReceiveListener(this);
    }

    @Override
    public void Disconnected(IRemoteHost host)
    {
      System.out.printf("Disconnect from %s reported\n", host);
      connected.remove(host);
    }

    @Override
    public void ConnectionFailed(IAddress host)
    {
      System.out.printf("Connection to %s failed\n", host);
    }

    @Override
    public INetworkTransport GetTransport()
    {
      return null;
    }
    
    @Override
    public void Disconnected(boolean iAmInitiator)
    {
    }

    @Override
    public void ReceiveData(byte[] data) throws ProtocolException
    {
      received.add(SHA256Hash.Digest(data));
    }    

    @Override
    public void AddressSupplied(IRemoteHost host, IAddress address, long cookie, boolean wandAcceptanceConfirmation)
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
  }
  
  static class ServerWrapper implements INetworkTransport, Runnable
  {
    final TCPServer server;
    int connects = 0;

    public ServerWrapper(TCPServer server)
    {
      this.server = server;
    }

    @Override
    public void run()
    {
      server.run();
    }

    public void Stop()
    {
      server.Close();
    }

    @Override
    public void SetConnectionsListener(IConnections listener)
    {
      server.SetConnectionsListener(listener);
    }

    @Override
    public IAddress ReadAddress(DataInputStream stream) throws IOException
    {
      return server.ReadAddress(stream);
    }

    @Override
    public int GetCurrentConnectionAttempts()
    {
      return server.GetCurrentConnectionAttempts();
    }

    @Override
    public void Connect(IAddress toWhom, boolean test)
    {
      ++connects;
      server.Connect(toWhom, test);
    }

    @Override
    public IDiscovery GetDiscovery()
    {
      return server.GetDiscovery();
    }

    @Override
    public int GetConnectionsCount()
    {
      return server.GetConnectionsCount();
    }

    @Override
    public IRemoteHost GetConnectedHost(IAddress whom)
    {
      return server.GetConnectedHost(whom);
    }
  }

  public TCPServerTest()
  {
    Logger root = Logger.getLogger("");
    root.setLevel(Level.WARNING);
    Handler[] handlers = root.getHandlers();
    for(Handler h: handlers)
      h.setLevel(Level.WARNING);
  }
  
  
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testBind() throws IOException, InterruptedException
  {
    Random rand = new Random(12);    
    TCPServer server = new TCPServer(13456, new DiscoveryStub(), rand);
    Thread serverThread = new Thread(server);
    serverThread.start();
    Thread.sleep(1000);
    assert(serverThread.isAlive());
    server.Close();
  }
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testConnect() throws IOException, InterruptedException
  {
    Random rand = new Random(12);    
    int p1 = 13457, p2 = 13458;
    ConnectionsStub c1 = new ConnectionsStub(), c2 = new ConnectionsStub();
    TCPServer server1 = new TCPServer(p1, new DiscoveryStub(), rand), server2 = new TCPServer(p2, new DiscoveryStub(), rand);
    server1.SetConnectionsListener(c1);
    server2.SetConnectionsListener(c2);
    Thread serverThread1 = new Thread(server1), serverThread2 = new Thread(server2);
    serverThread1.start();
    serverThread2.start();
    Thread.sleep(1000);
    server1.Connect(new IPAddress(InetAddress.getLocalHost(), p2), false);
    Thread.sleep(1000);    
    assert(serverThread1.isAlive());
    assert(serverThread2.isAlive());
    assert(c1.connected.size() == 1);
    assert(c2.connected.size() == 1);
    
    IRemoteHost h1 = c1.connected.iterator().next(), h2 = c2.connected.iterator().next();
    
    System.out.println(h1.GetAddress());
    System.out.println(h2.GetAddress());
    
    h2.Disconnect();
    Thread.sleep(1000);    

    assert(server1.GetConnectionsCount() == 0);
    assert(server2.GetConnectionsCount() == 0);    

    server1.Close();
    server2.Close();  
  }
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testNet() throws IOException, InterruptedException
  {
    int p0 = 13459;
    int numNodes = 30;
    Random rand = new Random(12);
    
    ArrayList<ConnectionsStub> cs = new ArrayList<>(numNodes);
    ArrayList<TCPServer> servers = new ArrayList<>(numNodes);
    
    for(int i = 0; i < numNodes; ++i)
    {
      ConnectionsStub c1 = new ConnectionsStub();
      TCPServer server = new TCPServer(p0 + i, new DiscoveryStub(), rand);
      server.SetConnectionsListener(c1);
      Thread serverThread = new Thread(server);
      serverThread.start();
      cs.add(c1);
      servers.add(server);
    }
    
    Thread.sleep(1000);
    for(TCPServer s : servers)
      for(int i = 0; i < Connections.WantConnections; ++i)
        s.Connect(new IPAddress(InetAddress.getLocalHost(), p0 + rand.nextInt(numNodes)), false);
    
    Thread.sleep(5000);
    
    for(ConnectionsStub c : cs)
      assert(c.connected.size() >= Connections.WantConnections - 1);
    
    for(TCPServer s : servers)
      s.Close();
  }
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testTransfer() throws IOException, InterruptedException, ProtocolException
  {
    int p1 = 14457, p2 = 14458;
    int numMsg = 10000;
    Random rand = new Random(12);
    
    ConnectionsStub c1 = new ConnectionsStub(), c2 = new ConnectionsStub();
    TCPServer server1 = new TCPServer(p1, new DiscoveryStub(), rand), server2 = new TCPServer(p2, new DiscoveryStub(), rand);
    server1.SetConnectionsListener(c1);
    server2.SetConnectionsListener(c2);
    Thread serverThread1 = new Thread(server1), serverThread2 = new Thread(server2);
    serverThread1.start();
    serverThread2.start();
    Thread.sleep(1000);
    server1.Connect(new IPAddress(InetAddress.getLocalHost(), p2), false);
    Thread.sleep(1000);    
    assert(serverThread1.isAlive());
    assert(serverThread2.isAlive());
    assert(c1.connected.size() == 1);
    assert(c2.connected.size() == 1);
    
    HashSet<SHA256Hash> messages = new HashSet<>(numMsg);
    for(int i = 0; i < numMsg; ++i)
    {
      ConnectionsStub c = rand.nextBoolean() ? c1 : c2;
      byte[] msg = new byte[rand.nextInt(RemoteNodeMessages.MaxMessageSize) + 1];
      rand.nextBytes(msg);
      messages.add(SHA256Hash.Digest(msg));
      c.connected.iterator().next().SendData(msg);
    }
    
    Thread.sleep(5000);
    HashSet<SHA256Hash> receivedMessages = new HashSet<>(c1.received);
    receivedMessages.addAll(c2.received);
    assert(messages.equals(receivedMessages));
    
    server1.Close();
    server2.Close();    
  }  
  
  
  
  
  
  
  
  
  
  
  
  
  
  /* Tests using the real Node */
    
  static class NodeData
  {
    final Node node;
    SimulatedDiscovery discovery;
    ServerWrapper server;
    Thread serverThread, discThread;
    Connections connections;
    final SimulatedDiscovery.AddressBook book;
    final Random rand;
    int port;
    
    public NodeData(SimulatedDiscovery.AddressBook book, int port, Random rand) throws IOException
    {
      this.book = book;
      this.rand = rand;
      this.port = port;
      
      IPAddress addr = new IPAddress(InetAddress.getLocalHost(), port);
      book.Add(addr);
      discovery = new SimulatedDiscovery(book, addr);
      discThread = new Thread(discovery);
      server = new ServerWrapper(new TCPServer(port, discovery, rand));
      node = new Node(new Storage("b", "e", "d"));
      connections = new Connections(server, node, rand);
      serverThread = new Thread(server);
    }
    
    public void Start()
    {
      discThread.start();
      serverThread.start();
    }
    
    public void Stop() throws InterruptedException
    {
      server.Stop();
      serverThread.join(10000);
      discThread.join(10000);
      
      assert(!serverThread.isAlive());
      assert(!discThread.isAlive());
    }
    
    public void Restart() throws IOException
    {
      boolean restart = true;
      while(true)
        try
        {
          if(restart)
            server.server.Restart();
          else
          {
            System.out.printf("Could not bid to port %d, trying another\n", port);
            book.Remove(discovery.GetMyAddress());
            port += rand.nextInt(1000) + 1000;
            IPAddress addr = new IPAddress(InetAddress.getLocalHost(), port);
            book.Add(addr);
            discovery = new SimulatedDiscovery(book, addr);
            server = new ServerWrapper(new TCPServer(port, discovery, rand)); 
            connections = new Connections(server, node, rand);             
          }
          
          break;
        }
        catch(BindException be)
        {
          restart = false;
        }
      
      discThread = new Thread(discovery);
      serverThread = new Thread(server);  
      
      Start();
      connections.EnsureConnected();      
    }
  }
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testBind2() throws IOException, InterruptedException
  {
    Random rand = new Random(12);
    SimulatedDiscovery.AddressBook book = new SimulatedDiscovery.AddressBook(rand);
    NodeData nd = new NodeData(book, 23456, rand);
    nd.Start();
    Thread.sleep(1000);
    assert(nd.serverThread.isAlive());
    nd.server.Stop();    
  }
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void test2() throws IOException, InterruptedException
  {
    Random rand = new Random(12);
    for(int repeat = 0; repeat < 10; ++repeat)
    {
      int p0 = 23457;                
      System.out.printf("Repeat %d\n", repeat);
      SimulatedDiscovery.AddressBook book = new SimulatedDiscovery.AddressBook(rand);
      NodeData nd1 = new NodeData(book, p0++, rand), nd2 = new NodeData(book, p0++, rand);
      nd1.Start();
      nd2.Start();
      Thread.sleep(500);
      assert(nd1.serverThread.isAlive());
      assert(nd2.serverThread.isAlive());
      assert(nd1.discThread.isAlive());
      assert(nd2.discThread.isAlive());
      
      for(int i = 0; i < 10; ++i)
        if(!nd1.node.Connections().isEmpty() || !nd2.node.Connections().isEmpty())
          break;
        else
        {
          System.out.println("Still not connected...");
          Thread.sleep(250);
        }
      
      assert(nd1.node.Connections().size() == 1);
      assert(nd2.node.Connections().size() == 1);
      assert(nd1.server.connects <= 1);
      assert(nd1.server.connects <= 1);

      Post p = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
                new Date()));

      nd1.node.storage.Add(p);
      Thread.sleep(500);
      assert(nd2.node.storage.Contains(p));

      nd1.Stop();
      nd2.Stop();
    }
  }  
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testNet2() throws IOException, InterruptedException
  {
    int numNodes = 30, numPosts = 2000;
    int p0 = 24457;                
    Random rand = new Random(12);
    
    for(int repeat = 0; repeat < 1; ++repeat)
    {
      System.out.printf("Repeat %d\n", repeat);

      System.out.printf("Connecting %d hosts\n", numNodes);

      SimulatedDiscovery.AddressBook book = new SimulatedDiscovery.AddressBook(rand);
      ArrayList<NodeData> nodes = new ArrayList<>(numNodes);
      for(int i = 0; i < numNodes; ++i)
      {
        NodeData nd;
        while(true)
          try
          {
            nd = new NodeData(book, p0++, rand);
            break;
          }
          catch(BindException ex)
          {
            System.out.printf("Can't bind to port %d, trying another\n", p0 - 1);
            Thread.sleep(100);
          }
        
        nodes.add(nd);
        nd.Start();
      }

      Thread.sleep(2000);

      for(NodeData nd : nodes)
      {
        assert(nd.serverThread.isAlive());
        assert(nd.discThread.isAlive());
        if(!(nd.server.GetConnectionsCount() >= Connections.WantConnections))
          Thread.sleep(10000);
        
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
      }

      System.out.printf("Sending %d posts\n", numPosts);

      ArrayList<Post> posts = StorageTest.GenerateStream(numPosts, rand, 111, "b", "e", "d");
      for(Post p : posts)
      {
        nodes.get(rand.nextInt(numNodes)).node.storage.Add(p);
        Thread.sleep(rand.nextInt(50));
      }   

      Thread.sleep(2000);
      for(int i = 1; i < numNodes; ++i)
        StorageTest.AssertSynced(nodes.get(0).node.storage, nodes.get(i).node.storage);     

      System.out.println("Disconnecting 5 nodes");

      for(int i = 0; i < 5; ++i)
        nodes.get(i).Stop();

      Thread.sleep(2000);
      for(int i = 0; i < numNodes; ++i)
      {
        NodeData nd = nodes.get(i);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        if(i < 5)
          assert(nd.server.GetConnectionsCount() == 0);
        else
        {
          if(!(nd.server.GetConnectionsCount() >= Connections.WantConnections))
            Thread.sleep(10000);
          
          assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        }
      }

      System.out.printf("Sending %d more posts\n", numPosts);    

      posts = StorageTest.GenerateStream(numPosts, rand, 10111, "b", "e", "d");
      for(Post p : posts)
      {
        nodes.get(rand.nextInt(numNodes - 5) + 5).node.storage.Add(p);
        Thread.sleep(rand.nextInt(50));
      }

      Thread.sleep(2000);
      for(int i = 1; i < numNodes; ++i)
        StorageTest.AssertSynced(nodes.get(i < 5 ? 0 : 5).node.storage, nodes.get(i).node.storage);

      System.out.println("Reconnecting 5 nodes");

      for(int i = 0; i < 5; ++i)
        nodes.get(i).Restart();

      Thread.sleep(2000);

      for(NodeData nd : nodes)
      {
        assert(nd.serverThread.isAlive());
        assert(nd.discThread.isAlive());
        if(!(nd.server.GetConnectionsCount() >= Connections.WantConnections))
          Thread.sleep(10000);
        
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
      }

      for(int i = 1; i < numNodes; ++i)
        StorageTest.AssertSynced(nodes.get(0).node.storage, nodes.get(i).node.storage);    

      for(NodeData nd : nodes)
        nd.Stop();
    }
  }
  
  
  /**
   * Test of class TCPServer.
   */
  @Test
  public void testConnectMore2() throws IOException, InterruptedException
  {
    int numNodes = 60, toDisconnect = 30;
    int p0 = 24457;                
    Random rand = new Random(12);
    
    for(int repeat = 0; repeat < 1; ++repeat)
    {
      System.out.printf("Repeat %d\n", repeat);
      System.out.printf("Connecting %d hosts\n", numNodes);

      SimulatedDiscovery.AddressBook book = new SimulatedDiscovery.AddressBook(rand);
      ArrayList<NodeData> nodes = new ArrayList<>(numNodes);
      for(int i = 0; i < numNodes; ++i)
      {
        NodeData nd;
        while(true)
          try
          {
            nd = new NodeData(book, p0++, rand);
            break;
          }
          catch(BindException ex)
          {
            System.out.printf("Can't bind to port %d, trying another\n", p0 - 1);
            Thread.sleep(100);
          }
        
        nodes.add(nd);
        nd.Start();
      }

      Thread.sleep(15000);

      int totalConnectionRequests = 0;
      for(NodeData nd : nodes)
      {
        assert(nd.serverThread.isAlive());
        assert(nd.discThread.isAlive());
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        totalConnectionRequests += nd.server.connects;
      }
      
      System.out.println("Checking convergence...");      
      Thread.sleep(5000);
      int totalConnectionRequests2 = 0;
      for(NodeData nd : nodes)
      {
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        totalConnectionRequests2 += nd.server.connects;
      }
      
      assert(totalConnectionRequests == totalConnectionRequests2);

      System.out.printf("Disconnecting %d nodes\n", toDisconnect);

      for(int i = 0; i < toDisconnect; ++i)
        nodes.get(i).Stop();

      Thread.sleep(15000);
      totalConnectionRequests = 0;
      for(int i = 0; i < numNodes; ++i)
      {
        NodeData nd = nodes.get(i);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        if(i < toDisconnect)
          assert(nd.server.GetConnectionsCount() == 0);
        else
          assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        
        totalConnectionRequests += nd.server.connects;
      }
      
      System.out.println("Checking convergence...");
      Thread.sleep(5000);
      totalConnectionRequests2 = 0;
      for(int i = 0; i < numNodes; ++i)
      {
        NodeData nd = nodes.get(i);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        if(i < toDisconnect)
          assert(nd.server.GetConnectionsCount() == 0);
        else
          assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        
        totalConnectionRequests2 += nd.server.connects;
      }  
      
      assert(totalConnectionRequests == totalConnectionRequests2);

      System.out.printf("Reconnecting %d nodes\n", toDisconnect);

      for(int i = 0; i < toDisconnect; ++i)
        nodes.get(i).Restart();

      Thread.sleep(15000);
      totalConnectionRequests = 0;
      for(NodeData nd : nodes)
      {
        assert(nd.serverThread.isAlive());
        assert(nd.discThread.isAlive());
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        totalConnectionRequests += nd.server.connects;
      }
      
      System.out.println("Checking convergence...");      
      Thread.sleep(5000);
      totalConnectionRequests2 = 0;
      for(NodeData nd : nodes)
      {
        assert(nd.serverThread.isAlive());
        assert(nd.discThread.isAlive());
        assert(nd.server.GetConnectionsCount() >= Connections.WantConnections);
        assert(nd.node.Connections().size() == nd.server.GetConnectionsCount());
        totalConnectionRequests2 += nd.server.connects;
      }      
      
      assert(totalConnectionRequests == totalConnectionRequests2);      

      for(NodeData nd : nodes)
        nd.Stop();
    }
  }  
}
