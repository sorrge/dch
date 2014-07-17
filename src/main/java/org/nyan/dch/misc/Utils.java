/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nyan.dch.misc;

import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sorrge
 */
public class Utils
{
  static class Stopper implements Runnable
  {
    private final IStoppable toStop;

    public Stopper(IStoppable toStop)
    {
      this.toStop = toStop;
    }
    
    @Override
    public void run()
    {
      toStop.Close();
    }
  }
  
  public static void StopOnShutdown(IStoppable toStop)
  {
    Runtime.getRuntime().addShutdownHook(new Thread(new Stopper(toStop)));
  }
  
  
  public static void AddToBaos(ByteArrayOutputStream baos, String string)
  {
    AddToBaos(baos, string.getBytes(Charsets.UTF_8));
  }
  
  public static void AddToBaos(ByteArrayOutputStream baos, byte[] bytes)
  {
    try
    {
      baos.write(EncodeUInt32(bytes.length));
      baos.write(bytes);
    }
    catch (IOException ex)
    {
    }
  }  
  
  public static void AddToBaosConstSize(ByteArrayOutputStream baos, byte[] bytes)
  {
    try
    {
      baos.write(bytes);
    }
    catch (IOException ex)
    {
      Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
    }
  }    

  public static byte[] EncodeUInt32(long num)
  {
    byte[] res = new byte[4];
    BitcoinUtils.uint32ToByteArrayLE(num, res, 0);
    return res;
  }
  
  public static byte[] EncodeUInt64(long num)
  {
    byte[] res = new byte[8];
    BitcoinUtils.uint64ToByteArrayLE(num, res, 0);
    return res;
  }
  
  public static int ByteArrayToInt(byte[] b) 
  {
      return   b[3] & 0xFF |
              (b[2] & 0xFF) << 8 |
              (b[1] & 0xFF) << 16 |
              (b[0] & 0xFF) << 24;
  }

  public static byte[] IntToByteArray(int a)
  {
      return new byte[] 
      {
          (byte) ((a >> 24) & 0xFF),
          (byte) ((a >> 16) & 0xFF),   
          (byte) ((a >> 8) & 0xFF),   
          (byte) (a & 0xFF)
      };
  }  
}
