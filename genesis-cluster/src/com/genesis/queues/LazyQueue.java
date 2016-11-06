package com.genesis.queues;

import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesis.router.server.ServerState;

import io.netty.channel.Channel;
import pipe.work.Work.WorkMessage;

public class LazyQueue implements Queue {

	LinkedBlockingDeque<WorkChannel> lazy;
	
	private ServerState state;
	
	private static Logger logger = LoggerFactory.getLogger("lazy queue");
	
	public LazyQueue(ServerState state) {
		this.state = state;
		lazy = new LinkedBlockingDeque<WorkChannel>();
		
	}
	
	@Override
	public void put(WorkMessage work, Channel channel) {
		lazy.add(new WorkChannel(work, channel));
	}

	@Override
	public WorkChannel get() {
		try {
			return lazy.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int getSize() {
		return lazy.size();
	}
	
	
	public WorkMessage getTask(){
		WorkChannel tc= get();
		if(tc != null ){
			return tc.getWorkMessage();
		}
		return null;
	}
	
	public boolean process(){
		if(lazy.size() == 0) {
			logger.info("lazy queue size is 0, process other tasks ?");
			return false;
		}
		WorkChannel t = get();
		WorkMessage work = t.getWorkMessage();
		Channel channel = t.getChannel();
		if(channel.isActive() && channel.isOpen())
			channel.writeAndFlush(work);
		return true;
	}

}
