/**
 * Copyright (C) 2014 Christian M. Schmid
 *
 * This file is part of the huemorse.
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

package com.chschmid.huemorse.server.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.chschmid.huemorse.server.HueMorse;
import com.chschmid.huemorse.server.HueMorseInterface;

/**
 * The PiMorse Server handler
 * @author Christian M. Schmid
 */
@Sharable
public class MorseServerHandler extends SimpleChannelInboundHandler<String> {
	private HueMorseInterface hue;
	
    private static final Logger logger = Logger.getLogger(MorseServerHandler.class.getName());
    
    public MorseServerHandler(HueMorseInterface hue){
    	this.hue = hue;
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    }

    public void channelRead0(ChannelHandlerContext ctx, String request) throws Exception {
        if (HueMorse.DEBUG) System.out.println(HueMorse.HUEMORSE + " Server: " + request);
        hue.addMorseCode(request);
        ctx.close().addListener(ChannelFutureListener.CLOSE);
    }

    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
        ctx.close();
    }
}
