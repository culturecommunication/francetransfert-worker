package workers;

public class StatsWorker{
//	
//    RedisManager manager ;
//    JedisPubSub jedisPubSub ;
//    
//    public StatsWorker() {
//        this.jedisPubSub = createPubSub();
//        this.manager = RedisManager.getInstance();
//    }
//
//	private JedisPubSub createPubSub() {
//		JedisPubSub jedisPubSub = null;
//		try {
//	    	jedisPubSub = new JedisPubSub() {
//	    		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
//	            @Override
//	            public void onMessage(String channel, String message) {
//	            	StatsWorkerTask task = new StatsWorkerTask();
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
//
//	@Override
//	public void run() {
//		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
//		while (true) {
//			List<String> returnedBLPOPList = manager.subscribeFT("email-notification-queue");
//			StatsWorkerTask task = new StatsWorkerTask();
//        	executor.execute(task);
//			System.out.println(returnedBLPOPList);
//		}
//		manager.subscribe(jedisPubSub, "stats-worker-queue");
//	}
}