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

package org.nyan.dch.node;

import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.posts.Board;
import org.nyan.dch.posts.IPostAddedListener;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Storage;
import org.nyan.dch.posts.Thread;

/**
 * @author sorrge
 */
public class Node implements IRemoteResponseListener, IPostAddedListener
{
  private final HashSet<IRemoteNode> connections = new HashSet<>();
  private final SetMultimap<IRemoteNode, SHA256Hash> knownPostsByNode = Multimaps.synchronizedSetMultimap(HashMultimap.<IRemoteNode, SHA256Hash>create());
  private final HashSet<Post> knownPosts = new HashSet<>();
  public final Storage storage;

  public Node(Storage storage)
  {
    this.storage = storage;
    storage.AddPostAddedListener(this);
  }

  @Override
  public void OnlineStatusChanged(IRemoteNode node, boolean nodeIsOnline, boolean iAmInitiator)
  {
    if(nodeIsOnline)
    {
      connections.add(node);
      if(!iAmInitiator)
        for(String board : storage.GetChan().GetBoards())
          try
          {
            SortedSet<Thread> myThreads = storage.GetChan().GetBoard(board).GetThreadsChronologically();
            ArrayList<SHA256Hash> myThreadIds = new ArrayList<>(myThreads.size());
            for(Thread t : myThreads)
              myThreadIds.add(t.GetId());
            
            node.UpdateThreadsAndGetOthers(board, myThreadIds);
          }
          catch(ProtocolException ex)
          {
            System.err.format("Error updating threads on board %s from node %s: %s", board, node.toString(), ex.getMessage());            
          }
    }
    else
    {
      connections.remove(node);
      knownPostsByNode.removeAll(node);
    }
  }

  @Override
  public void PostReceived(IRemoteNode node, PostData post)
  {
    //assert(connections.contains(node));
    if(!CheckPost(post))
      return;
    
    Post newPost = new Post(post);
    knownPostsByNode.put(node, newPost.GetId());   
    AddPost(newPost, node);
  }

  private void AddPost(Post newPost, IRemoteNode node)
  {
    if(knownPosts.add(newPost) && !storage.Contains(newPost))
    {
      //System.out.printf("Added post %s to node %s\n", newPost.GetId().toString(), toString());
      
      storage.Add(newPost);
      Distribute(newPost, node);
    }
  }

  private void Distribute(Post newPost, IRemoteNode excludedNode)
  {
    for(IRemoteNode otherNode : connections)
      if(otherNode != excludedNode && knownPostsByNode.put(otherNode, newPost.GetId()))
        PushPostToNode(otherNode, newPost);
  }

  private void PushPostToNode(IRemoteNode otherNode, Post newPost)
  {
    try
    {
      otherNode.PushPost(newPost.GetData());
    }
    catch(ProtocolException pe)
    {
      System.err.format("Error sending post %s to node %s: %s", newPost.GetId().toString(), otherNode.toString(), pe.getMessage());
    }
  }

  @Override
  public void UpdateThreadsAndSendOthers(IRemoteNode node, String board, Collection<SHA256Hash> otherThreads)
  {
    knownPostsByNode.putAll(node, otherThreads);
    Board b = storage.GetChan().GetBoard(board);
    SortedSet<Thread> myThreads = b.GetThreadsChronologically();
    
    //System.out.printf("Node %s requested %d threads to update from node %s\n", node.toString(), otherThreads.size(), toString());
    
    for(Thread thread : myThreads)
    {
      if(otherThreads.contains(thread.GetId()))
        GetOtherPosts(node, board, thread.GetId(), thread.GetPostIds());
      else
        for(Post p : thread.GetPosts())
          PushPostToNode(node, p);
    }
    
    for(SHA256Hash threadId : otherThreads)
      if(!b.GetThreads().contains(threadId))
        GetOtherPosts(node, board, threadId, new ArrayList<>()); // request the entire thread
  }

  private void GetOtherPosts(IRemoteNode node, String board, SHA256Hash threadId, Collection<SHA256Hash> posts)
  {
    try
    {
      node.GetOtherPostsInThread(board, threadId, posts);
    }
    catch(ProtocolException ex)
    {
      System.err.format("Error synchronizing thread %s from boards %s with node %s: %s", threadId.toString(), board, node.toString(), ex.getMessage());
    }
  }

  @Override
  public void SendOtherPostsInThread(IRemoteNode node, String board, SHA256Hash threadId, Collection<SHA256Hash> otherPosts)
  {
    knownPostsByNode.putAll(node, otherPosts);
    Thread t = storage.GetChan().GetBoard(board).GetThread(threadId);
    if(t == null)
    {
      //System.out.printf("Requested thread %s not found on node %s\n", threadId.toString(), toString());
      return;
    }
    
    //System.out.printf("Syncing thread %s from node %s with node %s\n", threadId.toString(), toString(), node.toString());    
    
    Set<SHA256Hash> otherSet = new HashSet<>(otherPosts);
    for(Post p : t.GetPosts())
      if(!otherSet.contains(p.GetId()))
        PushPostToNode(node, p);
    
    Set<SHA256Hash> toPull = Sets.difference(otherSet, t.GetPostIds());
    if(!toPull.isEmpty())
      try
      {
        node.PullPosts(toPull);
      }
      catch (ProtocolException ex)
      {
        System.err.printf("Error requesting %d posts from node %s: %s\n", toPull.size(), node.toString(), ex.getMessage());
      }
  }

  @Override
  public void SendPosts(IRemoteNode node, Collection<SHA256Hash> postsNeeded)
  {
    for(SHA256Hash id : postsNeeded)
      if(storage.Contains(id))
      {
        PushPostToNode(node, storage.GetPost(id));
        knownPostsByNode.put(node, id);
      }
  }
  
  public void AddLocalPost(Post post)
  {
    AddPost(post, null);
  }
  
  public Collection<IRemoteNode> Connections()
  {
    return connections;
  }

  @Override
  public void PostAdded(Post post)
  {
    Distribute(post, null);
  }

  private boolean CheckPost(PostData post)
  {
    String body = post.GetBody();
    if(body.length() == 0 || body.length() > 500)
      return false;
    
    if(post.GetSentAt().getTime() - new Date().getTime() > 3 * 60 * 1000)
      return false;
    
    if(body.codePoints().anyMatch(cp -> Character.isISOControl(cp)))
      return false;
    
    return true;
  }
}
