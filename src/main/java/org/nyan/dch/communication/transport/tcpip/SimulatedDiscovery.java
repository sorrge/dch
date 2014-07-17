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

package org.nyan.dch.communication.transport.tcpip;

import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.AlwaysAccepting;
import org.nyan.dch.communication.IAcceptance;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.IDiscoveryListener;
import org.nyan.dch.misc.RandomSet;

/**
 *
 * @author sorrge
 */
public class SimulatedDiscovery implements IDiscovery, Runnable
{
  public static class AddressBook
  {
    private final RandomSet<IAddress> addresses = new RandomSet<>();
    private final Random rand;

    public AddressBook(Random rand)
    {
      this.rand = rand;
    }
    
    public synchronized void Add(IAddress addr)
    {
      addresses.add(addr);
    }
    
    public synchronized IAddress Get()
    {
      return addresses.isEmpty() ? null : addresses.get(rand.nextInt(addresses.size()));
    }
    
    public synchronized void Remove(IAddress addr)
    {
      addresses.remove(addr);
    }
  }
  
  private final AddressBook addressBook;
  private boolean discovering = false;
  private IDiscoveryListener listener;
  private boolean shouldWork = true;
  private final IAddress myAddress;
  private final AlwaysAccepting acceptance = new AlwaysAccepting();

  public SimulatedDiscovery(AddressBook addressBook, IAddress myAddress)
  {
    this.addressBook = addressBook;
    this.myAddress = myAddress;
  }
  
  @Override
  public void BeginDiscovery()
  {
    discovering = true;
  }

  @Override
  public void EndDiscovery()
  {
    discovering = false;
  }

  @Override
  public void SetDiscoveryListener(IDiscoveryListener listener)
  {
    this.listener = listener;
  }
  
  @Override
  public void run()
  {
    while(shouldWork)
    {
      try
      {
        if(discovering && listener != null)
        {
          IAddress addr = addressBook.Get();
          if(addr != null)
            listener.AddAddress(addr);
        }
        
        Thread.sleep(300);
      }
      catch (InterruptedException ex)
      {
        break;
      }
    }
  }  
  
  @Override
  public void Close()
  {
    shouldWork = false;
  }  

  @Override
  public IAddress GetMyAddress()
  {
    return myAddress;
  }  
  

  @Override
  public void Restart()
  {
    shouldWork = true;
  } 

  @Override
  public IAcceptance GetAcceptance()
  {
    return acceptance;
  }  
}
