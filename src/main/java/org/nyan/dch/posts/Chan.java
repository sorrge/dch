/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nyan.dch.posts;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author sorrge
 */
public class Chan implements Serializable
{
  private final Map<String, Board> boards = new HashMap<>();
  
  public Chan(Storage storage, String... boardNames)
  {
    for(String name : boardNames)
      boards.put(name, new Board(name, storage));
  }
  
  public void Add(Post post)
  {
    Board board = boards.get(post.GetBoard());
    if(board != null)
      board.Add(post);
  }

  void Wipe()
  {
    for(Board b : boards.values())
      b.Wipe();
  }
  
  public Set<String> GetBoards()
  {
    return boards.keySet();
  }

  public Board GetBoard(String name)
  {
    return boards.get(name);
  }
}
