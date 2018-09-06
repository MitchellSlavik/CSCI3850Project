import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Main
 */
public class WebSearchProject {
  public static ArrayList<String> stopWords = new ArrayList<String>();
  public static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> keywords = new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();
  public static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> cleanedKeywords = new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();
  public static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> stemmedKeywords = new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();
  
  public static Porter porter = new Porter();
  public static void main(String[] args) {
    if(args.length != 1) {
      System.out.println("Usage: java Main [path to docset folder]");
      return;
    }
    
    long start = System.currentTimeMillis();
    
    BufferedReader reader;
    try {
      reader = new BufferedReader(new FileReader("stop-word-list.txt"));
      String line = reader.readLine();
      while (line != null) {
        stopWords.add(line.toLowerCase());
        line = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    File f = new File(args[0]);
    
    File[] files = f.listFiles();
    int numFiles = files.length;
    CountDownLatch latch = new CountDownLatch(numFiles);
    ExecutorService es = Executors.newFixedThreadPool(16);
    
    for(File file : files) {
      es.execute(new FileReaderRunner(file, latch));
    }
    
    while(latch.getCount() != 0) {
      double count = (double) latch.getCount();
      showProgress((numFiles - count) / numFiles);
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    
    long afterReads = System.currentTimeMillis();
    long total = afterReads - start;
    System.out.println("Files processed! (Took "+total+"ms)             "); // space needed to remove progress bar
    System.out.println("");

	System.out.println("---------------------------");
    processResult(keywords, "Raw keywords");
    long afterRaw = System.currentTimeMillis();
    long t2 = afterRaw - afterReads;
    System.out.println("Raw keywords complete! (Took "+t2+"ms)");
	System.out.println("---------------------------");
    
    processResult(cleanedKeywords, "No Stop Words");
    long afterStop = System.currentTimeMillis();
    long t3 = afterStop - afterRaw;
    System.out.println("No Stop Words complete! (Took "+t3+"ms)");
	System.out.println("---------------------------");
	  
    processResult(stemmedKeywords, "Stemmed Keywords");
    long afterStem = System.currentTimeMillis();
    long t4 = afterStem - afterStop;
    System.out.println("Stemmed Keywords complete! (Took "+t4+"ms)");
	System.out.println("---------------------------");

    total = System.currentTimeMillis() - start;
    System.out.println("Program complete! (Took "+total+"ms)");
    System.out.println("");
  }
  
  /**
   * takes in the list of words and processes a top 10, bottom 10, and 'meaningful' result from it and pretty prints the findings
   * @param words 
   * @param listDesc
   */
  public static void processResult(ConcurrentHashMap<String, CopyOnWriteArrayList<String>> words, String listDesc) {
	  ArrayList<String> top10 = new ArrayList<String>();
	  ArrayList<String> bottom10 = new ArrayList<String>();

	  
	  TreeMap<Integer, ArrayList<String>> rankedWords = new TreeMap<>();

	  System.out.println("Beginning analysis for " + listDesc + "...");
	  for(Entry<String, CopyOnWriteArrayList<String>> entry : words.entrySet()) {
		  Integer n = entry.getValue().size();
		  if(rankedWords.containsKey(n)) {
			  rankedWords.get(n).add(entry.getKey());
			  // Collections.sort(rankedWords.get(n)); // sort all words in that number entry alphabetically
		  } else {
			  ArrayList<String> a = new ArrayList<>();
			  a.add(entry.getKey());
			  rankedWords.put(n, a);
		  }
	  }
	  // compile top 10 words
	  Iterator<Integer> iterator = rankedWords.descendingKeySet().iterator();
	  for (int i = 0; iterator.hasNext() && i < 10; i++) {
		  if(top10.size() >= 10) {
			  break;
		  }
		  ArrayList<String> topWords = rankedWords.get(iterator.next());
		  for(String w: topWords) {
			  if(top10.size() < 10) {
				  top10.add(w);  
			  } else {
				  break;
			  }
		  }
	  }
	  
	  // compile bottom 10 words
	  Iterator<Integer> iter = rankedWords.keySet().iterator();
	  for (int i = 0; iter.hasNext() && i < 10;  i++) {
		  if(bottom10.size() >= 10) {
			  break;
		  }
		  ArrayList<String> bottomWords = rankedWords.get(iter.next());
		  for(String w: bottomWords) {
			  if(bottom10.size() < 10) {
				  bottom10.add(w);
			  } else {
				  break;
			  }
		  }
	  }
	  Collections.sort(top10);
	  Collections.sort(bottom10);
	  
	  // compile meaningful words based off if their length > 5
	  // since meaningful is relative at this point
	  HashMap<String, Boolean> topMeaningful = new HashMap<>();
	  for(String w : top10) {
		  topMeaningful.put(w, w.length()>5);
	  }

	  System.out.println("Unique keywords: " + words.size());
	  System.out.println("Top 10 words: " + String.join(", ", top10));
	  System.out.println("Top 10 meaningful words (meaningful = len>5): " + topMeaningful.toString());
	  System.out.println("Bottom 10 words: " + String.join(", ", bottom10));
	  
  }
  
  
  public static void showProgress(double percent) {
    int len = 30;
    
    String progress = "";
    for(int i = 0; i < (int)(percent * len); i++) {
      progress += "#";
    }
    
    String space = "";
    for(int i = 0; i < len - (int)(percent * len); i++) {
      space += " ";
    }
    System.out.printf("|"+progress+space+"| %5.1f %%\r", percent * 100);
  }
  
  public static class FileReaderRunner implements Runnable {
    
    private File file;
    private String fileName;
    private CountDownLatch latch;
    
    public FileReaderRunner(File f, CountDownLatch latch) {
      this.file = f;
      this.latch = latch;
      this.fileName = file.getName();
    }
    
    private String readFile() {
      try {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return "";
      }
    }

    @Override
    public void run() {
      String content = readFile();
      content = content.replaceAll("</?.*?>", "").replaceAll("[`'\\.!?\\-,]", "").replaceAll("\\s+", " ").toLowerCase();
      String[] rawTokens = content.split(" ");
      
      String stemmedToken = "";
      for(String token : rawTokens) {
    	// raw processing
        if(!keywords.containsKey(token)) {
          CopyOnWriteArrayList<String> arr = new CopyOnWriteArrayList<String>();
          arr.add(fileName);
          keywords.put(token, arr);
        } else {
          keywords.get(token).add(fileName);
        }

        // stop word processing
        if(stopWords.indexOf(token) == -1) {
  		  if(!cleanedKeywords.containsKey(token)) {
  			  CopyOnWriteArrayList<String> a = new CopyOnWriteArrayList<String>();
  			  a.add(fileName);
  			  cleanedKeywords.put(token, a);
  		  } else {
  			  cleanedKeywords.get(token).add(fileName);
  		  }
  		  
  		  // stem + stop word
  		  stemmedToken = porter.stripAffixes(token);
  		  if(!stemmedKeywords.containsKey(stemmedToken)) {
			CopyOnWriteArrayList<String> a = new CopyOnWriteArrayList<String>();
			a.add(fileName);
			stemmedKeywords.put(stemmedToken, a);
		  } else {
			stemmedKeywords.get(stemmedToken).add(fileName);
		  }
	  	}
      }
      latch.countDown();
    }
    
  }
}