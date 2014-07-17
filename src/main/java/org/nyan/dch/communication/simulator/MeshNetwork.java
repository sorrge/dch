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

import com.google.common.collect.HashMultimap;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.Storage;

/**
 *
 * @author sorrge
 */
public class MeshNetwork
{
  public static final int TargetNodeConnections = 10;
  
  public final HashMultimap<Node, RemoteNode> net = HashMultimap.create();
  public final ArrayList<Node> nodes = new ArrayList<>();
  public final Stats stats = new Stats();
  public final String[] boardNames;
  
  public MeshNetwork(int numNodes, Random rand, String... boardNames)
  {
    this.boardNames = boardNames;
    nodes.ensureCapacity(numNodes);
    for(int i = 0; i < numNodes; ++i)
      nodes.add(new Node(new Storage(boardNames)));
    
    for(int i = 0; i < numNodes; ++i)
      ConnectNode(nodes.get(i), rand);
  }

  private void ConnectNode(Node n, Random rand)
  {
    Set<RemoteNode> connections = net.get(n);
    while(connections.size() < TargetNodeConnections)
    {
      Node n2 = nodes.get(rand.nextInt(nodes.size()));
      if(n2 == n || connections.contains(n2))
        continue;
      
      RemoteNode r1 = new RemoteNode(n, true, stats);
      RemoteNode r2 = new RemoteNode(n2, false, stats);
      r1.Connect(r2);
      r2.Connect(r1);
      n.OnlineStatusChanged(r2, true, true);
      n2.OnlineStatusChanged(r1, true, false);
      stats.messagesPassed += 2;
      net.put(n, r2);
      net.put(n2, r1);
    }
  }
  
  public Node AddNode(Random rand)
  {
    Node n = new Node(new Storage(boardNames));
    AddNode(n, rand);
    return n;
  }
  
  public void AddNode(Node node, Random rand)
  {
    nodes.add(node);
    ConnectNode(node, rand);    
  }
  
  public Node RemoveNode(int i)
  {
    Node n = nodes.remove(i);
    for(RemoteNode rn : net.get(n))
    {
      for(RemoteNode rn2 : net.get(rn.node))
        if(rn2.node == n)
        {
          rn.node.OnlineStatusChanged(rn2, false, false);
          net.remove(rn.node, rn2);
          break;
        }
      
      n.OnlineStatusChanged(rn, false, true);
    }
    
    net.removeAll(n);    
    return n;
  }
}
