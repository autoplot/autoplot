// SocketOpener.java
//
// Java 1.1.
// Use at your own risk!

package ftpfs.ftp;

import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;

// The SocketOpener class opens sockets with timeout.  JDK 1.1 does not
// allow sockets to be opened with timeout; this class is a workaround.
//
// This code was donated to the public domain by the author on 17 December
// 1997 in a message on Sun's "Duke Bucks" forum.
//
// Known problem: Under JDK 1.1.5, when a timeout occurs a phantom thread
// gets left behind for a while.  That's because stopping a thread that is
// blocked on winsock i/o does not take effect immediately.
//
// When this code is moved to JDK 1.2, the stop should be changed to an
// interrupt (since stop is deprecated starting with JDK 1.2).  We can't
// just use interrupt now, because it doesn't work under JDK 1.1
//
// Version: 8 May 1998
// Originally by Wayne Conrad.
// Major design improvements by Jon Steelman <steelman@mindspring.com>.

public class SocketOpener
{

  // The SocketFactory class creates the socket.

  interface SocketFactory
  {
    Socket makeSocket() throws IOException;
  };
  SocketFactory socketFactory;


  // The socket, or null if it couldn't be created.

  Socket socket;


  // The exception that occured when creating the socket, or null if no
  // exception occured.

  IOException ioexception;


  // Constructor.  This SocketOpener will use Socket (String, int) to create
  // the socket.

  public SocketOpener (final String host, final int port)
  {
    socketFactory = new SocketFactory()
    {
      public Socket makeSocket() throws IOException
        {return new Socket (host, port);}
    };
  }


  // Constructor.  This SocketOpener will use Socket (String, int) to create
  // the socket.

  public SocketOpener (final Proxy proxy )
  {
    socketFactory = new SocketFactory()
    {
      public Socket makeSocket() throws IOException
        {return new Socket (proxy);}
    };
  }

  // Constructor.  This SocketOpener will use Socket (InetAddress, int, int)
  // to create the socket.

  public SocketOpener (final InetAddress address, final int port)
  {
    socketFactory = new SocketFactory()
    {
      public Socket makeSocket() throws IOException
        {return new Socket (address, port);}
    };
  }


  // Constructor.  This SocketOpener will use Socket (String, int,
  // InetAddress, int) to create the socket.

  public SocketOpener(final String host, final int port, final InetAddress localAddr, final int localPort)
  {
    socketFactory = new SocketFactory()
    {
      public Socket makeSocket() throws IOException
      {return new Socket (host, port, localAddr, localPort);}
    };
  }


  // Constructor.  This SocketOpener will use Socket (String, int,
  // InetAddress, int) to create the socket.

  public SocketOpener
  (
  final InetAddress address, final int port,
  final InetAddress localAddr, final int localPort
  )
  {
    socketFactory = new SocketFactory()
    {
      public Socket makeSocket() throws IOException
        {return new Socket (address, port, localAddr, localPort);}
    };
  }


  // Open the socket with a timeout in milliseconds.  If timeout == 0, then
  // use the socket's natural timeout.  Note that if timeout is greater than
  // the socket's natural timeout, the natural timeout will happen anyhow.
  public synchronized Socket makeSocket (long timeout) throws IOException
  {
    socket = null;
    ioexception = null;
    Thread socketThread = new Thread()
    {
      public void run()
      {
        try {socket = socketFactory.makeSocket();}
        catch (IOException e) {ioexception = e;}
      }
    };
    socketThread.setName ("MakeSocketThread");
    socketThread.setDaemon (true);
    socketThread.start();
    try
    {socketThread.join (timeout);}
    catch (InterruptedException e)
    {}
    //socketThread.stop();
    if (ioexception != null)
      throw ioexception;
    if (socket == null)
      throw new InterruptedIOException("Operation timed out");
    return socket;
  }

/*

  // Make a test connection.

  private static void test (String address, int port, long timeout)
  {
    System.out.print ("Opening " + address + ":" + port);
    System.out.println (", timeout = " + timeout);
    long startMsec = System.currentTimeMillis();
    try
    {
      SocketOpener opener = new SocketOpener (address, port);
      Socket socket = opener.makeSocket (timeout);
      System.out.println ("Opened");
      socket.close();
    }
    catch (IOException e)
    {
      System.out.println (e);
    }
    long elapsedMillis = System.currentTimeMillis() - startMsec;
    System.out.println ("Elapsed time = " + elapsedMillis);
  }


/*
  // Test harness.

  static public void main (String[] args)
  {
    test("www.microsoft.com", 80, 0);
    test("www.microsoft.com", 81, 1000);
  }
  */
};
