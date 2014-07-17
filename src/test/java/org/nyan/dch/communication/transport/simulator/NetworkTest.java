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

package org.nyan.dch.communication.transport.simulator;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import static org.junit.Assert.*;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.communication.RemoteNodeMessages;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Storage;
import org.nyan.dch.posts.StorageTest;
import org.nyan.dch.posts.Thread;

/**
 *
 * @author sorrge
 */
public class NetworkTest
{

  public NetworkTest()
  {
    Logger root = Logger.getLogger("");
    root.setLevel(Level.WARNING);
    Handler[] handlers = root.getHandlers();
    for(Handler h: handlers)
      h.setLevel(Level.WARNING);    
  }
  
  /**
   * Test of class Network.
   */
  @Test
  public void testAddNode() throws ProtocolException
  {
    Random rand = new Random(12);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };    
    Node n1 = new Node(new Storage(boards)), n2 = new Node(new Storage(boards));
    net.AddNode(n1);
    net.AddNode(n2);
    
    assert(net.StepN(100));
    
    ArrayList<IAddress> addrs = new ArrayList<>(net.GetAddresses());
    assert(net.GetConnection(addrs.get(0)).GetConnectedHost(addrs.get(1)) != null);
    assert(net.GetConnection(addrs.get(1)).GetConnectedHost(addrs.get(0)) != null);
    assert(n1.Connections().size() == 1);
    assert(n2.Connections().size() == 1);
    
    Post p = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date()));
    
    n1.AddLocalPost(p);
    assert(net.StepN(100));
    assert(n2.storage.Contains(p));
    
    net.RemoveHost(addrs.get(0));
    assert(n1.Connections().isEmpty());
    assert(n2.Connections().isEmpty());   
    assert(net.GetConnection(addrs.get(0)) == null);
    assert(net.GetConnection(addrs.get(1)).GetConnectedHost(addrs.get(0)) == null);    
  }
  
  /**
   * Test of class Network.
   */
  @Test
  public void testMoreNodes() throws ProtocolException
  {
    Random rand = new Random(12);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };        
    int numNodes = 100;
    
    for(int i = 0; i < numNodes; ++i)
      net.AddNode(new Node(new Storage(boards)));
    
    assert(net.StepN(100 * numNodes));
    ArrayList<Node> allNodes = new ArrayList<>(numNodes);

    for(NetworkConnection nc : net.GetHosts())
    {
      assert(nc.Connections().size() >= Connections.WantConnections);
      assert(nc.Connections().size() == ((Node)((Connections)nc.GetConnections()).node).Connections().size());
      for(HostConnection hc : nc.Connections())
        assert(net.GetConnection(hc.GetAddress()).Connections().contains(hc.GetRemoteHost()));
      
      allNodes.add((Node)((Connections)nc.GetConnections()).node);
    }
    
    assert(allNodes.size() == numNodes);
    
    Post p = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date()));
   
    allNodes.get(rand.nextInt(numNodes)).AddLocalPost(p);
    assert(net.StepN(100 * numNodes));
    
    for(Node n : allNodes)
      assert(n.storage.Contains(p));
  }  
  
  /**
   * Test of class Network.
   */
  @Test
  public void testThreadProp() throws ProtocolException
  {
    assert(Thread.MaxPosts > 0);
    Random rand = new Random(12);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };        
    int numNodes = 100, numPosts = Thread.MaxPosts * 2;
    
    for(int i = 0; i < numNodes; ++i)
      net.AddNode(new Node(new Storage(boards)));
    
    assert(net.StepN(100 * numNodes));
    ArrayList<Node> allNodes = new ArrayList<>(numNodes);

    for(NetworkConnection nc : net.GetHosts())
      allNodes.add((Node)((Connections)nc.GetConnections()).node);
    
    assert(allNodes.size() == numNodes);
    
    ArrayList<Post> thread = StorageTest.GenerateThread(numPosts, rand, "b", 111);
    for(Post p : thread)
    {
      allNodes.get(rand.nextInt(numNodes)).AddLocalPost(p);
      assert(net.StepN(100 * numNodes));
    }
    
    for(Node n : allNodes)
      for(Post p : thread)
        assert(n.storage.Contains(p));      
  }    
  
  
  /**
   * Test of class Network.
   */
  @Test
  public void testSync() throws ProtocolException
  {
    Random rand = new Random(123);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };        
    int numNodes = 70, numPosts = 5000;
    
    for(int i = 0; i < numNodes; ++i)
      net.AddNode(new Node(new Storage(boards)));
    
    assert(net.StepN(100 * numNodes));
    ArrayList<Node> allNodes = new ArrayList<>(numNodes + 1);
    ArrayList<IAddress> addresses = new ArrayList<>(numNodes + 1);

    for(IAddress addr : net.GetAddresses())
    {
      allNodes.add((Node)((Connections)net.GetConnection(addr).GetConnections()).node);
      addresses.add(addr);
    }
    
    assert(allNodes.size() == numNodes);
    
    ArrayList<Post> posts = StorageTest.GenerateStream(numPosts, rand, 111, "b", "e", "d");
    for(Post p : posts)
    {
      allNodes.get(rand.nextInt(numNodes)).AddLocalPost(p);
      assert(net.StepN(100 * numNodes));
    }
    
    for(int i = 1; i < numNodes; ++i)
      StorageTest.AssertSynced(allNodes.get(0).storage, allNodes.get(i).storage);    
    
    Node newNode = new Node(new Storage(boards));
    IAddress newAddr = net.AddNode(newNode);
    assert(net.StepN(100 * numNodes));
    StorageTest.AssertSynced(newNode.storage, allNodes.get(0).storage);
    
    allNodes.add(newNode);
    addresses.add(newAddr);
    IAddress toDelete = addresses.get(rand.nextInt(numNodes));
    Node disconnected = (Node)((Connections)net.GetConnection(toDelete).GetConnections()).node;
    net.RemoveHost(toDelete);
    
    for(NetworkConnection nc : net.GetHosts())
      for(HostConnection hc : nc.Connections())
        assert(((Connections)((RemoteNodeMessages)hc.GetRemoteHost().GetMyNode()).connections).node != disconnected);
        
    posts = StorageTest.GenerateStream(numPosts, rand, 1110000, "b", "e", "d");
    for(Post p : posts)
    {
      Node n = allNodes.get(rand.nextInt(numNodes));
      if(n != disconnected)
        n.AddLocalPost(p);
      
      assert(net.StepN(100 * numNodes));
    }    
    
    Node oldNode = allNodes.get(0);
    if(oldNode == disconnected)
      oldNode = allNodes.get(1);
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(disconnected.storage), postsOnBoard2 = StorageTest.GatherPosts(oldNode.storage);
    assert(!postsOnBoard1.equals(postsOnBoard2)); 
    
    for(Node n : allNodes)
      System.out.printf("Node %s has %d posts\n", n, StorageTest.GatherPosts(n.storage).size());
    
    System.out.printf("Reconnecting node %s\n", disconnected.toString());
    
    net.AddNode(disconnected);
    assert(net.StepN(numPosts * 100));
    
    for(Node n : allNodes)
      System.out.printf("Node %s has %d posts\n", n, StorageTest.GatherPosts(n.storage).size());
    
    Set<SHA256Hash> missing = Sets.difference(StorageTest.GatherPosts(oldNode.storage), StorageTest.GatherPosts(disconnected.storage));
    for(SHA256Hash s : missing)
    {
      Post p = oldNode.storage.GetPost(s);
      System.out.printf("Post %s missing, thread: %s\n", s, p.GetThreadId());
    }

    for(NetworkConnection nc : net.GetHosts())
    {
      assert(nc.Connections().size() >= Math.min(Connections.WantConnections, numNodes));      
      assert(nc.Connections().size() == ((Node)((Connections)nc.GetConnections()).node).Connections().size());
      for(HostConnection hc : nc.Connections())
        assert(net.GetConnection(hc.GetAddress()).Connections().contains(hc.GetRemoteHost()));
    }
    
    StorageTest.AssertSynced(disconnected.storage, oldNode.storage);
  }    
  
  /**
   * Test synchronization of a split thread
   */  
  @Test
  public void testSyncThread() throws ProtocolException
  {
    final int numPosts = 10;
    Random rand = new Random(12);
    String[] boards = new String[] { "b", "e", "d" };
    Network net = new Network(rand);
    
    Node n1 = new Node(new Storage(boards));
    Node n2 = new Node(new Storage(boards));
    net.AddNode(n1);
    assert(net.StepN(numPosts * 100)); 

    ArrayList<Post> allPosts = StorageTest.GenerateThread(numPosts, rand, "b", 111);
    n1.AddLocalPost(allPosts.get(0));
    
    for(int i = 1; i < numPosts; ++i)
    {
      n2.AddLocalPost(allPosts.get(i));
      assert(net.StepN(numPosts * 100));       
    }
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(n1.storage), postsOnBoard2 = StorageTest.GatherPosts(n2.storage);
    assert(!postsOnBoard1.equals(postsOnBoard2));
    
    System.out.printf("Node %s has %d posts\n", n1.toString(), StorageTest.GatherPosts(n1.storage).size());
    System.out.printf("Node %s has %d posts\n", n2.toString(), StorageTest.GatherPosts(n2.storage).size());
    
    net.AddNode(n2);
    assert(net.StepN(numPosts * 100)); 

    StorageTest.AssertSynced(n1.storage, n2.storage);
  }    
  
  /**
   * Test of class Network. Syncing after some kind of network split
   */
  @Test
  public void testSyncApprox() throws ProtocolException
  {
    Random rand = new Random(123);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };        
    int numNodes = 70, numPosts = 5000;
    
    for(int i = 0; i < numNodes; ++i)
      net.AddNode(new Node(new Storage(boards)));
    
    assert(net.StepN(100 * numNodes));
    ArrayList<Node> allNodes = new ArrayList<>(numNodes + 1);
    ArrayList<IAddress> addresses = new ArrayList<>(numNodes + 1);

    for(IAddress addr : net.GetAddresses())
    {
      allNodes.add((Node)((Connections)net.GetConnection(addr).GetConnections()).node);
      addresses.add(addr);
    }
    
    assert(allNodes.size() == numNodes);
    
    ArrayList<Post> posts = StorageTest.GenerateStream(numPosts, rand, 111, "b", "e", "d");
    for(Post p : posts)
    {
      allNodes.get(rand.nextInt(numNodes)).AddLocalPost(p);
      assert(net.StepN(100 * numNodes));
    }
    
    for(int i = 1; i < numNodes; ++i)
      StorageTest.AssertSynced(allNodes.get(0).storage, allNodes.get(i).storage);    
    
    Node newNode = new Node(new Storage(boards));
    IAddress newAddr = net.AddNode(newNode);
    assert(net.StepN(100 * numNodes));
    StorageTest.AssertSynced(newNode.storage, allNodes.get(0).storage);
    
    allNodes.add(newNode);
    addresses.add(newAddr);
    IAddress toDelete = addresses.get(rand.nextInt(numNodes));
    Node disconnected = (Node)((Connections)net.GetConnection(toDelete).GetConnections()).node;
    net.RemoveHost(toDelete);
    
    for(NetworkConnection nc : net.GetHosts())
      for(HostConnection hc : nc.Connections())
        assert(((Connections)((RemoteNodeMessages)hc.GetRemoteHost().GetMyNode()).connections).node != disconnected);
        
    posts = StorageTest.GenerateStream(numPosts, rand, 1110000, "b", "e", "d");
    for(Post p : posts)
    {
      Node n = allNodes.get(rand.nextInt(numNodes));
      n.AddLocalPost(p);
      assert(net.StepN(100 * numNodes));
    }    
    
    Node oldNode = allNodes.get(0);
    if(oldNode == disconnected)
      oldNode = allNodes.get(1);
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(disconnected.storage), postsOnBoard2 = StorageTest.GatherPosts(oldNode.storage);
    assert(!postsOnBoard1.equals(postsOnBoard2)); 
    
    for(Node n : allNodes)
      System.out.printf("Node %s has %d posts\n", n, StorageTest.GatherPosts(n.storage).size());
    
    System.out.printf("Reconnecting node %s\n", disconnected.toString());
    
    net.AddNode(disconnected);
    assert(net.StepN(numPosts * 100));
    
    for(Node n : allNodes)
      System.out.printf("Node %s has %d posts\n", n, StorageTest.GatherPosts(n.storage).size());
    
    Set<SHA256Hash> missing = Sets.difference(StorageTest.GatherPosts(oldNode.storage), StorageTest.GatherPosts(disconnected.storage));
    for(SHA256Hash s : missing)
    {
      Post p = oldNode.storage.GetPost(s);
      System.out.printf("Post %s missing, thread: %s\n", s, p.GetThreadId());
    }

    for(NetworkConnection nc : net.GetHosts())
    {
      assert(nc.Connections().size() >= Math.min(Connections.WantConnections, numNodes));      
      assert(nc.Connections().size() == ((Node)((Connections)nc.GetConnections()).node).Connections().size());
      for(HostConnection hc : nc.Connections())
        assert(net.GetConnection(hc.GetAddress()).Connections().contains(hc.GetRemoteHost()));
    }
    
    StorageTest.AssertApproxSynced(disconnected.storage, oldNode.storage, 0.9);
  }    
  
  
  
  
  
  /**
   * Test of class Network.
   */
  @Test
  public void testMoreNodesNAT() throws ProtocolException
  {
    Random rand = new Random(12);
    Network net = new Network(rand);
    String[] boards = new String[] { "b", "e", "d" };        
    int numNodes = 300;
    
    for(int i = 0; i < numNodes; ++i)
      net.AddNode(new Node(new Storage(boards)), rand.nextBoolean());
    
    assert(net.StepN(100 * numNodes));
    ArrayList<Node> allNodes = new ArrayList<>(numNodes);

    for(NetworkConnection nc : net.GetHosts())
    {
      assert(nc.Connections().size() >= Connections.WantConnections);
      assert(nc.Connections().size() == ((Node)((Connections)nc.GetConnections()).node).Connections().size());
      for(HostConnection hc : nc.Connections())
        assert(net.GetConnection(hc.GetAddress()).Connections().contains(hc.GetRemoteHost()));
      
      allNodes.add((Node)((Connections)nc.GetConnections()).node);
      assert(!((Acceptance)nc.GetAcceptance()).NeedToConfirmAcceptance());
      assert(((Acceptance)nc.GetAcceptance()).CanAcceptConnections() == nc.CanAccept());
    }
    
    assert(allNodes.size() == numNodes);
    
    Post p = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date()));
   
    allNodes.get(rand.nextInt(numNodes)).AddLocalPost(p);
    assert(net.StepN(100 * numNodes));
    
    for(Node n : allNodes)
      assert(n.storage.Contains(p));
  }    
}
