package isf;

import org.json.JSONObject;

import isf.ui.UIManager;

public class SpamThread extends Thread {

	
	private static boolean paused = false;
	private static String tag = "IOTASPAM9DOT9COM99999999999";
	private static SpamThread spamThread;
	private static long timePauseStarted, totalPauses;
	
	private static final UIManager UIM = new UIManager("SpamThrd");
	
	private static final TimeBomb SPAM_BOMB = new TimeBomb("sending spam transaction", 1) {
		@Override
		boolean onCall() {
			NodeManager.sendSpam();
			return true;
		}
	};
	
	@Override
	public void run() {
		
		TimeManager.addTask(new Task(120000, true) { @Override void onCall() { updateRemoteControl(); } });
		
		spamThread = this;
		
		while(true) {
			
			if(paused) {
				synchronized (spamThread) {
					try {
						spamThread.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			
			SPAM_BOMB.call(60000);
		}
	}
	
	public static long getTotalPauses() {
		return totalPauses;
	}
	
	private static void updateRemoteControl() {
		JSONObject obj = APIManager.requestCommand();
		
		if(obj.getBoolean("pause") && !SpamThread.isPaused()) {
			timePauseStarted = System.currentTimeMillis();
			UIM.logWrn("spamming paused remotely by iotaspam.com: " + obj.getString("message"));
		} else if(!obj.getBoolean("pause") && SpamThread.isPaused()) {
			totalPauses += System.currentTimeMillis() - timePauseStarted;
			UIM.logWrn("spamming restarted remotely by iotaspam.com");
		}
		
		SpamThread.setPaused(obj.getBoolean("pause"));
	}
	
	public static boolean isPaused() {
		return paused;
	}

	private static void setPaused(boolean paused) {
		SpamThread.paused = paused;
		
		if(!paused) {
			synchronized(spamThread) {
				spamThread.notify();
			}
		}
	}
	
	public static String getTag() {
		return tag;
	}
	
	public static void setTag(String tag) {
		SpamThread.tag = trytesPadRight(tag, 27);
	}
	
	private static String trytesPadRight(String s, int n) {
		while (s.length() < n)
			s += '9';
		return s;
	}
}