package workers;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


public class ZipWorker implements Runnable{
	
    RedisManager manager;
    JedisPubSub jedisPubSub;
    public ZipWorker() {
    	this.manager = RedisManager.getInstance();
//    	this.jedisPubSub = createPubSub();
    }

//	private JedisPubSub createPubSub() {
//		JedisPubSub jedisPubSub = null;
//		try {
//	    	jedisPubSub = new JedisPubSub() {
//	    		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
//	            @Override
//	            public void onMessage(String channel, String message) {
//	            	ZipWorkerTask task = new ZipWorkerTask(message);
//	            	executor.execute(task);
//	                System.out.println("Channel " + channel + " has sent a message : " + message );
//	            }
//	                     
//	            @Override
//	            public void onSubscribe(String channel, int subscribedChannels) {
//	                System.out.println("Client is Subscribed to channel : "+ channel);
//	            }
//	                 
//	            @Override
//	            public void onUnsubscribe(String channel, int subscribedChannels) {
//	            }
//	                 
//	        };
//	                 
//	        } catch(Exception ex) {         
//	            System.out.println("Exception : " + ex.getMessage());           
//	        }
//		return jedisPubSub;
//	}

	@Override
	public void run() {
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
		while (true) {
			List<String> returnedBLPOPList = manager.subscribeFT("zip-worker-queue");
			ZipWorkerTask task = new ZipWorkerTask(returnedBLPOPList.get(1));
        	executor.execute(task);
			System.out.println(returnedBLPOPList);
		}
//		manager.subscribe(jedisPubSub, "zip-worker-queue");
	}
}