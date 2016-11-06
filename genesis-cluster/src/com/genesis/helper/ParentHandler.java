package com.genesis.helper;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesis.monitors.NetworkMonitor;
import com.genesis.router.server.STATE;
import com.genesis.router.server.ServerState;
import com.genesis.router.server.edges.EdgeInfo;

import io.netty.channel.Channel;
import pipe.common.Common.Node;
import pipe.work.Work.DragonBeat;
import pipe.work.Work.NodeLinks;
import pipe.work.Work.WorkMessage;

public class ParentHandler implements ServerHandler{

	private static Logger logger = LoggerFactory.getLogger("parent handler");
	protected ServerState state;
	
	public ParentHandler(ServerState state) {
		this.state = state;
	}
	
	@Override
	public void handleTask(WorkMessage msg, Channel channel) {
		
		
	}

	@Override
	public void handleBeat(WorkMessage msg, Channel channel) {
		// TODO Auto-generated method stub
		state.getEmon().updateHeartBeat(msg.getHeader().getOrigin(),
				msg.getHeader().getTime(),msg.getState());
	}

	@Override
	public void handleMessage(WorkMessage msg, Channel channel) {
		
	}

	@Override
	public void handleState(WorkMessage msg, Channel channel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void handleVote(WorkMessage msg, Channel channel) {
		// TODO Auto-generated method stub
		
	}

	public void handleDragonL2(WorkMessage msg, Channel channel) {
		logger.debug("Dragon L2 "+msg);

		NetworkMonitor nmon = NetworkMonitor.getInstance();
		DragonBeat dragon = msg.getDragon();
		 
		List<NodeLinks> links = dragon.getNodelinksList();
		nmon.nmap = links;
		state.getEmon().passOnDragon("L2",links,nmon.getOutCheckSum());
	}
	
	public void handleDragonL1(WorkMessage msg, Channel channel) {

		NetworkMonitor nmon = NetworkMonitor.getInstance();
		DragonBeat dragon = msg.getDragon();
		 
		List<NodeLinks> links = new ArrayList<NodeLinks>();
		links.addAll(dragon.getNodelinksList());
		logger.debug("L1 links received : "+links);

		links.add(state.getEmon().prepareDragonBeatMsg());
		logger.debug("L1 links received and transmitted : "+links);
		
		state.getEmon().passOnDragon("L1",links,nmon.getOutCheckSum());
	}
	
	protected void handleLeader(WorkMessage msg, Channel channel) {
		// TODO Auto-generated method stub
		switch(msg.getLeader().getAction()){
			case THELEADERIS: {
			
				Node origin = msg.getHeader().getOrigin();
				EdgeInfo leader = new EdgeInfo(origin.getId(),origin.getHost(),origin.getPort());
				leader.status = "ALIVE";
				state.getEmon().setLeader(leader);
				state.getEmon().passMsg(msg);
				logger.info("leader received, updated leader : "+leader.getRef());
				state.state = STATE.FOLLOWER;
				break;
			}
			case WHOISTHELEADER: {
				switch(msg.getLeader().getState()){
					case LEADERDEAD: {
						if(state.state != STATE.VOTED){
							state.state = STATE.VOTED;
							
							state.getEmon().handleElectionMessage(msg);
						}
						break;
					}
					default:{
						// probably a new node, help it to find leader
						WorkMessage workMessage = state.getEmon().helpFindLeaderNode(msg);
					}
				
					
				break;
			}
		}
	}
	}
	
	
}
