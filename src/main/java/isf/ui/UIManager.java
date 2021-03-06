package isf.ui;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Scanner;

import isf.logic.CronJob;
import isf.logic.CronJobManager;
import org.json.JSONArray;
import org.json.JSONObject;

import isf.APIManager;
import isf.Configs;
import isf.FileManager;
import isf.P;
import java.util.Scanner;

public class UIManager {
	
	private static boolean debug = true;
	
	public static String ANSI_RESET, ANSI_BRIGHT_BLACK, ANSI_BLACK, ANSI_RED , ANSI_GREEN, ANSI_YELLOW, ANSI_BLUE, ANSI_PURPLE, ANSI_CYAN, ANSI_WHITE, ANSI_BOLD;

	private static final PrintStream ORIGINAL_STREAM = System.out, ORIGINAL_ERR = System.err;
	private static final PrintStream DUMMY_STREAM = new PrintStream(new OutputStream(){public void write(int b) { }});
	private static ArrayList<String> logs = new ArrayList<String>();
	private static long logFileID = System.currentTimeMillis(), lastLogSaved = System.currentTimeMillis();
	
	private static long pauseUntil;
	
	private final String identifier;
	
	public UIManager(String identifier) {
		this.identifier = identifier;
	}
	
	public static void toggleColors(boolean enabled) {
		ANSI_RESET = enabled ? "\u001B[0m" : "";
		ANSI_BRIGHT_BLACK = enabled ? "\u001B[90m" : "";
		ANSI_BLACK = enabled ? "\u001B[30m" : "";
		ANSI_RED = enabled ? "\u001B[31m" : "";
		ANSI_GREEN = enabled ? "\u001B[32m" : "";
		ANSI_YELLOW = enabled ? "\u001B[33m" : "";
		ANSI_BLUE = enabled ? "\u001B[34m" : "";
		ANSI_PURPLE = enabled ? "\u001B[35m" : "";
		ANSI_CYAN = enabled ? "\u001B[36m" : "";
		ANSI_WHITE = enabled ? "\u001B[37m" : "";
		ANSI_BOLD = enabled ? "\u001B[1m" : "";
	}
	
	static {
		System.setOut(DUMMY_STREAM);
		setSystemErrorEnabled(true);
		toggleColors(true);

        CronJobManager.addCronJob(new CronJob(60000, false, false) { @Override public void onCall() { saveLogs(); }});
	}
	
	public void print(String line) {
		if(pauseUntil > System.currentTimeMillis()) {
			try { Thread.sleep(pauseUntil - System.currentTimeMillis()); } catch (InterruptedException e) { }
		}
		
		ORIGINAL_STREAM.println(line+ANSI_RESET);
		logs.add(line+ANSI_RESET);
	}
	
	public static void saveLogs() {

        lastLogSaved = System.currentTimeMillis();
        String logString = "";
        for(int i = 0; i < logs.size(); i++) logString += logs.get(i) + "\r\n";
        logString = logString.replaceAll("\\e\\[[\\d;]*[^\\d;]",""); // remove ansi https://stackoverflow.com/a/25189932

        if(!FileManager.exists("logs")) FileManager.mkdirs("logs");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

        FileManager.write("logs/"+sdf.format(logFileID)+".txt", logString);

        if(logs.size() > 1000) {
            logFileID = System.currentTimeMillis();
            logs = new ArrayList<String>();
        }
	}
	
	public void logPln(String msg) {
		print("["+(new SimpleDateFormat(Configs.get(P.LOG_TIME_FORMAT))).format(Calendar.getInstance().getTime())+"] " + padRight("[" + identifier + "]", 11) + " " + msg);
	}
	
	public void logInf(String msg) { logPln("[INF] " + msg); }
	public void logWrn(String msg) { logPln(ANSI_BOLD+ANSI_YELLOW + "[WRN] " + msg); pause(1); }
	public void logErr(String msg) { logPln(ANSI_BOLD+ANSI_RED + "[ERR] " + msg); pause(1); }
	public void logDbg(String msg) { if(debug) logPln(ANSI_BRIGHT_BLACK + "[DBG] " + msg); }

	public static void updateDebugEnabled() {
		debug = Configs.getBln(P.LOG_DEBUG_ENABLED);
	}
	
	public static boolean isDebugEnabled() {
		return debug;
	}
	
	public static void setSystemErrorEnabled(boolean enabled) {
		System.setErr(enabled ? ORIGINAL_ERR : DUMMY_STREAM);
	}
	
	public void logException(Throwable e, boolean terminate) {
		logErr(e.getMessage());
		
		if(debug) {
		    synchronized (ORIGINAL_ERR) {
                ORIGINAL_ERR.println(ANSI_BRIGHT_BLACK);
                //setSystemErrorEnabled(true);
                e.printStackTrace();
                //setSystemErrorEnabled(false);
                ORIGINAL_ERR.println(ANSI_RESET);
            }
		}
		
		if(terminate) {
			logDbg("program will be terminated now due to above error");
			System.exit(1);
		}
	}
	
	private static void pause(int s) {
		pauseUntil = System.currentTimeMillis()+s*1000;
	}

	public String readLine() {
		Scanner scanner = new Scanner(System.in);
        ORIGINAL_STREAM.print("\n  > ");
		String line = scanner.nextLine();
		return line;
	}
	
	public int askQuestionInt(UIQuestionInt question) {
		return Integer.parseInt(askQuestion(question));
	}
	
	public String askQuestion(UIQuestion question) {

		String answer = null;
		do {
			if(answer != null)
				print("\n"+ANSI_BOLD + ANSI_YELLOW+(answer.length() == 0 ? "please answer the above question" : "answer '"+answer+"' is not a valid answer"));
			print("\n"+ANSI_BOLD+question.getQuestion());
			answer = readLine();
			if(!question.isCaseSensitive()) answer = answer.toLowerCase();
		} while(!question.isAnswer(answer));
		return answer;
	}
	
	public boolean askForBoolean(final String questionString) {

		String answer = askQuestion(new UIQuestion() {
			@Override
			public boolean isAnswer(String str) {
				return str.equals("y") || str.equals("n") || str.equals("yes") || str.equals("no");
			}
			
		}.setQuestion(questionString + " [y/n; yes/no]"));
		return answer.equals("y") || answer.equals("yes");
	}
	
	public void printUpdates() {
		logDbg("checking for updates (will appear below if there are any)");
		JSONObject jsonObj = APIManager.requestUpdates();
		int screenIndex = 0;
		
		if(jsonObj.getJSONArray("screens").length() == 0)
			return;
			
		do {

			final JSONObject screen = jsonObj.getJSONArray("screens").getJSONObject(screenIndex);
			final JSONArray answers = screen.getJSONArray("answers");
			
			final UIQuestion uiQuestion = new UIQuestion() {
				@Override
				public boolean isAnswer(String str) {
					for(int i = 0; i < answers.length(); i++)
						if(answers.getJSONObject(i).getString("answer").equals(str)) return true;
					return false;
				}
			}.setQuestion(screen.getString("text"));
			String answer = askQuestion(uiQuestion);

			screenIndex = -1;
			for(int i = 0; i < answers.length(); i++)
				if(answers.getJSONObject(i).getString("answer").equals(answer))
					screenIndex = answers.getJSONObject(i).getInt("goto");
		} while(screenIndex >= 0);
	}
	
	public static String padRight(String s, int n) {
	    return s.length() < n ? String.format("%1$-" + n + "s", s) : s;
	}

	public static String padLeft(String s, int n) {
		return s.length() < n ? String.format("%1$" + n + "s", s) : s;
	}
}
