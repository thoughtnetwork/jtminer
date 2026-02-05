# jtminer
Java block miner for Thought Network

This is a Cuckoo Cycle-based solo miner for the Thought Network.

### Building ###
Building jtminer requires Java 8 (or higher) Development Kit and Maven.

jtminer depends on the [thought4j RPC library] (https://github.com/thoughtnetwork/thought4j).  
Clone and install thought4j before building jtminer.

`git clone https://github.com/thoughtnetwork/thought4j.git`  
`cd thought4j`  
`mvn install`  

Once thought4j is installed, clone and build jtminer.

`git clone https://github.com/thoughtnetwork/jtminer`  
`cd jtminer`  
`mvn install`  

The build will produce a shaded jar file in the target directory of the repository.  

### Running ###
Mining with jtminer requires a running Thought wallet or Thought daemon with the RPC server enabled.  Binary distributions of a daemon and wallet can be found at https://github.com/thoughtnetwork/thought-wallet.

Install the distribution, then run the wallet.  This will sync the wallet with the Thought blockchain, and generate the local configuration directory.  Close the wallet and edit the Thought configuration file to enable the RPC server.  The Thought configuration file can be found on Linux and Mac in /home/username/.thoughtcore/thought.conf, and on Windows at C:/Users/username/AppData/Roaming/ThoughtCore/thought.conf.  Create the thought.conf file in the appropriate location if one does not exist.

Add the following lines to the configuration file:  
`server=1`  
`rpcuser=someusername`  
`rpcpassword=somepassword`  

Run the wallet again, and the RPC service will be enabled.

Using the wallet interface, generate a receiving address (File menu, Receiving Addresses).  

Execute jtminer from a command prompt in the jtminer directory:  
`java -jar target/jtminer-0.4-SNAPSHOT-jar-with-dependencies.jar --help`    
This will display the usage message for jtminer.    
```
usage: Miner
 -c,--coinbase-addr <arg>   Address to deliver coinbase reward to
 -D,--debug <arg>           Set debugging output on
 -f,--config <arg>          Configuration file to load options from
 -h,--host <arg>            Thought RPC server host (default: localhost)
 -H,--help <arg>            Displays usage information
 -P,--port <arg>            Thought RPC server port (default: 10617)
 -p,--password <arg>        Thought server RPC password
 -t,--threads <arg>         Number of miner threads to use
 -u,--user <arg>            Thought server RPC user
```

To start mining on testnet, issue the following command:  
`java -jar target/jtminer-0.4-SNAPSHOT-jar-with-dependencies.jar --host localhost --port 11617 --user someusername --password somepassword --coinbase-addr the-address-created-in-wallet`  

To start mining on mainnet, issue the following command:  
`java -jar target/jtminer-0.4-SNAPSHOT-jar-with-dependencies.jar --host localhost --port 10617 --user someusername --password somepassword --coinbase-addr the-address-created-in-wallet`  

Replace the values for user and password with the ones you created in the thought.conf for rpcuser and rpcpassword, and the coinbase address with the receiving address you created in the wallet.

Optionally, arguments can be specified in a Java-style properties file (see jtminer.config.example), and the miner can be started with the following command:
`java -jar target/jtminer-0.4-SNAPSHOT-jar-with-dependencies.jar --config jtminer.properties`

This option may be more secure than specifying a password on the command line.  Note that command line options and a configuration file can be mixed.  In this case, the command line options will override values specified in the config file.



  





