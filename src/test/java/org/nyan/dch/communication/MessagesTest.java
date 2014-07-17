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

package org.nyan.dch.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import org.junit.Test;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.node.IRemoteNode;
import org.nyan.dch.node.IRemoteResponseListener;
import org.nyan.dch.posts.Board;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Thread;
import org.nyan.dch.posts.StorageTest;

/**
 *
 * @author sorrge
 */
public class MessagesTest
{
  static class TestListener implements IRemoteResponseListener
  {
    RemoteNodeMessages.MessageTypes type;
    PostData post;
    ArrayList<SHA256Hash> hashes = new ArrayList<>();
    String board;
    SHA256Hash thread;

    @Override
    public void PostReceived(IRemoteNode node, PostData post)
    {
      type = RemoteNodeMessages.MessageTypes.PushPost;
      this.post = post;
    }

    @Override
    public void OnlineStatusChanged(IRemoteNode node, boolean isOnline, boolean iAmInitiator)
    {
    }

    @Override
    public void UpdateThreadsAndSendOthers(IRemoteNode node, String board, Collection<SHA256Hash> myThreads)
    {
      type = RemoteNodeMessages.MessageTypes.UpdateThreadsAndGetOthers;
      this.board = board;
      hashes.addAll(myThreads);
    }

    @Override
    public void SendOtherPostsInThread(IRemoteNode node, String board, SHA256Hash threadId, Collection<SHA256Hash> myPosts)
    {
      type = RemoteNodeMessages.MessageTypes.GetOtherPostsInThread;
      this.board = board;
      this.thread = threadId;
      hashes.addAll(myPosts);    
    }

    @Override
    public void SendPosts(IRemoteNode node, Collection<SHA256Hash> postsNeeded)
    {
      type = RemoteNodeMessages.MessageTypes.PullPosts;
      hashes.addAll(postsNeeded);
    }
  }
  
  class TestConnections implements IConnections
  {
    @Override
    public boolean AddAddress(IAddress address)
    {
      return false;
    }

    @Override
    public void Connected(IRemoteHost host, boolean iAmTheInitiator)
    {
    }

    @Override
    public void Disconnected(IRemoteHost host)
    {
    }

    @Override
    public void ConnectionFailed(IAddress host)
    {
    }

    @Override
    public INetworkTransport GetTransport()
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void AddressSupplied(IRemoteHost host, IAddress address, long cookie, boolean wandAcceptanceConfirmation)
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
  }
  
  class Loopback implements IRemoteHost
  {
    byte[] sentData;
    IRemoteHostListener node;
    
    @Override
    public void SendData(byte[] data)
    {
      sentData = data;
      assert(sentData.length <= RemoteNodeMessages.MaxMessageSize);  
      try
      {
        node.ReceiveData(data);
      }
      catch (ProtocolException ex)
      {
        fail();
      }
    }

    @Override
    public void SetReceiveListener(IRemoteHostListener node)
    {
      this.node = node;      
    }

    @Override
    public IAddress GetAddress()
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void Disconnect()
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void SetAddress(IAddress address, long cookie)
    {
    }

    @Override
    public long GetCookie()
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
  }
  
  /**
   * Test of EncodePushPost method, of class Messages.
   * @throws java.lang.Exception
   */
  @Test
  public void testEncodePushPost() throws Exception
  {
    Random rand = new Random(12);
    ArrayList<Post> posts = StorageTest.GenerateStream(10000, rand, 100, "b", "e", "d");
    Loopback h = new Loopback();
    RemoteNodeMessages n = new RemoteNodeMessages(h, new TestConnections());
    h.SetReceiveListener(n);
    TestListener l = new TestListener();
    n.SetResponseListener(l, true);
    for(Post p : posts)
    {
      n.PushPost(p.GetData());
      assert(l.type == RemoteNodeMessages.MessageTypes.PushPost);
      assert(new Post(l.post).equals(p));
    }
  }

  /**
   * Test of EncodePullPosts method, of class Messages.
   */
  @Test
  public void testEncodePullPosts() throws Exception
  {
    Random rand = new Random(12);
    int numHashes = 100000;
    ArrayList<SHA256Hash> hashes = GenerateHashes(numHashes, rand);
    Loopback h = new Loopback();
    RemoteNodeMessages n = new RemoteNodeMessages(h, new TestConnections());
    h.SetReceiveListener(n);    
    TestListener l = new TestListener();
    n.SetResponseListener(l, true);
    
    n.PullPosts(hashes);
    assert(l.type == RemoteNodeMessages.MessageTypes.PullPosts);    
    assert(l.hashes.equals(hashes));
  }

  private ArrayList<SHA256Hash> GenerateHashes(int numHashes, Random rand)
  {
    ArrayList<SHA256Hash> hashes = new ArrayList<>(numHashes);
    for(int i = 0; i < numHashes; ++i)
    {
      byte[] hash = new byte[SHA256Hash.BytesLength];
      rand.nextBytes(hash);
      hashes.add(new SHA256Hash(hash));
    }
    return hashes;
  }

  /**
   * Test of EncodeUpdateThreadsAndGetOthers method, of class Messages.
   */
  @Test
  public void testEncodeUpdateThreadsAndGetOthers() throws Exception
  {
    Random rand = new Random(12);
    ArrayList<SHA256Hash> hashes = GenerateHashes(Board.MaxThreads, rand);
    Loopback h = new Loopback();
    RemoteNodeMessages n = new RemoteNodeMessages(h, new TestConnections());
    h.SetReceiveListener(n);    
    TestListener l = new TestListener();
    n.SetResponseListener(l, true);
    
    n.UpdateThreadsAndGetOthers("b", hashes);
    assert(l.type == RemoteNodeMessages.MessageTypes.UpdateThreadsAndGetOthers);
    assert(l.board.equals("b"));
    assert(l.hashes.equals(hashes));    
  }

  /**
   * Test of EncodeGetOtherPostsInThread method, of class Messages.
   */
  @Test
  public void testEncodeGetOtherPostsInThread() throws Exception
  {
    Random rand = new Random(12);
    int numPosts = Thread.MaxPosts * 5;
    ArrayList<SHA256Hash> hashes = GenerateHashes(numPosts, rand);
    SHA256Hash thread = hashes.get(numPosts - 1);
    hashes.remove(numPosts - 1);
    Loopback h = new Loopback();
    RemoteNodeMessages n = new RemoteNodeMessages(h, new TestConnections());
    h.SetReceiveListener(n);    
    TestListener l = new TestListener();
    n.SetResponseListener(l, true);
    
    n.GetOtherPostsInThread("b", thread, hashes);
    assert(l.type == RemoteNodeMessages.MessageTypes.GetOtherPostsInThread);
    assert(l.board.equals("b"));
    assert(l.thread.equals(thread));
    assert(l.hashes.equals(hashes));      
  }  
}
