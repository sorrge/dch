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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.node.IRemoteNode;
import org.nyan.dch.node.IRemoteResponseListener;
import org.nyan.dch.posts.Board;
import org.nyan.dch.posts.PostData;

/**
 *
 * @author sorrge
 */
public class RemoteNodeMessages implements IRemoteNode, IRemoteHostListener
{
  public static final int MaxMessageSize = 100000, GiveAddressesCount = 10;

  public static enum MessageTypes 
  {
    PushPost(0), PullPosts(1), UpdateThreadsAndGetOthers(2), GetOtherPostsInThread(3), GiveAddresses(4), GiveMyAddress(5);
    
    public final int id;
    
    private static final HashMap<Integer, MessageTypes> idMap = new HashMap<>();

    private MessageTypes(int id)
    {
      this.id = id;
    }
    
    static
    {
      for (MessageTypes type : MessageTypes.values()) 
        idMap.put(type.id, type);
    }
    
    static MessageTypes FromID(int id)
    {
      return idMap.get(id);
    }
  };
  
  private final IRemoteHost host;
  private IRemoteResponseListener node;
  public final IConnections connections;

  public RemoteNodeMessages(IRemoteHost host, IConnections connections)
  {
    this.host = host;
    this.connections = connections;
  }

  public void SetResponseListener(IRemoteResponseListener node, boolean iAmInitiator)
  {
    this.node = node;
    node.OnlineStatusChanged(this, true, iAmInitiator);
  }
  
  @Override
  public void ReceiveData(byte[] data) throws ProtocolException
  {
    if (data.length > MaxMessageSize)
      throw new ProtocolException("Received message is too long");

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    DataInputStream str = new DataInputStream(bais);
    try
    {
      MessageTypes t = MessageTypes.FromID(str.readInt());
      if (t == null)
        throw new ProtocolException("Received an unknown message");
      
      switch (t)
      {
        case PushPost:
          node.PostReceived(this, new PostData(str));
          break;
        case PullPosts:
          node.SendPosts(this, DecodeHashes(bais, str));
          break;
        case UpdateThreadsAndGetOthers:
          String board = str.readUTF();
          if (board.length() > Board.MaxBoardNameLength)
            throw new ProtocolException(String.format("Board name is longer than %d characters", Board.MaxBoardNameLength));
          
          node.UpdateThreadsAndSendOthers(this, board, DecodeHashes(bais, str));
          break;
        case GetOtherPostsInThread:
          String board1 = str.readUTF();
          if (board1.length() > Board.MaxBoardNameLength)
            throw new ProtocolException(String.format("Board name is longer than %d characters", Board.MaxBoardNameLength));

          SHA256Hash threadId = new SHA256Hash(str);
          node.SendOtherPostsInThread(this, board1, threadId, DecodeHashes(bais, str));
          break;
        case GiveAddresses:
          int addressesRead = 0;
          while(bais.available() > 0)
          {
            if(++addressesRead > GiveAddressesCount)
              throw new ProtocolException("Too many addresses received");

            connections.AddAddress(connections.GetTransport().ReadAddress(str));
          }
          
          break;
        case GiveMyAddress:
          IAddress addr = connections.GetTransport().ReadAddress(str);
          long cookie = str.readLong();
          boolean wantConfirmation = str.readBoolean();
          connections.AddressSupplied(host, addr, cookie, wantConfirmation);
          break;
        default:
          throw new ProtocolException(String.format("Can't process message: %s", t.toString()));
      }
      
      if(bais.available() != 0)
        throw new ProtocolException("Garbage after the end of a received message");
    }
    catch (IOException ex)
    {
      throw new ProtocolException("Unknown error while parsing the message");
    }
  }
  
  private static ArrayList<SHA256Hash> DecodeHashes(ByteArrayInputStream bais, DataInputStream str) throws IOException, ProtocolException
  {
    int numHashes = bais.available() / SHA256Hash.BytesLength;
    if(numHashes * SHA256Hash.BytesLength != bais.available())
      throw new ProtocolException("Unexpected end of message");
    
    ArrayList<SHA256Hash> hashes = new ArrayList<>(numHashes);
    for(int i = 0; i < numHashes; ++i)
      hashes.add(new SHA256Hash(str));
    
    return hashes;
  }  
  
  @Override
  public void Disconnected(boolean iAmInitiator)
  {
    node.OnlineStatusChanged(this, false, iAmInitiator);
    connections.Disconnected(host);
  }
  
  @Override
  public void PushPost(PostData post) throws ProtocolException
  {
    ByteArrayOutputStream res = new ByteArrayOutputStream(MaxMessageSize / 10);
    try
    {
      DataOutputStream str = new DataOutputStream(res);
      str.writeInt(MessageTypes.PushPost.id);
      post.Write(str);
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }
    
    if(res.size() > MaxMessageSize)
      throw new ProtocolException("The message is too large"); 
    
    host.SendData(res.toByteArray());
  }

  @Override
  public void PullPosts(Collection<SHA256Hash> postsNeeded) throws ProtocolException
  {
    final int hashesPerMessage = (MaxMessageSize - Integer.SIZE / 8) / SHA256Hash.BytesLength;
    final int totalMessages = (postsNeeded.size() - 1) / hashesPerMessage + 1;
    ByteArrayOutputStream baos = new ByteArrayOutputStream(totalMessages > 1 ? MaxMessageSize : postsNeeded.size() * SHA256Hash.BytesLength + Integer.SIZE / 8);
    try
    {
      DataOutputStream str = new DataOutputStream(baos);
      str.writeInt(MessageTypes.PullPosts.id);
      int inCurrentStream = 0;
      for(SHA256Hash s : postsNeeded)
      {
        s.Write(str);
        if(++inCurrentStream >= hashesPerMessage)
        {
          host.SendData(baos.toByteArray());
          baos.reset();
          str.writeInt(MessageTypes.PullPosts.id);
          inCurrentStream = 0;
        }
      }
      
      if(inCurrentStream > 0)
        host.SendData(baos.toByteArray());        
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }
  }

  @Override
  public void UpdateThreadsAndGetOthers(String board, Collection<SHA256Hash> myThreads) throws ProtocolException
  {
    if(board.length() > Board.MaxBoardNameLength)
      throw new ProtocolException(String.format("Board name is longer than %d characters", board.length()));
    
    if(myThreads.size() > Board.MaxThreads)
      throw new ProtocolException("Too many threads to update");
    
    ByteArrayOutputStream boam = new ByteArrayOutputStream(MaxMessageSize / 10);
    try
    {
      DataOutputStream str = new DataOutputStream(boam);
      str.writeInt(MessageTypes.UpdateThreadsAndGetOthers.id);
      str.writeUTF(board);
      if(boam.size() + myThreads.size() * SHA256Hash.BytesLength > MaxMessageSize)
        throw new ProtocolException(String.format("Can't update %d threads on board %s", myThreads.size(), board));
      
      for(SHA256Hash t : myThreads)
        t.Write(str);
      
      host.SendData(boam.toByteArray());
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }      
  }

  @Override
  public void GetOtherPostsInThread(String board, SHA256Hash threadId, Collection<SHA256Hash> myPosts) throws ProtocolException
  {
    if(board.length() > Board.MaxBoardNameLength)
      throw new ProtocolException(String.format("Board name is longer than %d characters", Board.MaxBoardNameLength));
    
    ByteArrayOutputStream boam = new ByteArrayOutputStream(MaxMessageSize / 10);
    try
    {
      DataOutputStream str = new DataOutputStream(boam);
      str.writeInt(MessageTypes.GetOtherPostsInThread.id);
      str.writeUTF(board);
      threadId.Write(str);
      if(boam.size() + myPosts.size() * SHA256Hash.BytesLength > MaxMessageSize)
        throw new ProtocolException(String.format("Can't update %d posts on board %s in thread %s", myPosts.size(), board, threadId.toString()));
      
      for(SHA256Hash t : myPosts)
        t.Write(str);
      
      host.SendData(boam.toByteArray());
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }     
  }
  
  public void GiveAddresses(Collection<IAddress> addresses) throws ProtocolException
  {
    if(addresses.isEmpty() || addresses.size() > GiveAddressesCount)
      throw new ProtocolException("Wrong number of addresses given");
    
    ByteArrayOutputStream boam = new ByteArrayOutputStream(MaxMessageSize / 10);
    try
    {
      DataOutputStream str = new DataOutputStream(boam);
      str.writeInt(MessageTypes.GiveAddresses.id);
      
      for(IAddress a : addresses)
        a.Write(str);
      
      host.SendData(boam.toByteArray());
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }         
  }
  
  void GiveMyAddress(IAddress myAddress, boolean wantConfirmation) throws ProtocolException
  {
    if(myAddress == null)
      throw new ProtocolException("Wrong address given");
    
//    System.out.printf("Giving my address: %s, want confirmation: %s\n", myAddress, wantConfirmation);
    
    ByteArrayOutputStream boam = new ByteArrayOutputStream(MaxMessageSize / 10);
    try
    {
      DataOutputStream str = new DataOutputStream(boam);
      str.writeInt(MessageTypes.GiveMyAddress.id);
      myAddress.Write(str);
      str.writeLong(host.GetCookie());
      str.writeBoolean(wantConfirmation);
      host.SendData(boam.toByteArray());
    }
    catch(IOException ex)
    {
      throw new ProtocolException("Unknown error while preparing the message");
    }   
  }  
}
