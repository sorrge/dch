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

package org.nyan.dch.communication.simulator;

import org.nyan.dch.communication.simulator.RemoteNode;
import org.nyan.dch.communication.simulator.MeshNetwork;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.Test;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.node.IRemoteNode;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.StorageTest;

/**
 *
 * @author sorrge
 */
public class MeshNetworkTest
{
  /**
   * Test of network construction
   */  
  @Test
  public void testNetBuilding()
  {
    final int numNodes = 100;
    MeshNetwork net = new MeshNetwork(numNodes, new Random(12), "b", "e", "d");
    assert(net.nodes.size() == numNodes);
    for(Node n : net.nodes)
    {
      assert(net.net.get(n).size() >= MeshNetwork.TargetNodeConnections);
      for(RemoteNode n1 : net.net.get(n))
      {
        assert(n1.node != n);
        boolean found = false;
        for(RemoteNode n2 : net.net.get(n1.node))
          if(n2.node == n)
          {
            found = true;
            break;
          }
        
        assert(found);
      }
    }
    
    System.out.printf("Connections per node: %1f\n", net.net.size() / (float)numNodes);    
    System.out.printf("Messages during network build: %1d\n", net.stats.messagesPassed);
  }
  
  /**
   * Test propagation of a post
   */  
  @Test
  public void testPostPropagation()
  {
    final int numNodes = 100;
    Random rand = new Random(12);
    MeshNetwork net = new MeshNetwork(numNodes, rand, "b", "e", "d");
    
    net.stats.messagesPassed = 0;
    
    Node source = net.nodes.get(rand.nextInt(numNodes));
    Post post = new Post(new PostData("b", null, "asdf", "jkl;", new Date()));
    source.AddLocalPost(post);
    for(Node n : net.nodes)
    {
      assert(n.storage.Contains(post));
      assert(n.storage.GetChan().GetBoard("b").GetThread(post.GetId()).GetPosts().contains(post));
    }
    
    System.out.printf("Messages to propagate 1 post: %1d\n", net.stats.messagesPassed);    
  }
  
  /**
   * Test propagation of a thread
   */  
  @Test
  public void testThreadPropagation()
  {
    final int numNodes = 100;
    final int numPosts = 200;
    Random rand = new Random(12);
    MeshNetwork net = new MeshNetwork(numNodes, rand, "b", "e", "d");
    
    net.stats.messagesPassed = 0;
    
    ArrayList<Post> allPosts = StorageTest.GenerateThread(numPosts, rand, "b", 111);
    Post OP = allPosts.get(0);
    
    for(Post p : allPosts)
      net.nodes.get(rand.nextInt(numNodes)).AddLocalPost(p);      
    
    for(Node n : net.nodes)
    {
      assert(n.storage.GetChan().GetBoard("b").GetThread(OP.GetId()).GetPosts().size() == numPosts);      
      for(Post p : allPosts)
      {
        assert(n.storage.Contains(p));
        assert(n.storage.GetChan().GetBoard("b").GetThread(OP.GetId()).GetPosts().contains(p));
      }
    }
    
    System.out.printf("Messages to propagate %d posts: %d\n", numPosts, net.stats.messagesPassed);        
    System.out.printf("Messages per post: %f\n", net.stats.messagesPassed / (float)numPosts);    
    System.out.printf("Messages per post-node: %f\n", net.stats.messagesPassed / (float)numPosts / numNodes);    
  }  
  
  /**
   * Test synchronization of a node
   */  
  @Test
  public void testSync()
  {
    final int numNodes = 100;
    final int numPosts = 1000;
    Random rand = new Random(12);
    String[] boards = new String[] { "b", "e", "d" };
    MeshNetwork net = new MeshNetwork(numNodes, rand, boards);
    
    net.stats.messagesPassed = 0;
    
    ArrayList<Post> allPosts = StorageTest.GenerateStream(numPosts, rand, 111, boards);
    
    for(int i = 0; i < numPosts; ++i)
      net.nodes.get(rand.nextInt(numNodes)).AddLocalPost(allPosts.get(i));      
    
    System.out.printf("Messages to propagate %d posts: %d\n", numPosts, net.stats.messagesPassed);        
    System.out.printf("Messages per post: %f\n", net.stats.messagesPassed / (float)numPosts);    
    System.out.printf("Messages per post-node: %f\n", net.stats.messagesPassed / (float)numPosts / numNodes);
    
    Node oldNode = net.nodes.get(0);
    Node newNode = net.AddNode(rand);
    StorageTest.AssertSynced(newNode.storage, oldNode.storage);
  }    
  
  /**
   * Test propagation of a stream
   */  
  @Test
  public void testStream()
  {
    final int numNodes = 100;
    final int numPosts = 1000;
    Random rand = new Random(12);
    String[] boards = new String[] { "b", "e", "d" };
    MeshNetwork net = new MeshNetwork(numNodes, rand, boards);
    
    net.stats.messagesPassed = 0;
    
    ArrayList<Post> allPosts = StorageTest.GenerateStream(numPosts, rand, 111, boards);
    
    for(int i = 0; i < numPosts; ++i)
      net.nodes.get(rand.nextInt(numNodes)).AddLocalPost(allPosts.get(i));      
    
    for(int i = 1; i < numNodes; ++i)
      StorageTest.AssertSynced(net.nodes.get(0).storage, net.nodes.get(i).storage);
    
    System.out.printf("Messages to propagate %d posts: %d\n", numPosts, net.stats.messagesPassed);        
    System.out.printf("Messages per post: %f\n", net.stats.messagesPassed / (float)numPosts);    
    System.out.printf("Messages per post-node: %f\n", net.stats.messagesPassed / (float)numPosts / numNodes);
  } 
  
  /**
   * Test resynchronization of a node
   */  
  @Test
  public void testReSync()
  {
    final int numNodes = 100;
    final int numPosts = 1000;
    Random rand = new Random(12);
    String[] boards = new String[] { "b", "e", "d" };
    MeshNetwork net = new MeshNetwork(numNodes, rand, boards);
    
    net.stats.messagesPassed = 0;
    
    ArrayList<Post> allPosts = StorageTest.GenerateStream(numPosts, rand, 111, boards);
    
    for(int i = 0; i < numPosts; ++i)
      net.nodes.get(rand.nextInt(numNodes)).AddLocalPost(allPosts.get(i));
    
    Node n = net.RemoveNode(rand.nextInt(numNodes));
    
    allPosts = StorageTest.GenerateStream(numPosts, rand, 11000, boards);
    for(int i = 0; i < numPosts; ++i)
      net.nodes.get(rand.nextInt(numNodes - 1)).AddLocalPost(allPosts.get(i));    
    
    Node oldNode = net.nodes.get(0);
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(n.storage), postsOnBoard2 = StorageTest.GatherPosts(oldNode.storage);
    assert(!postsOnBoard1.equals(postsOnBoard2));
        
    net.AddNode(n, rand);
    StorageTest.AssertSynced(n.storage, oldNode.storage);
    postsOnBoard1 = StorageTest.GatherPosts(n.storage);
    assert(postsOnBoard1.equals(postsOnBoard2));
  }
  
  /**
   * Test synchronization of a split thread
   */  
  @Test
  public void testSyncThread()
  {
    final int numPosts = 10;
    Random rand = new Random(12);
    String[] boards = new String[] { "b", "e", "d" };
    MeshNetwork net = new MeshNetwork(2, rand, boards);
    Node n2 = net.RemoveNode(rand.nextInt(2));
    Node n1 = net.nodes.get(0);
    
    ArrayList<Post> allPosts = StorageTest.GenerateThread(numPosts, rand, "b", 111);
    n1.AddLocalPost(allPosts.get(0));
    
    for(int i = 1; i < numPosts; ++i)
      n2.AddLocalPost(allPosts.get(i));
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(n1.storage), postsOnBoard2 = StorageTest.GatherPosts(n2.storage);
    assert(!postsOnBoard1.equals(postsOnBoard2));
    
    System.out.printf("Node %s has %d posts\n", n1.toString(), StorageTest.GatherPosts(n1.storage).size());
    System.out.printf("Node %s has %d posts\n", n2.toString(), StorageTest.GatherPosts(n2.storage).size());
    
    net.AddNode(n2, rand);
    StorageTest.AssertSynced(n1.storage, n2.storage);
  }  
}
