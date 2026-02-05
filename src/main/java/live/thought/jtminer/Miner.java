/*
 * jtminer Java mining software for the Thought Network
 * 
 * Copyright (c) 2018 - 2019, Thought Network LLC
 * 
 * Based on code from Litecoin JMiner
 * Copyright 2011  LitecoinPool.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as 
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package live.thought.jtminer;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import live.thought.jtminer.algo.Cuckoo;
import live.thought.jtminer.algo.CuckooSolve;
import live.thought.jtminer.data.BlockImpl;
import live.thought.jtminer.util.Console;
import live.thought.thought4j.ThoughtRPCClient;

/**
 * @author phil_000
 *
 */
public class Miner implements Observer
{
  /** RELEASE VERSION */
  public static final String               VERSION             = "v0.5";
  /** Options for the command line parser. */
  protected static final Options           options             = new Options();
  /** The Commons CLI command line parser. */
  protected static final CommandLineParser gnuParser           = new GnuParser();
  /** Default values for connection. */
  private static final String              DEFAULT_HOST        = "localhost";
  private static final String              DEFAULT_PORT        = "10617";
  private static final String              DEFAULT_USER        = "user";
  private static final String              DEFAULT_PASS        = "password";

  private static final String              HOST_PROPERTY       = "host";
  private static final String              PORT_PROPERTY       = "port";
  private static final String              USER_PROPERTY       = "user";
  private static final String              PASS_PROPERTY       = "password";
  private static final String              THREAD_PROPERTY     = "threads";
  private static final String              COINBASE_PROPERTY   = "coinbase-addr";
  private static final String              SCRIPT_PROPERTY     = "script";
  private static final String              DEBUG_OPTION        = "debug";
  private static final String              HELP_OPTION         = "help";
  private static final String              CONFIG_OPTION       = "config";
  private static final String              VOTING_OPTION       = "vote";
  private static final String              SCRIPT_OPTION       = "script";
  /** Longpoll client */
  private Poller                           poller;
  /** Performance metrics */
  private long                             lastWorkTime        = 0L;
  private long                             lastWorkCycles      = 0L;
  private long                             lastWorkSolutions   = 0L;
  private long                             lastWorkErrors;
  private AtomicLong                       cycles              = new AtomicLong(0L);
  private AtomicLong                       errors              = new AtomicLong(0L);
  private AtomicLong                       solutions           = new AtomicLong(0L);
  private long                             attempted           = 0L;
  private long                             accepted            = 0L;

  /** Work in progress */
  private volatile Work                    curWork             = null;
  private AtomicInteger                    cycleIndex          = new AtomicInteger(0);

  /** Single instance */
  private static Miner                     instance;
  /** Control printing of debug messages */
  private static int                       debugLevel          = 1;

  /** Runtime parameters */
  protected String                         coinbaseAddr;
  protected boolean                        moreElectricity;
  protected int                            nThreads;
  protected String                         script;

  /** Connection for solvers */
  private ThoughtRPCClient                 client;
  
  /** BIP-9 voting **/
  private List<Integer>                    voteBits;

  /** Set up command line options. */
  static
  {
    options.addOption("h", HOST_PROPERTY, true, "Thought RPC server host (default: localhost)");
    options.addOption("P", PORT_PROPERTY, true, "Thought RPC server port (default: 10617)");
    options.addOption("u", USER_PROPERTY, true, "Thought server RPC user");
    options.addOption("p", PASS_PROPERTY, true, "Thought server RPC password");
    options.addOption("t", THREAD_PROPERTY, true, "Number of miner threads to use");
    options.addOption("c", COINBASE_PROPERTY, true, "Address to deliver coinbase reward to");
    options.addOption("H", HELP_OPTION, true, "Displays usage information");
    options.addOption("D", DEBUG_OPTION, true, "Set debugging output on");
    options.addOption("f", CONFIG_OPTION, true, "Configuration file to load options from.  Command line options override config file.");
    options.addOption("v", VOTING_OPTION, true, "Comma separated list of BIP-9 soft fork voting bits to set in mined blocks.");
    options.addOption("s", SCRIPT_OPTION, true, "Add an optional script to the generated coinbase transaction.  Can be used to 'sign' mined coinbase.");

    Console.setLevel(debugLevel);
  }

  /**
   * Constructs an instance of Miner. Protected for Singleton.
   * 
   * @param host
   *          The thoughtd RPC host
   * @param port
   *          The thoughtd RPC port
   * @param user
   *          The thoughtd RPC username
   * @param pass
   *          The thoughtd RPC password
   * @param coinbase
   *          The address to assign coinbase transactions to
   * @param nThread
   *          The number of solver threads to use. Will default to the number of
   *          available CPUs.
   */
  protected Miner(String host, int port, String user, String pass, String coinbase, int nThread, List<Integer> voteBits, String script)
  {
    Console.output(String.format("@|bg_blue,fg_white jtminer %s: A Java block miner for Thought Network.|@", VERSION));
    Miner.instance = this;
    if (nThread < 1)
    {
      throw new IllegalArgumentException("Invalid number of threads: " + nThread);
    }
    else
    {
      this.nThreads = nThread;
    }
    this.coinbaseAddr = coinbase;
    this.script = script;
    URL url = null;
    try
    {
      url = new URL("http://" + user + ':' + pass + "@" + host + ":" + port + "/");
      client = new ThoughtRPCClient(url);

      // Poller thread gets its own connection
      poller = new Poller(new ThoughtRPCClient(url), voteBits);
      Thread t = new Thread(poller);
      poller.addObserver(this);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException("Invalid URL: " + url);
    }

    // Set up timer for performance metric reporting
    TimerTask reporter = new TimerTask()
    {
      public void run()
      {
        report();
      }
    };
    Timer timer = new Timer("Timer");

    long delay = 15000L;
    long period = 15000L;
    timer.scheduleAtFixedRate(reporter, delay, period);
  }

  public static Miner getInstance()
  {
    return instance;
  }

  public int getDebugLevel()
  {
    return debugLevel;
  }

  public Poller getPoller()
  {
    return poller;
  }

  public String getCoinbaseAddress()
  {
    return coinbaseAddr;
  }
  
  public String getScript()
  {
    return script;
  }

  public void incrementCycles()
  {
    cycles.incrementAndGet();
  }

  public void incrementSolutions()
  {
    solutions.incrementAndGet();
  }

  public void incrementErrors()
  {
    errors.incrementAndGet();
  }

  public void update(Observable o, Object arg)
  {
    Notification n = (Notification) arg;
    if (n == Notification.SYSTEM_ERROR)
    {
      Console.output("@|red System error|@");
      moreElectricity = false;
    }
    else if (n == Notification.PERMISSION_ERROR)
    {
      Console.output("@|red Permission Error|@");
      moreElectricity = false;
    }
    else if (n == Notification.AUTHENTICATION_ERROR)
    {
      Console.output("@|red Invalid worker username or password|@");
      moreElectricity = false;
    }
    else if (n == Notification.TERMINATED)
    {
      Console.output("@|red Poller terminated. Exiting.|@");
      moreElectricity = false;
    }
    else if (n == Notification.CONNECTION_ERROR)
    {
      Console.output("@|yellow Connection error, retrying in " + poller.getRetryPause() / 1000L + " seconds|@");
    }
    else if (n == Notification.COMMUNICATION_ERROR)
    {
      Console.output("@|red Communication error|@");
    }
    else if (n == Notification.LONG_POLLING_FAILED)
    {
      Console.output("@|red Long polling failed|@");
    }
    else if (n == Notification.LONG_POLLING_ENABLED)
    {
      Console.output("@|bold,white Long polling activated|@");
    }
    else if (n == Notification.NEW_BLOCK_DETECTED)
    {
      Console.debug("LONGPOLL detected new block", 2);
    }
    else if (n == Notification.POW_TRUE)
    {
      attempted++;
      accepted++;
      Console.output(String.format("@|bold,white Accepted block %d of %d|@ @|bold,green (yay!!!)|@", accepted, attempted));
    }
    else if (n == Notification.POW_FALSE)
    {
      attempted++;
      Console.output(String.format("@|white Rejected block attempt %d|@ @|bold,red (boo...)|@", attempted));
    }
    else if (n == Notification.NEW_WORK)
    {
      Console.debug("Getting new work.", 2);
      curWork = getPoller().getWork();
      if (null != curWork)
      {
        Console.debug("New work retrieved.", 2);
      }
      cycleIndex.set(0);
    }
  }

  protected void report()
  {
    if (lastWorkTime > 0L)
    {
      long currentCycles = cycles.get() - lastWorkCycles;
      long currentErrors = errors.get() - lastWorkErrors;
      long currentSolutions = solutions.get() - lastWorkSolutions;
      float speed = (float) currentCycles / Math.max(1, System.currentTimeMillis() - lastWorkTime);
      Console.output(String.format("%d cycles, %d solutions, %d errors, %.2f kilocycles/sec", currentCycles, currentSolutions,
          currentErrors, speed));
    }
    lastWorkTime = System.currentTimeMillis();
    lastWorkCycles = cycles.get();
    lastWorkErrors = errors.get();
    lastWorkSolutions = solutions.get();
  }

  protected static void usage()
  {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("Miner", options);
  }

  public void run()
  {
    moreElectricity = true;
    Console.output("Using " + nThreads + " threads.");

    while (moreElectricity)
    {
      ArrayList<Thread> threads = new ArrayList<Thread>(nThreads);
      if (null != curWork)
      {
        Console.debug(String.format("Target: %064x", curWork.getTarget()), 2);
        Console.debug("Starting " + nThreads + " solvers.", 2);

        BlockImpl block = curWork.getBlock();
        int blockNonce = cycleIndex.getAndIncrement();
        block.setNonce(blockNonce);
        CuckooSolve solve = new CuckooSolve(block.getHeader(), Cuckoo.NNODES, nThreads);
        for (int n = 0; n < nThreads; n++)
        {
          Solver solver = new Solver(client, curWork, cycleIndex.getAndIncrement(), solve);
          solver.addObserver(Miner.getInstance());
          Miner.getInstance().getPoller().addObserver(solver);

          Thread t = new Thread(solver);
          threads.add(t);
          t.start();
        }

        for (Thread t : threads)
        {
          try
          {
            t.join();
          }
          catch (InterruptedException e)
          {
            // Swallow
          }
        }
      }
      // Wait a bit for some work
      try
      {
        Thread.sleep(1000);
      }
      catch (InterruptedException e)
      {
        // quiet
      }
    }
  }
  
  public static void main(String[] args)
  {
    String host = null;
    int port = -1;
    String user = null;
    String pass = null;
    String coinbase = null;
    String script = null;
    int nThread = -1;
    CommandLine commandLine = null;
    List<Integer> voting = new ArrayList<Integer>();

    try
    {
      Properties props = new Properties();
      // Read the command line
      commandLine = gnuParser.parse(options, args);
      // Check for the help option
      if (commandLine.hasOption(HELP_OPTION))
      {
        usage();
        System.exit(0);
      }
      // Check for a config file specified on the command line
      if (commandLine.hasOption(CONFIG_OPTION))
      {
        try
        {
          props.load(new FileInputStream(new File(commandLine.getOptionValue(CONFIG_OPTION))));
        }
        catch (Exception e)
        {
          Console.output(String.format("@|red Specified configuration file %s unreadable or not found.|@", commandLine.getOptionValue(CONFIG_OPTION)));
          System.exit(1);
        }
      }
      // Command line options override config file values
      if (commandLine.hasOption(HOST_PROPERTY))
      {
        props.setProperty(HOST_PROPERTY, commandLine.getOptionValue(HOST_PROPERTY));
      }
      if (commandLine.hasOption(PORT_PROPERTY))
      {
        props.setProperty(PORT_PROPERTY, commandLine.getOptionValue(PORT_PROPERTY));
      }
      if (commandLine.hasOption(USER_PROPERTY))
      {
        props.setProperty(USER_PROPERTY, commandLine.getOptionValue(USER_PROPERTY));
      }
      if (commandLine.hasOption(PASS_PROPERTY))
      {
        props.setProperty(PASS_PROPERTY, commandLine.getOptionValue(PASS_PROPERTY));
      }
      if (commandLine.hasOption(THREAD_PROPERTY))
      {
        props.setProperty(THREAD_PROPERTY, commandLine.getOptionValue(THREAD_PROPERTY));
      }
      if (commandLine.hasOption(COINBASE_PROPERTY))
      {
        props.setProperty(COINBASE_PROPERTY, commandLine.getOptionValue(COINBASE_PROPERTY));
      }      
      if (commandLine.hasOption(DEBUG_OPTION) || null != props.getProperty(DEBUG_OPTION))
      {
        Console.setLevel(2);
      }
      if (commandLine.hasOption(VOTING_OPTION))
      {
        props.setProperty(VOTING_OPTION, commandLine.getOptionValue(VOTING_OPTION));
      }
      if (commandLine.hasOption(SCRIPT_OPTION))
      {
        props.setProperty(SCRIPT_OPTION, commandLine.getOptionValue(SCRIPT_OPTION));
      }
      
      host = props.getProperty(HOST_PROPERTY, DEFAULT_HOST);
      port = Integer.parseInt(props.getProperty(PORT_PROPERTY, DEFAULT_PORT));
      user = props.getProperty(USER_PROPERTY, DEFAULT_USER);
      pass = props.getProperty(PASS_PROPERTY, DEFAULT_PASS);
      coinbase = props.getProperty(COINBASE_PROPERTY);
      script = props.getProperty(SCRIPT_PROPERTY);
      if (null == coinbase)
      {
        Console.output("@|red No coinbase address specified.|@");
        usage();
        System.exit(1);
      }
      String s = props.getProperty(THREAD_PROPERTY);
      if (null == s)
      {
        nThread = Runtime.getRuntime().availableProcessors();
      }
      else
      {
        nThread = Integer.parseInt(s);
      }
      String votes = props.getProperty(VOTING_OPTION);
      if (null != votes && votes.length() > 0)
      {
        String[] bits = votes.split(",");
        for (String b : bits)
        {
          voting.add(Integer.parseInt(b));
        }     
      }
      
      Miner miner = new Miner(host, port, user, pass, coinbase, nThread, voting, script);
      miner.run();
      Console.end();
    }
    catch (ParseException pe)
    {
      System.err.println(pe.getLocalizedMessage());
      usage();
    }
    catch (Exception e)
    {
      System.err.println(e.getLocalizedMessage());
    }
  }

}
