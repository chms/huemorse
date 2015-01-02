/**
 * huemorse
 * 
 * Copyright (C) 2014 Christian M. Schmid
 *
 * huemorse is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.chschmid.huemorse.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import com.chschmid.huemorse.server.handler.MorseServerInitializer;
import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.model.PHLight;

/**
 * huemorse server/cli
 * @author Christian M. Schmid
 */
public class HueMorse {
	public static boolean DEBUG = false;
	public static boolean cli = false;
	public static boolean search = false;
	
	public static final String DEFAULT_USERNAME  = "newdeveloper";
	public static final int    DEFAULT_LIGHT_ID  = 1;
	
	private static String username = DEFAULT_USERNAME;
	private static String bridgeIP = "";
	private static int lightID     = DEFAULT_LIGHT_ID;
	
	private static HueMorseInterface hue;
	
	public static final String HUEMORSE  = "huemorse";
	public static final String TITLE     = HUEMORSE + ", Copyright (C) 2014 Christian M. Schmid";
	public static final String ERROR_NO_IP  = HUEMORSE + ": You have to provide a bridge IP. Use -s to search for and list brigdes";
	public static final String ERROR_RF     = HUEMORSE + ": Could not initialize RF";
	public static final String ERROR_CLI    = "Try '" + HUEMORSE + " --help' for more information.";
	
	public static final int SERVER_PORT = 22042;
	
	public static void main(String args[])  throws Exception {
		// Application Title
        System.out.println(TITLE);
        
		// Apache CLI Parser
		Options options = getCLIOptions();
		CommandLineParser parser = new PosixParser();

        try {
            CommandLine line = parser.parse( options, args);
            if(line.hasOption("b")) bridgeIP = line.getOptionValue("b");
            else if (!(line.hasOption("s"))) {
            	System.out.println(ERROR_NO_IP);
            	return;
            }
            if(line.hasOption("c")) cli = true;
            if(line.hasOption("d")) DEBUG = true;
            if(line.hasOption("l")) lightID = Integer.parseInt(line.getOptionValue("l"));
            if(line.hasOption("u")) username = line.getOptionValue("u");
            if(line.hasOption("s")) search = true;
            if(line.hasOption("h")) {
            	printHelp();
            	return;
            }
        }
        catch( ParseException exp ) {
            System.out.println(HUEMORSE + ": " + exp.getMessage());
            System.out.println(ERROR_CLI);
        	return;
        }

        hue = new HueMorseInterface();
        if (search && bridgeIP.equals("")) searchAndListBridges();
        else {
        	if (DEBUG) {
        		System.out.println("Bridge IP: " + bridgeIP);
        		System.out.println("Username:  " + username);
        	}
        	hue.connect(bridgeIP, username);
        	hue.setLightID(lightID);
        	
        	if (search) searchAndListLights();
        	else {
        		if (cli) simpleCommandLineInterface();
        		else     startServers();
        	}
        }
        hue.close();
        System.exit(0);
	}
	
	/**
	 * Initializes the options for the Apache CLI Parser
	 */
	public static Options getCLIOptions() {
		Options options = new Options();
		options.addOption("b", "bridge", true,  "hue bridge ip");
		options.addOption("c",           false, "start in command line mode");
		options.addOption("d", "debug",  false, "enable debug output");
		options.addOption("h", "help",   false, "display this help and exit");
		options.addOption("l", "light",  true,  "ID of light to use");
		options.addOption("s", "search", false, "search for bridges (when no bridge IP is given) or for lights");
		options.addOption("u", "user",   true,  "username for interacting with the hue bridge");
		return options;
	}
	
	/**
	 * Prints some help to the standard output
	 */
	public static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(HUEMORSE, getCLIOptions());
	}
	
	/**
	 * Searches for bridges and lists them
	 */
	private static void searchAndListBridges() {
		System.out.println("Searching for bridges ...");
		List<PHAccessPoint> bridges = hue.listBridges();
		if (bridges == null) {
			System.out.println("Found no bridges:");
		} else {
			System.out.println("Found " + bridges.size() + " bridges:");
			int i = 1;
			for (PHAccessPoint bridge: bridges) {
				System.out.println("  Bridge " + i +": " + bridge.getIpAddress());
				i++;
			}
		}
	}
	
	/**
	 * Searches for bridges and lists them
	 */
	private static void searchAndListLights() {
		System.out.println("Searching for lights ...");
		List<PHLight> list = hue.listLights();
		for (PHLight light: list) System.out.println(light.getIdentifier() + ": " + light.getName() + " (" + light.getModelNumber() + ")");
	}
	
	/**
	 * Starts huemorse with simple CLI
	 */
	private static void simpleCommandLineInterface() {
		char x = 'y';

        while (x != 'q') {
        	System.out.print("Enter '0' (off), '1' (on) to control light #" + lightID + ": ");
        	try {
				x = (char) System.in.read();
				System.in.skip(System.in.available());
			} catch (IOException e) { }
        	if (x == '0') hue.switchLight(lightID, false);
        	if (x == '1') hue.switchLight(lightID, true);
        }
	}
	
	/**
	 * Start server huemorse server
	 */
	private static void startServers() throws Exception {
		if (DEBUG) System.out.println("Starting servers");
		
        EventLoopGroup bossGroupHueMorse   = new NioEventLoopGroup();
        EventLoopGroup workerGroupHueMorse = new NioEventLoopGroup();
        
        try {
            ServerBootstrap huemorse = new ServerBootstrap();

            huemorse.group(bossGroupHueMorse, workerGroupHueMorse)
            .channel(NioServerSocketChannel.class)
            .childHandler(new MorseServerInitializer(hue));
            
            huemorse.bind(SERVER_PORT).sync().channel().closeFuture().sync();    
        } finally {
        	bossGroupHueMorse.shutdownGracefully();
        	workerGroupHueMorse.shutdownGracefully();
        }
	}
}
