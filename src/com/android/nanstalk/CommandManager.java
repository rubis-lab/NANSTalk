package com.android.nanstalk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import net.daum.mf.speech.api.SpeechRecognizerClient;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.content.ContentResolver;
import android.database.Cursor;

public class CommandManager {
	public enum Cid	{
		//SYS_EXE,
		SYS_CTRL,
		SYS_FINISH,
		SYS_BACK,
		SYS_FRONT, 
		SYS_REAR,
		SYS_PHONE,
		SYS_RESET,
		SYS_MIRACAST,
		MESSENGER_FRIEND_LIST,
		MESSENGER_CHAT_LIST,
		MESSENGER_SEARCH,
		MESSENGER_SEND,
		NAVIGATION_CONTINUE,
		NAVIGATION_YES,
		NAVIGATION_NO,
		NAVIGATION_LOCATION,
		NAVIGATION_MAP,
		NAVIGATION_NAVIGATE,
		NAVIGATION_NAVEND,
		VIDEO_START,
		VIDEO_STOP,
		VIDEO_PLAY,
		BROWSER_UPSIDE,
		BROWSER_DOWNSIDE,
		BROWSER_RIGHTSIDE,
		BROWSER_LEFTSIDE,
	}
	
	public enum Copt {
		POSTFIX, 
		PREFIX,
		WITHAPP,		
		WITHCONTENT,
		WITHCONTACT,
	}
	
	public class Command {
		public String cmd;		// string command
		
		public Set<Copt> options;
		// is command used as prefix?
		public boolean isPrefix()		{return options.contains(Copt.PREFIX);}		
		// is command used as postfix?
		public boolean isPostfix()		{return options.contains(Copt.POSTFIX);}
		// is command used with application name?
		public boolean isWithApp()		{return options.contains(Copt.WITHAPP);}
		// is command used with non fixed parameter?
		public boolean isWithContent()	{return options.contains(Copt.WITHCONTENT);}
		// is command used with contact name?
		public boolean isWithContact()	{return options.contains(Copt.WITHCONTACT);}
		// is command used without any parameter? 
		public boolean isNoParam()		
		{
			return !(options.contains(Copt.WITHCONTENT)	|| 
						options.contains(Copt.WITHAPP)  ||
						options.contains(Copt.WITHCONTACT));}
		public int isStatic;	// is command only used with application name?
		public Cid cid;			// command id
		
		public Command(Cid id, String cmd, Set<Copt> options) {
			this.cid = id;
			this.cmd = cmd;
			this.options = options;
		}		
	};
	
	public class CommandContent	{
		public String content;
		public Command cmd;
		public String appName;
		public String packageName;
		public String matchingStr;
		public String contentOri;
		public String contact;
		public int distance;
		
		public CommandContent(String appName, Command cmd, String matchingStr) {
			this.appName = appName;
			this.packageName = getPackageNameByAppName(appName);
			this.cmd = cmd;
			this.content = "";
			this.matchingStr = trans(matchingStr).replaceAll(" ", "");		
		}
	}

	private static final String TAG = "CommandManager";
	private ArrayList<String> appList;
	private ArrayList<CommandContent> cmdAppList;
	private ArrayList<ArrayList<Command>> cmdList;
	private ArrayList<String> contactList;
	private ContentResolver resolver;
	
	public CommandManager(ContentResolver resolver) {		
		this.resolver = resolver;
		initCmdAppList();
	}
	
	private void initAppList() {
		appList = new ArrayList<String>();
		appList.add("");
		appList.add("메신저");
		appList.add("내비게이션");
		appList.add("비디오");
		appList.add("브라우저");
	}
	
	private void initCmdList() {
		cmdList = new ArrayList<ArrayList<Command>>();
		
		cmdList.add(new ArrayList<Command>());
		// set common command;
		ArrayList<Command> commonCmd = cmdList.get(0);
		// ex. 카카오톡 실행
		// commonCmd.add(new Command(Cid.SYS_EXE,				"실행",		EnumSet.of(Copt.POSTFIX, Copt.WITHAPP)));
		commonCmd.add(new Command(Cid.SYS_FRONT,			"앞으로",	EnumSet.of(Copt.POSTFIX, Copt.WITHAPP)));		
		commonCmd.add(new Command(Cid.SYS_REAR,				"뒤로",		EnumSet.of(Copt.POSTFIX, Copt.WITHAPP)));
		commonCmd.add(new Command(Cid.SYS_PHONE,			"폰으로",	EnumSet.of(Copt.POSTFIX, Copt.WITHAPP)));
		
		// ex. 헬로 카카오톡
		commonCmd.add(new Command(Cid.SYS_CTRL,				"헬로",		EnumSet.of(Copt.PREFIX, Copt.WITHAPP)));
		commonCmd.add(new Command(Cid.SYS_CTRL,				"hello",	EnumSet.of(Copt.PREFIX, Copt.WITHAPP)));
		
		// ex. 서울대입구 검색
		commonCmd.add(new Command(Cid.NAVIGATION_NAVIGATE,		"안내",		EnumSet.of(Copt.POSTFIX, Copt.WITHCONTENT)));
		commonCmd.add(new Command(Cid.MESSENGER_SEND,			"전송",		EnumSet.of(Copt.POSTFIX, Copt.WITHCONTENT)));
		
		// ex. 계속
		commonCmd.add(new Command(Cid.MESSENGER_FRIEND_LIST,	"목록",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.MESSENGER_CHAT_LIST,		"대화",		EnumSet.of(Copt.PREFIX)));
		
		commonCmd.add(new Command(Cid.NAVIGATION_YES,			"예",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.NAVIGATION_NO,			"아니오",	EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.NAVIGATION_CONTINUE,		"계속",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.NAVIGATION_LOCATION,		"장소",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.NAVIGATION_MAP,			"지도",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.NAVIGATION_NAVEND,		"안내종료",	EnumSet.of(Copt.PREFIX)));
		
		commonCmd.add(new Command(Cid.VIDEO_START,			"시작",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.VIDEO_STOP,				"정지",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.VIDEO_PLAY,				"재생",		EnumSet.of(Copt.PREFIX)));
		
		commonCmd.add(new Command(Cid.SYS_RESET,			"모으기",	EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.SYS_FINISH,			"닫기",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.SYS_BACK,				"뒤로",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.SYS_MIRACAST,			"화면연결",	EnumSet.of(Copt.PREFIX)));
		
		commonCmd.add(new Command(Cid.BROWSER_UPSIDE,		"위로",			EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.BROWSER_DOWNSIDE,		"아래로",		EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.BROWSER_RIGHTSIDE,	"오른쪽으로",	EnumSet.of(Copt.PREFIX)));
		commonCmd.add(new Command(Cid.BROWSER_LEFTSIDE,		"왼쪽으로",		EnumSet.of(Copt.PREFIX)));
		
		// ex. 김강욱 검색
		commonCmd.add(new Command(Cid.MESSENGER_SEARCH,			"검색",		EnumSet.of(Copt.POSTFIX, Copt.WITHCONTACT)));
	}
	
	private void initContactList() {
		contactList = new ArrayList<String>();
		try  {
			Uri people = Contacts.CONTENT_URI;
			String[] projection = new String[] {Contacts.DISPLAY_NAME};
			String[] selectionArgs = null;
			Cursor cursor = resolver.query(people, projection, null, selectionArgs, null);
			
			if (cursor.moveToFirst()) {
				do {
					String name = cursor.getString(0);
					contactList.add(name);				
				} while (cursor.moveToNext());
			} else {
				Log.d(TAG, "warning : empty contact");
			}
		} catch (Exception e) {
			Log.d(TAG, e.toString());
		}
	}
	
	private void initCmdAppList() {
		initAppList();
		initCmdList();
		initContactList();

		cmdAppList = new ArrayList<CommandContent>();
		// for global command
		for (int i = 0; i < cmdList.get(0).size(); i++)	{		
			Command cmd = cmdList.get(0).get(i);
			if (cmd.isWithApp()) {
				//iterate except global app name (index = 0)
				for (int j = 1; j < appList.size(); j++) {
					String appName = appList.get(j);
					if (cmd.isPrefix()) {
						String matchingStr = cmd.cmd + " " + appName;
						CommandContent content = new CommandContent(appName, cmd, matchingStr);
						cmdAppList.add(content);
					}
					if (cmd.isPostfix()) {
						String matchingStr = appName + " " + cmd.cmd;
						CommandContent content = new CommandContent(appName, cmd, matchingStr);
						cmdAppList.add(content);					
					}
				}
			} else if (cmd.isWithContent() || cmd.isNoParam()) {
				CommandContent content = new CommandContent("", cmd, cmd.cmd);
				cmdAppList.add(content);
			} else if (cmd.isWithContact())	{
				for (int j = 0; j < contactList.size(); j++) {
					String name = contactList.get(j);
					if (cmd.isPrefix()) {
						String matchingStr = cmd.cmd + " " + name;
						CommandContent content = new CommandContent("", cmd, matchingStr);
						content.content = trans(name);
						content.contact = name;
						cmdAppList.add(content);
						Log.d(TAG, "contact : " + name);
					}
					if (cmd.isPostfix()) {
						String matchingStr = name + " " + cmd.cmd;
						CommandContent content = new CommandContent("", cmd, matchingStr);
						content.content = trans(name);
						content.contact = name;
						cmdAppList.add(content);
						Log.d(TAG, "contact : " + name);
					}
				}
			}
		}
		for (int i = 0; i < cmdAppList.size(); i++) {			
			CommandContent cmdContent = cmdAppList.get(i);		
			Log.d(TAG, "command : " + cmdContent.matchingStr);
		}
	}
	
	private CommandContent findCommand(String text) {
		int minIdx = -1;
		int minDistance = 0;
		int contentIdxStart = -1;
		int contentIdxEnd = -1;		
		String str2 = text;
		String str4 = text.replaceAll(" ", "");
		for (int i = 0; i < cmdAppList.size(); i++) {			
			CommandContent cmdContent = cmdAppList.get(i);
			String str1 = cmdContent.matchingStr;

			if (cmdContent.cmd.isWithApp() || cmdContent.cmd.isNoParam() || cmdContent.cmd.isWithContact()) {
				int distance = getTextDistance(str1, str4);
				Log.d(TAG, "result : " + str1 + " / " + str4 + "(" + distance + ")");
				if (minIdx < 0 || distance < minDistance) {
					minDistance = distance;
					minIdx = i;
					contentIdxStart = -1;
					contentIdxEnd = -1;		
					if (distance == 0) 
						break;
				}				
			} else if (cmdContent.cmd.isWithContent()) {
				int searchrange = 3;
				for (int len = str1.length() - searchrange; len <= str1.length() + searchrange; len++) {
					if (len <= 0) continue;
					if (len > str2.length()) break;
					if (cmdContent.cmd.isPrefix()) {
						String str3 = str2.substring(0, len);
						int distance = getTextDistance(str1, str3);
						Log.d(TAG, "result : " + str1 + " / " + str3 + "(" + distance + ")");
						if (minIdx < 0 || distance < minDistance) {
							minDistance = distance;
							minIdx = i;
							contentIdxStart = len;
							contentIdxEnd = str2.length();
							if (distance == 0) 
								break;
						}						
					} else if (cmdContent.cmd.isPostfix()) {
						String str3 = str2.substring(str2.length() - len, str2.length());
						int distance = getTextDistance(str1, str3);
						Log.d(TAG, "result : " + str1 + " / " + str3 + "(" + distance + ")");
						if (minIdx < 0 || distance < minDistance) {
							minDistance = distance;
							minIdx = i;
							contentIdxStart = 0;
							contentIdxEnd = str2.length()-len;							
							if (distance == 0) 
								break;
						}
					}
				}
				if (minDistance == 0) 
					break;
			}
		}
		
		CommandContent ret = cmdAppList.get(minIdx);
		ret.distance = minDistance;
		
		if (contentIdxStart < 0)
			ret.content = "";
		else
			ret.content = str2.substring(contentIdxStart, contentIdxEnd);
		
		if (ret.cmd.isWithContact()) {
			ret.content = trans(ret.contact);
		}
		
		return ret;
	}
	
	private int getTextDistance(String a, String b) {
		int [] costs = new int [b.length() + 1];
		for (int i = 0; i < costs.length; i++) {
			costs[i] = i;
		}
		
		for (int i = 1; i <= a.length(); i++) {
			costs[0] = i;
			int nw = i - 1;
			for (int j = 1; j <= b.length(); j++) {
				int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
				nw = costs[j];
				costs[j] = cj;
			}
		}
		return costs[b.length()];
	}
	
    public CommandContent getResults(Bundle results) {
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizerClient.KEY_RECOGNITION_RESULTS);
        int minDistance = -1;
        int minIdx = -1;
        for (int i = 0; i < texts.size(); i++) {
        	String text = trans(texts.get(i));        	        	
        	CommandContent content;
        	content = findCommand(text);
        	if (minDistance < 0 || content.distance < minDistance) {
        		minDistance = content.distance;        		
        		minIdx = i;
        		if (minDistance == 0) 
        			break;
        	}        			
        }
        
        for (int i = 0; i < texts.size(); i++){
        	Log.d(TAG, texts.get(i));
        }

        CommandContent minContent = findCommand(trans(texts.get(minIdx)));
        minContent.contentOri = texts.get(minIdx);
        minContent.distance = minDistance;
        String debugmsg =        		
        		"ori: " + minContent.contentOri + "\n" +
        		"app: " + minContent.appName + "\n" + 
        		"cmd: " + minContent.cmd.cmd + "\n" +
        		"cid: " + minContent.cmd.cid + "\n" +
        		"con: " + minContent.content + "\n" +
        		"fri: " + minContent.contact + "\n" +
        		"dis: " + minDistance;
        Log.d(TAG, debugmsg);
        return minContent;
    }
    
    public String getCommandString(CommandContent cmdContent) {
    	String result = "";
    	String appName = cmdContent.appName;		// associated application name. can be empty string, i.e., ""		
		Cid cid = cmdContent.cmd.cid;				// command ID
		String content = cmdContent.content;		// content in translated english. do not need to convert using trans() function
    	if(!appName.equals("")) {
    		if(cid == Cid.SYS_CTRL)
    			result = cidToString(cid) + " " + appName;
    		else
    			result = appName + " " + cidToString(cid);
    	} else {
    		if(cid == Cid.MESSENGER_SEARCH || cid == Cid.MESSENGER_SEND)
    			result = content + " " + cidToString(cid);
    		else
    			result = cidToString(cid);
    	}
    	return result;
    }
    
    private String cidToString(Cid cid) {
    	switch(cid) {
    	// case SYS_EXE: 				return "실행";
    	case SYS_CTRL: 					return "헬로우";
    	case SYS_FINISH: 				return "종료";
    	case SYS_FRONT: 				return "앞으로";
    	case SYS_REAR: 					return "뒤로";
    	case SYS_PHONE: 				return "폰으로";
    	case SYS_RESET: 				return "모으기";
    	case MESSENGER_SEARCH: 			return "검색";
    	case MESSENGER_SEND: 			return "전송";
    	case MESSENGER_FRIEND_LIST: 	return "목록";
    	case MESSENGER_CHAT_LIST: 		return "대화";
    	case NAVIGATION_NAVIGATE: 		return "안내";
    	case NAVIGATION_CONTINUE: 		return "계속";
    	case NAVIGATION_LOCATION: 		return "장소";
    	case NAVIGATION_MAP: 			return "지도";
    	case NAVIGATION_NAVEND: 		return "안내종료";
    	case VIDEO_START: 				return "시작";
    	case VIDEO_STOP: 				return "정지";
    	case VIDEO_PLAY: 				return "재생";
    	case BROWSER_UPSIDE: 			return "아래로";
    	case BROWSER_DOWNSIDE: 			return "위로";
    	case BROWSER_RIGHTSIDE: 		return "오른쪽으로";
    	case BROWSER_LEFTSIDE: 			return "왼쪽으로";
    	}
    	return "";
    }
    
	private String getPackageNameByAppName(String appName) {
		String packageName = "";
	    if (appName.equals("메신저"))
	    	packageName = "com.facebook.orca";
	    else if (appName.equals("내비게이션"))
	    	packageName = "com.locnall.KimGiSa";
	    else if (appName.equals("비디오"))
	    	packageName = "com.mxtech.videoplayer.ad";
	    else if (appName.equals("브라우저"))
	    	packageName = "com.android.browser";
	    return packageName;
	}
	
	
	/** 초성 - 가(ㄱ), 날(ㄴ) 닭(ㄷ) */
    private static char[] arrChoSung = { 0x3131, 0x3132, 0x3134, 0x3137, 0x3138,
		0x3139, 0x3141, 0x3142, 0x3143, 0x3145, 0x3146, 0x3147, 0x3148,
		0x3149, 0x314a, 0x314b, 0x314c, 0x314d, 0x314e };
	
	/** 중성 - 가(ㅏ), 야(ㅑ), 뺨(ㅑ)*/
    private static char[] arrJungSung = { 0x314f, 0x3150, 0x3151, 0x3152,
		0x3153, 0x3154, 0x3155, 0x3156, 0x3157, 0x3158, 0x3159, 0x315a,
		0x315b, 0x315c, 0x315d, 0x315e, 0x315f, 0x3160, 0x3161, 0x3162,
		0x3163 };
	
	/** 종성 - 가(없음), 갈(ㄹ) 천(ㄴ) */
    private static char[] arrJongSung = { 0x0000, 0x3131, 0x3132, 0x3133,
		0x3134, 0x3135, 0x3136, 0x3137, 0x3139, 0x313a, 0x313b, 0x313c,
		0x313d, 0x313e, 0x313f, 0x3140, 0x3141, 0x3142, 0x3144, 0x3145,
		0x3146, 0x3147, 0x3148, 0x314a, 0x314b, 0x314c, 0x314d, 0x314e };
	
	/** 초성 - 가(ㄱ), 날(ㄴ) 닭(ㄷ) */
    private static String[] arrChoSungEng = { "r", "R", "s", "e", "E",
		"f", "a", "q", "Q", "t", "T", "d", "w",
		"W", "c", "z", "x", "v", "g" };
	
	/** 중성 - 가(ㅏ), 야(ㅑ), 뺨(ㅑ)*/
    private static String[] arrJungSungEng = { "k", "o", "i", "O",
		"j", "p", "u", "P", "h", "hk", "ho", "hl",
		"y", "n", "nj", "np", "nl", "b", "m", "ml",
		"l" };
	
	/** 종성 - 가(없음), 갈(ㄹ) 천(ㄴ) */
    private static String[] arrJongSungEng = { "", "r", "R", "rt",
		"s", "sw", "sg", "e", "f", "fr", "fa", "fq",
		"ft", "fx", "fv", "fg", "a", "q", "qt", "t",
		"T", "d", "w", "c", "z", "x", "v", "g" };
	
	/** 단일 자음 - ㄱ,ㄴ,ㄷ,ㄹ... (ㄸ,ㅃ,ㅉ은 단일자음(초성)으로 쓰이지만 단일자음으론 안쓰임) */
    private static String[] arrSingleJaumEng = { "r", "R", "rt",
		"s", "sw", "sg", "e","E" ,"f", "fr", "fa", "fq",
		"ft", "fx", "fv", "fg", "a", "q","Q", "qt", "t",
		"T", "d", "w", "W", "c", "z", "x", "v", "g" };
	
	private String trans(String word) {
		String result		= "";
		String resultEng	= "";
	
		for (int i = 0; i < word.length(); i++) {
			char chars = (char) (word.charAt(i) - 0xAC00);
			if (chars >= 0 && chars <= 11172) {
				int chosung 	= chars / (21 * 28);
				int jungsung 	= chars % (21 * 28) / 28;
				int jongsung 	= chars % (21 * 28) % 28;
				result = result + arrChoSung[chosung] + arrJungSung[jungsung];
				if (jongsung != 0x0000)
					result =  result + arrJongSung[jongsung];
				resultEng = resultEng + arrChoSungEng[chosung] + arrJungSungEng[jungsung];
				if (jongsung != 0x0000)
					resultEng =  resultEng + arrJongSungEng[jongsung];
			} else {
				result = result + ((char)(chars + 0xAC00));
				if (chars>=34097 && chars<=34126) {
					int jaum 	= (chars-34097);
					resultEng = resultEng + arrSingleJaumEng[jaum];
				} else if (chars>=34127 && chars<=34147) {
					int moum 	= (chars-34127);
					resultEng = resultEng + arrJungSungEng[moum];
				} else {
					resultEng = resultEng + ((char)(chars + 0xAC00));
				}
			}	
		}
		return resultEng;
	}
}