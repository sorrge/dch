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

package org.nyan.dch.posts;

import com.google.common.collect.HashMultimap;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class Board implements Serializable
{
  public static final int MaxThreads = 15, MaxOrphans = Thread.MaxPosts * 2, MaxBoardNameLength = 20;
  
  private final String name;
  private final Map<SHA256Hash, Thread> threads = new HashMap<>();
  private final SortedSet<Thread> threadList = new TreeSet<>(new Thread.BumpedComparator());
  private final HashMultimap<SHA256Hash, Post> orphans = HashMultimap.create();
  private final TreeSet<Post> orphansQueue = new TreeSet<>(new Post.DateIdComparator());
  private final Storage storage;
  
  public Board(String name, Storage storage)
  {
    if(name.length() > MaxBoardNameLength)
      throw new IllegalArgumentException("Board name is too long");
    
    this.name = name;
    this.storage = storage;
  }
  
  public void Add(Post post)
  {
    if(post.GetThreadId() == null)
    { // this is the OP of a new thread
      Thread newThread = new Thread(post);
      threads.put(post.GetId(), newThread);
      threadList.add(newThread);
      storage.FinalizeAdd(post);
      for(Post previouslyLoadedPost : orphans.removeAll(post.GetId()))
        AddPostToThread(newThread, previouslyLoadedPost);
      
      if(threadList.size() > MaxThreads)
      {
        Thread drowned = threadList.first();
        //System.out.printf("Thread %s has drowned\n", drowned.GetId());
        DeleteThread(drowned);
      }
    }
    else
    { // this post belongs to a previous thread
      Thread thread = threads.get(post.GetThreadId());
      if(thread == null)
      { // the thread does not exist. It means its OP has not been received yet. Put this post on hold until the OP will be received
        if(orphans.put(post.GetThreadId(), post))
        {         
          orphansQueue.add(post);          
          while(orphansQueue.size() > MaxOrphans)
          {
            Post toForget = orphansQueue.pollFirst();
            orphans.remove(toForget.GetThreadId(), toForget);
          }
        }
      }
      else
        AddPostToThread(thread, post);
    }
  }
  
  void AddPostToThread(Thread thread, Post post)
  {
    threadList.remove(thread);
    thread.Add(post);
    storage.FinalizeAdd(post);
    threadList.add(thread);
  }

  void DeleteThread(Thread drowned)
  {
    threadList.remove(drowned);
    threads.remove(drowned.GetId());
    for(Post removedPost : drowned.GetPosts())
      storage.FinalizeRemove(removedPost);
  }

  void Wipe()
  {
    Thread[] ts = new Thread[threads.size()];
    for(Thread t : threads.values().toArray(ts))
      DeleteThread(t);
    
    orphans.clear();
    orphansQueue.clear();
  }
  
  public Set<SHA256Hash> GetThreads()
  {
    return threads.keySet();
  }
  
  public Thread GetThread(SHA256Hash OPId)
  {
    return threads.get(OPId);
  }
  
  public SortedSet<Thread> GetThreadsChronologically()
  {
    return threadList;
  }
}
