package workers;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;

import redis.clients.jedis.JedisPubSub;


public class StatsWorker implements Runnable{
	
    RedisManager manager ;
    JedisPubSub jedisPubSub ;
    
    public StatsWorker(RedisManager manager) {
    	this.manager = manager;
        this.jedisPubSub = createPubSub();       
    }

	private JedisPubSub createPubSub() {
		JedisPubSub jedisPubSub = null;
		try {
	    	jedisPubSub = new JedisPubSub() {
	    		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
	            @Override
	            public void onMessage(String channel, String message) {
	            	StatsWorkerTask task = new StatsWorkerTask();
	            	executor.execute(task);
	                System.out.println("Channel " + channel + " has sent a message : " + message );
	            }
	                     
	            @Override
	            public void onSubscribe(String channel, int subscribedChannels) {
	                System.out.println("Client is Subscribed to channel : "+ channel);
	            }
	                 
	            @Override
	            public void onUnsubscribe(String channel, int subscribedChannels) {
	            }
	                 
	        };
	                 
	        } catch(Exception ex) {         
	            System.out.println("Exception : " + ex.getMessage());           
	        }
		return jedisPubSub;
	}

	@Override
	public void run() {
		manager.subscribe(jedisPubSub, "stats-worker-queue");
	}
}