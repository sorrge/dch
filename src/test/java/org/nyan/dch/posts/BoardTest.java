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

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class BoardTest
{ 
  /**
   * Test of Add method, of class Board. Test orphans keeping
   */
  @Test
  public void testAddOrphans()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    
    long dd = 111;     
    ArrayList<Post> allPosts = StorageTest.GenerateThread(10000, rand, "b", dd);
    
    for(int i = 1; i < 10000; ++i)
      storage.Add(allPosts.get(i));
    
    assert(storage.NumPosts() == 0);
    
    storage.Add(allPosts.get(0));
    assert(storage.NumPosts() == Board.MaxOrphans + 1);
    for(int i = 0; i < Board.MaxOrphans; ++i)
      assert(storage.Contains(allPosts.get(allPosts.size() - i - 1)));
    
    CheckCapacity(storage, "b", rand, dd + 11000);
  }
  
  /**
   * Test of Add method, of class Board. Test thread formation
   */
  @Test
  public void testAddPostToThread()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    
    ArrayList<Post> allPosts = StorageTest.GenerateThread(10000, rand, "b", 111);
    for(Post p : allPosts)
      storage.Add(p);
    
    assert(storage.NumPosts() == 10000);
  }  
  
  /**
   * Test of Add method, of class Board. Test thread drowning
   */
  @Test
  public void testAddDrowning()
  {
    assert(Board.MaxThreads > 0);
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    CheckCapacity(storage, "b", rand, 111);
  }
  
  void CheckCapacity(Storage storage, String board, Random rand, long dd)
  {
    ArrayList<Post> allPosts = StorageTest.GenerateOPs(Board.MaxThreads * 5 + 10, rand, board, dd);    
    for(Post p : allPosts)
      storage.Add(p);
    
    assert(storage.GetChan().GetBoard(board).GetThreads().size() == Board.MaxThreads);
    for(int i = 0; i < Board.MaxThreads; ++i)
      assert(storage.Contains(allPosts.get(allPosts.size() - i - 1)));    
  }
  
  /**
   * Test of copying the board content
   */
  @Test
  public void testReadd()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    for(Post p : StorageTest.GenerateStream(10000, rand, 111, "b"))
      storage.Add(p);
    
    for(Post p : StorageTest.GenerateThread(Thread.MaxPosts, rand, "b", 11000))
      storage.Add(p);   
    
    Storage storage2 = new Storage("b");
    for(String board : storage.GetChan().GetBoards())
      for(SHA256Hash threadId : storage.GetChan().GetBoard(board).GetThreads())
        for(Post p : storage.GetChan().GetBoard(board).GetThread(threadId).GetPosts())
          storage2.Add(p);
    
    StorageTest.AssertSynced(storage, storage2);
    
    storage2.Wipe();
    assert(storage2.NumPosts() == 0);
    
    for(String board : storage.GetChan().GetBoards())
      for(SHA256Hash threadId : storage.GetChan().GetBoard(board).GetThreads())
        for(SHA256Hash id : storage.GetChan().GetBoard(board).GetThread(threadId).GetPostIds())
          storage2.Add(storage.GetPost(id));   
    
    StorageTest.AssertSynced(storage, storage2);
  }  
  
  /**
   * Test of Wipe method
   */
  @Test
  public void testWipe()
  {
    Storage storage = new Storage("b");
    Random rand = new Random(12); 
    ArrayList<Post> allPosts = StorageTest.GenerateStream(10000, rand, 111, "b");
    for(Post p : allPosts)
      storage.Add(p);
    
    storage.Wipe();
    assert(storage.NumPosts() == 0);
    assert(StorageTest.GatherPosts(storage).isEmpty()); 
  }    
}
