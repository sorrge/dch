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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import org.nyan.dch.misc.IStoppable;

/**
 *
 * @author sorrge
 */
public class AcceptanceChecker implements IAcceptance, IStoppable
{
  private boolean canAccept = true, acceptanceConfirmed = false;
  private Timer timer = null;
  
  @Override
  public void AcceptanceConfirmationRequested()
  {
    timer = new Timer();
    timer.schedule(new TimerTask()
    {
      @Override
      public void run()
      {
        if(!acceptanceConfirmed)
        {
          System.out.println("We can't accept connections, it seems.");          
          canAccept = false;
          acceptanceConfirmed = true;
        }
        
        timer.cancel();
        timer = null;
      }
    }, 1000 * 60);
  }

  @Override
  public void AcceptanceConfirmed()
  {
    if(!acceptanceConfirmed || !canAccept)
      System.out.println("We can accept connections.");
    
    acceptanceConfirmed = true;
    canAccept = true;
  }

  @Override
  public boolean CanAcceptConnections()
  {
    return canAccept;
  }

  @Override
  public boolean NeedToConfirmAcceptance()
  {
    return !acceptanceConfirmed;
  }

  @Override
  public void Close()
  {
    if(timer != null)
    {
      timer.cancel();
      timer = null;
    }
  }
}
