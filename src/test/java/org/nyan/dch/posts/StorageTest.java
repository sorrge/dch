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

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class StorageTest
{
  public static ArrayList<Post> GenerateStream(int numPosts, Random rand, long dd, String... boards)
  {
    ArrayList<Post> allPosts = new ArrayList<>(numPosts);
    for(int i = 0; i < numPosts; ++i)
    {
      Post newPost;
      if(allPosts.isEmpty() || rand.nextBoolean())
      {
        SHA256Hash thread = rand.nextBoolean() ? null : allPosts.isEmpty() || rand.nextBoolean() ? 
                SHA256Hash.Digest(String.valueOf(rand.nextInt()).getBytes()) : 
                allPosts.get(allPosts.size() - 1 - rand.nextInt(Math.min(100, allPosts.size()))).GetId();

        String board = rand.nextBoolean() ? String.valueOf((char)rand.nextInt(256)) : boards[rand.nextInt(boards.length)];

        newPost = new Post(new PostData(board, thread, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
                new Date(dd++)));
      }
      else
      {
        Post parent = allPosts.get(allPosts.size() - 1 - rand.nextInt(Math.min(100, allPosts.size())));
        newPost = new Post(new PostData(parent.GetBoard(), parent.GetThreadId() == null ? parent.GetId() : parent.GetThreadId(), String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
                new Date(dd++)));        
      }
      
      allPosts.add(newPost);
    }
    
    return allPosts;
  }
  
  public static ArrayList<Post> GenerateOPs(int numPosts, Random rand, String board, long dd)
  {
    ArrayList<Post> allPosts = new ArrayList<>(numPosts);
    for(int i = 0; i < numPosts; ++i)
      allPosts.add(new Post(new PostData(board, null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date(dd++))));
    
    return allPosts;
  }  
  
  public static Set<SHA256Hash> GatherPosts(Storage storage)
  {
    HashSet<SHA256Hash> res = new HashSet<>();
    for(String board : storage.GetChan().GetBoards())
      for(SHA256Hash threadId : storage.GetChan().GetBoard(board).GetThreads())
        res.addAll(storage.GetChan().GetBoard(board).GetThread(threadId).GetPostIds());
    
    return res;
  }
  
  public static void AssertSynced(Storage s1, Storage s2)
  {
    assert(s1.GetChan().GetBoards().equals(s2.GetChan().GetBoards()));
    for(String board : s1.GetChan().GetBoards())
    {
      assert(s1.GetChan().GetBoard(board).GetThreads().equals(s2.GetChan().GetBoard(board).GetThreads()));
      for(SHA256Hash threadId : s1.GetChan().GetBoard(board).GetThreads())
        assert(s1.GetChan().GetBoard(board).GetThread(threadId).GetPostIds().equals(s2.GetChan().GetBoard(board).GetThread(threadId).GetPostIds()));
    }
    
    Set<SHA256Hash> postsOnBoard1 = StorageTest.GatherPosts(s1), postsOnBoard2 = StorageTest.GatherPosts(s2);
    assert(postsOnBoard1.equals(postsOnBoard2));
    
    if(s1.NumPosts() != s2.NumPosts())
      fail();
    
    List<Post> posts1 = s1.GetPostsAfter(new Date(0));
    for(Post p : posts1)
      if(!s2.Contains(p))
        fail();
  }  
  
  public static void AssertApproxSynced(Storage s1, Storage s2, double percentThreads)
  {
    Set<String> boards = Sets.intersection(s1.GetChan().GetBoards(), s2.GetChan().GetBoards());
    int unmatched = 0, total = 0;
    for(String board : boards)
    {
      Set<SHA256Hash> ts1 = s1.GetChan().GetBoard(board).GetThreads(), ts2 = s2.GetChan().GetBoard(board).GetThreads();
      Set<SHA256Hash> all = Sets.union(ts1, ts2);
      Set<SHA256Hash> common = Sets.intersection(ts1, ts2);
      total += all.size();
      unmatched += all.size() - common.size();
      
      for(SHA256Hash tid : common)
        if(!s1.GetChan().GetBoard(board).GetThread(tid).GetPostIds().equals(s2.GetChan().GetBoard(board).GetThread(tid).GetPostIds()))
          ++unmatched;
    }
    
    assert(unmatched <= total * percentThreads);
  }    
  
  public static ArrayList<Post> GenerateThread(int numPosts, Random rand, String board, long dd)
  {
    Post OP = new Post(new PostData(board, null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
            new Date(dd++)));

    ArrayList<Post> allPosts = new ArrayList<>(numPosts);
    allPosts.add(OP);
      
    for(int i = 1; i < numPosts; ++i)
      allPosts.add(new Post(new PostData(board, OP.GetId(), String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date(dd++))));
    
    return allPosts;
  }    
  
  /**
   * Test of Add method, of class Storage.
   */
  @org.junit.Test
  public void testAdd()
  {
    String[] boards = new String[] {"b", "d", "e"};
    Storage storage = new Storage(boards);
    Random rand = new Random(12);
  
    ArrayList<Post> allPosts = GenerateStream(10000, rand, 111, boards);
    int maxPosts = 0;
    for(Post p : allPosts)
    {
      storage.Add(p);
      maxPosts = Math.max(maxPosts, storage.NumPosts());
    }
    
    List<Post> history = storage.GetPostsAfter(new Date(0));
    Set<SHA256Hash> postsOnBoard = GatherPosts(storage);
    assert(history.size() == postsOnBoard.size()); 
    for(Post p : history)
      assert(postsOnBoard.contains(p.GetId()));
    
    System.out.printf("Posts at the end: %1$d, max seen posts: %2$d\n", storage.NumPosts(), maxPosts);
  }

  /**
   * Test of Contains method, of class Storage.
   */
  @org.junit.Test
  public void testContains()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    
    Post OP = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
            new Date()));

    storage.Add(OP);
      
    Post newPost = new Post(new PostData("b", OP.GetId(), String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date()));
    
    assert(storage.NumPosts() == 1);
    assert(storage.Contains(OP));
    assert(!storage.Contains(newPost));
  }  
  
  /**
   * Test of GetPostsAfter method, of class Storage.
   */
  @org.junit.Test
  public void testGetPostsAfter()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    
    ArrayList<Post> allPosts = new ArrayList<>();
    int maxPosts = 0;
    long dd = 111;
    for(int i = 0; i < 100000; ++i)
    {
      Post newPost = new Post(new PostData("b", null, String.valueOf(rand.nextInt()), String.valueOf(rand.nextInt()),
              new Date(dd++)));
      
      allPosts.add(newPost);
      storage.Add(newPost);
      maxPosts = Math.max(maxPosts, storage.NumPosts());
    }
    
    int numPosts = storage.NumPosts();
    assert(numPosts > 0);
    for(int i = 0; i < numPosts; ++i)
      assert(storage.Contains(allPosts.get(allPosts.size() - i - 1)));
    
    assert(storage.GetPostsAfter(allPosts.get(allPosts.size() - 1).GetSentAt()).size() == 1);
    List<Post> end = storage.GetPostsAfter(allPosts.get(allPosts.size() - numPosts).GetSentAt());
    assert(end.size() == numPosts);
    for(int i = 0; i < numPosts; ++i)
      assert(end.get(i).equals(allPosts.get(allPosts.size() - numPosts + i)));  
    
    end = storage.GetPostsAfter(new Date(0));
    for(Post p : end)
      assert(storage.Contains(p));
  }    
}
