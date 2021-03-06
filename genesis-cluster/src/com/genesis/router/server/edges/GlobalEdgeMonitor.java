package com.genesis.router.server.edges;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesis.helper.GlobalInit;
import com.genesis.queues.GlobalQueue;
import com.genesis.router.container.GlobalConf.RoutingEntry;
import com.genesis.router.server.ServerState;
import com.google.protobuf.ByteString;

import global.Global.File;
import global.Global.GlobalHeader;
import global.Global.GlobalMessage;
import global.Global.Request;
import global.Global.RequestType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class GlobalEdgeMonitor {

	private static Logger logger = LoggerFactory.getLogger("global edge monitor");
	private ServerState state;
	private EdgeList2 globalOutboud = new EdgeList2();	
	
	public GlobalEdgeMonitor(ServerState state) {
		this.state = state;
		
		if (state.getGlobalConf().getRouting() != null) {
			for (RoutingEntry e : state.getGlobalConf().getRouting()) {
				globalOutboud.addNode(e.getClusterId(), e.getHost(), e.getPort());
			}
		}
	}
	
	public void pushMessagesIntoCluster(GlobalMessage global){
		for(EdgeInfo ei: globalOutboud.map.values()) {
			if(ei.getChannel() != null && ei.isActive()){
				ei.getChannel().writeAndFlush(global);
				//break;
			}
			else{
				try{
					logger.info("trying to connect to cluster node " + ei.getRef());
					EventLoopGroup group = new NioEventLoopGroup();
					GlobalInit si = new GlobalInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					
					ei.setChannel(channel.channel());
					ei.setActive(channel.channel().isActive());
					ei.getChannel().writeAndFlush(global);
				}
				catch(Exception e){
					logger.error("Failed connecting to other cluster node "+ei.getRef()+" , i'm clueless");
				}
			}
		}
	}

	public void forwardRequest(String id, CommandMessage msg,byte[] data) {
		GlobalMessage.Builder wm = GlobalMessage.newBuilder();
		GlobalHeader.Builder header = GlobalHeader.newBuilder();
		
		header.setTime(System.currentTimeMillis());
		header.setDestinationId(state.getGlobalConf().getClusterId());
		header.setClusterId(state.getGlobalConf().getClusterId());
		
		wm.setGlobalHeader(header.build());
		
		Request.Builder request = Request.newBuilder();
		request.setRequestId(id);
		
		File.Builder file = File.newBuilder();
		logger.info("file name decoded as "+msg.getReqMsg().getKey());
		file.setFilename(msg.getReqMsg().getKey());
		
		logger.info("In GlobalEdge Monitor ::: operation is "+ msg.getReqMsg().getOperation());
		switch(msg.getReqMsg().getOperation()){
			case POST:
				request.setRequestType(RequestType.WRITE);
				file.setData(ByteString.copyFrom(data));
				file.setChunkId(msg.getReqMsg().getSeqNo());
				file.setTotalNoOfChunks(msg.getReqMsg().getNoOfChunks());
				request.setFile(file.build());

				break;
		
			case PUT:
				request.setRequestType(RequestType.UPDATE);
				file.setData(ByteString.copyFrom(data));
				file.setChunkId(msg.getReqMsg().getSeqNo());
				file.setTotalNoOfChunks(msg.getReqMsg().getNoOfChunks());
				request.setFile(file.build());

				break;
				
			case DEL:
				request.setRequestType(RequestType.DELETE);
				request.setFileName(msg.getReqMsg().getKey());
				break;
			case GET:
				request.setRequestType(RequestType.READ);
				request.setFileName(msg.getReqMsg().getKey());
				break;
		}
		wm.setRequest(request.build());
		for(EdgeInfo ei : globalOutboud.map.values()){
			if(ei.getChannel() != null && ei.isActive()){
				state.getGlobalOutboundQueue().put(wm.build(), ei.getChannel());
			}
			else{
				try{
					logger.info("trying to connect to node " + ei.getRef());
					EventLoopGroup group = new NioEventLoopGroup();
					GlobalInit si = new GlobalInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					if(channel != null) {
						ei.setChannel(channel.channel());
						ei.setActive(channel.channel().isActive());
						Thread.sleep(50);
						state.getGlobalOutboundQueue().put(wm.build(), ei.getChannel());
					}
				}
				catch(Exception e){
					logger.info("Error connecting to the other lcuster node");
				}
			}
		}
	}

	public void sendPing(GlobalMessage msg) {
		// TODO Auto-generated method stub
		logger.info("received ping ========================="+msg.getPing());
		logger.info("forwarding the ping to other clusters ==========================");
		for(EdgeInfo ei : globalOutboud.map.values()){
			if(ei.getChannel() != null && ei.isActive()){
				ei.getChannel().writeAndFlush(msg);
			}
			else{
				try{
					logger.info("trying to connect to node " + ei.getRef());
					EventLoopGroup group = new NioEventLoopGroup();
					GlobalInit si = new GlobalInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					if(channel != null) {
						ei.setChannel(channel.channel());
						ei.setActive(channel.channel().isActive());
						Thread.sleep(50);
						channel.channel().writeAndFlush(msg);
					}	
				}
				catch(Exception e){
					logger.info("Error connecting to the other lcuster node");
				}
			}
		}
	}

	public GlobalQueue getQueue() {
		// TODO Auto-generated method stub
		return state.getGlobalOutboundQueue();
	}
}
